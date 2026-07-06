/**
 * Configurazione dell'ambito di una scansione di accessibilità.
 *
 * Permette di limitare i controlli a specifici [ViolationArea] oppure
 * eseguire una scansione completa su tutti gli ambiti disponibili.
 */
package dev.accessscope.scanner.data

/**
 * Definisce quali ambiti tematici WCAG includere in una sessione di scansione.
 *
 * @property enabledAreas Insieme degli ambiti attivi per la scansione corrente.
 */
data class ScanScope(
    val enabledAreas: Set<ViolationArea> = ViolationArea.entries.toSet(),
) {
    /**
     * Verifica se un ambito è incluso nella scansione corrente.
     *
     * @param area Ambito da verificare.
     * @return `true` se [area] è tra gli ambiti abilitati.
     */
    fun includes(area: ViolationArea): Boolean = area in enabledAreas

    /** `true` se tutti gli ambiti [ViolationArea] sono abilitati. */
    val isFullScan: Boolean get() = enabledAreas.size == ViolationArea.entries.size

    /**
     * Restituisce un'etichetta leggibile per l'ambito configurato.
     *
     * @return «Completa», «Nessuna», elenco di titoli o conteggio ambiti.
     */
    fun label(): String = when {
        isFullScan -> "Completa"
        enabledAreas.isEmpty() -> "Nessuna"
        enabledAreas.size <= 2 -> enabledAreas.joinToString(", ") { it.title }
        else -> "${enabledAreas.size} ambiti"
    }

    companion object {
        /** Scansione completa con tutti gli ambiti abilitati. */
        val FULL = ScanScope()

        /**
         * Scansione limitata al solo ambito etichette e nomi accessibili.
         *
         * @return [ScanScope] con solo [ViolationArea.LABELS].
         */
        fun labelsOnly() = ScanScope(setOf(ViolationArea.LABELS))

        /**
         * Scansione limitata al solo ambito screen reader (TalkBack).
         *
         * @return [ScanScope] con solo [ViolationArea.SCREEN_READER].
         */
        fun talkBackOnly() = ScanScope(setOf(ViolationArea.SCREEN_READER))

        /**
         * Scansione limitata al solo ambito colori e contrasto.
         *
         * @return [ScanScope] con solo [ViolationArea.COLOR].
         */
        fun contrastOnly() = ScanScope(setOf(ViolationArea.COLOR))
    }
}
