/**
 * Regole pure per la selezione delle app da monitorare durante una scansione.
 */
package dev.accessscope.scanner.ui.selection

/**
 * Politica di selezione app: una sola app monitorabile per sessione (stabilità servizio a11y).
 */
object AppSelectionPolicy {

    /** Numero massimo di app selezionabili contemporaneamente. */
    const val MAX_MONITORED_APPS = 1

    /**
     * Esito di un tentativo di aggiungere o rimuovere un package dalla selezione.
     */
    sealed class ToggleResult {
        /** Selezione aggiornata con successo. */
        data class Updated(val selected: Set<String>) : ToggleResult()

        /**
         * Limite raggiunto: la selezione non cambia e va mostrato un avviso all'utente.
         *
         * @property selected Selezione corrente invariata.
         * @property blockedPackage Package che l'utente ha tentato di aggiungere.
         * @property message Messaggio da mostrare nel dialog.
         */
        data class LimitReached(
            val selected: Set<String>,
            val blockedPackage: String,
            val message: String,
        ) : ToggleResult()
    }

    /**
     * Inverte la selezione di [packageName] rispettando [MAX_MONITORED_APPS].
     *
     * @param replaceOnLimit Se true (lancio automatico), sostituisce l'app corrente invece di bloccare.
     */
    fun toggleSelection(
        current: Set<String>,
        packageName: String,
        replaceOnLimit: Boolean = false,
    ): ToggleResult {
        if (packageName in current) {
            return ToggleResult.Updated(current - packageName)
        }
        if (current.size >= MAX_MONITORED_APPS) {
            if (replaceOnLimit) {
                return ToggleResult.Updated(setOf(packageName))
            }
            return ToggleResult.LimitReached(
                selected = current,
                blockedPackage = packageName,
                message = limitMessage(),
            )
        }
        return ToggleResult.Updated(current + packageName)
    }

    /** Riduce la selezione al massimo consentito, preservando l'ordine di inserimento. */
    fun enforceMax(selected: Set<String>): Set<String> =
        if (selected.size <= MAX_MONITORED_APPS) selected
        else selected.take(MAX_MONITORED_APPS).toSet()

    /** Toglie package non più installati da selezione o preferiti. */
    fun filterToInstalled(packages: Set<String>, installed: Set<String>): Set<String> =
        packages.intersect(installed)

    /**
     * Allinea selezione e preferiti all'elenco app installate.
     *
     * @return Coppia (preferiti puliti, selezione valida) dopo eventuale ripristino da preferiti.
     */
    fun sanitizeAgainstInstalled(
        selected: Set<String>,
        favorites: Set<String>,
        installed: Set<String>,
        preferredPrimary: String? = null,
    ): Pair<Set<String>, Set<String>> {
        val validFavorites = filterToInstalled(favorites, installed)
        val validSelected = filterToInstalled(enforceMax(selected), installed)
        val restored = restoreSelectionFromFavorites(
            current = validSelected,
            favorites = validFavorites,
            preferredPrimary = preferredPrimary?.takeIf { it in validFavorites },
        )
        return validFavorites to filterToInstalled(restored, installed)
    }

    fun limitMessage(): String =
        "AccessScope monitora una sola app alla volta. " +
            "Deseleziona l'app corrente prima di sceglierne un'altra, " +
            "oppure attiva il lancio automatico per sostituirla con un tap."

    /**
     * All'aggiunta di un preferito, occupa l'unico slot di monitoraggio (sostituisce l'eventuale selezione).
     */
    fun selectOnFavoriteAdded(packageName: String): Set<String> = setOf(packageName)

    /** Alla rimozione di un preferito, toglie il package dalla selezione. */
    fun selectOnFavoriteRemoved(current: Set<String>, packageName: String): Set<String> =
        current - packageName

    /** Le app preferite non possono essere deselezionate manualmente (solo rimuovendo la stella). */
    fun isFavoriteProtectedFromDeselect(packageName: String, favorites: Set<String>): Boolean =
        packageName in favorites

    fun favoriteDeselectBlockedMessage(): String =
        "Le app preferite restano sempre selezionate per il lancio. Rimuovi la stella per deselezionarle."

    /**
     * Se la selezione è vuota ma esistono preferiti, ripristina il preferito primario.
     *
     * @param preferredPrimary Package da preferire (es. primo preferito in elenco ordinato).
     */
    fun restoreSelectionFromFavorites(
        current: Set<String>,
        favorites: Set<String>,
        preferredPrimary: String? = null,
    ): Set<String> {
        val enforced = enforceMax(current)
        if (enforced.isNotEmpty()) return enforced
        val primary = preferredPrimary?.takeIf { it in favorites } ?: favorites.firstOrNull()
        return primary?.let { setOf(it) } ?: emptySet()
    }
}
