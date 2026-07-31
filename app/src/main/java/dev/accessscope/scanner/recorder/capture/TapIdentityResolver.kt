/**
 * Risoluzione identità tap a record-time (competence: capture, non optimize).
 *
 * Costruisce candidati selettore già in REC così la pipeline raffina invece di inventare.
 */
package dev.accessscope.scanner.recorder.capture

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.model.SelectorCandidate

/**
 * Identità semantica di un tap risolto dall’albero a11y.
 *
 * @property viewId Resource-id preferito (clickable ancestor se disponibile).
 * @property text Testo migliore.
 * @property contentDescription Content description.
 * @property clickableBounds Bounds del target cliccabile (per point %).
 * @property candidates Catena ordinata id → cd → text (point aggiunto dal caller se serve).
 * @property weak `true` se manca selettore semantico.
 */
data class ResolvedTapIdentity(
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val clickableBounds: Rect?,
    val candidates: List<SelectorCandidate>,
    val weak: Boolean,
)

/**
 * Estrae id/testo/cd e candidati salendo ai parent cliccabili.
 */
object TapIdentityResolver {

    private const val MAX_ANCESTOR_WALK = 8

    /**
     * Risolve l’identità di un tap dal nodo e dall’evento.
     *
     * @param node Nodo source (può essere `null` in Compose).
     * @param event Evento a11y originale (testo evento come fallback).
     * @return Identità + candidati; [ResolvedTapIdentity.weak] se solo point/vuoto.
     */
    fun resolve(
        node: AccessibilityNodeInfo?,
        event: AccessibilityEvent,
    ): ResolvedTapIdentity {
        var bestId: String? = null
        var clickableId: String? = null
        var bestText: String? = eventLabel(event)
        var bestCd: String? = event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
        var clickableBounds: Rect? = null
        val idCandidates = linkedSetOf<String>()
        val textCandidates = linkedSetOf<String>()
        val cdCandidates = linkedSetOf<String>()

        bestText?.let { textCandidates += it }
        bestCd?.let { cdCandidates += it }

        if (node == null) {
            return finish(bestId, bestText, bestCd, null, idCandidates, textCandidates, cdCandidates)
        }

        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        repeat(MAX_ANCESTOR_WALK) {
            val c = current ?: return@repeat
            val id = c.viewIdResourceName?.takeIf { it.isNotBlank() }
            if (id != null && !MaestroSelectorHeuristics.isNoiseViewId(id)) {
                if (bestId == null) bestId = id
                idCandidates += id
                if (c.isClickable || c.isCheckable) {
                    clickableId = id
                    val b = Rect()
                    c.getBoundsInScreen(b)
                    if (!b.isEmpty) clickableBounds = Rect(b)
                }
            }
            val t = c.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val cd = c.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
            if (t != null) {
                if (bestText == null) bestText = t
                textCandidates += t
            }
            if (cd != null) {
                if (bestCd == null) bestCd = cd
                cdCandidates += cd
            }
            if (clickableBounds == null && (c.isClickable || c.isCheckable)) {
                val b = Rect()
                c.getBoundsInScreen(b)
                if (!b.isEmpty) clickableBounds = Rect(b)
            }
            val parent = c.parent
            c.recycle()
            current = parent
        }
        current?.recycle()

        // Preferisci id del clickable; scarta strutturale se abbiamo testo.
        var chosenId = clickableId ?: bestId
        if (chosenId != null &&
            MaestroSelectorHeuristics.isStructuralContainerViewId(chosenId) &&
            (bestText != null || bestCd != null)
        ) {
            chosenId = null
        }

        return finish(chosenId, bestText, bestCd, clickableBounds, idCandidates, textCandidates, cdCandidates)
    }

    private fun finish(
        viewId: String?,
        text: String?,
        contentDescription: String?,
        bounds: Rect?,
        ids: Set<String>,
        texts: Set<String>,
        cds: Set<String>,
    ): ResolvedTapIdentity {
        val candidates = mutableListOf<SelectorCandidate>()
        // Ordine: id stabile non strutturale → cd → text
        val primaryId = viewId?.takeUnless {
            MaestroSelectorHeuristics.isStructuralContainerViewId(it) ||
                MaestroSelectorHeuristics.isNoiseViewId(it)
        }
        if (primaryId != null) {
            candidates += SelectorCandidate(viewId = primaryId)
        }
        ids.filter {
            it != primaryId &&
                !MaestroSelectorHeuristics.isStructuralContainerViewId(it) &&
                !MaestroSelectorHeuristics.isNoiseViewId(it)
        }.forEach { candidates += SelectorCandidate(viewId = it) }

        contentDescription?.let { candidates += SelectorCandidate(contentDescription = it) }
        cds.filter { it != contentDescription }.forEach {
            candidates += SelectorCandidate(contentDescription = it.take(80))
        }
        text?.let { candidates += SelectorCandidate(text = it.take(80)) }
        texts.filter { it != text }.forEach {
            candidates += SelectorCandidate(text = it.take(80))
        }

        val deduped = candidates.distinctBy { it.dedupeKey() }.filterNot { it.isBlank() }
        val weak = viewId.isNullOrBlank() && text.isNullOrBlank() && contentDescription.isNullOrBlank()
        return ResolvedTapIdentity(
            viewId = viewId,
            text = text?.take(80),
            contentDescription = contentDescription?.take(80),
            clickableBounds = bounds,
            candidates = deduped,
            weak = weak,
        )
    }

    private fun eventLabel(event: AccessibilityEvent): String? =
        event.text?.firstOrNull()?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
}
