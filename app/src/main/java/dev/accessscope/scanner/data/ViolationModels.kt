/**
 * Modelli per violazioni rilevate, controlli superati e risultati TalkBack.
 */
package dev.accessscope.scanner.data

/**
 * Singola violazione di accessibilità rilevata su un elemento UI.
 *
 * Aggrega metadati sul tipo di problema, la vista coinvolta, la schermata
 * e valori misurati per la generazione del report.
 */
data class AccessibilityViolation(
    val type: ViolationType,
    val viewClassName: String,
    val screenTitle: String,
    val packageName: String,
    val details: String,
    val viewId: String? = null,
    val bounds: String? = null,
    val sectionTitle: String? = null,
    val confidence: Float = 1.0f,
    val timestampMs: Long = System.currentTimeMillis(),
    val screenFingerprint: String? = null,
    val elementLabel: String? = null,
    val measuredValue: String? = null,
    val requiredValue: String? = null,
    val remediation: String? = null,
    val boundsLeft: Int? = null,
    val boundsTop: Int? = null,
    val boundsRight: Int? = null,
    val boundsBottom: Int? = null,
    val screenEvidenceId: String? = null,
    val evidenceImagePath: String? = null,
    val foregroundColorHex: String? = null,
    val backgroundColorHex: String? = null,
    val evidenceKind: EvidenceKind = EvidenceKind.SCREENSHOT,
) {
    /** Ambito tematico derivato dal [type] della violazione. */
    val area: ViolationArea get() = type.area

    /** Spiegazione semplificata derivata dal [type] della violazione. */
    val simpleExplanation: String get() = type.plainHint

    /** Riferimento WCAG derivato dal [type] della violazione. */
    val wcagReference: String get() = type.wcagRef

    /**
     * Sezione da usare nel report: preferisce [sectionTitle] se valorizzata,
     * altrimenti ricade su [screenTitle].
     */
    val reportSection: String
        get() = sectionTitle?.takeIf { it.isNotBlank() } ?: screenTitle

    /**
     * Chiave univoca per deduplicare violazioni identiche nella stessa sessione.
     *
     * Combina tipo, package, ambito schermata e identità dell'elemento.
     */
    val dedupeKey: String
        get() = ViolationDedupeRules.keyFor(this)

    /** `true` se la violazione ha coordinate spaziali valide sull'elemento. */
    fun hasSpatialBounds(): Boolean =
        boundsLeft != null && boundsTop != null && boundsRight != null && boundsBottom != null &&
            (boundsRight ?: 0) > (boundsLeft ?: 0) && (boundsBottom ?: 0) > (boundsTop ?: 0)

    companion object {
        /**
         * Verifica se un [viewId] corrisponde a un widget globale dell'app host
         * (es. icone della top bar) che compare su più schermate.
         */
        fun isGlobalWidget(viewId: String?): Boolean {
            if (viewId.isNullOrBlank()) return false
            val short = viewId.substringAfterLast('/').lowercase()
            return short.contains("topbar_icon") ||
                short == "layout_topbar_icon_left" ||
                short == "layout_topbar_icon_right" ||
                short == "topbar_icon_left" ||
                short == "topbar_icon_right"
        }
    }
}

/** Controllo superato durante la scansione (campione rappresentativo). */
data class PassedCheck(
    val area: ViolationArea,
    val checkLabel: String,
    val screenTitle: String,
    val packageName: String,
    val elementSummary: String,
    val viewId: String? = null,
    val bounds: String? = null,
    val wcagRef: String? = null,
)

/** Riepilogo aggregato dei controlli superati per ambito e schermata. */
data class CheckAreaSummary(
    val area: ViolationArea,
    val screenTitle: String,
    val packageName: String,
    val passedCount: Int,
    val samples: List<PassedCheck> = emptyList(),
    /** Fingerprint schermata per attribuzione report dinamico (opzionale per retrocompatibilità). */
    val screenFingerprint: String? = null,
)

/** Risultato dell'analisi simulata dello screen reader su un nodo dell'albero di accessibilità. */
data class ScreenReaderFinding(
    val packageName: String,
    val screenTitle: String,
    val nodeClassName: String,
    val announcedText: String?,
    val issue: String,
    val viewId: String? = null,
    val sectionTitle: String? = null,
    /** Fingerprint schermata per attribuzione report dinamico (opzionale). */
    val screenFingerprint: String? = null,
) {
    /**
     * Sezione da usare nel report: preferisce [sectionTitle] se valorizzata,
     * altrimenti ricade su [screenTitle].
     */
    val reportSection: String
        get() = sectionTitle?.takeIf { it.isNotBlank() } ?: screenTitle
}
