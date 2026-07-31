/**
 * Catalogo tipi step inseribili dall’editor Maestro (competence: UI catalog, non Compose).
 */
package dev.accessscope.scanner.ui.maestro

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.ScrollDirection

/**
 * Categoria visuale nel picker «Aggiungi step».
 */
enum class InsertStepCategory(val labelIt: String) {
    Interaction("Interazione"),
    Wait("Attesa"),
    Assert("Verifica"),
    Advanced("App / avanzate"),
}

/**
 * Voce del catalogo: etichetta utente + factory azione default.
 *
 * @property category Gruppo UI.
 * @property label Nome breve nel dialog.
 * @property description Hint sotto il titolo.
 * @property factory Crea l’azione con package del flusso.
 */
data class InsertStepOption(
    val category: InsertStepCategory,
    val label: String,
    val description: String,
    val factory: (packageName: String) -> RecordedAction,
)

/**
 * Elenco completo e ordinato delle opzioni di inserimento step.
 */
object InsertStepCatalog {

    /**
     * Tutte le voci del picker, raggruppate per [InsertStepCategory].
     *
     * @return Lista piatta (il dialog raggruppa per category).
     */
    fun all(): List<InsertStepOption> = listOf(
        InsertStepOption(
            InsertStepCategory.Interaction, "tapOn (id)", "Tap per resource-id",
        ) { pkg -> RecordedAction.Tap(pkg, viewId = "button") },
        InsertStepOption(
            InsertStepCategory.Interaction, "tapOn (testo)", "Tap per testo visibile",
        ) { pkg -> RecordedAction.Tap(pkg, text = "OK") },
        InsertStepOption(
            InsertStepCategory.Interaction, "doubleTapOn", "Doppio tap",
        ) { pkg -> RecordedAction.DoubleTap(pkg, text = "OK") },
        InsertStepOption(
            InsertStepCategory.Interaction, "longPressOn", "Pressione prolungata",
        ) { pkg -> RecordedAction.LongPress(pkg, text = "OK") },
        InsertStepOption(
            InsertStepCategory.Interaction, "swipe", "Swipe verticale (es. scroll lista)",
        ) { pkg ->
            RecordedAction.Swipe(
                pkg,
                startPercentX = 50f, startPercentY = 80f,
                endPercentX = 50f, endPercentY = 20f,
            )
        },
        InsertStepOption(
            InsertStepCategory.Interaction, "scroll", "Scroll direzione DOWN",
        ) { pkg -> RecordedAction.Scroll(pkg, direction = ScrollDirection.DOWN) },
        InsertStepOption(
            InsertStepCategory.Interaction, "scrollUntilVisible", "Scroll finché compare un target",
        ) { pkg -> RecordedAction.ScrollUntilVisible(pkg, visibleText = "OK") },
        InsertStepOption(
            InsertStepCategory.Interaction, "inputText", "Digita testo in un campo",
        ) { pkg -> RecordedAction.InputText(pkg, text = "") },
        InsertStepOption(
            InsertStepCategory.Interaction, "eraseText", "Cancella testo campo",
        ) { pkg -> RecordedAction.EraseText(pkg) },
        InsertStepOption(
            InsertStepCategory.Interaction, "hideKeyboard", "Nascondi tastiera",
        ) { pkg -> RecordedAction.HideKeyboard(pkg) },
        InsertStepOption(
            InsertStepCategory.Interaction, "pressKey", "Tasto hardware (Enter/Back/…)",
        ) { pkg -> RecordedAction.PressKey(pkg, key = "Enter") },
        InsertStepOption(
            InsertStepCategory.Interaction, "back", "Pulsante Indietro",
        ) { pkg -> RecordedAction.Back(pkg) },
        InsertStepOption(
            InsertStepCategory.Wait, "wait (1s)", "Attesa cieca 1000 ms",
        ) { pkg -> RecordedAction.Wait(pkg, timeoutMs = 1_000L) },
        InsertStepOption(
            InsertStepCategory.Wait, "wait (2s)", "Attesa cieca 2000 ms",
        ) { pkg -> RecordedAction.Wait(pkg, timeoutMs = 2_000L) },
        InsertStepOption(
            InsertStepCategory.Wait, "waitUntil (testo)", "Attende elemento visibile",
        ) { pkg -> RecordedAction.Wait(pkg, timeoutMs = 10_000L, visibleText = "OK") },
        InsertStepOption(
            InsertStepCategory.Wait, "waitForAnimation", "Attende fine animazione UI",
        ) { pkg -> RecordedAction.WaitForAnimation(pkg) },
        InsertStepOption(
            InsertStepCategory.Assert, "assertVisible", "Verifica elemento presente",
        ) { pkg -> RecordedAction.AssertVisible(pkg, text = "OK") },
        InsertStepOption(
            InsertStepCategory.Assert, "assertNotVisible", "Verifica elemento assente",
        ) { pkg -> RecordedAction.AssertNotVisible(pkg, text = "OK") },
        InsertStepOption(
            InsertStepCategory.Advanced, "openLink", "Apri URL",
        ) { pkg -> RecordedAction.OpenLink(pkg, url = "https://") },
        InsertStepOption(
            InsertStepCategory.Advanced, "stopApp", "Chiudi l’app target",
        ) { pkg -> RecordedAction.StopApp(pkg) },
        InsertStepOption(
            InsertStepCategory.Advanced, "Raw YAML", "Frammento Maestro grezzo",
        ) { pkg ->
            RecordedAction.RawMaestroYaml(pkg, yamlLines = "- tapOn: \"OK\"")
        },
    )

    /**
     * Raggruppa [all] per categoria nell’ordine enum.
     *
     * @return Mappa categoria → opzioni.
     */
    fun byCategory(): Map<InsertStepCategory, List<InsertStepOption>> =
        InsertStepCategory.entries.associateWith { cat ->
            all().filter { it.category == cat }
        }
}
