package dev.accessscope.scanner.util

import android.content.Context

class FavoriteAppsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun isFavorite(packageName: String): Boolean = packageName in getFavorites()

    fun toggle(packageName: String): Set<String> {
        val updated = getFavorites().toMutableSet()
        if (!updated.add(packageName)) updated.remove(packageName)
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
        return updated
    }

    companion object {
        private const val PREFS_NAME = "accessscope_favorites"
        private const val KEY_FAVORITES = "favorite_packages"
    }
}
