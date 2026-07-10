package dev.accessscope.cli

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class DeviceInfo(
    val serial: String,
    val model: String,
    val state: String,
    @SerializedName("isEmulator") val isEmulator: Boolean,
)

object DeviceManager {
    private val gson = Gson()

    fun listDevices(): List<DeviceInfo> {
        ensureAdbAvailable()
        val adb = Adb()
        val output = adb.run("devices", "-l").output
        return output.lineSequence()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 2) return@mapNotNull null
                val serial = parts[0]
                val state = parts[1]
                val model = parts.firstOrNull { it.startsWith("model:") }
                    ?.removePrefix("model:")
                    ?.replace("_", " ")
                    ?: serial
                val isEmulator = serial.startsWith("emulator-") ||
                    line.contains("emulator") ||
                    model.contains("sdk_gphone", ignoreCase = true)
                DeviceInfo(serial = serial, model = model, state = state, isEmulator = isEmulator)
            }
            .toList()
    }

    fun listDevicesJson(): String = gson.toJson(listDevices())
}
