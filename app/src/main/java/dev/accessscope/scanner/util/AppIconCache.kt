/**
 * Cache in memoria per le icone delle applicazioni installate.
 *
 * Evita decode bitmap ripetuti durante lo scroll della lista app,
 * utilizzando una politica LRU con dimensione massima fissa.
 */
package dev.accessscope.scanner.util

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.LinkedHashMap

/**
 * Cache LRU thread-safe per icone app convertite in [ImageBitmap] Compose.
 */
object AppIconCache {

    private const val MAX_ENTRIES = 96
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_ENTRIES
    }

    /**
     * Restituisce l'icona in cache o la carica dal [PackageManager].
     *
     * @param packageManager Gestore package per il recupero dell'icona.
     * @param packageName Identificatore del package dell'app.
     * @return [ImageBitmap] dell'icona a 64×64 px, oppure `null` se il package non esiste.
     */
    fun getOrLoad(packageManager: PackageManager, packageName: String): ImageBitmap? {
        synchronized(cache) {
            cache[packageName]?.let { return it }
        }
        val drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val bitmap = drawable.toBitmap(width = 64, height = 64).asImageBitmap()
        synchronized(cache) {
            cache[packageName] = bitmap
        }
        return bitmap
    }

    /**
     * Precarica in cache le icone per un insieme di package.
     *
     * @param packageManager Gestore package per il recupero delle icone.
     * @param packageNames Collezione di package da precaricare.
     */
    fun preload(packageManager: PackageManager, packageNames: Collection<String>) {
        packageNames.forEach { getOrLoad(packageManager, it) }
    }

    /** Svuota completamente la cache delle icone. */
    fun clear() = synchronized(cache) { cache.clear() }
}
