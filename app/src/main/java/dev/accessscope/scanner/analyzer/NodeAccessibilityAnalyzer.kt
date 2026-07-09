/**
 * Analizzatore di accessibilità basato sull'albero [AccessibilityNodeInfo].
 *
 * Percorre la gerarchia delle view di una schermata Android, costruisce snapshot
 * normalizzati ([NodeSnapshot]) e applica controlli WCAG per etichette, touch target,
 * struttura semantica, form, contrasto colore, media/WebView e simulazione TalkBack.
 *
 * L'ambito dei controlli è filtrato da [ScanScope]; le eccezioni e i falsi positivi
 * sono gestiti tramite [PrecisionRules] e regole specifiche per package.
 */
package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles

/**
 * Motore principale di analisi dell'accessibilità su un singolo albero di nodi.
 *
 * Riceve soglie pixel derivate dalla densità dello schermo e un [ScanScope] che
 * limita quali aree di controllo ([ViolationArea]) vengono eseguite.
 *
 * @param minTouchTargetPx Dimensione minima in pixel per target di tocco (tipicamente 48 dp).
 * @param minTouchSpacingPx Spaziatura minima in pixel tra target adiacenti (tipicamente 8 dp).
 * @param minTextHeightPx Altezza minima in pixel del testo (tipicamente 12 sp).
 * @param recommendedTextHeightPx Altezza consigliata in pixel per testo interattivo (tipicamente 16 sp).
 * @param density Densità del display (`DisplayMetrics.density`) usata per calcoli WCAG e padding.
 * @param dynamicContentSilent Se true, segnala contenuti dinamici non annunciati a TalkBack.
 * @param scanScope Ambito di scansione che abilita/disabilita gruppi di controlli.
 */
