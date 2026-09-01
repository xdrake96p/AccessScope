package dev.accessscope.scanner.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressione export YAML Maestro (Beta).
 */
class MaestroYamlExporterTest {

    @Test
    fun export_trustsUpstreamFiltering_doesNotReFilterChromeTapsItself() {
        // L'exporter non ha più un proprio controllo "è un tap SystemUI/chrome" — era una quarta
        // copia dello stesso controllo già applicato a monte da optimize()/sanitizeForPlay()
        // (l'unico chiamante di produzione, FlowStore.writeArtifacts, passa sempre azioni già
        // filtrate da entrambi). Documenta esplicitamente il contratto: se l'exporter viene
        // chiamato direttamente con un tap SystemUI non filtrato (solo nei test, mai in
        // produzione), ora compare nello YAML invece di sparire in silenzio — un canary se in
        // futuro qualcuno bypassasse la pipeline di sanitizzazione prima di chiamare export().
        val yaml = MaestroYamlExporter.export(
            appId = "com.example.app",
            flowName = "Demo",
            actions = listOf(
                RecordedAction.Tap(
                    packageName = "com.android.systemui",
                    viewId = "com.android.systemui:id/back",
                    text = "Indietro",
                ),
            ),
        )
        assertTrue(yaml.contains("Indietro"))
    }

