package dev.accessscope.scanner.util

import android.content.Context
import android.content.Intent

object AppLaunchHelper {

    /** Apre la prima app selezionata che ha un launcher. Ritorna il package aperto. */
    fun launchFirstAvailable(context: Context, packages: Set<String>): String? {
        val pm = context.packageManager
        for (packageName in packages) {
            val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: continue
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return packageName
        }
        return null
    }
}
