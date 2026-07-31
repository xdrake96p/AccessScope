package dev.accessscope.cli

object PluginVersionChecker {
    private val semverPattern = Regex("""(\d+)\.(\d+)\.(\d+)""")

    /**
     * Confronta la versione del plugin IDE in uso con la versione minima richiesta dal manifest.
     *
     * Restituisce un messaggio di avviso (mai un'eccezione bloccante): un plugin IDE non
     * aggiornato non deve impedire install/setup-check, solo segnalare che alcune funzionalità
     * potrebbero non essere allineate all'ultima release dell'app.
     * Se [ACCESS_SCOPE_PLUGIN_VERSION] non è impostata (invocazione CLI diretta), il check viene saltato.
     */
    fun compatibilityWarning(manifest: ReleaseManifest): String? {
        val minRequired = manifest.minPluginVersion?.takeIf { it.isNotBlank() } ?: return null
        val pluginVersion = System.getenv("ACCESS_SCOPE_PLUGIN_VERSION")?.takeIf { it.isNotBlank() }
            ?: return null
        if (compareSemver(pluginVersion, minRequired) < 0) {
            return "IDE plugin v$pluginVersion is older than the recommended v$minRequired. " +
                "Some features may be out of sync with the app — consider updating the plugin."
        }
        return null
    }

    internal fun compareSemver(left: String, right: String): Int {
        val leftParts = parseSemver(left) ?: return -1
        val rightParts = parseSemver(right) ?: return 1
        for (index in 0..2) {
            val delta = leftParts[index] - rightParts[index]
            if (delta != 0) return delta
        }
        return 0
    }

    private fun parseSemver(raw: String): IntArray? {
        val match = semverPattern.find(raw.trim()) ?: return null
        return intArrayOf(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt(),
        )
    }
}
