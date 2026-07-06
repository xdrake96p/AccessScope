package dev.accessscope.scanner.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import dev.accessscope.scanner.data.InstalledAppInfo

object PackageHelper {

    fun loadLaunchableApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        return activities
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: packageName
                InstalledAppInfo(packageName = packageName, label = label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

object PermissionHelper {

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) return false

        val expected = "${context.packageName}/${serviceClass.name}"
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

    fun accessibilitySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
}
