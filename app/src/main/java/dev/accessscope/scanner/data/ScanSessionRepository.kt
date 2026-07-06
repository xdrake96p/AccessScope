package dev.accessscope.scanner.data

import android.content.Context
import dev.accessscope.scanner.util.DebugTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScanSessionRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ScanSessionState())
    val state: StateFlow<ScanSessionState> = _state.asStateFlow()

    private val violationKeys = LinkedHashSet<String>()
    private val screenReaderKeys = LinkedHashSet<String>()

    var stopCallback: (() -> Unit)? = null

    fun restorePersistedScan(): Boolean {
        if (!prefs.getBoolean(KEY_SCANNING, false)) return false
        val packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty()
        if (packages.isEmpty()) {
            clearPersistedScan()
            return false
        }
        _state.value = ScanSessionState(
            isScanning = true,
            selectedPackages = packages,
        )
        return true
    }

    fun startScan(selectedPackages: Set<String>) {
        violationKeys.clear()
        screenReaderKeys.clear()
        _state.value = ScanSessionState(
            isScanning = true,
            selectedPackages = selectedPackages,
            lastPdfPath = null,
            errorMessage = null,
        )
        prefs.edit()
            .putBoolean(KEY_SCANNING, true)
            .putStringSet(KEY_PACKAGES, selectedPackages)
            .apply()
        // #region agent log
        DebugTrace.log("H3", "Repository.startScan", "started", mapOf(
            "packages" to selectedPackages.joinToString(","),
        ))
        // #endregion
    }

    fun stopScan() {
        _state.update { it.copy(isScanning = false) }
        clearPersistedScan()
    }

    private fun clearPersistedScan() {
        prefs.edit()
            .remove(KEY_SCANNING)
            .remove(KEY_PACKAGES)
            .apply()
    }

    fun addViolations(violations: List<AccessibilityViolation>) {
        val newOnes = violations.filter { violationKeys.add(it.dedupeKey) }
        if (newOnes.isEmpty()) return
        _state.update { current ->
            current.copy(violations = current.violations + newOnes)
        }
    }

    fun addScreenReaderFindings(findings: List<ScreenReaderFinding>) {
        val newOnes = findings.filter {
            val key = "${it.packageName}|${it.screenTitle}|${it.reportSection}|${it.nodeClassName}|${it.issue}|${it.viewId}"
            screenReaderKeys.add(key)
        }
        if (newOnes.isEmpty()) return
        _state.update { current ->
            current.copy(screenReaderFindings = current.screenReaderFindings + newOnes)
        }
    }

    fun incrementScreenCount() {
        _state.update { it.copy(scannedScreens = it.scannedScreens + 1) }
    }

    fun setPdfPath(path: String?) {
        _state.update { it.copy(lastPdfPath = path, errorMessage = null) }
    }

    fun setError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun isTargetPackage(packageName: String): Boolean {
        val targets = _state.value.selectedPackages
        return targets.isNotEmpty() && packageName in targets
    }

    fun requestStopFromOverlay() {
        // #region agent log
        DebugTrace.log("H-STOP1", "Repository.requestStop", "invoke", mapOf(
            "hasCallback" to (stopCallback != null),
            "isScanning" to _state.value.isScanning,
        ))
        // #endregion
        stopCallback?.invoke()
    }

    companion object {
        private const val PREFS_NAME = "access_scope_scan"
        private const val KEY_SCANNING = "is_scanning"
        private const val KEY_PACKAGES = "selected_packages"
    }
}
