package dev.accessscope.scanner.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import dev.accessscope.scanner.data.InstalledAppInfo

object PackageHelper {

    fun loadInstalledApps(context: Context, includeSystemApps: Boolean): List<InstalledAppInfo> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }

        return installed
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { info -> includeSystemApps || !info.isFilteredSystemApp() }
            .mapNotNull { info ->
                val label = runCatching { pm.getApplicationLabel(info).toString().trim() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: info.packageName
                InstalledAppInfo(
                    packageName = info.packageName,
                    label = label,
                    isSystemApp = info.isSystemApp(),
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ !it.isSystemApp }, { it.label.lowercase() }))
            .toList()
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

    private fun ApplicationInfo.isFilteredSystemApp(): Boolean = isSystemApp()
}

object PermissionHelper {

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) return false

        val expected = ComponentName(context, serviceClass).flattenToString()
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Apre direttamente AccessScope nelle impostazioni accessibilità (Android 13+) o la lista servizi. */
    fun accessibilityServiceIntent(context: Context, serviceClass: Class<*>): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                putExtra(
                    Intent.EXTRA_COMPONENT_NAME,
                    ComponentName(context, serviceClass).flattenToString(),
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
}
