/**
 * Helper per il caricamento delle app installate e la gestione dei permessi di sistema.
 *
 * Contiene [PackageHelper] per l'enumerazione delle applicazioni e
 * [PermissionHelper] per verificare e aprire le impostazioni di accessibilità e overlay.
 */
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

/**
 * Utility per enumerare le applicazioni installate sul dispositivo.
 */
object PackageHelper {

    /**
     * Carica l'elenco delle app installate, escludendo AccessScope stesso.
     *
     * @param context Contesto Android per l'accesso al [PackageManager].
     * @param includeSystemApps Se `true`, include le app di sistema preinstallate.
     * @return Lista ordinata di [InstalledAppInfo] (app utente prima, poi per etichetta).
     */
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

/**
 * Utility per verificare permessi di sistema e aprire le relative schermate impostazioni.
 */
object PermissionHelper {

    /**
     * Verifica se un servizio di accessibilità è abilitato nelle impostazioni di sistema.
     *
     * @param context Contesto Android.
     * @param serviceClass Classe del servizio di accessibilità da verificare.
     * @return `true` se il servizio compare tra quelli abilitati.
     */
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

    /**
     * Verifica se il servizio di accessibilità è collegato o abilitato in impostazioni.
     *
     * Considera pronto il servizio anche se abilitato ma non ancora connesso,
     * per evitare falsi negativi durante l'avvio.
     *
     * @param context Contesto Android.
     * @param serviceClass Classe del servizio di accessibilità.
     * @return `true` se il servizio è istanziato o abilitato nelle impostazioni.
     */
    fun isAccessibilityServiceConnected(
        context: Context,
        serviceClass: Class<*>,
    ): Boolean =
        AccessScopeAccessibilityService.instance != null ||
            isAccessibilityServiceEnabled(context, serviceClass)

    /**
     * Verifica se il servizio di accessibilità è abilitato e pronto all'uso.
     *
     * @param context Contesto Android.
     * @param serviceClass Classe del servizio di accessibilità.
     * @return `true` se il servizio è abilitato nelle impostazioni di sistema.
     */
    fun isAccessibilityServiceReady(context: Context, serviceClass: Class<*>): Boolean =
        isAccessibilityServiceEnabled(context, serviceClass)

    /**
     * Verifica se l'app ha il permesso di disegnare sopra le altre applicazioni.
     *
     * @param context Contesto Android.
     * @return `true` se il permesso overlay è concesso.
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Crea un intent per aprire le impostazioni del servizio di accessibilità.
     *
     * Su Android 13+ apre direttamente il dettaglio del servizio;
     * su versioni precedenti apre la lista generale dei servizi.
     *
     * @param context Contesto Android.
     * @param serviceClass Classe del servizio di accessibilità.
     * @return [Intent] configurato con [Intent.FLAG_ACTIVITY_NEW_TASK].
     */
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

    /**
     * Crea un intent per gestire il permesso di overlay dell'app corrente.
     *
     * @param context Contesto Android.
     * @return [Intent] verso [Settings.ACTION_MANAGE_OVERLAY_PERMISSION].
     */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    /**
     * Crea un intent per aprire la schermata dettaglio dell'app corrente.
     *
     * @param context Contesto Android.
     * @return [Intent] verso [Settings.ACTION_APPLICATION_DETAILS_SETTINGS].
     */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
}
