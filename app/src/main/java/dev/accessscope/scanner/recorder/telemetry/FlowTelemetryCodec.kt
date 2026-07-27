/**
 * Serializzazione JSON telemetria registrazione Maestro.
 */
package dev.accessscope.scanner.recorder.telemetry

import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.RecordedTransition
import dev.accessscope.scanner.recorder.model.ScreenSnapshot
import dev.accessscope.scanner.recorder.model.TransitionKind
import org.json.JSONArray
import org.json.JSONObject

/** Codec per `{id}.telemetry.json`. */
object FlowTelemetryCodec {

    /**
     * Serializza telemetria in JSON.
     *
     * @param telemetry Telemetria da salvare.
     * @return JSON indentato.
     */
    fun toJson(telemetry: FlowTelemetry): String {
        val root = JSONObject()
        val snaps = JSONArray()
        telemetry.snapshots.forEach { s ->
            snaps.put(
                JSONObject().apply {
                    put("fingerprint", s.fingerprint)
                    putOpt("title", s.title)
                    put("packageName", s.packageName)
                    put("timestampMs", s.timestampMs)
                    put("actionIndex", s.actionIndex)
                },
            )
        }
        root.put("snapshots", snaps)
        val trans = JSONArray()
        telemetry.transitions.forEach { t ->
            trans.put(
                JSONObject().apply {
                    put("fromIndex", t.fromIndex)
                    put("toIndex", t.toIndex)
                    put("deltaMs", t.deltaMs)
                    putOpt("fromFingerprint", t.fromFingerprint)
                    putOpt("toFingerprint", t.toFingerprint)
                    put("kind", t.kind.name)
                },
            )
        }
        root.put("transitions", trans)
        return root.toString(2)
    }

    /**
     * Deserializza telemetria da JSON.
     *
     * @param json Contenuto file telemetry.
     * @return Telemetria o vuota se parse fallisce.
     */
    fun fromJson(json: String): FlowTelemetry = runCatching {
        val root = JSONObject(json)
        val snaps = root.optJSONArray("snapshots") ?: JSONArray()
        val snapshots = buildList {
            for (i in 0 until snaps.length()) {
                val o = snaps.getJSONObject(i)
                add(
                    ScreenSnapshot(
                        fingerprint = o.getString("fingerprint"),
                        title = o.optString("title").takeIf { it.isNotBlank() },
                        packageName = o.optString("packageName", ""),
                        timestampMs = o.optLong("timestampMs"),
                        actionIndex = o.optInt("actionIndex"),
                    ),
                )
            }
        }
        val trans = root.optJSONArray("transitions") ?: JSONArray()
        val transitions = buildList {
            for (i in 0 until trans.length()) {
                val o = trans.getJSONObject(i)
                add(
                    RecordedTransition(
                        fromIndex = o.getInt("fromIndex"),
                        toIndex = o.getInt("toIndex"),
                        deltaMs = o.getLong("deltaMs"),
                        fromFingerprint = o.optString("fromFingerprint").takeIf { it.isNotBlank() },
                        toFingerprint = o.optString("toFingerprint").takeIf { it.isNotBlank() },
                        kind = runCatching {
                            TransitionKind.valueOf(o.getString("kind"))
                        }.getOrDefault(TransitionKind.SameScreen),
                    ),
                )
            }
        }
        FlowTelemetry(snapshots = snapshots, transitions = transitions)
    }.getOrDefault(FlowTelemetry())
}
