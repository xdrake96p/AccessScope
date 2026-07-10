package dev.accessscope.scanner.analyzer.node

import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType

internal object ViolationBuilder {

    fun remediationFor(type: ViolationType): String = when (type) {
        ViolationType.LOW_COLOR_CONTRAST, ViolationType.LOW_NON_TEXT_CONTRAST ->
            "Aumenta il contrasto tra primo piano e sfondo (testo più scuro o sfondo più chiaro)."
        ViolationType.MISSING_LABEL, ViolationType.IMAGE_MISSING_ALT, ViolationType.CUSTOM_ACTION_UNLABELED ->
            "Aggiungi contentDescription o testo visibile che descriva l'azione o l'icona."
        ViolationType.SMALL_TOUCH_TARGET, ViolationType.INSUFFICIENT_TOUCH_SPACING ->
            "Ingrandisci l'area tappabile ad almeno 48×48 dp o aumenta lo spazio tra i controlli."
        ViolationType.TEXT_TOO_SMALL ->
            "Usa una dimensione testo ≥ 12sp (16sp consigliato per testo interattivo)."
        ViolationType.DYNAMIC_CONTENT_SILENT ->
            "Annuncia i cambi di contenuto con liveRegion o AccessibilityEvent TYPE_ANNOUNCEMENT."
        else -> "Correggi secondo ${type.wcagRef} e verifica con TalkBack."
    }

    fun v(
        screenFingerprint: String?,
        type: ViolationType,
        snap: NodeSnapshot,
        pkg: String,
        screen: String,
        details: String,
        confidence: Float,
        measuredValue: String? = null,
        requiredValue: String? = null,
        foregroundColorHex: String? = null,
        backgroundColorHex: String? = null,
    ) = AccessibilityViolation(
        type = type,
        viewClassName = snap.className,
        screenTitle = screen,
        packageName = pkg,
        details = details,
        viewId = snap.viewId,
        bounds = snap.boundsLabel(),
        sectionTitle = snap.sectionTitle,
        confidence = confidence,
        screenFingerprint = screenFingerprint,
        elementLabel = snap.accessibleName()?.take(80) ?: snap.text?.trim()?.take(80),
        measuredValue = measuredValue,
        requiredValue = requiredValue,
        foregroundColorHex = foregroundColorHex,
        backgroundColorHex = backgroundColorHex,
        remediation = remediationFor(type),
        boundsLeft = snap.bounds.left,
        boundsTop = snap.bounds.top,
        boundsRight = snap.bounds.right,
        boundsBottom = snap.bounds.bottom,
    )
}
