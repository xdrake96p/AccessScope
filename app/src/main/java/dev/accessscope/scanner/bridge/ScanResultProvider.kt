/**
 * ContentProvider che espone risultati di scansione e stato app ai plugin IDE via adb.
 */
package dev.accessscope.scanner.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.util.PermissionHelper
import org.json.JSONObject

/**
 * Provider esportato per lettura machine-readable di sessioni e stato scansione.
 *
 * URI supportati:
 * - `content://dev.accessscope.scanner.results/status`
 * - `content://dev.accessscope.scanner.results/latest?package={pkg}`
 * - `content://dev.accessscope.scanner.results/session/{sessionId}`
 */
class ScanResultProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        val app = context.applicationContext as? AccessScopeApp ?: return null
        val segments = uri.pathSegments

        val json = when {
            segments.isEmpty() || segments.firstOrNull() == "status" -> buildStatusJson(app)
            segments.firstOrNull() == "latest" -> {
                val packageName = uri.getQueryParameter("package")
                    ?: return jsonCursor(errorJson("missing_package", "Query parameter 'package' is required"))
                app.scanHistoryStore.getLatest(packageName)?.let { session ->
                    app.scanHistoryStore.sessionToJson(session)
                } ?: errorJson("not_found", "No session found for package $packageName")
            }
            segments.firstOrNull() == "session" && segments.size >= 2 -> {
                val sessionId = segments[1]
                app.scanHistoryStore.getSession(sessionId)?.let { session ->
                    app.scanHistoryStore.sessionToJson(session)
                } ?: errorJson("not_found", "Session $sessionId not found")
            }
            else -> errorJson("invalid_uri", "Unsupported URI: $uri")
        }

        return jsonCursor(json)
    }

    private fun buildStatusJson(app: AccessScopeApp): JSONObject {
        val context = app.applicationContext
        val packageInfo = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()

        val state = app.scanRepository.state.value
        return JSONObject().apply {
            put("versionCode", packageInfo?.longVersionCode ?: 0L)
            put("versionName", packageInfo?.versionName.orEmpty())
            put("isScanning", state.isScanning)
            put("accessibilityEnabled", PermissionHelper.isAccessibilityServiceEnabled(
                context,
                AccessScopeAccessibilityService::class.java,
            ))
            put("overlayEnabled", PermissionHelper.canDrawOverlays(context))
            put("selectedPackages", org.json.JSONArray(state.selectedPackages.toList()))
            put("violationCount", state.violations.size)
            put("uniqueScreens", state.uniqueScreens)
        }
    }

    private fun errorJson(code: String, message: String): JSONObject = JSONObject().apply {
        put("error", code)
        put("message", message)
    }

    private fun jsonCursor(json: JSONObject): Cursor =
        MatrixCursor(arrayOf(COLUMN_JSON)).apply { addRow(arrayOf(json.toString())) }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/json"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val COLUMN_JSON = "result"
    }
}
