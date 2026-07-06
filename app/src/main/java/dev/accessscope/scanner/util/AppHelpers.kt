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
import dev.accessscope.scanner.service.AccessScopeAccessibilityService

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

        val component = ComponentName(context, serviceClass)
        val expectedFull = component.flattenToString()
        val expectedShort = component.flattenToShortString()
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val entry = splitter.next().trim()
            if (entry.equals(expectedFull, ignoreCase = true)) return true
            if (entry.equals(expectedShort, ignoreCase = true)) return true
        }
        return false
    }

    /** True se il servizio è collegato; se abilitato in impostazioni consideriamo pronto per evitare falsi negativi. */
    fun isAccessibilityServiceConnected(
        context: Context,
        serviceClass: Class<*>,
    ): Boolean =
        AccessScopeAccessibilityService.instance != null ||
            isAccessibilityServiceEnabled(context, serviceClass)

    fun isAccessibilityServiceReady(context: Context, serviceClass: Class<*>): Boolean =
        isAccessibilityServiceEnabled(context, serviceClass)

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

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
}
