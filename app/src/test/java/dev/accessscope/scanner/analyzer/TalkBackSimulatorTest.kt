package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regressione [TalkBackSimulator]: fedeltà del modello di annuncio rispetto a TalkBack reale
 * (non alla vecchia simulazione, che concatenava contentDescription+text+hint sempre).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class TalkBackSimulatorTest {

    @Test
    fun contentDescription_replacesText_notConcatenated() {
        // Bug corretto: prima l'annuncio era "Descrizione, Testo visibile" — TalkBack reale
        // annuncia SOLO la contentDescription quando presente, mai entrambe.
        val node = snap(text = "12,50 €", contentDescription = "Saldo disponibile")
        assertEquals("Saldo disponibile, pulsante", TalkBackSimulator.buildAnnouncement(node))
    }

    @Test
    fun hint_announcedOnlyWhenTextAndDescriptionAreBlank() {
        val filled = snap(className = "android.widget.EditText", text = "mario.rossi", hintText = "Nome utente")
        assertEquals("mario.rossi, campo di testo", TalkBackSimulator.buildAnnouncement(filled))

        val empty = snap(className = "android.widget.EditText", text = null, hintText = "Nome utente")
        assertEquals("Nome utente, campo di testo", TalkBackSimulator.buildAnnouncement(empty))
    }

    @Test
    fun roles_coverSwitchCheckboxRadioSliderSpinnerTab() {
        assertTrue(TalkBackSimulator.buildAnnouncement(snap(className = "android.widget.Switch", text = "Wi-Fi"))!!.contains("interruttore"))
        assertTrue(TalkBackSimulator.buildAnnouncement(snap(className = "android.widget.CheckBox", text = "Ricordami"))!!.contains("casella di controllo"))
        assertTrue(TalkBackSimulator.buildAnnouncement(snap(className = "android.widget.RadioButton", text = "Contanti"))!!.contains("pulsante di opzione"))
        assertTrue(TalkBackSimulator.buildAnnouncement(snap(className = "android.widget.SeekBar", text = "Volume"))!!.contains("cursore"))
        assertTrue(TalkBackSimulator.buildAnnouncement(snap(className = "android.widget.Spinner", text = "Paese"))!!.contains("selettore"))
        assertTrue(TalkBackSimulator.buildAnnouncement(snap(className = "com.google.android.material.tabs.TabLayout\$Tab", text = "Home"))!!.contains("scheda"))
    }

    @Test
    fun checkable_announcesSelectedState() {
        val checked = snap(className = "android.widget.CheckBox", text = "Ricordami", isCheckable = true, isChecked = true)
        assertTrue(TalkBackSimulator.buildAnnouncement(checked)!!.endsWith("selezionato"))
        assertFalse(TalkBackSimulator.buildAnnouncement(checked)!!.endsWith("non selezionato"))

        val unchecked = checked.copy(isChecked = false)
        assertTrue(TalkBackSimulator.buildAnnouncement(unchecked)!!.endsWith("non selezionato"))
    }

    @Test
    fun disabledAndPassword_areAnnounced() {
        val disabled = snap(text = "Conferma", isEnabled = false)
        assertTrue(TalkBackSimulator.buildAnnouncement(disabled)!!.contains("disabilitato"))

        val password = snap(className = "android.widget.EditText", text = "••••", isPassword = true)
        assertTrue(TalkBackSimulator.buildAnnouncement(password)!!.contains("password"))
    }

    @Test
    fun heading_isAnnounced() {
        val heading = snap(text = "Dati personali", isHeading = true)
        assertTrue(TalkBackSimulator.buildAnnouncement(heading)!!.contains("intestazione"))
    }

    @Test
    fun range_announcesPercentage() {
        val slider = snap(className = "android.widget.SeekBar", text = null, rangeCurrent = 30f, rangeMin = 0f, rangeMax = 60f)
        assertTrue(TalkBackSimulator.buildAnnouncement(slider)!!.contains("50 per cento"))
    }

    @Test
    fun collectionPosition_announcesRowAndColumn() {
        val cell = snap(text = "15", collectionRow = 1, collectionColumn = 2)
        val announced = TalkBackSimulator.buildAnnouncement(cell)!!
        assertTrue(announced.contains("riga 2"))
        assertTrue(announced.contains("colonna 3"))
    }

    @Test
    fun simulate_flagsSilentElement() {
        val silent = snap(className = "android.view.View", text = null, contentDescription = null, isClickable = true)
        val findings = TalkBackSimulator.simulate(listOf(silent), "com.example", "Home")
        assertTrue(findings.any { it.issue.contains("non avrebbe testo") })
    }

    @Test
    fun simulate_excludesAccessibilityExcludedNodes() {
        // Un nodo importantForAccessibility=false non riceve mai focus TalkBack reale:
        // prima del refactor su NodeSnapshot non potevamo escluderlo qui.
        val excluded = snap(text = null, isClickable = true, isAccessibilityExcluded = true)
        val findings = TalkBackSimulator.simulate(listOf(excluded), "com.example", "Home")
        assertTrue(findings.any { it.issue.contains("Nessun elemento focalizzabile") })
    }

    @Test
    fun simulate_noFocusableNodes_flagsUnnavigableScreen() {
        val onlyText = snap(className = "android.widget.TextView", text = "Testo statico", isClickable = false)
        val findings = TalkBackSimulator.simulate(listOf(onlyText), "com.example", "Home")
        assertTrue(findings.any { it.issue.contains("Nessun elemento focalizzabile") })
    }

    @Test
    fun simulate_majoritySilent_flagsAggregateFinding() {
        val nodes = listOf(
            snap(className = "android.view.View", text = null, contentDescription = null, isClickable = true),
            snap(className = "android.view.View", text = null, contentDescription = null, isClickable = true),
            snap(text = "OK", isClickable = true),
        )
        val findings = TalkBackSimulator.simulate(nodes, "com.example", "Home")
        assertTrue(findings.any { it.issue.contains("Oltre il 50%") })
    }

    @Test
    fun simulate_wellLabeledScreen_producesNoFindings() {
        val nodes = listOf(
            snap(text = "Accedi", isClickable = true),
            snap(className = "android.widget.EditText", text = "user@example.com", isEditable = true),
        )
        val findings = TalkBackSimulator.simulate(nodes, "com.example", "Home")
        assertTrue(findings.isEmpty())
    }

    // --- Regressione falsi positivi su container generici (banking app reale it.nexi.bff:
    // 20/20 violazioni SCREEN_READER_ANNOUNCEMENT erano su RelativeLayout/ScrollView/ViewGroup/
    // RecyclerView/ViewPager/HorizontalScrollView/FrameLayout/LinearLayout, mai su widget reali) ---

    @Test
    fun simulate_clickableRowContainerWithLabeledChild_isNotFlaggedSilent() {
        // Riga cliccabile senza cd/text proprio (pattern comune: RelativeLayout come "card" di
        // lista) ma con una TextView figlia non focalizzabile che porta il vero contenuto —
        // TalkBack reale annuncerebbe il testo del figlio, non il silenzio.
        val row = snap(
            className = "android.widget.RelativeLayout",
            text = null,
            isClickable = true,
            bounds = Rect(0, 0, 400, 120),
        )
        val label = snap(
            className = "android.widget.TextView",
            text = "Saldo disponibile: 1.250,00 €",
            isClickable = false,
            bounds = Rect(20, 20, 380, 100),
        )
        val findings = TalkBackSimulator.simulate(listOf(row, label), "it.nexi.bff", "Home")
        assertTrue(findings.none { it.issue.contains("non avrebbe testo") })
    }

    @Test
    fun simulate_scrollableRecyclerViewWithoutOwnLabel_isNotFlaggedSilent() {
        // RecyclerView cliccabile (raro ma possibile) e scrollabile, senza cd/text proprio, ma
        // con voci di lista etichettate al suo interno.
        val list = snap(
            className = "androidx.recyclerview.widget.RecyclerView",
            text = null,
            isClickable = true,
            isScrollable = true,
            bounds = Rect(0, 0, 400, 800),
        )
        val item = snap(
            className = "android.widget.TextView",
            text = "Bonifico a Mario Rossi",
            isClickable = false,
            bounds = Rect(10, 10, 390, 60),
        )
        val findings = TalkBackSimulator.simulate(listOf(list, item), "it.nexi.bff", "Movimenti")
        assertTrue(findings.none { it.issue.contains("non avrebbe testo") })
    }

    @Test
    fun simulate_scrollOnlyContainerWithoutOwnLabel_isNotAFocusCandidate() {
        // isScrollable da solo non basta più a rendere un container un focus stop: ScrollView,
        // ViewPager, HorizontalScrollView senza cd/text proprio e senza essere anche clickable/
        // focusable non sono candidati — TalkBack naviga direttamente sui figli, non li ferma qui.
        val viewPager = snap(
            className = "androidx.viewpager.widget.ViewPager",
            text = null,
            isClickable = false,
            isFocusable = false,
            isScrollable = true,
        )
        val findings = TalkBackSimulator.simulate(listOf(viewPager), "it.nexi.bff", "Home")
        assertTrue(findings.none { it.issue.contains("non avrebbe testo") })
        // Nessun candidato al focus: la schermata risulta innavigabile (diagnostica corretta,
        // non un finding puntuale sul container).
        assertTrue(findings.any { it.issue.contains("Nessun elemento focalizzabile") })
    }

    @Test
    fun simulate_scrollContainerWithOwnContentDescription_remainsFocusCandidate() {
        // Un container scrollabile con un nome accessibile esplicito è stato reso un target
        // intenzionale dallo sviluppatore: resta candidato e, se davvero silenzioso, va segnalato.
        val labeled = snap(
            className = "android.widget.ScrollView",
            text = null,
            contentDescription = "Lista transazioni",
            isClickable = false,
            isScrollable = true,
        )
        val findings = TalkBackSimulator.simulate(listOf(labeled), "it.nexi.bff", "Movimenti")
        assertTrue(findings.none { it.issue.contains("non avrebbe testo") })
        assertTrue(findings.none { it.issue.contains("Nessun elemento focalizzabile") })
    }

    @Test
    fun simulate_clickableContainerWithoutAnyDescendant_isStillFlaggedSilent() {
        // Precisione del fix: un container generico va escluso solo quando un discendente porta
        // davvero il contenuto. Senza altri nodi in schermata (nessun discendente da cui ereditare
        // un'etichetta) resta correttamente segnalato come silenzioso.
        val emptyContainer = snap(
            className = "android.widget.FrameLayout",
            text = null,
            isClickable = true,
        )
        val findings = TalkBackSimulator.simulate(listOf(emptyContainer), "it.nexi.bff", "Home")
        assertTrue(findings.any { it.issue.contains("non avrebbe testo") })
    }

    @Test
    fun simulate_unlabeledCustomClickableLeafView_isStillFlaggedSilent() {
        // Custom View cliccabile senza figli e senza cd/text: non è un container di layout noto
        // (non contiene "layout"/"viewgroup"/"scrollview"/ecc. nel nome classe), quindi il fix sui
        // container non si applica — resta correttamente segnalata.
        val customView = snap(
            className = "it.nexi.bff.widget.TapTargetView",
            text = null,
            contentDescription = null,
            isClickable = true,
        )
        val findings = TalkBackSimulator.simulate(listOf(customView), "it.nexi.bff", "Home")
        assertTrue(findings.any { it.issue.contains("non avrebbe testo") })
    }

    private fun snap(
        className: String = "android.widget.Button",
        text: String? = "Testo",
        contentDescription: String? = null,
        hintText: String? = null,
        isClickable: Boolean = true,
        isEditable: Boolean = false,
        isFocusable: Boolean = false,
        isScrollable: Boolean = false,
        isCheckable: Boolean = false,
        isChecked: Boolean = false,
        isEnabled: Boolean = true,
        isPassword: Boolean = false,
        isHeading: Boolean = false,
        isExpanded: Boolean? = null,
        stateDescription: String? = null,
        isAccessibilityExcluded: Boolean = false,
        collectionRow: Int = -1,
        collectionColumn: Int = -1,
        rangeCurrent: Float? = null,
        rangeMin: Float? = null,
        rangeMax: Float? = null,
        bounds: Rect = Rect(0, 0, 200, 80),
    ) = NodeSnapshot(
        className = className,
        bounds = bounds,
        viewId = null,
        text = text,
        contentDescription = contentDescription,
        hintText = hintText,
        tooltipText = null,
        isClickable = isClickable,
        isLongClickable = false,
        isFocusable = isFocusable,
        isEditable = isEditable,
        isCheckable = isCheckable,
        isChecked = isChecked,
        isScrollable = isScrollable,
        isEnabled = isEnabled,
        isPassword = isPassword,
        isHeading = isHeading,
        headingLevel = 0,
        hasLabeledBy = false,
        hasLabelFor = false,
        errorText = null,
        stateDescription = stateDescription,
        isExpanded = isExpanded,
        collectionRow = collectionRow,
        collectionColumn = collectionColumn,
        childCount = 0,
        isAccessibilityExcluded = isAccessibilityExcluded,
        isLikelyDecorative = false,
        traversalIndex = 0,
        rangeCurrent = rangeCurrent,
        rangeMin = rangeMin,
        rangeMax = rangeMax,
        unlabeledActionCount = 0,
        minTextHeightPx = 36,
        minTouchTargetPx = 144,
    )
}
