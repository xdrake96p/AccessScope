/**
 * Persistenza flussi Maestro YAML + actions.json su filesystem app (Maestro Beta).
 *
 * Competenza I/O: non contiene euristiche di riconoscimento (vedi capture/) né
 * ranking selettori (optimization/). Il gate ZeroEdit vive in quality/.
 */
package dev.accessscope.scanner.recorder

import android.content.Context
import dev.accessscope.scanner.recorder.model.OptimizationContext
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.optimization.selector.SelectorRanker
import dev.accessscope.scanner.recorder.quality.ZeroEditGate
import dev.accessscope.scanner.recorder.quality.ZeroEditReport
import dev.accessscope.scanner.recorder.telemetry.FlowTelemetryCodec
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.DebugSessionLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Store file-based per flussi registrati/importati.
 *
 * Directory: `filesDir/maestro_flows/` con `index.json`, `{id}.yaml`, `{id}.actions.json`.
 */
class FlowStore(context: Context) {

    private val rootDir = File(context.applicationContext.filesDir, DIR_NAME).also { it.mkdirs() }
    private val indexFile = File(rootDir, INDEX_NAME)

    /**
     * Elenco flussi ordinati dal più recente.
     */
    fun listFlows(): List<SavedFlow> =
        loadIndex().map { it.withActionsFlag() }.sortedByDescending { it.createdAtMs }

    /**
     * Salva un nuovo flusso da azioni (applica [FlowOptimizer] + gate ZeroEdit).
     *
     * @param enforceZeroEdit Se `true` (default), heal + lint Error bloccano YAML “sporco”
     *   (salva comunque gli artifacts heal-ati e logga il report; l’UI può leggere [lastZeroEditReport]).
     * @return [SavedFlow] creato.
     */
    fun saveFlow(
        name: String,
        appId: String,
        appLabel: String,
        actions: List<RecordedAction>,
        optimize: Boolean = true,
        optimizationContext: OptimizationContext? = null,
        telemetry: FlowTelemetry? = null,
        enforceZeroEdit: Boolean = true,
    ): SavedFlow {
        val id = UUID.randomUUID().toString().take(8)
        val safeName = name.ifBlank {
            "Flusso ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ITALY).format(Date())}"
        }
        val ctx = optimizationContext ?: OptimizationContext(appId = appId, telemetry = telemetry)
        val optimized = if (optimize) FlowOptimizer.optimize(actions, ctx) else actions
        val report = if (enforceZeroEdit && optimize) {
            ZeroEditGate.evaluate(optimized, healFirst = true)
        } else {
            ZeroEditReport(issues = emptyList(), actions = optimized)
        }
        lastZeroEditReport = report
        val finalActions = report.actions
        if (report.hasErrors) {
            AppFileLogger.info(
                "FlowStore",
                "zero_edit_errors id=$id count=${report.errorCount} msg=${report.userSummary()}",
            )
        }
        val stepCount = writeArtifacts(id, appId, safeName, finalActions, ctx)
        if (telemetry != null) {
            telemetryFile(id).writeText(FlowTelemetryCodec.toJson(telemetry), Charsets.UTF_8)
        }
        val flow = SavedFlow(
            id = id,
            name = safeName,
            appId = appId,
            appLabel = appLabel,
            createdAtMs = System.currentTimeMillis(),
            stepCount = stepCount,
            yamlRelativePath = "$id.yaml",
            hasActionsJson = true,
        )
        // hasActionsJson non è nell'index: ricalcolato a lettura da file.
        writeIndex(
            loadIndex() + flow.copy(hasActionsJson = false),
        )
        return flow
    }

    /** Ultimo report ZeroEdit prodotto da [saveFlow] / [updateFlow] (thread UI). */
    @Volatile
    var lastZeroEditReport: ZeroEditReport? = null
        private set

    /**
     * Aggiorna azioni/nome di un flusso esistente e rigenera YAML.
     *
     * @param id Id flusso.
     * @param actions Nuove azioni (ottimizzate se [optimize]).
     * @param name Nome opzionale aggiornato.
     * @return Flusso aggiornato o `null` se assente.
     */
    fun updateFlow(
        id: String,
        actions: List<RecordedAction>,
        name: String? = null,
        optimize: Boolean = false,
        enforceZeroEdit: Boolean = false,
    ): SavedFlow? {
        val current = loadIndex()
        val existing = current.find { it.id == id } ?: return null
        val safeName = name?.ifBlank { null } ?: existing.name
        val ctx = OptimizationContext(appId = existing.appId)
        val optimized = if (optimize) {
            FlowOptimizer.optimize(actions, ctx)
        } else {
            actions
        }
        val report = if (enforceZeroEdit || optimize) {
            ZeroEditGate.evaluate(optimized, healFirst = true)
        } else {
            ZeroEditReport(issues = emptyList(), actions = optimized)
        }
        lastZeroEditReport = report
        val finalActions = report.actions
        val stepCount = writeArtifacts(id, existing.appId, safeName, finalActions, ctx)
        // #region agent log
        DebugSessionLog.log(
            "H6",
            "FlowStore.updateFlow",
            "editor_save",
            mapOf(
                "id" to id,
                "count" to finalActions.size,
                "types" to finalActions.joinToString(",") { it::class.simpleName.orEmpty() },
                "optimize" to optimize,
                "zeroEditErrors" to report.errorCount,
            ),
        )
        // #endregion
        val updated = existing.copy(
            name = safeName,
            stepCount = stepCount,
            hasActionsJson = true,
        )
        writeIndex(current.map { if (it.id == id) updated.copy(hasActionsJson = false) else it })
        return updated
    }

