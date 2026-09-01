/**
 * Serializzazione JSON delle [RecordedAction] per `{id}.actions.json` (Maestro Beta).
 */
package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.SelectorCandidate
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Codec tipizzato per persistenza e replay in-app.
 */
object ActionJsonCodec {

    /**
     * Serializza la lista azioni in JSON array.
     *
     * @param actions Azioni da salvare.
     * @return JSON indentato.
     */
    fun toJson(actions: List<RecordedAction>): String {
        val arr = JSONArray()
        actions.forEach { arr.put(toObject(it)) }
        return arr.toString(2)
    }

    /**
     * Deserializza JSON array in azioni.
     *
     * @param json Contenuto `{id}.actions.json`.
     * @return Lista azioni; lista vuota se parse fallisce.
     */
    fun fromJson(json: String): List<RecordedAction> = runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                add(fromObject(arr.getJSONObject(i)))
            }
        }
    }.getOrDefault(emptyList())

    private fun toObject(action: RecordedAction): JSONObject = JSONObject().apply {
        put("type", action::class.simpleName)
        put("packageName", action.packageName)
        put("timestampMs", action.timestampMs)
        when (action) {
            is RecordedAction.LaunchApp -> Unit
            is RecordedAction.Tap -> {
                putOpt("viewId", action.viewId)
                putOpt("text", action.text)
                putOpt("contentDescription", action.contentDescription)
                putOpt("pointPercentX", action.pointPercentX)
                putOpt("pointPercentY", action.pointPercentY)
                if (action.executionMode != StepExecutionMode.Required) {
                    put("executionMode", action.executionMode.name)
                }
                putOpt("conditionVisibleId", action.conditionVisibleId)
                putOpt("conditionVisibleText", action.conditionVisibleText)
                if (action.selectorChain.isNotEmpty()) {
                    put(
                        "selectorChain",
                        JSONArray().apply {
                            action.selectorChain.forEach { c ->
                                put(
                                    JSONObject().apply {
                                        putOpt("viewId", c.viewId)
                                        putOpt("text", c.text)
                                        putOpt("contentDescription", c.contentDescription)
                                        putOpt("pointPercentX", c.pointPercentX)
                                        putOpt("pointPercentY", c.pointPercentY)
                                    },
                                )
                            }
                        },
                    )
                }
                if (action.weakSelector) put("weakSelector", true)
            }
            is RecordedAction.DoubleTap -> {
                putOpt("viewId", action.viewId)
                putOpt("text", action.text)
                putOpt("contentDescription", action.contentDescription)
                putOpt("pointPercentX", action.pointPercentX)
                putOpt("pointPercentY", action.pointPercentY)
            }
            is RecordedAction.LongPress -> {
                putOpt("viewId", action.viewId)
                putOpt("text", action.text)
                putOpt("contentDescription", action.contentDescription)
                putOpt("pointPercentX", action.pointPercentX)
                putOpt("pointPercentY", action.pointPercentY)
            }
            is RecordedAction.InputText -> {
                put("text", action.text)
                putOpt("viewId", action.viewId)
                put("isPassword", action.isPassword)
                if (action.executionMode != StepExecutionMode.Required) {
                    put("executionMode", action.executionMode.name)
                }
            }
            is RecordedAction.EraseText -> putOpt("viewId", action.viewId)
            is RecordedAction.Scroll -> put("direction", action.direction.name)
            is RecordedAction.ScrollUntilVisible -> {
                putOpt("visibleId", action.visibleId)
                putOpt("visibleText", action.visibleText)
                put("direction", action.direction.name)
                put("timeoutMs", action.timeoutMs)
            }
            is RecordedAction.Swipe -> {
                put("startPercentX", action.startPercentX)
                put("startPercentY", action.startPercentY)
                put("endPercentX", action.endPercentX)
                put("endPercentY", action.endPercentY)
            }
            is RecordedAction.Back -> Unit
            is RecordedAction.PressKey -> put("key", action.key)
            is RecordedAction.AssertVisible -> {
                putOpt("viewId", action.viewId)
                putOpt("text", action.text)
                put("timeoutMs", action.timeoutMs)
                if (action.executionMode != StepExecutionMode.Required) {
                    put("executionMode", action.executionMode.name)
                }
            }
            is RecordedAction.AssertNotVisible -> {
                putOpt("viewId", action.viewId)
                putOpt("text", action.text)
                put("timeoutMs", action.timeoutMs)
            }
            is RecordedAction.OpenLink -> put("url", action.url)
            is RecordedAction.StopApp -> Unit
            is RecordedAction.Wait -> {
                put("timeoutMs", action.timeoutMs)
                putOpt("visibleId", action.visibleId)
                putOpt("visibleText", action.visibleText)
            }
            is RecordedAction.WaitForAnimation -> putOpt("timeoutMs", action.timeoutMs)
            is RecordedAction.HideKeyboard -> Unit
            is RecordedAction.RawMaestroYaml -> put("yamlLines", action.yamlLines)
        }
    }

    private fun fromObject(o: JSONObject): RecordedAction {
        val pkg = o.optString("packageName", "")
        val ts = o.optLong("timestampMs", System.currentTimeMillis())
        return when (o.getString("type")) {
            "LaunchApp" -> RecordedAction.LaunchApp(pkg, ts)
            "Tap" -> RecordedAction.Tap(
                packageName = pkg,
                viewId = o.optStringOrNull("viewId"),
                text = o.optStringOrNull("text"),
                contentDescription = o.optStringOrNull("contentDescription"),
                pointPercentX = o.optFloatOrNull("pointPercentX"),
                pointPercentY = o.optFloatOrNull("pointPercentY"),
                executionMode = runCatching {
                    StepExecutionMode.valueOf(o.optString("executionMode", "Required"))
                }.getOrDefault(StepExecutionMode.Required),
                conditionVisibleId = o.optStringOrNull("conditionVisibleId"),
                conditionVisibleText = o.optStringOrNull("conditionVisibleText"),
                selectorChain = parseSelectorChain(o.optJSONArray("selectorChain")),
                weakSelector = o.optBoolean("weakSelector", false),
                timestampMs = ts,
            )
            "DoubleTap" -> RecordedAction.DoubleTap(
                packageName = pkg,
                viewId = o.optStringOrNull("viewId"),
                text = o.optStringOrNull("text"),
                contentDescription = o.optStringOrNull("contentDescription"),
                pointPercentX = o.optFloatOrNull("pointPercentX"),
                pointPercentY = o.optFloatOrNull("pointPercentY"),
                timestampMs = ts,
            )
            "LongPress" -> RecordedAction.LongPress(
                packageName = pkg,
                viewId = o.optStringOrNull("viewId"),
                text = o.optStringOrNull("text"),
                contentDescription = o.optStringOrNull("contentDescription"),
                pointPercentX = o.optFloatOrNull("pointPercentX"),
                pointPercentY = o.optFloatOrNull("pointPercentY"),
                timestampMs = ts,
            )
            "InputText" -> RecordedAction.InputText(
                packageName = pkg,
                text = o.optString("text", ""),
                viewId = o.optStringOrNull("viewId"),
                isPassword = o.optBoolean("isPassword", false),
                executionMode = runCatching {
                    StepExecutionMode.valueOf(o.optString("executionMode", "Required"))
                }.getOrDefault(StepExecutionMode.Required),
                timestampMs = ts,
            )
            "EraseText" -> RecordedAction.EraseText(
                packageName = pkg,
                viewId = o.optStringOrNull("viewId"),
                timestampMs = ts,
            )
            "Scroll" -> RecordedAction.Scroll(
                packageName = pkg,
                direction = parseDirection(o.optString("direction", "DOWN")),
                timestampMs = ts,
            )
            "ScrollUntilVisible" -> RecordedAction.ScrollUntilVisible(
                packageName = pkg,
                visibleId = o.optStringOrNull("visibleId"),
                visibleText = o.optStringOrNull("visibleText"),
                direction = parseDirection(o.optString("direction", "DOWN")),
                timeoutMs = o.optLong("timeoutMs", 20_000L),
                timestampMs = ts,
            )
            "Swipe" -> RecordedAction.Swipe(
                packageName = pkg,
                startPercentX = o.optDouble("startPercentX", 50.0).toFloat(),
                startPercentY = o.optDouble("startPercentY", 80.0).toFloat(),
                endPercentX = o.optDouble("endPercentX", 50.0).toFloat(),
                endPercentY = o.optDouble("endPercentY", 20.0).toFloat(),
                timestampMs = ts,
            )
            "Back" -> RecordedAction.Back(pkg, ts)
            "PressKey" -> RecordedAction.PressKey(
                packageName = pkg,
                key = o.optString("key", "Enter"),
                timestampMs = ts,
            )
            "AssertVisible" -> RecordedAction.AssertVisible(
                packageName = pkg,
                viewId = o.optStringOrNull("viewId"),
                text = o.optStringOrNull("text"),
                timeoutMs = o.optLong("timeoutMs", 10_000L),
                executionMode = runCatching {
                    StepExecutionMode.valueOf(o.optString("executionMode", "Required"))
                }.getOrDefault(StepExecutionMode.Required),
                timestampMs = ts,
            )
            "AssertNotVisible" -> RecordedAction.AssertNotVisible(
                packageName = pkg,
                viewId = o.optStringOrNull("viewId"),
                text = o.optStringOrNull("text"),
                timeoutMs = o.optLong("timeoutMs", 5_000L),
                timestampMs = ts,
            )
            "OpenLink" -> RecordedAction.OpenLink(
                packageName = pkg,
                url = o.optString("url", ""),
                timestampMs = ts,
            )
            "StopApp" -> RecordedAction.StopApp(pkg, ts)
            "Wait" -> RecordedAction.Wait(
                packageName = pkg,
                timeoutMs = o.optLong("timeoutMs", 10_000L),
                visibleId = o.optStringOrNull("visibleId"),
                visibleText = o.optStringOrNull("visibleText"),
                timestampMs = ts,
            )
            "WaitForAnimation" -> RecordedAction.WaitForAnimation(
                packageName = pkg,
                timeoutMs = if (o.has("timeoutMs") && !o.isNull("timeoutMs")) o.getLong("timeoutMs") else null,
                timestampMs = ts,
            )
            "HideKeyboard" -> RecordedAction.HideKeyboard(pkg, ts)
            "RawMaestroYaml" -> RecordedAction.RawMaestroYaml(
                packageName = pkg,
                yamlLines = o.optString("yamlLines", ""),
                timestampMs = ts,
            )
            else -> error("Tipo azione sconosciuto: ${o.optString("type")}")
        }
    }

    private fun parseSelectorChain(arr: JSONArray?): List<SelectorCandidate> {
        if (arr == null || arr.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                add(
                    SelectorCandidate(
                        viewId = c.optStringOrNull("viewId"),
                        text = c.optStringOrNull("text"),
                        contentDescription = c.optStringOrNull("contentDescription"),
                        pointPercentX = c.optFloatOrNull("pointPercentX"),
                        pointPercentY = c.optFloatOrNull("pointPercentY"),
                    ),
                )
            }
        }.filterNot { it.isBlank() }
    }

    private fun parseDirection(raw: String): ScrollDirection =
        runCatching { ScrollDirection.valueOf(raw) }.getOrDefault(ScrollDirection.DOWN)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optFloatOrNull(key: String): Float? =
        if (!has(key) || isNull(key)) null else optDouble(key).toFloat()

    private fun JSONObject.putOpt(key: String, value: Any?) {
        if (value != null) put(key, value)
    }
}
