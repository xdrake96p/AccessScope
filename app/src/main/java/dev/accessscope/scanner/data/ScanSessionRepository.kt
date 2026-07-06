package dev.accessscope.scanner.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScanSessionRepository {

    private val _state = MutableStateFlow(ScanSessionState())
    val state: StateFlow<ScanSessionState> = _state.asStateFlow()

    private val violationKeys = LinkedHashSet<String>()
    private val screenReaderKeys = LinkedHashSet<String>()

    var stopCallback: (() -> Unit)? = null

    fun startScan(selectedPackages: Set<String>) {
        violationKeys.clear()
        screenReaderKeys.clear()
        _state.value = ScanSessionState(
            isScanning = true,
            selectedPackages = selectedPackages,
        )
    }

    fun stopScan() {
        _state.update { it.copy(isScanning = false) }
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
            val key = "${it.packageName}|${it.screenTitle}|${it.nodeClassName}|${it.issue}|${it.viewId}"
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
        stopCallback?.invoke()
    }
}
