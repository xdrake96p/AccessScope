package dev.accessscope.scanner.analyzer.node

import android.graphics.Bitmap
import android.graphics.Rect
import dev.accessscope.scanner.analyzer.CheckCollector
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.PrecisionRules
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationType


internal object NodeFormsSingleChecker {
    fun check(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        scanScope: ScanScope,
        screenFingerprint: String?,
    ) {
        fun includes(area: ViolationArea): Boolean = scanScope.includes(area)
                if (includes(ViolationArea.FORMS) && snap.isEditable && !snap.hasInputLabel()) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.INPUT_LABEL, snap, packageName, screenTitle,
                        "Campo senza etichetta associata.", 0.95f,
                    )
                }
        
                if (includes(ViolationArea.FORMS) && snap.isEditable && snap.errorText.isNullOrBlank() &&
                    snap.text?.contains("error", true) == true
                ) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.INPUT_ERROR_MISSING, snap, packageName, screenTitle,
                        "Errore visivo probabile senza messaggio accessibile.", 0.7f,
                    )
                }
        
                if (includes(ViolationArea.FORMS) && snap.isEditable && snap.isEnabled &&
                    !PrecisionRules.isRequiredFieldHint(snap.hintText, snap.text, snap.contentDescription) &&
                    snap.hintText?.contains('*') != true &&
                    snap.className.contains("required", true)
                ) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.REQUIRED_FIELD_UNMARKED, snap, packageName, screenTitle,
                        "Campo probabilmente obbligatorio non marcato.", 0.65f,
                    )
                }
                if (includes(ViolationArea.FORMS) && snap.isEditable && !snap.isPassword && snap.className.contains("password", true)) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.PASSWORD_NOT_MASKED, snap, packageName, screenTitle,
                        "Campo password non marcato isPassword.", 0.95f,
                    )
                }
                if (includes(ViolationArea.FORMS) && snap.rangeMin != null && snap.rangeMax != null &&
                    (snap.rangeCurrent == null || snap.className.contains("SeekBar", true) || snap.className.contains("Slider", true))
                ) {
                    val hasValue = snap.stateDescription?.isNotBlank() == true || snap.contentDescription?.contains("%") == true
                    if (!hasValue && snap.rangeCurrent == snap.rangeMin) {
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.SLIDER_VALUE_MISSING, snap, packageName, screenTitle,
                            "Slider/progresso senza valore annunciato.", 0.78f,
                        )
                    }
                }
    }
}
