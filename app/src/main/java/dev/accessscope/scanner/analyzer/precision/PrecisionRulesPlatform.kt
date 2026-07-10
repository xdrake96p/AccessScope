/**
 * Pattern piattaforma Android generici.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionLabels
import dev.accessscope.scanner.analyzer.precision.PrecisionStructural

internal object PrecisionRulesPlatform {
    private val MAP_SURFACE_MARKERS = listOf("mapview", "googlemap", "maps.", "maplibre", "heremap")
    private val MEDIA_PLAYER_MARKERS = listOf("playerview", "styledplayerview", "videoview", "exoplayer")
    private val MODAL_OVERLAY_MARKERS = listOf("dialog", "bottomsheet", "popupwindow", "alertdialog", "modalbottomsheet")
    private val SKELETON_ID_MARKERS = listOf("skeleton", "shimmer", "placeholder", "loading", "stub", "skel")
    private val LOTTIE_MARKERS = listOf("lottie", "lottieanimationview")

    /** Superficie mappa (MapView, GoogleMap embed): i marker interni non sono controlli UI standard. */
    fun isMapSurface(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        val id = PrecisionGeometry.viewIdShort(snap).lowercase()
        return MAP_SURFACE_MARKERS.any { cls.contains(it) } ||
            (id.contains("map") && snap.bounds.width() > 200 && snap.bounds.height() > 200)
    }

    /** Area di riproduzione video (ExoPlayer, VideoView): contenuto dinamico senza live region atteso. */
    fun isMediaPlayerSurface(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        if (cls.contains("mediacontroller")) return false
        return MEDIA_PLAYER_MARKERS.any { cls.contains(it) }
    }

    /** Animazione Lottie non interattiva: decorativa, contrasto non significativo. */
    fun isLottieAnimation(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        val id = PrecisionGeometry.viewIdShort(snap).lowercase()
        return LOTTIE_MARKERS.any { cls.contains(it) || id.contains(it) }
    }

    /** Placeholder skeleton/shimmer durante il caricamento. */
    fun isSkeletonPlaceholder(snap: NodeSnapshot): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap).lowercase()
        if (SKELETON_ID_MARKERS.any { id.contains(it) }) return true
        val cls = snap.className.lowercase()
        return cls.contains("shimmer") || cls.contains("skeleton") || cls.contains("placeholder")
    }

    /** Host Jetpack Compose (ComposeView o nodo semantics). */
    fun isComposeHost(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        return cls.contains("composeview") || cls.contains("abstractcomposeview") || cls.contains("androidx.compose")
    }

    /** Nodo semantics Compose senza viewId: contrasto screenshot inaffidabile. */
    fun shouldSkipComposeContrast(snap: NodeSnapshot): Boolean {
        if (!snap.viewId.isNullOrBlank()) return false
        return isComposeHost(snap) || snap.className.contains("Semantics", true)
    }

    /**
     * Touch target su semantics Compose senza viewId e senza ruolo interattivo esplicito:
     * spesso spacing/layout interno, non un controllo autonomo.
     */
    fun shouldSkipComposeTouch(snap: NodeSnapshot): Boolean {
        if (!snap.viewId.isNullOrBlank()) return false
        if (!isComposeHost(snap) && !snap.className.contains("Semantics", true)) return false
        if (snap.isEditable || (snap.isInteractiveClickable() && snap.hasAccessibleName())) return false
        return snap.bounds.width() < snap.minTouchTargetPx || snap.bounds.height() < snap.minTouchTargetPx
    }

    /** Bounds del modal/bottom sheet dominante, se presente. */
    fun findModalOverlayBounds(all: List<NodeSnapshot>): Rect? {
        val viewport = PrecisionGeometry.estimateViewport(all)
        if (viewport.isEmpty) return null
        val screenArea = viewport.width() * viewport.height().toFloat()
        return all
            .filter { snap ->
                val cls = snap.className.lowercase()
                val id = PrecisionGeometry.viewIdShort(snap).lowercase()
                val isModal = MODAL_OVERLAY_MARKERS.any { cls.contains(it) } ||
                    id.contains("bottom_sheet") || id.contains("dialog") || id.contains("modal")
                isModal && snap.bounds.width() * snap.bounds.height() >= screenArea * 0.35f
            }
            .maxByOrNull { it.bounds.width() * it.bounds.height() }
            ?.bounds
    }

    /** Nodo dietro un overlay modale a schermo intero: non analizzare (evita FP su sfondo oscurato). */
    fun isObscuredByModalOverlay(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        val modal = findModalOverlayBounds(all) ?: return false
        val viewport = PrecisionGeometry.estimateViewport(all)
        val screenArea = viewport.width() * viewport.height().toFloat()
        if (modal.width() * modal.height() < screenArea * 0.45f) return false
        return !modal.contains(snap.bounds.centerX(), snap.bounds.centerY())
    }

    fun isInsideMapOrMediaSurface(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { host ->
            host != snap &&
                (isMapSurface(host) || isMediaPlayerSurface(host)) &&
                host.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY())
        }

    fun isInsideWebView(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { host ->
            host != snap &&
                host.isWebView() &&
                host.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY())
        }

    /**
     * Area cliccabile senza contenuto visibile (empty state, hit slop TextView, wrapper con figlio etichettato).
     * Evita SMALL_TOUCH_TARGET su zone vuote e contenitori che inoltrano il tap a figli etichettati.
     */
    fun isEmptyClickableHitArea(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isInteractiveClickable()) return false
        if (snap.className.contains("TextView", ignoreCase = true) &&
            !snap.isHeading &&
            !snap.hasVisibleText() &&
            snap.hintText.isNullOrBlank() &&
            snap.contentDescription.isNullOrBlank()
        ) {
            return true
        }
        if (snap.hasVisibleText() || !snap.contentDescription.isNullOrBlank() || !snap.hintText.isNullOrBlank()) {
            return false
        }
        val cls = snap.className
        if (cls.contains("Button", ignoreCase = true) || cls.contains("ImageButton", ignoreCase = true) || snap.isCheckable) {
            return false
        }
        if (PrecisionLabels.hasLabeledDescendant(snap, all)) return true
        val viewport = PrecisionGeometry.estimateViewport(all)
        if (viewport.isEmpty) return false
        val screenArea = viewport.width() * viewport.height().toFloat()
        if (screenArea <= 0f) return false
        val snapArea = snap.bounds.width() * snap.bounds.height().toFloat()
        if (snapArea >= screenArea * 0.5f) {
            val hasSemanticChild = all.any { other ->
                other != snap &&
                    snap.bounds.contains(other.bounds.centerX(), other.bounds.centerY()) &&
                    isSemanticClickTarget(other)
            }
            if (!hasSemanticChild) return true
        }
        return false
    }

    /**
     * Target cliccabile semantico (pulsante, switch, checkbox) — esclude layout container clickable.
     * Usato per overlap/spacing: evita FP su FrameLayout/ScrollView clickable senza perdere Button reali.
     */
    fun isSemanticClickTarget(snap: NodeSnapshot): Boolean {
        if (!snap.isInteractiveClickable()) return false
        return !isClickableLayoutShell(snap)
    }

    /**
     * Contenitore layout reso clickable (card intera, riga lista) — non un controllo UI autonomo.
     */
    fun isClickableLayoutShell(snap: NodeSnapshot): Boolean {
        if (!snap.isInteractiveClickable()) return false
        val cls = snap.className
        if (cls.contains("Button", ignoreCase = true) ||
            cls.contains("Chip", ignoreCase = true) ||
            snap.isCheckable
        ) {
            return false
        }
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in setOf("gridview", "recycler", "scrollview_port", "scroll", "content")) return true
        return cls.contains("Layout", ignoreCase = true) ||
            cls.contains("ViewGroup", ignoreCase = true) ||
            cls.contains("ScrollView", ignoreCase = true) ||
            cls.contains("RecyclerView", ignoreCase = true) ||
            cls.contains("GridView", ignoreCase = true) ||
            (snap.isScrollable && snap.bounds.area() > snap.minTouchTargetPx * snap.minTouchTargetPx * 6)
    }

    /**
     * Overlap tra shell layout e discendente strutturale (non doppio target reale).
     * Mantiene segnalazioni card+Button quando il controllo interno è un vero pulsante.
     */
    fun isLayoutShellOverlap(a: NodeSnapshot, b: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!Rect.intersects(a.bounds, b.bounds)) return false
        val (shell, other) = when {
            isClickableLayoutShell(a) && !isClickableLayoutShell(b) -> a to b
            isClickableLayoutShell(b) && !isClickableLayoutShell(a) -> b to a
            else -> return false
        }
        if (!shell.bounds.contains(other.bounds.centerX(), other.bounds.centerY())) return false
        val otherIsRealControl = other.className.contains("Button", ignoreCase = true) ||
            other.className.contains("ImageButton", ignoreCase = true)
        if (otherIsRealControl) return false
        return PrecisionStructural.isScrollContainer(shell) ||
            shell.className.contains("FrameLayout", ignoreCase = true) ||
            shell.className.contains("ConstraintLayout", ignoreCase = true) ||
            shell.bounds.area() > other.bounds.area() * 2
    }

    /** Nodo dentro RecyclerView/GridView/ScrollView ampio (griglia densa — spacing intenzionale). */
    fun isInsideDenseScrollGrid(snap: NodeSnapshot, all: List<NodeSnapshot>, screenArea: Int): Boolean {
        if (screenArea <= 0) return false
        return all.any { host ->
            host != snap &&
                host.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY()) &&
                host.bounds.area() > screenArea * 0.22f &&
                (
                    host.isScrollable ||
                        host.className.contains("RecyclerView", ignoreCase = true) ||
                        host.className.contains("GridView", ignoreCase = true) ||
                        host.className.contains("ScrollView", ignoreCase = true)
                    )
        }
    }

    /**
     * Salta l'analisi per nodi che generano rumore strutturale su pattern UI comuni.
     * I controlli interattivi reali (CTA, campi, media controls) restano analizzati.
     */
    fun shouldSkipPlatformNoiseAnalysis(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (isSkeletonPlaceholder(snap)) return true
        if (isLottieAnimation(snap) && !snap.isInteractiveClickable()) return true
        if (isObscuredByModalOverlay(snap, all)) return true
        if (isMapSurface(snap) && !snap.isMediaControl()) return true
        if (isMediaPlayerSurface(snap)) return true
        if (isInsideMapOrMediaSurface(snap, all) && !snap.isMediaControl()) return true
        if (isInsideWebView(snap, all) && snap.viewId.isNullOrBlank() && !snap.isInteractiveClickable()) return true
        return false
    }

    /**
     * WebView barrier: segnala solo WebView grandi senza figli a11y e senza nodi accessibili sovrapposti.
     */
    fun shouldReportWebViewBarrier(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isWebView()) return false
        if (snap.bounds.width() <= 100 || snap.bounds.height() <= 100) return false
        if (snap.childCount > 0) return false
        val hasAccessibleOverlap = all.any { other ->
            other != snap &&
                other.hasAccessibleName() &&
                android.graphics.Rect.intersects(snap.bounds, other.bounds) &&
                snap.bounds.contains(other.bounds.centerX(), other.bounds.centerY())
        }
        return !hasAccessibleOverlap
    }
}
