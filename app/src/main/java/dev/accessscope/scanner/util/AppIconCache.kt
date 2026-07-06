package dev.accessscope.scanner.util

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.LinkedHashMap

/**
 * Cache LRU per icone app — evita decode bitmap ripetuti durante lo scroll della lista.
 */
object AppIconCache {

    private const val MAX_ENTRIES = 96
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_ENTRIES
    }

    fun getOrLoad(packageManager: PackageManager, packageName: String): ImageBitmap? {
        synchronized(cache) {
            cache[packageName]?.let { return it }
        }
        val drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val bitmap = drawable.toBitmap(width = 96, height = 96).asImageBitmap()
        synchronized(cache) {
            cache[packageName] = bitmap
        }
        return bitmap
    }

    fun preload(packageManager: PackageManager, packageNames: Collection<String>) {
        packageNames.forEach { getOrLoad(packageManager, it) }
    }

    fun clear() = synchronized(cache) { cache.clear() }
}