    @Test
    fun export_includesBetaHeaderAndLaunchApp() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example.app",
            flowName = "Demo",
            actions = listOf(
                RecordedAction.Tap(
                    packageName = "com.example.app",
                    viewId = "com.example.app:id/btn_login",
                    text = "Accedi",
                ),
                RecordedAction.InputText(
                    packageName = "com.example.app",
                    text = "user@test.com",
                ),
            ),
        )
        assertTrue(yaml.contains("AccessScope Maestro (Beta)"))
        assertTrue(yaml.contains("appId: \"com.example.app\""))
        assertTrue(yaml.contains("- launchApp"))
        assertTrue(yaml.contains("id: \"btn_login\""))
        assertTrue(yaml.contains("- inputText: \"user@test.com\""))
        assertTrue(yaml.contains("tags:"))
        assertTrue(yaml.contains("- beta"))
    }

    @Test
    fun export_masksPasswordInput() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Pwd",
            actions = listOf(
                RecordedAction.InputText(
                    packageName = "com.example",
                    text = "****",
                    isPassword = true,
                ),
            ),
        )
        assertTrue(yaml.contains("\${PASSWORD}") || yaml.contains("\${PIN}"))
        assertTrue(yaml.contains("secret placeholder") || yaml.contains("CredentialVault") || yaml.contains("PIN"))
        // launchApp + wait (synthetic) + tapOn+inputText steps
        assertTrue(
            MaestroYamlExporter.countSteps(
                listOf(RecordedAction.InputText("com.example", "****", isPassword = true)),
            ) >= 2,
        )
    }

    @Test
    fun export_includesHideKeyboardAndWait() {
        val actions = FlowOptimizer.optimize(
            listOf(
                RecordedAction.LaunchApp("com.example"),
                RecordedAction.InputText("com.example", "hello"),
                RecordedAction.HideKeyboard("com.example"),
                RecordedAction.Tap("com.example", text = "Go"),
            ),
        )
        val yaml = MaestroYamlExporter.export("com.example", "Flow", actions)
        assertTrue(yaml.contains("- hideKeyboard"))
        assertTrue(yaml.contains("waitForAnimationToEnd"))
    }

    @Test
    fun export_usesPointFallback() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Point",
            actions = listOf(
                RecordedAction.Tap(
                    packageName = "com.example",
                    pointPercentX = 50f,
                    pointPercentY = 25f,
                ),
            ),
        )
        // Percentuali intere: Maestro non parsa i decimali su swipe/point (verificato con `maestro check-syntax`).
        assertTrue(yaml.contains("point: \"50%,25%\""))
    }

    @Test
    fun export_newActionTypes() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Extras",
            actions = listOf(
                RecordedAction.LaunchApp("com.example"),
                RecordedAction.DoubleTap("com.example", text = "Zoom"),
                RecordedAction.EraseText("com.example", viewId = "com.example:id/username"),
                RecordedAction.Swipe("com.example", 50f, 80f, 50f, 20f),
                RecordedAction.PressKey("com.example", "Enter"),
                RecordedAction.AssertVisible("com.example", viewId = "com.example:id/home"),
                RecordedAction.AssertNotVisible("com.example", text = "Loading"),
                RecordedAction.ScrollUntilVisible("com.example", visibleText = "POLIZZA"),
                RecordedAction.OpenLink("com.example", "https://example.com"),
                RecordedAction.StopApp("com.example"),
                RecordedAction.RawMaestroYaml("com.example", "copyTextFrom: \"src\""),
            ),
        )
        assertTrue(yaml.contains("- doubleTapOn: \"Zoom\""))
        // eraseText su un campo specifico è tapOn(id) + eraseText bare — eraseText non
        // accetta `id:` in Maestro reale (verificato con `maestro check-syntax`).
        assertTrue(yaml.contains("id: \"username\""))
        assertTrue(yaml.contains("- eraseText"))
        assertTrue(yaml.contains("- swipe:"))
        assertTrue(yaml.contains("start: \"50%,80%\""))
        assertTrue(yaml.contains("- pressKey: Enter"))
        assertTrue(yaml.contains("- assertVisible:"))
        assertTrue(yaml.contains("- assertNotVisible: \"Loading\""))
        assertTrue(yaml.contains("- scrollUntilVisible:"))
        assertTrue(yaml.contains("    element:"))
        assertTrue(yaml.contains("- openLink: \"https://example.com\""))
        assertTrue(yaml.contains("- stopApp"))
        assertTrue(yaml.contains("- copyTextFrom: \"src\""))
    }

    @Test
    fun export_escapesRegexMetacharsInTapText() {
        // Bug: Maestro tratta text/id come regex full-match — un'etichetta con parentesi
        // falliva il match a runtime senza errore di parsing (rottura silenziosa).
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Regex",
            actions = listOf(
                RecordedAction.LaunchApp("com.example"),
                RecordedAction.Tap(packageName = "com.example", text = "Accedi (Beta)"),
            ),
        )
        assertTrue(yaml.contains("- tapOn: \"Accedi \\\\(Beta\\\\)\""))
    }

    @Test
    fun export_scrollUntilVisible_nestsSelectorUnderElement() {
        // Bug: Maestro richiede id/text annidati sotto `element:` per scrollUntilVisible,
        // non direttamente — verificato con `maestro check-syntax` (parse failure altrimenti).
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Scroll",
            actions = listOf(
                RecordedAction.LaunchApp("com.example"),
                RecordedAction.ScrollUntilVisible("com.example", visibleId = "com.example:id/btn_pay"),
            ),
        )
        assertTrue(yaml.contains("- scrollUntilVisible:"))
        assertTrue(yaml.contains("    element:"))
        assertTrue(yaml.contains("      id: \"btn_pay\""))
    }

    @Test
    fun export_eraseTextWithViewId_isTapOnPlusBareEraseText() {
        // Bug: `eraseText: {id: ...}` non è sintassi Maestro valida (Unknown Property: id).
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Erase",
            actions = listOf(
                RecordedAction.LaunchApp("com.example"),
                RecordedAction.EraseText("com.example", viewId = "com.example:id/username"),
            ),
        )
        assertTrue(yaml.contains("- tapOn:\n    id: \"username\"\n- eraseText"))
    }

    @Test
    fun export_addsEnvHeader_whenSecretsPresent() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Secret",
            actions = listOf(
                RecordedAction.LaunchApp("com.example"),
                RecordedAction.InputText("com.example", text = "1234", isPassword = false, viewId = "com.example:id/pin"),
            ),
        )
        // isPinLikeField riconosce l'id "pin" -> placeholder ${PIN} + header env: dichiarato.
        assertTrue(yaml.contains("\${PIN}"))
        assertTrue(yaml.contains("env:"))
        assertTrue(yaml.contains("PIN:"))
    }

    @Test
    fun export_neverEmitsUnknownPlaceholder() {
        // Bug: un selettore assente produceva `"unknown"`, un letterale che fallisce a
        // runtime in modo confuso. Ora uno step senza selettore diventa un commento inerte.
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Empty",
            actions = listOf(
                RecordedAction.LaunchApp("com.example"),
                RecordedAction.AssertVisible("com.example"),
            ),
        )
        assertTrue(!yaml.contains("\"unknown\""))
    }
}
