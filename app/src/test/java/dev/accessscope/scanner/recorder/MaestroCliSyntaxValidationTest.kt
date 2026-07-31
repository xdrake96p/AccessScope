package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.OptimizationContext
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Valida lo YAML esportato dalla pipeline reale (optimize → sanitizeForPlay → export, la stessa
 * di [FlowStore.writeArtifacts]) contro il vero parser Maestro (`maestro check-syntax`), non
 * solo contro le nostre assunzioni sulla sintassi — un `maestro test` reale sullo YAML esportato
 * aveva già fallito una volta su comandi che i nostri test JVM consideravano corretti (Settimana
 * 2, `docs/PROJECT.md`).
 *
 * Se il binario `maestro` non è installato (dev locale senza Maestro), il test si salta: non
 * deve rompere `./gradlew test` su una macchina senza il CLI. In CI, dove il binario viene
 * installato prima della build (vedi `.github/workflows/release.yml`), diventa un gate reale
 * pre-release — una regressione di sintassi nell'exporter blocca la release invece di essere
 * scoperta solo a mano.
 */
class MaestroCliSyntaxValidationTest {

    @Test
    fun exportedYaml_passesRealMaestroCliCheckSyntax() {
        val maestroBinary = resolveMaestroBinary()
        assumeTrue("maestro CLI not installed locally — skipping real-CLI syntax gate", maestroBinary != null)

        val pkg = "com.example.app"
        val raw = listOf(
            RecordedAction.LaunchApp(pkg),
            RecordedAction.Tap(pkg, viewId = "com.example.app:id/login_button", text = "Login"),
            RecordedAction.InputText(pkg, "mario.rossi", viewId = "com.example.app:id/username"),
            RecordedAction.InputText(pkg, "hunter2", viewId = "com.example.app:id/password", isPassword = true),
            // Tap "point-only" con coordinate ormai obsolete: sopravvive a optimize() ma va
            // scartato da sanitizeForPlay prima dell'export (vedi FlowStoreTest).
            RecordedAction.Tap(pkg, pointPercentX = 0.2f, pointPercentY = 0.9f),
            RecordedAction.Tap(pkg, text = "Continua (Beta)"),
            RecordedAction.Tap(pkg, viewId = "com.example.app:id/submit", text = "Submit"),
        )
        val optimized = FlowOptimizer.optimize(raw, OptimizationContext(appId = pkg))
        val yamlActions = FlowOptimizer.sanitizeForPlay(optimized)
        val yaml = MaestroYamlExporter.export(pkg, "CLI syntax gate flow", yamlActions)

        val flowFile = File.createTempFile("accessscope-cli-gate-", ".yaml")
        try {
            flowFile.writeText(yaml)
            val process = ProcessBuilder(maestroBinary, "check-syntax", flowFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(60, TimeUnit.SECONDS)
            check(completed) { "maestro check-syntax timed out" }
            check(process.exitValue() == 0 && output.contains("OK")) {
                "maestro check-syntax rejected exported YAML:\n$output\n--- YAML ---\n$yaml"
            }
        } finally {
            flowFile.delete()
        }
    }

    private fun resolveMaestroBinary(): String? {
        val home = System.getProperty("user.home")
        val candidates = listOf(
            System.getenv("MAESTRO_CLI_PATH"),
            "$home/.maestro/bin/maestro",
            "/usr/local/bin/maestro",
        ).filterNotNull()
        return candidates.firstOrNull { File(it).canExecute() }
    }
}
