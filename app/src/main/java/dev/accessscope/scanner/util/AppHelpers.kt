/**
 * Helper per il caricamento delle app installate e la gestione dei permessi di sistema.
 *
 * Contiene [PackageHelper] per l'enumerazione delle applicazioni e
 * [PermissionHelper] per verificare e aprire le impostazioni di accessibilità e overlay.
 */
package dev.accessscope.scanner.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
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
 * Stato di bind del servizio di accessibilità AccessScope.
 *
 * Su Samsung/OEM, dopo `force-stop` o alcuni update APK il toggle può restare ON
 * ([EnabledUnbound]) senza `onServiceConnected` — nessun evento ricevuto.
 */
enum class AccessibilityBindState {
    /** Non presente in `enabled_accessibility_services`. */
    Disabled,

    /** Toggle ON ma istanza non viva (tipico post force-stop). */
    EnabledUnbound,

    /** Servizio bound: [AccessScopeAccessibilityService.instance] non null. */
    Connected,
}

/**
 * Utility per verificare permessi di sistema e aprire le relative schermate impostazioni.
 */
object PermissionHelper {

    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
        "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

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
     * Verifica se il servizio di accessibilità è realmente collegato (istanza viva).
     *
     * Non basta il toggle nelle impostazioni: dopo force-stop/install il toggle può
     * restare ON senza `onServiceConnected` (nessun evento ricevuto).
     *
     * @param context Contesto Android (non usato; tenuto per API simmetrica).
     * @param serviceClass Classe del servizio di accessibilità (non usata; simmetria API).
     * @return `true` solo se [AccessScopeAccessibilityService.instance] è non null.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isAccessibilityServiceConnected(
        context: Context,
        serviceClass: Class<*>,
    ): Boolean = AccessScopeAccessibilityService.instance != null

    /**
     * Stato completo toggle+bind del servizio.
     *
     * @param context Contesto Android.
     * @param serviceClass Classe del servizio di accessibilità.
     * @return [AccessibilityBindState] corrente.
     */
    fun accessibilityBindState(
        context: Context,
        serviceClass: Class<*>,
    ): AccessibilityBindState {
        val connected = isAccessibilityServiceConnected(context, serviceClass)
        if (connected) return AccessibilityBindState.Connected
        val enabled = isAccessibilityServiceEnabled(context, serviceClass)
        return if (enabled) {
            AccessibilityBindState.EnabledUnbound
        } else {
            AccessibilityBindState.Disabled
        }
    }

    /**
     * Verifica se il servizio è abilitato **e** collegato (pronto a ricevere eventi).
     *
     * @param context Contesto Android.
     * @param serviceClass Classe del servizio di accessibilità.
     * @return `true` solo in stato [AccessibilityBindState.Connected].
     */
    fun isAccessibilityServiceReady(context: Context, serviceClass: Class<*>): Boolean =
        accessibilityBindState(context, serviceClass) == AccessibilityBindState.Connected

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
     * Su API 33+ prova il dettaglio servizio (`ACCESSIBILITY_DETAILS_SETTINGS`) solo se
     * l’activity risolve; altrimenti (Samsung e OEM) apre la lista generale.
     *
     * @param context Contesto Android.
     * @param serviceClass Classe del servizio di accessibilità.
     * @return [Intent] configurato con [Intent.FLAG_ACTIVITY_NEW_TASK].
     */
    fun accessibilityServiceIntent(context: Context, serviceClass: Class<*>): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val details = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
                putExtra(
                    Intent.EXTRA_COMPONENT_NAME,
                    ComponentName(context, serviceClass).flattenToString(),
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (details.resolveActivity(context.packageManager) != null) {
                return details
            }
        }
        return generalAccessibilitySettingsIntent()
    }

    /**
     * Intent verso la lista generale dei servizi di accessibilità.
     */
    fun generalAccessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    /**
     * Avvia un intent impostazioni con fallback sicuro per evitare crash su OEM custom.
     *
     * Se [primary] non risolve (activity assente), usa subito [fallback] senza crash.
     *
     * @param context Contesto Android.
     * @param primary Intent principale da tentare per primo.
     * @param fallback Intent alternativo se il primario fallisce.
     * @param fallbackToast Messaggio breve mostrato all'utente in caso di fallback.
     * @return `true` se almeno uno degli intent è stato avviato con successo.
     */
    fun safeStartSettingsIntent(
        context: Context,
        primary: Intent,
        fallback: Intent = generalAccessibilitySettingsIntent(),
        fallbackToast: String? = "Aperta lista accessibilità generale",
    ): Boolean {
        if (primary.resolveActivity(context.packageManager) == null) {
            return startFallback(context, fallback, fallbackToast)
        }
        return try {
            context.startActivity(primary)
            true
        } catch (_: ActivityNotFoundException) {
            startFallback(context, fallback, fallbackToast)
        } catch (_: SecurityException) {
            startFallback(context, fallback, fallbackToast)
        }
    }

    private fun startFallback(
        context: Context,
        fallback: Intent,
        fallbackToast: String?,
    ): Boolean {
        return try {
            context.startActivity(fallback)
            if (!fallbackToast.isNullOrBlank()) {
                Toast.makeText(context, fallbackToast, Toast.LENGTH_SHORT).show()
            }
            true
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Impossibile aprire le impostazioni",
                Toast.LENGTH_LONG,
            ).show()
            false
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
