/**
 * Rappresentazione immutabile di un nodo dell'albero di accessibilità per l'analisi offline.
 *
 * Converte [android.view.accessibility.AccessibilityNodeInfo] in uno snapshot leggero
 * con proprietà semantiche, bounds, stato e metadati utili ai controlli WCAG,
 * evitando di trattenere riferimenti ai nodi Android durante l'analisi.
 */
package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Snapshot immutabile di un nodo UI con le proprietà rilevanti per i controlli di accessibilità.
 *
 * @property className Nome completo della classe Android del componente.
 * @property bounds Rettangolo del nodo in coordinate schermo (pixel).
 * @property viewId ID risorsa completo (es. `com.app:id/button`), oppure `null`.
 * @property text Testo visibile del nodo.
 * @property contentDescription Descrizione per screen reader.
 * @property hintText Testo hint per campi editabili.
 * @property tooltipText Tooltip (API 26+), se presente.
 * @property isClickable `true` se il nodo risponde al click.
 * @property isLongClickable `true` se il nodo risponde al long-press.
 * @property isFocusable `true` se il nodo è raggiungibile con il focus accessibility.
 * @property isEditable `true` se il nodo accetta input testuale.
 * @property isCheckable `true` se il nodo è un controllo selezionabile (checkbox, switch).
 * @property isChecked Stato selezionato per controlli checkable.
 * @property isScrollable `true` se il nodo è scrollabile.
 * @property isEnabled `true` se il controllo è abilitato.
 * @property isPassword `true` se il campo maschera l'input (API 26+).
 * @property isHeading `true` se il nodo è marcato come titolo strutturale.
 * @property headingLevel Livello heading stimato o esposto (0 = non heading).
 * @property hasLabeledBy `true` se esiste un nodo `labeledBy` associato.
 * @property hasLabelFor `true` se questo nodo etichetta un altro controllo.
 * @property errorText Messaggio di errore accessibility per campi form.
 * @property stateDescription Descrizione dello stato (API 30+), es. "espanso".
 * @property isExpanded Stato espanso per controlli espandibili; `null` se non determinabile.
 * @property collectionRow Indice riga in una griglia/lista strutturata (-1 se assente).
 * @property collectionColumn Indice colonna in una griglia/lista strutturata (-1 se assente).
 * @property childCount Numero di figli diretti al momento dello snapshot.
 * @property isAccessibilityExcluded `true` se marcato non importante per l'accessibilità.
 * @property isLikelyDecorative Euristica: immagine piccola e non interattiva, probabilmente decorativa.
 * @property traversalIndex Indice di ordine nell'attraversamento depth-first dell'albero.
 * @property rangeCurrent Valore corrente per slider/progresso.
 * @property rangeMin Valore minimo del range.
 * @property rangeMax Valore massimo del range.
 * @property unlabeledActionCount Numero di azioni personalizzate senza etichetta.
 * @property minTextHeightPx Soglia minima altezza testo in pixel (12sp convertito).
 * @property minTouchTargetPx Soglia minima target di tocco in pixel (48dp convertito).
 * @property textSizeSp Dimensione testo stimata in sp (da altezza bounds), se applicabile.
 * @property sectionTitle Titolo della sezione heading corrente durante l'attraversamento.
 */
