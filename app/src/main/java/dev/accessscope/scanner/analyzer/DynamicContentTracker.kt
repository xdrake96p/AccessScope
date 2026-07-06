package dev.accessscope.scanner.analyzer

import java.util.concurrent.ConcurrentHashMap

/**
 * Traccia cambi di contenuto senza annunci TYPE_ANNOUNCEMENT per rilevare live region mancanti.
 */
class DynamicContentTracker {

    private val contentChanges = ConcurrentHashMap<String, WindowState>()

    data class WindowState(
        var changeCount: Int = 0,
        var lastAnnouncementMs: Long = 0,
        var lastChangeMs: Long = 0,
    )

    fun onContentChanged(packageName: String, windowId: Int) {
        val key = "$packageName:$windowId"
        val now = System.currentTimeMillis()
        val state = contentChanges.getOrPut(key) { WindowState() }
        state.changeCount++
        state.lastChangeMs = now
    }

    fun onAnnouncement(packageName: String) {
        val now = System.currentTimeMillis()
        contentChanges.keys.filter { it.startsWith("$packageName:") }.forEach { key ->
            contentChanges[key]?.lastAnnouncementMs = now
        }
    }

    fun isSilentDynamicContent(packageName: String, windowId: Int): Boolean {
        val key = "$packageName:$windowId"
        val state = contentChanges[key] ?: return false
        val now = System.currentTimeMillis()
        return state.changeCount >= 4 &&
            state.lastAnnouncementMs < state.lastChangeMs &&
            now - state.lastChangeMs < 10_000
    }

    fun reset() = contentChanges.clear()
}
