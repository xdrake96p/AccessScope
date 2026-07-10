/**
 * Viewport, bounds e utility geometriche.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionLabels

internal object PrecisionGeometry {
    fun viewIdShort(snap: NodeSnapshot): String =
        snap.viewId?.substringAfterLast('/')?.lowercase().orEmpty()

    /**
     * Stima il rettangolo del viewport visibile a partire dall'insieme degli snapshot analizzati.
     *
     * Calcola il bounding box minimo che contiene i bounds di tutti i nodi forniti, approssimando
     * l'area effettivamente occupata dal contenuto sullo schermo.
     *
     * @param snapshots Elenco degli snapshot dei nodi presenti nella schermata corrente.
     * @return Rettangolo che delimita il viewport stimato, oppure [Rect] vuoto se la lista è vuota.
     */
    fun estimateViewport(snapshots: List<NodeSnapshot>): Rect {
        if (snapshots.isEmpty()) return Rect()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = 0
        var bottom = 0
        snapshots.forEach { snap ->
            left = minOf(left, snap.bounds.left)
            top = minOf(top, snap.bounds.top)
            right = maxOf(right, snap.bounds.right)
            bottom = maxOf(bottom, snap.bounds.bottom)
        }
        return Rect(left, top, right, bottom)
    }

    /**
     * Verifica se un nodo è fuori dal viewport o marginalmente visibile (rumore di layout).
     *
     * Un nodo viene considerato fuori schermo o marginale se non interseca il viewport,
     * se è micro-testo posizionato sotto il fold (oltre il 90% dell'altezza del viewport),
     * se eccede i margini laterali oltre la soglia touch target, oppure se presenta bounds
     * anomali. Le etichette di campo note ([isKnownContrastFieldLabel]) nel viewport
     * non vengono escluse per preservare il recall mirato.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param viewport Rettangolo del viewport visibile stimato.
     * @param packageName Nome del package dell'app, usato per profili di precisione specifici.
     * @return `true` se il nodo va trattato come fuori schermo o marginale; `false` altrimenti.
     */
    fun isOffScreenOrMarginalNode(snap: NodeSnapshot, viewport: Rect, packageName: String = ""): Boolean {
        if (viewport.isEmpty) return false
        if (!Rect.intersects(snap.bounds, viewport)) return true
        val belowFold = snap.bounds.top > viewport.top + (viewport.height() * 0.90f).toInt()
        val tiny = snap.bounds.height() < snap.minTextHeightPx * 0.75f
        if (belowFold && tiny) return true
        if (PrecisionLabels.isKnownContrastFieldLabel(snap, packageName) && !belowFold) return false
        if (snap.bounds.left < viewport.left - snap.minTouchTargetPx) return true
        if (snap.bounds.right > viewport.right + snap.minTouchTargetPx) return true
        return false
    }

    /**
     * Rileva bounds anomali che non rappresentano un'area tappabile reale.
     *
     * Identifica strisce verticali o orizzontali troppo sottili rispetto alla dimensione minima
     * del touch target (ad esempio 79×698 px), tipiche di artefatti di layout non interattivi.
     *
     * @param snap Snapshot del nodo i cui bounds devono essere verificati.
     * @return `true` se i bounds sono anomali e non tappabili; `false` altrimenti.
     */
    fun isAnomalousTouchBounds(snap: NodeSnapshot): Boolean {
        val w = snap.bounds.width()
        val h = snap.bounds.height()
        val min = snap.minTouchTargetPx
        val thinVertical = w < (min * 0.75f).toInt() && h > min * 4
        val thinHorizontal = h < min / 3 && w > min * 4
        val hairlineText = snap.hasVisibleText() && (h <= 2 || w <= 2)
        return thinVertical || thinHorizontal || hairlineText
    }
}