data class NodeSnapshot(
    val className: String,
    val bounds: Rect,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val hintText: String?,
    val tooltipText: String?,
    val isClickable: Boolean,
    val isLongClickable: Boolean,
    val isFocusable: Boolean,
    val isEditable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isScrollable: Boolean,
    val isEnabled: Boolean,
    val isPassword: Boolean,
    val isHeading: Boolean,
    val headingLevel: Int,
    val hasLabeledBy: Boolean,
    val hasLabelFor: Boolean,
    val errorText: String?,
    val stateDescription: String?,
    val isExpanded: Boolean?,
    val collectionRow: Int,
    val collectionColumn: Int,
    val childCount: Int,
    val isAccessibilityExcluded: Boolean,
    val isLikelyDecorative: Boolean,
    val traversalIndex: Int,
    val rangeCurrent: Float?,
    val rangeMin: Float?,
    val rangeMax: Float?,
    val unlabeledActionCount: Int,
    val minTextHeightPx: Int,
    val minTouchTargetPx: Int,
    val textSizeSp: Float? = null,
    val sectionTitle: String? = null,
) {
    /**
     * Produce un'etichetta compatta delle dimensioni e posizione del bounds.
     *
     * @return Stringa nel formato "W×H px @(left,top)".
     */
    fun boundsLabel() = "${bounds.width()}×${bounds.height()} px @(${bounds.left},${bounds.top})"

    /**
     * Verifica se il nodo espone un nome accessibile utilizzabile da TalkBack.
     *
     * @return `true` se almeno uno tra contentDescription, text, hint o labeledBy è presente.
     */
    fun hasAccessibleName(): Boolean =
        !contentDescription.isNullOrBlank() || !text.isNullOrBlank() ||
            !hintText.isNullOrBlank() || hasLabeledBy

    /**
     * Restituisce il nome accessibile con priorità: contentDescription, text, hint.
     *
     * @return Nome accessibile, oppure `null` se assente.
     */
    fun accessibleName(): String? =
        contentDescription?.takeIf { it.isNotBlank() }
            ?: text?.takeIf { it.isNotBlank() }
            ?: hintText?.takeIf { it.isNotBlank() }

    /**
     * Verifica se un campo input ha un'etichetta associata.
     *
     * @return `true` se ha nome accessibile, labeledBy, labelFor o messaggio di errore.
     */
    fun hasInputLabel(): Boolean =
        hasAccessibleName() || hasLabeledBy || hasLabelFor || !errorText.isNullOrBlank()

    /**
     * Verifica se il nodo mostra testo visibile non vuoto.
     *
     * @return `true` se [text] è valorizzato.
     */
    fun hasVisibleText(): Boolean = !text.isNullOrBlank()

    /**
     * Determina se il nodo è interattivo tramite click o tipo di controllo standard.
     *
     * @return `true` per click/long-click o classi Button, ImageButton, CheckBox, Switch, Toggle.
     */
    fun isInteractiveClickable(): Boolean {
        if (isClickable || isLongClickable) return true
        return className.contains("Button", true) ||
            className.contains("ImageButton", true) ||
            className.contains("CheckBox", true) ||
            className.contains("Switch", true) ||
            className.contains("Toggle", true)
    }

    /**
     * Indica se il nodo dovrebbe essere focalizzabile per essere utilizzabile con TalkBack.
     *
     * @return `true` per controlli clickabili, checkable o editabili.
     */
    fun shouldBeFocusable(): Boolean =
        isClickable || isCheckable || isEditable

    /**
     * Euristica: il testo assomiglia a un titolo strutturale di sezione.
     *
     * @return `true` per TextView non cliccabili con testo breve, escludendo importi e badge.
     */
    fun looksLikeStructuralHeading(): Boolean {
        val value = text?.trim().orEmpty()
        if (value.isEmpty() || value.length > 80) return false
        if (PrecisionRules.shouldSkipHeadingCheck(this)) return false
        if (PrecisionRules.isCurrencyOrAmountText(value)) return false
        return className.contains("TextView", true) && !isClickable && value.split(" ").size <= 12
    }

    /**
     * Verifica se la classe del nodo corrisponde a un'immagine o icona.
     *
     * @return `true` se il nome classe contiene "Image" o "Icon".
     */
    fun isImageClass(): Boolean =
        className.contains("Image", true) || className.contains("Icon", true)

    /**
     * Verifica se un'immagine non ha testo alternativo.
     *
     * @return `true` per classi immagine senza contentDescription né text.
     */
    fun isImageWithoutAlt(): Boolean =
        isImageClass() && contentDescription.isNullOrBlank() && text.isNullOrBlank()

    /**
     * Euristica: il nodo si comporta come un link ipertestuale.
     *
     * @return `true` per TextView/Link cliccabili o con URL nel nome accessibile.
     */
    fun isLikelyLink(): Boolean =
        isClickable && (
            className.contains("TextView", true) ||
                accessibleName()?.contains("http", true) == true ||
                className.contains("Link", true)
            )

    /**
     * Verifica se il nodo è una view custom non standard Android.
     *
     * @return `true` se la classe non corrisponde a tipi UI comuni (Button, TextView, ecc.).
     */
    fun isCustomView(): Boolean {
        val standard = listOf("Button", "TextView", "Image", "Edit", "Check", "Switch", "Radio", "Spinner", "WebView")
        return standard.none { className.contains(it, ignoreCase = true) }
    }

    /**
     * Verifica se il nodo ha un ruolo semantico standard esposto dalla piattaforma.
     *
     * @return `true` per Button, CheckBox, Switch, EditText.
     */
    fun hasStandardRole(): Boolean =
        className.contains("Button", true) ||
            className.contains("CheckBox", true) ||
            className.contains("Switch", true) ||
            className.contains("EditText", true)

    /**
     * Euristica: il nodo è un controllo di riproduzione media.
     *
     * @return `true` per classi media/ExoPlayer o nomi accessibili play/pause/stop.
     */
    fun isMediaControl(): Boolean {
        val name = accessibleName()?.lowercase().orEmpty()
        val cls = className.lowercase()
        return cls.contains("media") || cls.contains("exo") ||
            name in setOf("play", "pause", "stop", "riproduci", "pausa", "stop")
    }

    /**
     * Verifica se il nodo è un WebView.
     *
     * @return `true` se la classe contiene "WebView".
     */
    fun isWebView(): Boolean = className.contains("WebView", true)

    /**
     * Chiave di ordinamento visivo top-left per confronto con l'ordine di attraversamento.
     *
     * @return Valore numerico combinando top e left (top prioritario).
     */
    fun visualSortKey(): Int = bounds.top * 100_000 + bounds.left
}

/**
 * Converte un [AccessibilityNodeInfo] visibile in [NodeSnapshot], oppure `null` se escluso.
 *
 * @param traversalIndex Indice di attraversamento depth-first assegnato al nodo.
 * @param minTextHeightPx Soglia minima altezza testo in pixel.
 * @param minTouchTargetPx Soglia minima target di tocco in pixel.
 * @param sectionTitle Titolo heading della sezione corrente, se noto.
 * @return Snapshot del nodo, oppure `null` se non visibile o con bounds invalidi.
 */
