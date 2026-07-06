/**
 * Persistenza locale delle applicazioni preferite dell'utente.
 *
 * Salva e recupera l'insieme di package contrassegnati come favoriti
 * tramite [android.content.SharedPreferences].
 */
package dev.accessscope.scanner.util

import android.content.Context

/**
 * Store per gestire le app preferite dell'utente.
 *
 * @param context Contesto Android; viene usato [Context.getApplicationContext] per evitare leak.
 */
class FavoriteAppsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Restituisce l'insieme corrente di package preferiti.
     *
     * @return Set immutabile di package name, vuoto se nessun preferito è salvato.
     */
    fun getFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    /**
     * Verifica se un'app è tra i preferiti.
     *
     * @param packageName Package da verificare.
     * @return `true` se il package è tra i preferiti salvati.
     */
    fun isFavorite(packageName: String): Boolean = packageName in getFavorites()

    /**
     * Aggiunge o rimuove un'app dai preferiti (toggle).
     *
     * @param packageName Package da aggiungere o rimuovere.
     * @return Set aggiornato dei preferiti dopo l'operazione.
     */
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
