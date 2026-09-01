package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regressione [FocusOrderAnalyzer.analyzeHeadingLevels]: Android espone solo un booleano
 * `isHeading`, nessun livello — dedurlo dall'altezza del font (comportamento precedente)
 * produceva salti fantasma su TextView non heading. Bug reale trovato su it.nexi.bff/MPS:
 * "Ragione sociale (Obbligatorio)" (un'etichetta di form) segnalata come salto ~H1→~H3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class FocusOrderHeadingLevelTest {

    @Test
    fun largeTextView_notDeclaredHeading_producesNoViolation() {
        // Prima: looksLikeStructuralHeading() lo ammetteva comunque come candidato solo per
        // l'altezza del font. Ora serve isHeading=true dichiarato dall'app.
        val nodes = listOf(
            snap(text = "Entrate, ultimi 30 giorni", isHeading = false, bounds = Rect(0, 0, 400, 80)),
            snap(text = "Dettaglio movimento", isHeading = false, bounds = Rect(0, 100, 400, 140)),
        )
        val violations = FocusOrderAnalyzer.analyzeHeadingLevels(nodes, "com.example.app", "Home")
        assertTrue(violations.isEmpty())
    }

    @Test
    fun declaredHeadings_realLevelGap_producesViolation() {
        // Bounds larghi (> minTouchTargetPx * 3) per non ricadere nell'euristica "badge/pill"
        // (isLikelyStatusBadge), pensata per widget piccoli, non per titoli a piena larghezza.
        val nodes = listOf(
            snap(text = "Titolo pagina", isHeading = true, bounds = Rect(0, 0, 600, 80)),
            snap(text = "Sottotitolo minore", isHeading = true, bounds = Rect(0, 100, 600, 136)),
        )
        val violations = FocusOrderAnalyzer.analyzeHeadingLevels(nodes, "com.example.app", "Home")
        assertEquals(1, violations.size)
    }

    @Test
    fun requiredFieldHint_neverCountedAsHeading_evenIfMarkedHeading() {
        // Caso reale: l'app marca il label del campo come heading (o il nostro euristico lo
        // farebbe), ma "Ragione sociale (Obbligatorio)" è testo di form, non un titolo.
        val nodes = listOf(
            snap(text = "Titolo pagina", isHeading = true, bounds = Rect(0, 0, 400, 80)),
            snap(text = "Ragione sociale (Obbligatorio)", isHeading = true, bounds = Rect(0, 100, 400, 136)),
        )
        val violations = FocusOrderAnalyzer.analyzeHeadingLevels(nodes, "com.example.app", "Home")
        assertTrue(violations.isEmpty())
    }

    private fun snap(
        className: String = "android.widget.TextView",
        text: String? = "Testo",
        hintText: String? = null,
        isHeading: Boolean = false,
        bounds: Rect = Rect(0, 0, 200, 40),
    ) = NodeSnapshot(
        className = className,
        bounds = bounds,
        viewId = null,
        text = text,
        contentDescription = null,
        hintText = hintText,
        tooltipText = null,
        isClickable = false,
        isLongClickable = false,
        isFocusable = false,
        isEditable = false,
        isCheckable = false,
        isChecked = false,
        isScrollable = false,
        isEnabled = true,
        isPassword = false,
        isHeading = isHeading,
        headingLevel = 0,
        hasLabeledBy = false,
        hasLabelFor = false,
        errorText = null,
        stateDescription = null,
        isExpanded = null,
        collectionRow = -1,
        collectionColumn = -1,
        childCount = 0,
        isAccessibilityExcluded = false,
        isLikelyDecorative = false,
        traversalIndex = 0,
        rangeCurrent = null,
        rangeMin = null,
        rangeMax = null,
        unlabeledActionCount = 0,
        minTextHeightPx = 36,
        minTouchTargetPx = 144,
    )
}
