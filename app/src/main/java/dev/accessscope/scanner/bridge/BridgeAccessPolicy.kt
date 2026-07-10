/**
 * Controlli di accesso al bridge IDE ([ScanResultProvider]).
 *
 * Consente solo AccessScope stessa, `adb shell` (uid shell) e app firmate con la stessa
 * chiave release/debug. Blocca altre app installate sul device.
 */
package dev.accessscope.scanner.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process

object BridgeAccessPolicy {

    /**
     * @throws SecurityException se il chiamante non è autorizzato.
     */
    fun enforceBridgeAccess(context: Context) {
        val callingUid = Binder.getCallingUid()
        if (!isCallerAllowed(context, callingUid)) {
            throw SecurityException("Bridge access denied for uid=$callingUid")
        }
    }

    /**
     * @param callingUid UID Android del processo chiamante ([Binder.getCallingUid]).
     */
    fun isCallerAllowed(context: Context, callingUid: Int): Boolean {
        if (callingUid == Process.myUid()) return true
        if (callingUid == Process.SHELL_UID) return true
        if (callingUid == Process.ROOT_UID) return true
        return hasSharedAppSignature(context, callingUid)
    }

    private fun hasSharedAppSignature(context: Context, callingUid: Int): Boolean =
        runCatching {
            val pm = context.packageManager
            val packages = pm.getPackagesForUid(callingUid) ?: return false
            packages.any { pkg ->
                pm.checkSignatures(context.packageName, pkg) == PackageManager.SIGNATURE_MATCH
            }
        }.getOrDefault(false)
}
