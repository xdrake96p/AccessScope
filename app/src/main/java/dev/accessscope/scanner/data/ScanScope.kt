package dev.accessscope.scanner.data

data class ScanScope(
    val enabledAreas: Set<ViolationArea> = ViolationArea.entries.toSet(),
) {
    fun includes(area: ViolationArea): Boolean = area in enabledAreas

    val isFullScan: Boolean get() = enabledAreas.size == ViolationArea.entries.size

    fun label(): String = when {
        isFullScan -> "Completa"
        enabledAreas.isEmpty() -> "Nessuna"
        enabledAreas.size <= 2 -> enabledAreas.joinToString(", ") { it.title }
        else -> "${enabledAreas.size} ambiti"
    }

    companion object {
        val FULL = ScanScope()

        fun labelsOnly() = ScanScope(setOf(ViolationArea.LABELS))

        fun talkBackOnly() = ScanScope(setOf(ViolationArea.SCREEN_READER))

        fun contrastOnly() = ScanScope(setOf(ViolationArea.COLOR))
    }
}
