package dev.accessscope.cli

object PluginVersionChecker {
    private val semverPattern = Regex("""(\d+)\.(\d+)\.(\d+)""")

    /**
     * Verifica che la versione del plugin IDE in uso sia compatibile con il manifest release.
     *
     * Se [ACCESS_SCOPE_PLUGIN_VERSION] non è impostata (invocazione CLI diretta), il check viene saltato.
     */
    fun requireCompatible(manifest: ReleaseManifest) {
        val minRequired = manifest.minPluginVersion?.takeIf { it.isNotBlank() } ?: return
        val pluginVersion = System.getenv("ACCESS_SCOPE_PLUGIN_VERSION")?.takeIf { it.isNotBlank() }
            ?: return
        if (compareSemver(pluginVersion, minRequired) < 0) {
            error(
                "Aggiorna il plugin IDE alla versione $minRequired o superiore " +
                    "(versione attuale: $pluginVersion).",
            )
        }
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