    /**
     * Promuove i selettori vincenti della catena a campi primari e riscrive artifacts.
     *
     * @param id Id flusso.
     * @param wins Selettori che hanno funzionato a Play (chainIndex > 0).
     * @return Flusso aggiornato o `null`.
     */
    fun applySelectorWins(id: String, wins: List<dev.accessscope.scanner.recorder.model.SelectorWin>): SavedFlow? {
        if (wins.isEmpty()) return null
        val actions = readActions(id) ?: return null
        val updated = actions.map { action ->
            if (action !is RecordedAction.Tap) return@map action
            val win = wins.firstOrNull { w ->
                w.chainIndex > 0 &&
                    w.originalViewId == action.viewId &&
                    w.originalText == action.text
            } ?: return@map action
            val c = win.candidate
            val promoted = action.copy(
                viewId = c.viewId ?: action.viewId,
                text = c.text ?: action.text,
                contentDescription = c.contentDescription ?: action.contentDescription,
                pointPercentX = c.pointPercentX,
                pointPercentY = c.pointPercentY,
                selectorChain = emptyList(),
            )
            promoted.copy(
                selectorChain = SelectorRanker.buildChain(promoted, actions),
            )
        }
        return updateFlow(id, updated, optimize = false)
    }

    /** Legge il contenuto YAML di un flusso. */
    fun readYaml(flow: SavedFlow): String? {
        val file = File(rootDir, flow.yamlRelativePath)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    /** File assoluto YAML per share/FileProvider. */
    fun yamlFile(flow: SavedFlow): File = File(rootDir, flow.yamlRelativePath)

    /** Esiste `{id}.actions.json`? */
    fun hasActions(id: String): Boolean = actionsFile(id).exists()

    /**
     * Carica azioni tipizzate per Play/Edit.
     *
     * @return Lista o `null` se file assente/corrotto.
     */
    fun readActions(id: String): List<RecordedAction>? {
        val file = actionsFile(id)
        if (!file.exists()) return null
        val list = ActionJsonCodec.fromJson(file.readText(Charsets.UTF_8))
        return list.takeIf { it.isNotEmpty() || file.length() > 2 }
    }

    /** Elimina flusso, YAML e actions.json. */
    fun deleteFlow(id: String) {
        val current = loadIndex()
        val target = current.find { it.id == id } ?: return
        File(rootDir, target.yamlRelativePath).delete()
        actionsFile(id).delete()
        telemetryFile(id).delete()
        writeIndex(current.filterNot { it.id == id })
    }

    fun getFlow(id: String): SavedFlow? = loadIndex().find { it.id == id }?.withActionsFlag()

    /**
     * Directory root flussi (per import).
     */
    fun rootDirectory(): File = rootDir

    /**
     * Scrive YAML + actions.json per un flusso.
     *
     * Il YAML **non** viene generato dalle [actions] grezze: passa prima da
     * [FlowOptimizer.sanitizeForPlay], la stessa trasformazione che il Play in-app applica
     * prima di eseguire dal vivo. Prima le due pipeline divergevano — un flusso verde in-app
     * (che beneficiava del riordino overlay bloccanti, dei wait-target e della rinormalizzazione
     * selettori di sanitizeForPlay) poteva esportare un YAML privo di quegli stessi fix e fallire
     * nel `maestro` CLI reale ("verde in-app non predice verde in CI"). `actions.json` resta la
     * versione pre-sanitize: è quella che editor e ri-ottimizzazione al prossimo salvataggio si
     * aspettano.
     *
     * @return Numero di step Maestro nel YAML generato (per [SavedFlow.stepCount]).
     */
    private fun writeArtifacts(
        id: String,
        appId: String,
        name: String,
        actions: List<RecordedAction>,
        context: OptimizationContext,
    ): Int {
        val yamlActions = FlowOptimizer.sanitizeForPlay(actions)
        val yaml = MaestroYamlExporter.export(
            appId,
            name,
            yamlActions,
            context.scanIntel,
            context.telemetry,
        )
        File(rootDir, "$id.yaml").writeText(yaml, Charsets.UTF_8)
        actionsFile(id).writeText(ActionJsonCodec.toJson(actions), Charsets.UTF_8)
        return MaestroYamlExporter.countSteps(yamlActions)
    }

    private fun actionsFile(id: String): File = File(rootDir, "$id.actions.json")

    private fun telemetryFile(id: String): File = File(rootDir, "$id.telemetry.json")

    private fun SavedFlow.withActionsFlag(): SavedFlow =
        copy(hasActionsJson = hasActions(id))

    private fun loadIndex(): List<SavedFlow> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SavedFlow(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            appId = o.getString("appId"),
                            appLabel = o.optString("appLabel", o.getString("appId")),
                            createdAtMs = o.getLong("createdAtMs"),
                            stepCount = o.getInt("stepCount"),
                            yamlRelativePath = o.getString("yamlRelativePath"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(flows: List<SavedFlow>) {
        val arr = JSONArray()
        flows.forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("appId", f.appId)
                    put("appLabel", f.appLabel)
                    put("createdAtMs", f.createdAtMs)
                    put("stepCount", f.stepCount)
                    put("yamlRelativePath", f.yamlRelativePath)
                },
            )
        }
        indexFile.writeText(arr.toString(2), Charsets.UTF_8)
    }

    companion object {
        private const val DIR_NAME = "maestro_flows"
        private const val INDEX_NAME = "index.json"
    }
}
