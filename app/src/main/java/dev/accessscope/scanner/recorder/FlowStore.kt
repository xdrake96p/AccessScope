/**
 * Persistenza flussi Maestro YAML + actions.json su filesystem app (Maestro Beta).
 */
package dev.accessscope.scanner.recorder

import android.content.Context
import dev.accessscope.scanner.recorder.model.OptimizationContext
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.telemetry.FlowTelemetryCodec
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
     * Salva un nuovo flusso da azioni (applica [FlowOptimizer] prima di scrivere).
     *
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
    ): SavedFlow {
        val id = UUID.randomUUID().toString().take(8)
        val safeName = name.ifBlank {
            "Flusso ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ITALY).format(Date())}"
        }
        val ctx = optimizationContext ?: OptimizationContext(appId = appId, telemetry = telemetry)
        val finalActions = if (optimize) FlowOptimizer.optimize(actions, ctx) else actions
        writeArtifacts(id, appId, safeName, finalActions, ctx)
        if (telemetry != null) {
            telemetryFile(id).writeText(FlowTelemetryCodec.toJson(telemetry), Charsets.UTF_8)
        }
        val flow = SavedFlow(
            id = id,
            name = safeName,
            appId = appId,
            appLabel = appLabel,
            createdAtMs = System.currentTimeMillis(),
            stepCount = MaestroYamlExporter.countSteps(finalActions),
            yamlRelativePath = "$id.yaml",
            hasActionsJson = true,
        )
        // hasActionsJson non è nell'index: ricalcolato a lettura da file.
        writeIndex(
            loadIndex() + flow.copy(hasActionsJson = false),
        )
        return flow
    }

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
    ): SavedFlow? {
        val current = loadIndex()
        val existing = current.find { it.id == id } ?: return null
        val safeName = name?.ifBlank { null } ?: existing.name
        val finalActions = if (optimize) {
            FlowOptimizer.optimize(actions, OptimizationContext(appId = existing.appId))
        } else {
            actions
        }
        writeArtifacts(id, existing.appId, safeName, finalActions, OptimizationContext(appId = existing.appId))
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
            ),
        )
        // #endregion
        val updated = existing.copy(
            name = safeName,
            stepCount = MaestroYamlExporter.countSteps(finalActions),
            hasActionsJson = true,
        )
        writeIndex(current.map { if (it.id == id) updated.copy(hasActionsJson = false) else it })
        return updated
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

    private fun writeArtifacts(
        id: String,
        appId: String,
        name: String,
        actions: List<RecordedAction>,
        context: OptimizationContext,
    ) {
        val yaml = MaestroYamlExporter.export(
            appId,
            name,
            actions,
            context.scanIntel,
            context.telemetry,
        )
        File(rootDir, "$id.yaml").writeText(yaml, Charsets.UTF_8)
        actionsFile(id).writeText(ActionJsonCodec.toJson(actions), Charsets.UTF_8)
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
