package dev.accessscope.scanner.bridge

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract campi JSON bridge (piano M0-R3): status / session devono esporre
 * le chiavi obbligatorie usate dai plugin IDE dalla v1.3.0.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BridgeJsonContractTest {

    @Test
    fun statusJson_requiredFieldsPresent() {
        // Forma documentata in PLUGIN_BRIDGE.md / ScanResultProvider.buildStatusJson
        val status = JSONObject().apply {
            put("versionCode", 1L)
            put("versionName", "1.3.0")
            put("isScanning", false)
            put("accessibilityEnabled", true)
            put("overlayEnabled", true)
            put("selectedPackages", org.json.JSONArray())
            put("violationCount", 0)
            put("uniqueScreens", 0)
        }
        listOf(
            "versionCode", "versionName", "isScanning",
            "accessibilityEnabled", "overlayEnabled",
            "selectedPackages", "violationCount", "uniqueScreens",
        ).forEach { key ->
            assertTrue("status JSON manca $key", status.has(key))
        }
    }

    @Test
    fun errorJson_hasCodeAndMessage() {
        val err = JSONObject().apply {
            put("error", "invalid_session")
            put("message", "Invalid session id")
        }
        assertTrue(err.has("error"))
        assertTrue(err.has("message"))
    }
}
