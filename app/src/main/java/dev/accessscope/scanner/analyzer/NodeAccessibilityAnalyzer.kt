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
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import dev.accessscope.scanner.analyzer.node.NodeCrossNodeChecker
import dev.accessscope.scanner.analyzer.node.NodeSingleNodeChecker
import dev.accessscope.scanner.analyzer.node.NodeStructuralChecker
import dev.accessscope.scanner.analyzer.node.NodeTreeCollector
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationType

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

    private fun includes(area: ViolationArea): Boolean = scanScope.includes(area)

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
        val violations = mutableListOf<AccessibilityViolation>()
        val checkCollector = CheckCollector()
        val snapshots = mutableListOf<NodeSnapshot>()
        var traversalIndex = 0
        NodeTreeCollector.collectSnapshots(
            root, snapshots, ArrayDeque(), { traversalIndex++ },
            minTextHeightPx, minTouchTargetPx,
        )
        val customActionEmitted = mutableSetOf<String>()
        val viewport = PrecisionRules.estimateViewport(snapshots)
        val screenWidth = viewport.width()

        snapshots.forEach { snap ->
            if (!snap.isAccessibilityExcluded) {
                NodeSingleNodeChecker.checkSingleNode(
                    snap, snapshots, packageName, screenTitle,
                    violations, screenshot, customActionEmitted,
                    viewport, screenWidth, checkCollector,
                    scanScope, minTouchTargetPx, minTextHeightPx, recommendedTextHeightPx,
                    density, screenFingerprint,
                )
            }
        }

        if (includes(ViolationArea.LABELS) || includes(ViolationArea.TOUCH)) {
            NodeCrossNodeChecker.checkCrossNodeIssues(
                snapshots, packageName, screenTitle, violations, screenWidth,
                minTouchSpacingPx, screenFingerprint,
            )
        }
        if (includes(ViolationArea.STRUCTURE)) {
            NodeStructuralChecker.checkModalTitle(root, packageName, screenTitle, violations)
            NodeStructuralChecker.checkCollectionStructure(root, packageName, screenTitle, violations)
            NodeStructuralChecker.checkTables(snapshots, packageName, screenTitle, violations)
            NodeStructuralChecker.checkDuplicateViewIds(snapshots, packageName, screenTitle, violations, screenFingerprint)
            violations += FocusOrderAnalyzer.analyze(snapshots, packageName, screenTitle)
            violations += FocusOrderAnalyzer.analyzeHeadingLevels(snapshots, packageName, screenTitle)
        }
        if (includes(ViolationArea.LABELS)) {
            NodeStructuralChecker.checkDuplicateLinks(snapshots, packageName, screenTitle, violations, screenFingerprint)
        }
        if (includes(ViolationArea.MEDIA_WEB)) {
            NodeStructuralChecker.checkWebViews(snapshots, packageName, screenTitle, violations, screenFingerprint)
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
                screenFingerprint = screenFingerprint,
            )
        }

        val screenReaderFindings = if (includes(ViolationArea.SCREEN_READER)) {
            TalkBackSimulator().simulate(root, packageName, screenTitle, screenFingerprint)
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