fun AccessibilityNodeInfo.toSnapshot(
    traversalIndex: Int,
    minTextHeightPx: Int,
    minTouchTargetPx: Int,
    sectionTitle: String? = null,
): NodeSnapshot? {
    if (!isVisibleToUser) return null
    val bounds = Rect()
    getBoundsInScreen(bounds)
    if (bounds.width() <= 0 || bounds.height() <= 0) return null

    val range = rangeInfo
    val collectionItem = collectionItemInfo
    val isHeadingMarked = when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P -> isHeading
        else -> false
    } || (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        collectionItem?.isHeading == true
    } else {
        false
    })
    val headingLevel = when {
        isHeadingMarked -> estimateHeadingLevel(bounds.height())
        else -> 0
    }

    var unlabeledActions = 0
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        actionList?.forEach { action ->
            if (action.id < CUSTOM_ACTION_ID_MIN) return@forEach
            val label = action.label?.toString()
            if (label.isNullOrBlank()) unlabeledActions++
        }
    }

    val isExcluded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isImportantForAccessibility

    val smallThreshold = minTouchTargetPx / 2
    val classStr = className?.toString().orEmpty()
    val isImage = classStr.contains("Image", true) || classStr.contains("Icon", true)
    val density = minTouchTargetPx / 48f
    val textSizeSp = if (classStr.contains("TextView", true) && bounds.height() > 0) {
        // Approssimazione: l'altezza bounds è più vicina alla line-height (~1.2× textSize).
        // Usiamo density come proxy di scaledDensity (fontScale=1) per evitare falsi "large text"
        // su testi normali (es. 14sp che spesso misura ~18dp di altezza).
        bounds.height() / (density * 1.2f)
    } else {
        null
    }

    return NodeSnapshot(
        className = className?.toString() ?: "unknown",
        bounds = bounds,
        viewId = viewIdResourceName,
        text = text?.toString(),
        contentDescription = contentDescription?.toString(),
        hintText = hintText?.toString(),
        tooltipText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            tooltipText?.toString()
        } else {
            null
        },
        isClickable = isClickable,
        isLongClickable = isLongClickable,
        isFocusable = isFocusable,
        isEditable = isEditable || className?.toString().orEmpty().contains("EditText", true),
        isCheckable = isCheckable,
        isChecked = isChecked,
        isScrollable = isScrollable,
        isEnabled = isEnabled,
        isPassword = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) isPassword else false,
        isHeading = isHeadingMarked,
        headingLevel = headingLevel,
        hasLabeledBy = labeledBy != null,
        hasLabelFor = labelFor != null,
        errorText = error?.toString(),
        stateDescription = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            stateDescription?.toString()
        } else {
            null
        },
        isExpanded = resolveExpandedState(this),
        collectionRow = collectionItemInfo?.rowIndex ?: -1,
        collectionColumn = collectionItemInfo?.columnIndex ?: -1,
        childCount = childCount,
        isAccessibilityExcluded = isExcluded,
        isLikelyDecorative = isExcluded ||
            (bounds.width() < smallThreshold && bounds.height() < smallThreshold && isImage) ||
            (!isClickable && !isFocusable && !isCheckable && isImage),
        traversalIndex = traversalIndex,
        rangeCurrent = range?.current,
        rangeMin = range?.min,
        rangeMax = range?.max,
        unlabeledActionCount = unlabeledActions,
        minTextHeightPx = minTextHeightPx,
        minTouchTargetPx = minTouchTargetPx,
        textSizeSp = textSizeSp,
        sectionTitle = sectionTitle,
    )
}

/** Soglia minima ID per le azioni personalizzate accessibility (0x01000000). */
private const val CUSTOM_ACTION_ID_MIN = 0x01000000

/**
 * Stima il livello heading (1–5) dall'altezza del testo in pixel.
 *
 * @param heightPx Altezza del bounds in pixel.
 * @return Livello stimato, dove 1 è il titolo più prominente.
 */
private fun estimateHeadingLevel(heightPx: Int): Int = when {
    heightPx >= 72 -> 1
    heightPx >= 56 -> 2
    heightPx >= 44 -> 3
    heightPx >= 36 -> 4
    else -> 5
}

/**
 * Risolve lo stato espanso/collassato da stateDescription o tipo ExpandableListView.
 *
 * @param node Nodo di cui determinare lo stato.
 * @return `true` se espanso, `false` se collassato, `null` se non determinabile.
 */
private fun resolveExpandedState(node: AccessibilityNodeInfo): Boolean? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        val state = node.stateDescription?.toString()?.lowercase().orEmpty()
        if (state.contains("espans") || state.contains("expand") || state.contains("apert")) return true
        if (state.contains("collass") || state.contains("collaps") || state.contains("chius")) return false
    }
    if (node.className?.toString().orEmpty().contains("Expandable", true)) return null
    return null
}
