/**
 * Regole di deduplicazione violazioni stabili durante scroll e re-analisi.
 *
 * Separato da [AccessibilityViolation] per consentire test JVM senza dipendenze Android UI.
 */
package dev.accessscope.scanner.data

/**
 * Genera chiavi univoche per violazioni nella stessa sessione di scansione.
 *
 * Strategia conservativa: con [AccessibilityViolation.viewId] la chiave ignora
 * [AccessibilityViolation.screenFingerprint] (instabile in scroll); senza viewId
 * usa [AccessibilityViolation.elementLabel] se distintiva, altrimenti bounds quantizzati.
 */
object ViolationDedupeRules {

    private const val BOUNDS_QUANTUM_DP = 32

    /**
     * Calcola la chiave di deduplicazione per una violazione.
     *
     * @param violation Violazione da valutare.
     * @return Stringa stabile per confronto in sessione.
     */
    fun keyFor(violation: AccessibilityViolation): String {
        val type = violation.type
        val pkg = violation.packageName

        if (type == ViolationType.DYNAMIC_CONTENT_SILENT) {
            val identity = identityFor(violation)
            return "${type.name}|$pkg|global|$identity"
        }

        val viewId = violation.viewId?.takeIf { it.isNotBlank() }
        if (viewId != null) {
            val scope = if (AccessibilityViolation.isGlobalWidget(viewId)) "global" else "view"
            val normalized = normalizeViewId(viewId)
            return "${type.name}|$pkg|$scope|$normalized"
        }

        val shortClass = violation.viewClassName.substringAfterLast('.')
        val screen = violation.screenTitle.ifBlank { "unknown" }
        val label = violation.elementLabel?.let { normalizeElementLabel(it) }
        if (label != null) {
            return "${type.name}|$pkg|$screen|$shortClass|$label"
        }
        val quantized = quantizeBoundsLabel(violation.bounds)
        return "${type.name}|$pkg|$screen|$shortClass|$quantized"
    }

    /**
     * Estrae l'identità elementare quando non c'è viewId affidabile.
     */
    private fun identityFor(violation: AccessibilityViolation): String {
        val viewId = violation.viewId?.takeIf { it.isNotBlank() }
        if (viewId != null) return normalizeViewId(viewId)
        val label = violation.elementLabel?.let { normalizeElementLabel(it) }
        if (label != null) return label
        val shortClass = violation.viewClassName.substringAfterLast('.')
        return "$shortClass@${quantizeBoundsLabel(violation.bounds)}"
    }

    /**
     * Normalizza un'etichetta accessibile per dedupe (generico, multi-app).
     *
     * Ignora etichette troppo corte, importi puri o placeholder generici.
     */
    fun normalizeElementLabel(label: String): String? {
        val trimmed = label.trim().replace(Regex("\\s+"), " ")
        if (trimmed.length < 3) return null
        if (trimmed.matches(Regex("""^[\d.,€$£\s%-]+$"""))) return null
        if (trimmed.equals("...", ignoreCase = false)) return null
        return trimmed.lowercase()
    }

    /**
     * Normalizza un viewId Android rimuovendo suffissi numerici da template RecyclerView.
     *
     * @param viewId ID risorsa completo o short.
     * @return ID normalizzato per dedupe.
     */
    fun normalizeViewId(viewId: String): String {
        val short = viewId.substringAfterLast('/')
        val withoutIndex = short.replace(Regex("""_\d+$"""), "")
        return withoutIndex.ifBlank { short }
    }

    /**
     * Quantizza una stringa bounds "left,top-right,bottom" su griglia fissa.
     *
     * @param bounds Etichetta bounds o null.
     * @return Token quantizzato left/top o "unknown".
     */
    fun quantizeBoundsLabel(bounds: String?): String {
        if (bounds.isNullOrBlank()) return "unknown"
        val nodeMatch = NODE_BOUNDS_PATTERN.find(bounds.trim())
        if (nodeMatch != null) {
            val (left, top) = nodeMatch.destructured
            val qLeft = quantizeCoordinate(left.toIntOrNull() ?: 0)
            val qTop = quantizeCoordinate(top.toIntOrNull() ?: 0)
            return "${qLeft},${qTop}"
        }
        val legacyMatch = LEGACY_BOUNDS_PATTERN.matchEntire(bounds.trim())
        if (legacyMatch != null) {
            val (left, top, _, _) = legacyMatch.destructured
            val qLeft = quantizeCoordinate(left.toIntOrNull() ?: 0)
            val qTop = quantizeCoordinate(top.toIntOrNull() ?: 0)
            return "${qLeft},${qTop}"
        }
        return bounds.hashCode().toString()
    }

    private fun quantizeCoordinate(px: Int): Int {
        val quantum = BOUNDS_QUANTUM_DP
        return (px / quantum) * quantum
    }

    /** Formato [dev.accessscope.scanner.analyzer.NodeSnapshot.boundsLabel]: `W×H px @(left,top)`. */
    private val NODE_BOUNDS_PATTERN = Regex("""@\((-?\d+),(-?\d+)\)""")
    private val LEGACY_BOUNDS_PATTERN = Regex("""(-?\d+),(-?\d+)-(-?\d+),(-?\d+)""")
}
