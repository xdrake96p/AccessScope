/**
 * Analisi dell'ordine di focus TalkBack rispetto all'ordine visivo e della gerarchia dei titoli.
 *
 * Confronta l'ordine di attraversamento dell'albero di accessibilità con la disposizione
 * spaziale degli elementi focalizzabili e verifica salti nei livelli di heading esposti.
 */
package dev.accessscope.scanner.analyzer

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType
import kotlin.math.abs

/**
 * Analizzatore statico per violazioni legate all'ordine di navigazione e alla struttura dei titoli.
 */
object FocusOrderAnalyzer {

    /**
     * Confronta l'ordine di attraversamento TalkBack con l'ordine visivo (top-left).
     *
     * @param snapshots Snapshot immutabili dei nodi della schermata.
     * @param packageName Package dell'applicazione analizzata.
     * @param screenTitle Titolo umano della schermata corrente.
     * @return Lista di violazioni [ViolationType.ILLOGICAL_FOCUS_ORDER] se il tasso di inversioni supera la soglia.
     */
    fun analyze(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
    ): List<AccessibilityViolation> {
        val focusable = snapshots.filter {
            it.isFocusable || it.isInteractiveClickable() || it.isEditable || it.isCheckable
        }
        if (focusable.size < 3) return emptyList()

        val visualOrder = focusable.sortedBy { it.visualSortKey() }
        val traversalOrder = focusable.sortedBy { it.traversalIndex }

        val inversions = countInversions(
            traversalOrder.map { visualOrder.indexOf(it) },
        )
        val maxInversions = focusable.size * (focusable.size - 1) / 2
        val inversionRate = if (maxInversions > 0) inversions.toFloat() / maxInversions else 0f

        if (inversionRate < 0.45f) return emptyList()

        val inRecycler = snapshots.count {
            it.className.contains("RecyclerView", true) ||
                PrecisionRules.viewIdShort(it) in setOf("recycler_distinte", "recycler_effetti", "recycler")
        }
        if (inRecycler > 0 && inRecycler.toFloat() / snapshots.size > 0.5f) return emptyList()

        val confidence = (0.6f + inversionRate * 0.35f).coerceAtMost(0.95f)
        return listOf(
            AccessibilityViolation(
                type = ViolationType.ILLOGICAL_FOCUS_ORDER,
                viewClassName = "Schermata",
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Ordine TalkBack (${traversalOrder.take(5).joinToString { it.accessibleName() ?: "?" }}) " +
                    "non segue l'ordine visivo (${visualOrder.take(5).joinToString { it.accessibleName() ?: "?" }}). " +
                    "Inversioni: $inversions/$maxInversions.",
                confidence = confidence,
            ),
        )
    }

    /**
     * Verifica salti nella gerarchia dei livelli di heading (es. da H1 a H3 senza H2).
     *
     * @param snapshots Snapshot immutabili dei nodi della schermata.
     * @param packageName Package dell'applicazione analizzata.
     * @param screenTitle Titolo umano della schermata corrente.
     * @return Lista di violazioni [ViolationType.HEADING_LEVEL_SKIP] per ogni salto di livello rilevato.
     */
    fun analyzeHeadingLevels(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
    ): List<AccessibilityViolation> {
        val headings = snapshots
            .filter {
                (it.isHeading || it.looksLikeStructuralHeading()) &&
                    !PrecisionRules.shouldSkipHeadingCheck(it)
            }
            .map { snap ->
                val level = if (snap.headingLevel > 0) snap.headingLevel
                else estimateFromBounds(snap.bounds.height())
                snap to level
            }
            .sortedBy { it.first.visualSortKey() }

        val violations = mutableListOf<AccessibilityViolation>()
        for (i in 1 until headings.size) {
            val prev = headings[i - 1].second
            val curr = headings[i].second
            if (curr - prev > 1) {
                val snap = headings[i].first
                violations += AccessibilityViolation(
                    type = ViolationType.HEADING_LEVEL_SKIP,
                    viewClassName = snap.className,
                    screenTitle = screenTitle,
                    packageName = packageName,
                    details = "Salto da livello ~H$prev a ~H$curr su \"${snap.accessibleName() ?: snap.text}\".",
                    viewId = snap.viewId,
                    bounds = snap.boundsLabel(),
                    sectionTitle = snap.sectionTitle,
                    confidence = 0.8f,
                    boundsLeft = snap.bounds.left,
                    boundsTop = snap.bounds.top,
                    boundsRight = snap.bounds.right,
                    boundsBottom = snap.bounds.bottom,
                )
            }
        }
        return violations
    }

    /**
     * Stima il livello di heading (1–5) dall'altezza in pixel del testo.
     *
     * @param height Altezza del bounds del nodo in pixel.
     * @return Livello stimato, dove 1 è il titolo più prominente.
     */
    private fun estimateFromBounds(height: Int): Int = when {
        height >= 72 -> 1
        height >= 56 -> 2
        height >= 44 -> 3
        height >= 36 -> 4
        else -> 5
    }

    /**
     * Conta le inversioni in una sequenza di indici (misura di disordine rispetto all'ordine crescente).
     *
     * @param sequence Sequenza di posizioni nell'ordine visivo, nell'ordine di attraversamento.
     * @return Numero di coppie (i, j) con i < j e sequence[i] > sequence[j].
     */
    private fun countInversions(sequence: List<Int>): Int {
        var count = 0
        for (i in sequence.indices) {
            for (j in i + 1 until sequence.size) {
                if (sequence[i] > sequence[j]) count++
            }
        }
        return count
    }
}