class NodeAccessibilityAnalyzer(
    private val minTouchTargetPx: Int,
    private val minTouchSpacingPx: Int,
    private val minTextHeightPx: Int,
    private val recommendedTextHeightPx: Int,
    private val density: Float,
    private val dynamicContentSilent: Boolean = false,
    private val scanScope: ScanScope = ScanScope.FULL,
) {

    /**
     * Verifica se l'area di violazione indicata rientra nell'ambito di scansione corrente.
     *
     * @param area Area di controllo da valutare.
     * @return `true` se l'area è inclusa in [scanScope], altrimenti `false`.
     */
    private fun includes(area: ViolationArea): Boolean = scanScope.includes(area)

    /** Impronta della schermata corrente, propagata alle violazioni generate durante [analyzeTree]. */
    private var analyzeFingerprint: String? = null

    /**
     * Analizza l'intero albero di accessibilità e restituisce violazioni, finding TalkBack e riepiloghi.
     *
     * Flusso: raccolta snapshot → controlli per nodo → controlli cross-nodo e strutturali →
     * eventuale simulazione screen reader → aggregazione risultati.
     *
     * @param root Nodo radice dell'albero (tipicamente la root window attiva).
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo descrittivo della schermata (usato nei report).
     * @param screenshot Bitmap opzionale dello schermo, necessaria per i controlli di contrasto.
     * @param screenFingerprint Identificativo opzionale della schermata per correlare le violazioni.
     * @return [AnalysisResult] con violazioni, finding del simulatore TalkBack e riepiloghi per area.
     */
    fun analyzeTree(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
        screenshot: Bitmap? = null,
        screenFingerprint: String? = null,
    ): AnalysisResult {
        analyzeFingerprint = screenFingerprint
        val violations = mutableListOf<AccessibilityViolation>()
        val checkCollector = CheckCollector()
        val snapshots = mutableListOf<NodeSnapshot>()
        var traversalIndex = 0
        collectSnapshots(root, snapshots, ArrayDeque(), { traversalIndex++ })
        val customActionEmitted = mutableSetOf<String>()
        val viewport = PrecisionRules.estimateViewport(snapshots)
        val screenWidth = viewport.width()

        snapshots.forEach { snap ->
            if (!snap.isAccessibilityExcluded) {
                checkSingleNode(
                    snap, snapshots, packageName, screenTitle,
                    violations, screenshot, customActionEmitted,
                    viewport, screenWidth, checkCollector,
                )
            }
        }

        if (includes(ViolationArea.LABELS) || includes(ViolationArea.TOUCH)) {
            checkCrossNodeIssues(snapshots, packageName, screenTitle, violations, screenWidth)
        }
        if (includes(ViolationArea.STRUCTURE)) {
            checkModalTitle(root, packageName, screenTitle, violations)
            checkCollectionStructure(root, packageName, screenTitle, violations)
            checkTables(snapshots, packageName, screenTitle, violations)
            checkDuplicateViewIds(snapshots, packageName, screenTitle, violations)
            violations += FocusOrderAnalyzer.analyze(snapshots, packageName, screenTitle)
            violations += FocusOrderAnalyzer.analyzeHeadingLevels(snapshots, packageName, screenTitle)
        }
        if (includes(ViolationArea.LABELS)) {
            checkDuplicateLinks(snapshots, packageName, screenTitle, violations)
        }
        if (includes(ViolationArea.MEDIA_WEB)) {
            checkWebViews(snapshots, packageName, screenTitle, violations)
        }

        if (dynamicContentSilent && includes(ViolationArea.SCREEN_READER) &&
            !PrecisionRules.shouldSkipSilentDynamicContent(screenTitle, snapshots, packageName)
        ) {
            violations += AccessibilityViolation(
                type = ViolationType.DYNAMIC_CONTENT_SILENT,
                viewClassName = "Schermata",
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Il contenuto è cambiato più volte senza annunci TalkBack.",
                confidence = 0.85f,
            )
        }

        val screenReaderFindings = if (includes(ViolationArea.SCREEN_READER)) {
            TalkBackSimulator().simulate(root, packageName, screenTitle)
        } else {
            emptyList()
        }
        return AnalysisResult(
            violations,
            screenReaderFindings,
            checkCollector.buildSummaries(),
        )
    }

    /**
     * Attraversa ricorsivamente l'albero e popola la lista di [NodeSnapshot].
     *
     * Mantiene uno stack di heading strutturali per associare ogni nodo alla sezione corrente.
     * I nodi figlio vengono riciclati dopo la visita per evitare leak di [AccessibilityNodeInfo].
     *
     * @param node Nodo corrente in visita depth-first.
     * @param output Lista mutabile in cui appendere gli snapshot creati.
     * @param headingStack Stack delle intestazioni di sezione attive lungo il percorso radice→nodo.
     * @param nextIndex Fornitore dell'indice di traversata monotono crescente.
     */
    private fun collectSnapshots(
        node: AccessibilityNodeInfo,
        output: MutableList<NodeSnapshot>,
        headingStack: ArrayDeque<String>,
        nextIndex: () -> Int,
    ) {
        val sectionTitle = headingStack.lastOrNull()
        val index = nextIndex()
        val snap = node.toSnapshot(index, minTextHeightPx, minTouchTargetPx, sectionTitle)
        var pushedHeading: String? = null

        snap?.let { snapshot ->
            output.add(snapshot)
            val headingText = snapshot.text?.trim()?.takeIf { it.isNotBlank() }
            if (headingText != null && (snapshot.isHeading || snapshot.looksLikeStructuralHeading())) {
                headingStack.addLast(headingText)
                pushedHeading = headingText
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSnapshots(child, output, headingStack, nextIndex)
            child.recycle()
        }

        if (pushedHeading != null) {
            headingStack.removeLast()
        }
    }

    /**
     * Esegue tutti i controlli per-nodo applicabili a un singolo [NodeSnapshot].
     *
     * Applica filtri di precisione ([PrecisionRules]) prima di valutare etichette, touch,
     * focusabilità, form, heading, testo, immagini, scroll, stati UI, ruoli, slider,
     * tooltip, azioni custom, media e contrasto colore.
     *
     * @param snap Snapshot del nodo da analizzare.
     * @param all Lista completa degli snapshot della schermata (contesto cross-nodo locale).
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo descrittivo della schermata.
     * @param violations Lista mutabile a cui appendere le violazioni rilevate.
     * @param screenshot Bitmap opzionale per misurazioni di contrasto.
     * @param customActionEmitted Set di chiavi già emesse per evitare duplicati su azioni custom.
     * @param viewport Rettangoio stimato dell'area visibile utile.
     * @param screenWidth Larghezza del viewport in pixel.
     * @param checkCollector Raccoglitore dei pass positivi per i riepiloghi per area.
     */
    private fun checkSingleNode(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        screenshot: Bitmap?,
        customActionEmitted: MutableSet<String>,
        viewport: Rect,
        screenWidth: Int,
        checkCollector: CheckCollector,
    ) {
        if (PrecisionRules.shouldSkipDrawerNode(snap)) return
        if (PrecisionRules.shouldSkipPinPadWhenNotPinScreen(snap, screenTitle, packageName)) return
        if (PrecisionRules.shouldSkipHomeWidgetAnalysis(snap, all, packageName)) return
        if (PrecisionRules.shouldSkipStructuralNoise(snap, viewport, screenWidth, packageName)) return
        if (PrecisionRules.shouldSkipPlatformNoiseAnalysis(snap, all, packageName)) return
        val inMaterialCalendar = PrecisionRules.isMaterialCalendarRelatedNode(snap, screenTitle, all)

        if (includes(ViolationArea.LABELS)) {
            val missingLabel = !inMaterialCalendar &&
                (snap.isInteractiveClickable() || PrecisionRules.shouldReportMissingTopBarLabel(snap, all)) &&
                !snap.hasAccessibleName() &&
                !PrecisionRules.isIconInsideLabeledButton(snap, all) &&
                !PrecisionRules.shouldSkipContainerLabelCheck(snap, all, packageName)
            if (missingLabel) {
                violations += v(ViolationType.MISSING_LABEL, snap, packageName, screenTitle,
                    "Nessuna etichetta (testo, descrizione o hint).", 0.95f)
            } else if (snap.isInteractiveClickable() && snap.hasAccessibleName()) {
                checkCollector.recordPass(
                    ViolationArea.LABELS, screenTitle, packageName,
                    "Etichetta accessibile presente", snap, ViolationType.MISSING_LABEL.wcagRef,
                )
            } else if (PrecisionRules.shouldReportMissingTopBarLabel(snap, all) && snap.hasAccessibleName()) {
                checkCollector.recordPass(
                    ViolationArea.LABELS, screenTitle, packageName,
                    "Icona toolbar con descrizione", snap, ViolationType.MISSING_LABEL.wcagRef,
                )
            }
        }
        if (snap.isInteractiveClickable() && includes(ViolationArea.TOUCH)) {
            if (!inMaterialCalendar && !PrecisionRules.shouldSkipTouchTargetCheck(snap, all, packageName)) {
                if (snap.bounds.width() < minTouchTargetPx || snap.bounds.height() < minTouchTargetPx) {
                    violations += v(
                        ViolationType.SMALL_TOUCH_TARGET, snap, packageName, screenTitle,
                        "Misura ${snap.bounds.width()}×${snap.bounds.height()} px, minimo ${minTouchTargetPx} px.",
                        0.92f,
                        measuredValue = "${snap.bounds.width()}×${snap.bounds.height()} px",
                        requiredValue = "≥ ${minTouchTargetPx}×${minTouchTargetPx} px",
                    )
                } else {
                    checkCollector.recordPass(
                        ViolationArea.TOUCH, screenTitle, packageName,
                        "Target di tocco sufficiente",
                        snap, ViolationType.SMALL_TOUCH_TARGET.wcagRef,
                        "${snap.bounds.width()}×${snap.bounds.height()} px",
                    )
                }
            }
        }

        if (!inMaterialCalendar &&
            includes(ViolationArea.SCREEN_READER) &&
            snap.shouldBeFocusable() &&
            !snap.isFocusable &&
            !snap.isScrollable
        ) {
            if (PrecisionRules.hasFocusableOrEditableDescendant(snap, all)) return
            if (snap.isEditable && snap.hasAccessibleName()) return
            violations += v(ViolationType.NOT_FOCUSABLE, snap, packageName, screenTitle,
                "Interattivo ma non raggiungibile con TalkBack.", 0.9f)
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && !snap.hasInputLabel()) {
            violations += v(ViolationType.INPUT_LABEL, snap, packageName, screenTitle,
                "Campo senza etichetta associata.", 0.95f)
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && snap.errorText.isNullOrBlank() &&
            snap.text?.contains("error", true) == true
        ) {
            violations += v(ViolationType.INPUT_ERROR_MISSING, snap, packageName, screenTitle,
                "Errore visivo probabile senza messaggio accessibile.", 0.7f)
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && snap.isEnabled &&
            !PrecisionRules.isRequiredFieldHint(snap.hintText, snap.text, snap.contentDescription) &&
            snap.hintText?.contains('*') != true &&
            snap.className.contains("required", true)
        ) {
            violations += v(ViolationType.REQUIRED_FIELD_UNMARKED, snap, packageName, screenTitle,
                "Campo probabilmente obbligatorio non marcato.", 0.65f)
        }

        if (includes(ViolationArea.STRUCTURE) &&
            snap.looksLikeStructuralHeading() &&
            !snap.isHeading &&
            !PrecisionRules.shouldSkipHeadingCheck(snap) &&
            !PrecisionRules.isInsideCarouselOrListItem(snap, all, packageName) &&
            !snap.className.contains("Toolbar", true) &&
            snap.bounds.height() >= (minTextHeightPx * 1.5).toInt() &&
            (snap.text?.length ?: 0) in 4..60
        ) {
            violations += v(ViolationType.HEADING_HIERARCHY, snap, packageName, screenTitle,
                "Titolo visibile non marcato come heading.", 0.85f)
        }

        if (includes(ViolationArea.TEXT) && snap.hasVisibleText() &&
            !PrecisionRules.shouldSkipSmallTextCheck(snap, viewport, packageName) &&
            !PrecisionRules.isOffScreenOrMarginalNode(snap, viewport, packageName)
        ) {
            if (PrecisionRules.isAnomalousTouchBounds(snap)) return
            if (snap.bounds.height() < minTextHeightPx) {
                violations += v(ViolationType.TEXT_TOO_SMALL, snap, packageName, screenTitle,
                    "Altezza ~${snap.bounds.height()} px (< ${minTextHeightPx} px, circa 12sp).", 0.88f)
            } else if (snap.isInteractiveClickable() && snap.bounds.height() < recommendedTextHeightPx) {
                violations += v(ViolationType.TEXT_TOO_SMALL, snap, packageName, screenTitle,
                    "Testo cliccabile piccolo: ${snap.bounds.height()} px (consigliato ≥ $recommendedTextHeightPx px).", 0.75f)
            }
        }

        val textValue = snap.text.orEmpty()
        if (includes(ViolationArea.TEXT) &&
            (textValue.endsWith("…") || textValue.endsWith("...")) &&
            snap.contentDescription.isNullOrBlank()
        ) {
            violations += v(ViolationType.TEXT_TRUNCATED, snap, packageName, screenTitle,
                "Testo troncato senza descrizione completa.", 0.9f)
        }

        if (includes(ViolationArea.LABELS)) {
            when {
                snap.isImageWithoutAlt() && !PrecisionRules.isDecorative(snap) &&
                    !PrecisionRules.isIconInsideLabeledButton(snap, all) -> {
                    violations += v(ViolationType.IMAGE_MISSING_ALT, snap, packageName, screenTitle,
                        "Immagine senza testo alternativo.", 0.95f)
                }
                PrecisionRules.isDecorative(snap) && snap.hasAccessibleName() &&
                    !snap.contentDescription.isNullOrBlank() &&
                    !PrecisionRules.shouldSkipDecorativeLabeledCheck(snap, all) -> {
                    violations += v(ViolationType.DECORATIVE_IMAGE_LABELED, snap, packageName, screenTitle,
                        "Immagine decorativa con etichetta superflua.", 0.8f)
                }
            }

            snap.contentDescription?.let { cd ->
                if (snap.isImageClass() && PrecisionRules.isPoorAltText(cd)) {
                    violations += v(ViolationType.POOR_ALT_TEXT, snap, packageName, screenTitle,
                        "Descrizione generica: \"$cd\".", 0.85f)
                }
            }

            if (snap.isLikelyLink() && snap.hasAccessibleName() && isNonDescriptiveLink(snap.accessibleName()!!)) {
                violations += v(ViolationType.LINK_NOT_DESCRIPTIVE, snap, packageName, screenTitle,
                    "Link generico: \"${snap.accessibleName()}\".", 0.9f)
            }
        }

        if (includes(ViolationArea.STRUCTURE) && snap.isScrollable && !snap.hasAccessibleName()) {
            val screenArea = screenshot?.let { it.width * it.height } ?: estimateScreenArea(all)
            if (!PrecisionRules.shouldSkipScrollWithoutLabel(snap, all, screenArea, packageName)) {
                violations += v(ViolationType.SCROLLABLE_WITHOUT_LABEL, snap, packageName, screenTitle,
                    "Area scrollabile senza nome.", 0.88f)
            }
        }

        if (includes(ViolationArea.SCREEN_READER) && !snap.isEnabled && snap.isInteractiveClickable() &&
            snap.stateDescription.isNullOrBlank() &&
            !PrecisionRules.shouldSkipCarouselListItemAnalysis(snap, all, packageName)
        ) {
            violations += v(ViolationType.DISABLED_WITHOUT_INDICATION, snap, packageName, screenTitle,
                "Controllo disabilitato senza stato esposto.", 0.82f)
        }

        if (includes(ViolationArea.SCREEN_READER) && snap.className.contains("Expandable", true) && snap.isExpanded == null) {
            violations += v(ViolationType.EXPANDABLE_STATE_MISSING, snap, packageName, screenTitle,
                "Espandibile senza stato aperto/chiuso.", 0.8f)
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && !snap.isPassword && snap.className.contains("password", true)) {
            violations += v(ViolationType.PASSWORD_NOT_MASKED, snap, packageName, screenTitle,
                "Campo password non marcato isPassword.", 0.95f)
        }

        if (includes(ViolationArea.LABELS) && snap.isClickable && snap.isCustomView() &&
            !snap.hasAccessibleName() && !snap.hasStandardRole() &&
            !inMaterialCalendar &&
            !PrecisionRules.shouldSkipContainerLabelCheck(snap, all, packageName) &&
            !PrecisionRules.isCarouselContentContainer(snap, all, packageName) &&
            !PrecisionRules.shouldSkipHomeWidgetAnalysis(snap, all, packageName) &&
            !(PrecisionRules.isCtaContainer(snap, packageName) && PrecisionRules.hasTvCustomDescendant(snap, all))
        ) {
            violations += v(ViolationType.ROLE_UNDEFINED, snap, packageName, screenTitle,
                "View custom cliccabile senza ruolo semantico.", 0.85f)
        }

        if (includes(ViolationArea.FORMS) && snap.rangeMin != null && snap.rangeMax != null &&
            (snap.rangeCurrent == null || snap.className.contains("SeekBar", true) || snap.className.contains("Slider", true))
        ) {
            val hasValue = snap.stateDescription?.isNotBlank() == true || snap.contentDescription?.contains("%") == true
            if (!hasValue && snap.rangeCurrent == snap.rangeMin) {
                violations += v(ViolationType.SLIDER_VALUE_MISSING, snap, packageName, screenTitle,
                    "Slider/progresso senza valore annunciato.", 0.78f)
            }
        }

        if (includes(ViolationArea.LABELS) && !snap.tooltipText.isNullOrBlank() && snap.contentDescription.isNullOrBlank() && !snap.isFocusable) {
            violations += v(ViolationType.TOOLTIP_INACCESSIBLE, snap, packageName, screenTitle,
                "Tooltip \"${snap.tooltipText}\" non accessibile a TalkBack.", 0.8f)
        }

        if (!inMaterialCalendar &&
            includes(ViolationArea.SCREEN_READER) &&
            PrecisionRules.shouldReportCustomAction(snap, all, packageName)
        ) {
            val actionKey = snap.viewId?.takeIf { it.isNotBlank() }
                ?: "${snap.className}@${snap.bounds.hashCode()}"
            if (customActionEmitted.add(actionKey)) {
                violations += v(ViolationType.CUSTOM_ACTION_UNLABELED, snap, packageName, screenTitle,
                    "${snap.unlabeledActionCount} azione/i personalizzata/e senza etichetta.", 0.88f)
            }
        }

        if (includes(ViolationArea.MEDIA_WEB) && snap.isMediaControl() && !snap.hasAccessibleName()) {
            violations += v(ViolationType.MEDIA_CONTROL_UNLABELED, snap, packageName, screenTitle,
                "Controllo media senza etichetta.", 0.92f)
        }

        if (includes(ViolationArea.COLOR)) {
            screenshot?.let { checkContrast(snap, packageName, screenTitle, violations, it, all, viewport, checkCollector) }
        }
    }

    /**
     * Valuta il contrasto colore di testo o elementi UI non testuali usando lo screenshot.
     *
     * Per le etichette micro dei campi (`txt_data_*`) espande i bounds di campionamento
     * e applica soglie di confidenza ridotte. Registra pass positivi tramite [checkCollector]
     * quando il contrasto è sufficiente.
     *
     * @param snap Snapshot del nodo da misurare.
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo descrittivo della schermata.
     * @param violations Lista mutabile a cui appendere violazioni di contrasto insufficiente.
     * @param bitmap Screenshot della schermata per il campionamento pixel.
     * @param all Lista completa degli snapshot (contesto per regole di esclusione).
     * @param viewport Rettangoio stimato dell'area visibile utile.
     * @param checkCollector Raccoglitore dei pass positivi per i riepiloghi area colore.
     */
    private fun checkContrast(
        snap: NodeSnapshot,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        bitmap: Bitmap,
        all: List<NodeSnapshot>,
        viewport: Rect,
        checkCollector: CheckCollector,
    ) {
        if (PrecisionRules.isLayoutContainer(snap.className)) return
        if (PrecisionRules.isLikelyStatusBadge(snap)) return
        if (PrecisionRules.isHomeChartDecorativeText(snap, all, packageName)) return
        if (PrecisionRules.isBrandedOrPrimaryCtaText(snap, all, packageName)) return
        if (PrecisionRules.shouldSkipContrastCheck(snap, all, packageName)) return
        if (PrecisionRules.shouldSkipTopBarIconContrast(snap, all, viewport)) return
        val screenArea = bitmap.width * bitmap.height
        if (screenArea > 0 && snap.area() > screenArea * 0.6) return

        val largeTextIds = AppPrecisionProfiles.largeTextViewIds(packageName)
        val hintOnly = snap.isEditable && snap.text.isNullOrBlank() && !snap.hintText.isNullOrBlank()

        if (snap.hasVisibleText() || hintOnly) {
            val isFieldLabel = PrecisionRules.isKnownContrastFieldLabel(snap, packageName)
            val sampleBounds = if (isFieldLabel) expandBoundsForMicroLabel(snap.bounds) else snap.bounds
            val large = WcagContrast.isLargeText(snap, density, largeTextIds) || isFieldLabel
            val result = if (PrecisionRules.isButtonLikeTapTarget(snap) && !hintOnly && !isFieldLabel) {
                WcagContrast.measureTextContrastWithInnerBackground(bitmap, sampleBounds, large)
            } else {
                WcagContrast.measureTextContrast(bitmap, sampleBounds, large)
            } ?: return
            if (!WcagContrast.isReliableMeasurement(result)) return
            val baseMinConfidence = when {
                PrecisionRules.viewIdShort(snap).startsWith("txt_data_") -> 0.45f
                isFieldLabel -> 0.50f
                hintOnly -> 0.55f
                else -> 0.72f
            }
            val minConfidence = WcagContrast.minConfidenceForMeasurement(
                result = result,
                boundsWidthPx = snap.bounds.width(),
                boundsHeightPx = snap.bounds.height(),
                density = density,
                isSmallIcon = false,
                baseMin = baseMinConfidence,
            )
            if (result.confidence < minConfidence) return
            if (!isFieldLabel && !hintOnly &&
                WcagContrast.relativeLuminance(result.foreground) > 0.80 &&
                snap.bounds.height() <= (minTouchTargetPx * 0.85f).toInt()
            ) {
                return
            }
            val threshold = if (large || isFieldLabel) {
                WcagContrast.MIN_LARGE_TEXT_CONTRAST
            } else {
                WcagContrast.MIN_TEXT_CONTRAST
            }
            if (result.ratio < threshold) {
                violations += v(
                    ViolationType.LOW_COLOR_CONTRAST, snap, packageName, screenTitle,
                    "Contrasto ${"%.2f".format(result.ratio)}:1 (serve ≥ $threshold:1). " +
                        "${result.samplesUsed} campioni, confidenza ${(result.confidence * 100).toInt()}%.",
                    result.confidence,
                    measuredValue = "${"%.2f".format(result.ratio)}:1",
                    requiredValue = "≥ $threshold:1",
                )
            } else {
                checkCollector.recordPass(
                    ViolationArea.COLOR, screenTitle, packageName,
                    "Contrasto testo sufficiente",
                    snap, ViolationType.LOW_COLOR_CONTRAST.wcagRef,
                    "${"%.2f".format(result.ratio)}:1 (≥ $threshold:1)",
                )
            }
        } else if (snap.isInteractiveClickable() || snap.isImageClass()) {
            if (PrecisionRules.shouldSkipUiContrastCheck(snap, all, packageName)) return
            if (PrecisionRules.shouldSkipTopBarIconContrast(snap, all, viewport)) return
            val result = WcagContrast.measureUiContrast(bitmap, snap.bounds) ?: return
            if (!WcagContrast.isReliableMeasurement(result)) return
            val minConfidence = WcagContrast.minConfidenceForMeasurement(
                result = result,
                boundsWidthPx = snap.bounds.width(),
                boundsHeightPx = snap.bounds.height(),
                density = density,
                isSmallIcon = snap.isImageClass(),
                baseMin = 0.72f,
            )
            if (result.confidence < minConfidence) return
            if (result.ratio < WcagContrast.MIN_NON_TEXT_CONTRAST) {
                violations += v(
                    ViolationType.LOW_NON_TEXT_CONTRAST, snap, packageName, screenTitle,
                    "Contrasto UI ${"%.2f".format(result.ratio)}:1 (serve ≥ ${WcagContrast.MIN_NON_TEXT_CONTRAST}:1).",
                    result.confidence,
                    measuredValue = "${"%.2f".format(result.ratio)}:1",
                    requiredValue = "≥ ${WcagContrast.MIN_NON_TEXT_CONTRAST}:1",
                )
            } else {
                checkCollector.recordPass(
                    ViolationArea.COLOR, screenTitle, packageName,
                    "Contrasto icona/controllo sufficiente",
                    snap, ViolationType.LOW_NON_TEXT_CONTRAST.wcagRef,
                    "${"%.2f".format(result.ratio)}:1",
                )
            }
        }
    }

    /**
     * Esegue controlli che coinvolgono più nodi: nomi duplicati, sovrapposizione touch e spaziatura.
     *
     * @param snapshots Lista completa degli snapshot della schermata.
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo descrittivo della schermata.
     * @param violations Lista mutabile a cui appendere le violazioni rilevate.
     * @param screenWidth Larghezza del viewport in pixel (per regole di esclusione overlap).
     */
    private fun checkCrossNodeIssues(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        screenWidth: Int,
    ) {
        val maxBottom = snapshots.maxOfOrNull { it.bounds.bottom } ?: 0
        val screenArea = if (screenWidth > 0 && maxBottom > 0) screenWidth * maxBottom else 0
        val isMaterialCalendar = PrecisionRules.isMaterialCalendarContext(screenTitle, snapshots)
        val clickables = snapshots
            .filter { PrecisionRules.isSemanticClickTarget(it) }
            .filterNot { PrecisionRules.isObscuredByModalOverlay(it, snapshots) }
            .filterNot { snap -> isMaterialCalendar && PrecisionRules.isMaterialCalendarDayCell(snap, screenTitle, snapshots) }

        snapshots.mapNotNull { snap -> snap.accessibleName()?.lowercase()?.let { it to snap } }
            .groupBy({ it.first }, { it.second })
            .forEach { (name, nodes) ->
                if (name.isBlank() || nodes.size < 2) return@forEach
                if (nodes.all { PrecisionRules.isInsideWebView(it, snapshots) }) return@forEach
                val distinctBounds = nodes.map { it.bounds }.distinctBy { "${it.left},${it.top},${it.right},${it.bottom}" }
                if (distinctBounds.size < nodes.size) {
                    nodes.forEach { snap ->
                        violations += v(ViolationType.DUPLICATE_ACCESSIBLE_NAME, snap, packageName, screenTitle,
                            "Nome \"$name\" duplicato su elementi distinti.", 0.9f)
                    }
                }
            }

        for (i in clickables.indices) {
            for (j in i + 1 until clickables.size) {
                val a = clickables[i]
                val b = clickables[j]
                if (PrecisionRules.shouldSkipDrawerNode(a) || PrecisionRules.shouldSkipDrawerNode(b)) continue
                if (PrecisionRules.shouldSkipOverlapBetween(a, b, snapshots, packageName, screenWidth)) continue
                if (PrecisionRules.shouldSkipPinPadWhenNotPinScreen(a, screenTitle, packageName) ||
                    PrecisionRules.shouldSkipPinPadWhenNotPinScreen(b, screenTitle, packageName)
                ) {
                    continue
                }
                if (a.bounds.contains(b.bounds) || b.bounds.contains(a.bounds)) continue
                if (Rect.intersects(a.bounds, b.bounds)) {
                    val overlap = overlapArea(a.bounds, b.bounds)
                    val minArea = minOf(a.area(), b.area())
                    if (overlap > minArea * 0.45) {
                        violations += v(ViolationType.OVERLAPPING_TOUCH_TARGETS, a, packageName, screenTitle,
                            "Sovrapposizione ${overlap}px² con ${b.className}.", 0.88f)
                    }
                } else {
                    val distance = edgeDistance(a.bounds, b.bounds)
                    if (distance in 1 until minTouchSpacingPx &&
                        !PrecisionRules.shouldSkipTouchSpacingBetween(a, b, snapshots, screenArea)
                    ) {
                        violations += v(ViolationType.INSUFFICIENT_TOUCH_SPACING, a, packageName, screenTitle,
                            "Solo ${distance}px da un altro pulsante.", 0.85f)
                    }
                }
            }
        }

        snapshots.forEach { parent ->
            if (!parent.hasAccessibleName() || parent.isInteractiveClickable()) return@forEach
            snapshots.forEach { child ->
                if (parent == child || !parent.bounds.contains(child.bounds)) return@forEach
                if (parent.accessibleName() == child.accessibleName() && child.hasAccessibleName() && !child.isInteractiveClickable()) {
                    violations += v(ViolationType.REDUNDANT_ACCESSIBLE_NAME, child, packageName, screenTitle,
                        "Nome ripetuto dal contenitore.", 0.75f)
                }
            }
        }
    }

    /**
     * Segnala WebView di dimensioni significative senza figli accessibili esposti.
     *
     * @param snapshots Lista completa degli snapshot della schermata.
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo descrittivo della schermata.
     * @param violations Lista mutabile a cui appendere le violazioni rilevate.
     */
    private fun checkWebViews(snapshots: List<NodeSnapshot>, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        snapshots.filter { PrecisionRules.shouldReportWebViewBarrier(it, snapshots) }.forEach { snap ->
            violations += v(ViolationType.WEBVIEW_BARRIER, snap, packageName, screenTitle,
                "WebView senza contenuto accessibile esposto (${snap.bounds.width()}×${snap.bounds.height()} px).", 0.9f)
        }
    }

    /**
     * Verifica che griglie/tabulari con almeno 6 celle espongano intestazioni di riga/colonna.
     *
     * @param snapshots Lista completa degli snapshot della schermata.
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo descrittivo della schermata.
     * @param violations Lista mutabile a cui appendere le violazioni rilevate.
     */
    private fun checkTables(snapshots: List<NodeSnapshot>, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        val gridItems = snapshots.filter { it.collectionRow >= 0 && it.collectionColumn >= 0 }
        if (gridItems.size < 6) return
        val rows = gridItems.map { it.collectionRow }.distinct().size
        val cols = gridItems.map { it.collectionColumn }.distinct().size
        if (rows < 2 || cols < 2) return
        val hasHeader = gridItems.any { it.isHeading || it.collectionRow == 0 || it.collectionColumn == 0 }
        if (!hasHeader) {
            violations += AccessibilityViolation(
                type = ViolationType.TABLE_HEADER_MISSING,
                viewClassName = "Collection/Grid",
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Griglia ${rows}×${cols} senza intestazioni marcate.",
                confidence = 0.8f,
            )
        }
    }

    /**
     * Segnala [viewId] condivisi da più nodi quando non corrispondono a un template di lista legittimo.
     *
     * @param snapshots Lista completa degli snapshot della schermata.
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo descrittivo della schermata.
     * @param violations Lista mutabile a cui appendere le violazioni rilevate.
     */
    private fun checkDuplicateViewIds(snapshots: List<NodeSnapshot>, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        val screenArea = snapshots.maxOfOrNull { it.bounds.right * it.bounds.bottom } ?: 0
        snapshots.mapNotNull { snap -> snap.viewId?.let { it to snap } }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
            .forEach { (id, nodes) ->
                if (isListItemTemplate(id, nodes, packageName)) return@forEach
                if (screenArea > 0 && nodes.all { PrecisionRules.isInsideDenseScrollGrid(it, snapshots, screenArea) }) {
                    return@forEach
                }
                val representative = nodes.minByOrNull { it.traversalIndex } ?: return@forEach
                violations += v(
                    ViolationType.DUPLICATE_VIEW_ID,
                    representative,
                    packageName,
                    screenTitle,
                    "ID $id condiviso da ${nodes.size} elementi (anomalo, non template lista).",
                    0.95f,
                )
            }
    }

    /**
     * Determina se nodi con lo stesso [viewId] sono istanze legittime di un template di lista.
     *
     * Criteri: ID noto come template, stessa classe, dimensioni simili e posizioni verticali distinte.
     *
     * @param viewId Identificatore risorsa condiviso tra i nodi.
     * @param nodes Nodi che condividono lo stesso [viewId].
     * @param packageName Package dell'app in analisi.
     * @return `true` se il pattern è coerente con item di lista riciclati, altrimenti `false`.
     */
    private fun isListItemTemplate(viewId: String, nodes: List<NodeSnapshot>, packageName: String): Boolean {
        if (nodes.size < 2) return false
        // Material DatePicker: le celle giorno condividono `material_calendar_day` by design.
        if (viewId.substringAfterLast('/').equals("material_calendar_day", ignoreCase = true) && nodes.size >= 12) {
            return true
        }
        if (PrecisionRules.isKnownListTemplateId(viewId, packageName)) return true
        val sameClass = nodes.map { it.className }.distinct().size == 1
        if (!sameClass) return false
        val heights = nodes.map { it.bounds.height() }
        val avg = heights.average()
        if (heights.all { kotlin.math.abs(it - avg) <= avg * 0.15 + 2 }) return true
        val widths = nodes.map { it.bounds.width() }
        val widthAvg = widths.average()
        val widthSimilar = widths.all { kotlin.math.abs(it - widthAvg) <= widthAvg * 0.12 + 4 }
        val distinctTops = nodes.map { it.bounds.top }.distinct().size >= 2
        return widthSimilar && distinctTops
    }

    /**
     * Segnala link con testo identico ma destinazioni o posizioni probabilmente diverse.
     *
     * @param snapshots Lista completa degli snapshot della schermata.
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo descrittivo della schermata.
     * @param violations Lista mutabile a cui appendere le violazioni rilevate.
     */
    private fun checkDuplicateLinks(snapshots: List<NodeSnapshot>, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        snapshots.filter { it.isLikelyLink() && it.hasAccessibleName() }
            .groupBy { it.accessibleName()!!.lowercase() }
            .filter { it.value.size > 1 }
            .forEach { (text, links) ->
                val distinctIds = links.mapNotNull { it.viewId }.distinct()
                if (distinctIds.size > 1 || links.map { it.bounds }.distinct().size > 1) {
                    links.forEach { snap ->
                        violations += v(ViolationType.DUPLICATE_LINK_TEXT, snap, packageName, screenTitle,
                            "Link \"$text\" ripetuto con destinazioni probabilmente diverse.", 0.82f)
                    }
                }
            }
    }

    /**
     * Verifica che modali e dialog espongano un titolo accessibile.
     *
     * @param root Nodo radice dell'albero (usato per rilevare classi Dialog/BottomSheet).
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo corrente della schermata; generico o vuoto indica assenza di titolo.
     * @param violations Lista mutabile a cui appendere le violazioni rilevate.
     */
    private fun checkModalTitle(root: AccessibilityNodeInfo, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        val className = root.className?.toString().orEmpty()
        val isModal = listOf("Dialog", "BottomSheet", "Popup", "AlertDialog").any { className.contains(it, true) }
        if (isModal && (screenTitle == "Schermata" || screenTitle.isBlank())) {
            violations += AccessibilityViolation(
                type = ViolationType.MODAL_WITHOUT_TITLE,
                viewClassName = className,
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Modale senza titolo accessibile.",
                confidence = 0.9f,
            )
        }
    }

    /**
     * Verifica che liste/collection con molti figli espongano struttura (righe/colonne) via [AccessibilityNodeInfo.getCollectionInfo].
     *
     * @param root Nodo radice da cui avviare la visita breadth-first.
     * @param packageName Package dell'app in analisi.
     * @param screenTitle Titolo descrittivo della schermata.
     * @param violations Lista mutabile a cui appendere le violazioni rilevate.
     */
    private fun checkCollectionStructure(root: AccessibilityNodeInfo, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val collection = node.collectionInfo
            if (collection != null && node.childCount > 4) {
                val hasStructure = collection.rowCount > 0 || collection.columnCount > 0
                if (!hasStructure) {
                    violations += AccessibilityViolation(
                        type = ViolationType.COLLECTION_WITHOUT_STRUCTURE,
                        viewClassName = node.className?.toString() ?: "unknown",
                        screenTitle = screenTitle,
                        packageName = packageName,
                        details = "Lista con ${node.childCount} elementi senza struttura esposta.",
                        viewId = node.viewIdResourceName,
                        confidence = 0.85f,
                    )
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
    }

    /**
     * Calcola l'area in pixel² di intersezione tra due rettangoli.
     *
     * @param a Primo rettangolo.
     * @param b Secondo rettangolo.
     * @return Area di overlap in pixel², oppure `0` se i rettangoli non si intersecano.
     */
    private fun overlapArea(a: Rect, b: Rect): Int {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(b.bottom, b.bottom)
        return if (left < right && top < bottom) (right - left) * (bottom - top) else 0
    }

    /**
     * Calcola la distanza minima tra i bordi di due rettangoli non sovrapposti.
     *
     * @param a Primo rettangolo.
     * @param b Secondo rettangolo.
     * @return Distanza in pixel tra i bordi più vicini (`0` se i rettangoli si toccano o si sovrappongono).
     */
    private fun edgeDistance(a: Rect, b: Rect): Int {
        val dx = when {
            a.right < b.left -> b.left - a.right
            b.right < a.left -> a.left - b.right
            else -> 0
        }
        val dy = when {
            a.bottom < b.top -> b.top - a.bottom
            b.bottom < a.top -> a.top - b.bottom
            else -> 0
        }
        return maxOf(dx, dy)
    }

    /**
     * Verifica se il testo del link è generico e non descrittivo del destino.
     *
     * @param name Testo accessibile del link.
     * @return `true` se corrisponde a un pattern noto non descrittivo (es. "clicca qui", "altro").
     */
    private fun isNonDescriptiveLink(name: String): Boolean {
        val n = name.trim().lowercase()
        return NON_DESCRIPTIVE_LINKS.any { n == it || n.matches(Regex("^$it\\W*")) }
    }

    /**
     * Factory per costruire una [AccessibilityViolation] arricchita con metadati dallo snapshot.
     *
     * @param type Tipo di violazione WCAG rilevata.
     * @param snap Snapshot del nodo coinvolto.
     * @param pkg Package dell'app in analisi.
     * @param screen Titolo descrittivo della schermata.
     * @param details Messaggio descrittivo per l'utente o il report.
     * @param confidence Livello di confidenza della rilevazione (0–1).
     * @param measuredValue Valore misurato opzionale (es. rapporto di contrasto).
     * @param requiredValue Valore richiesto opzionale (es. soglia WCAG).
     * @return Istanza di [AccessibilityViolation] pronta per l'aggregazione nel report.
     */
    private fun v(
        type: ViolationType,
        snap: NodeSnapshot,
        pkg: String,
        screen: String,
        details: String,
        confidence: Float,
        measuredValue: String? = null,
        requiredValue: String? = null,
    ) = AccessibilityViolation(
        type = type,
        viewClassName = snap.className,
        screenTitle = screen,
        packageName = pkg,
        details = details,
        viewId = snap.viewId,
        bounds = snap.boundsLabel(),
        sectionTitle = snap.sectionTitle,
        confidence = confidence,
        screenFingerprint = analyzeFingerprint,
        elementLabel = snap.accessibleName()?.take(80) ?: snap.text?.trim()?.take(80),
        measuredValue = measuredValue,
        requiredValue = requiredValue,
        remediation = remediationFor(type),
    )

    /**
     * Restituisce un suggerimento di remediation testuale per il tipo di violazione indicato.
     *
     * @param type Tipo di violazione per cui generare il suggerimento.
     * @return Stringa con indicazioni pratiche di correzione in italiano.
     */
    private fun remediationFor(type: ViolationType): String = when (type) {
        ViolationType.LOW_COLOR_CONTRAST, ViolationType.LOW_NON_TEXT_CONTRAST ->
            "Aumenta il contrasto tra primo piano e sfondo (testo più scuro o sfondo più chiaro)."
        ViolationType.MISSING_LABEL, ViolationType.IMAGE_MISSING_ALT, ViolationType.CUSTOM_ACTION_UNLABELED ->
            "Aggiungi contentDescription o testo visibile che descriva l'azione o l'icona."
        ViolationType.SMALL_TOUCH_TARGET, ViolationType.INSUFFICIENT_TOUCH_SPACING ->
            "Ingrandisci l'area tappabile ad almeno 48×48 dp o aumenta lo spazio tra i controlli."
        ViolationType.TEXT_TOO_SMALL ->
            "Usa una dimensione testo ≥ 12sp (16sp consigliato per testo interattivo)."
        ViolationType.DYNAMIC_CONTENT_SILENT ->
            "Annuncia i cambi di contenuto con liveRegion o AccessibilityEvent TYPE_ANNOUNCEMENT."
        else -> "Correggi secondo ${type.wcagRef} e verifica con TalkBack."
    }

    /**
     * Calcola l'area in pixel² del rettangolo del nodo.
     *
     * @receiver Snapshot del nodo di cui calcolare l'area.
     * @return Prodotto larghezza × altezza dei [NodeSnapshot.bounds].
     */
    private fun NodeSnapshot.area() = bounds.width() * bounds.height()

    /**
     * Espande i bounds di un'etichetta micro per migliorare il campionamento del contrasto.
     *
     * Aggiunge un padding proporzionale alla densità (minimo 3 px) su tutti i lati.
     *
     * @param bounds Rettangolo originale dell'etichetta.
     * @return Nuovo [Rect] con padding applicato.
     */
    private fun expandBoundsForMicroLabel(bounds: Rect): Rect {
        val pad = (3 * density).toInt().coerceAtLeast(3)
        return Rect(
            bounds.left - pad,
            bounds.top - pad,
            bounds.right + pad,
            bounds.bottom + pad,
        )
    }

    /**
     * Stima l'area dello schermo quando lo screenshot non è disponibile.
     *
     * Usa l'estensione massima dei bounds tra tutti gli snapshot come proxy delle dimensioni.
     *
     * @param snapshots Lista completa degli snapshot della schermata.
     * @return Area stimata in pixel², oppure `0` se la lista è vuota.
     */
    private fun estimateScreenArea(snapshots: List<NodeSnapshot>): Int {
        if (snapshots.isEmpty()) return 0
        var maxRight = 0
        var maxBottom = 0
        snapshots.forEach { snap ->
            maxRight = maxOf(maxRight, snap.bounds.right)
            maxBottom = maxOf(maxBottom, snap.bounds.bottom)
        }
        return maxRight * maxBottom
    }

    /**
     * Risultato aggregato dell'analisi di un albero di accessibilità.
     *
     * @property violations Elenco delle violazioni WCAG rilevate sulla schermata.
     * @property screenReaderFindings Output del simulatore TalkBack (ordine di focus, annunci).
     * @property checkSummaries Riepiloghi per area di controllo con conteggi pass/fail.
     */
    data class AnalysisResult(
        val violations: List<AccessibilityViolation>,
        val screenReaderFindings: List<ScreenReaderFinding>,
        val checkSummaries: List<CheckAreaSummary> = emptyList(),
    )

    companion object {
        /** Testi di link generici (IT/EN) considerati non descrittivi del destino. */
        private val NON_DESCRIPTIVE_LINKS = setOf(
            "click here", "tap here", "here", "more", "read more", "learn more", "details", "link",
            "continue", "go", "ok", "submit", "clicca qui", "qui", "altro", "leggi", "leggi tutto",
            "scopri", "continua", "dettagli", "vai", "info", "apri", "tap",
        )

        /**
         * Crea un [NodeAccessibilityAnalyzer] con soglie pixel derivate dalla densità del display.
         *
         * @param density Densità del display (`DisplayMetrics.density`).
         * @param dynamicContentSilent Se true, abilita il controllo su contenuti dinamici non annunciati.
         * @param scanScope Ambito di scansione che limita le aree di controllo attive.
         * @return Istanza configurata con soglie 48 dp touch, 8 dp spacing, 12/16 sp testo.
         */
        fun create(
            density: Float,
            dynamicContentSilent: Boolean = false,
            scanScope: ScanScope = ScanScope.FULL,
        ) = NodeAccessibilityAnalyzer(
            minTouchTargetPx = (48 * density).toInt(),
            minTouchSpacingPx = (8 * density).toInt(),
            minTextHeightPx = (12 * density).toInt(),
            recommendedTextHeightPx = (16 * density).toInt(),
            density = density,
            dynamicContentSilent = dynamicContentSilent,
            scanScope = scanScope,
        )
    }
}
