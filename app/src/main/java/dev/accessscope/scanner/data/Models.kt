package dev.accessscope.scanner.data

enum class ViolationSeverity { CRITICAL, SERIOUS, MODERATE, MINOR }

enum class ViolationType(
    val displayName: String,
    val wcagRef: String,
    val severity: ViolationSeverity,
) {
    MISSING_LABEL(
        displayName = "Etichetta mancante",
        wcagRef = "WCAG 4.1.2 / 2.5.3",
        severity = ViolationSeverity.CRITICAL,
    ),
    SMALL_TOUCH_TARGET(
        displayName = "Target di tocco insufficiente",
        wcagRef = "WCAG 2.5.5",
        severity = ViolationSeverity.SERIOUS,
    ),
    INSUFFICIENT_TOUCH_SPACING(
        displayName = "Spaziatura target di tocco",
        wcagRef = "WCAG 2.5.8",
        severity = ViolationSeverity.MODERATE,
    ),
    OVERLAPPING_TOUCH_TARGETS(
        displayName = "Target di tocco sovrapposti",
        wcagRef = "WCAG 2.5.5",
        severity = ViolationSeverity.SERIOUS,
    ),
    HEADING_HIERARCHY(
        displayName = "Gerarchia titoli",
        wcagRef = "WCAG 1.3.1 / 2.4.6",
        severity = ViolationSeverity.MODERATE,
    ),
    NOT_FOCUSABLE(
        displayName = "Elemento non focalizzabile",
        wcagRef = "WCAG 2.1.1 / 4.1.2",
        severity = ViolationSeverity.CRITICAL,
    ),
    INPUT_LABEL(
        displayName = "Campo input senza etichetta",
        wcagRef = "WCAG 3.3.2 / 4.1.2",
        severity = ViolationSeverity.CRITICAL,
    ),
    INPUT_ERROR_MISSING(
        displayName = "Errore input non descritto",
        wcagRef = "WCAG 3.3.1 / 3.3.3",
        severity = ViolationSeverity.SERIOUS,
    ),
    TEXT_TOO_SMALL(
        displayName = "Testo troppo piccolo",
        wcagRef = "WCAG 1.4.4 / 1.4.12",
        severity = ViolationSeverity.SERIOUS,
    ),
    TEXT_TRUNCATED(
        displayName = "Testo troncato senza alternativa",
        wcagRef = "WCAG 1.4.4",
        severity = ViolationSeverity.MODERATE,
    ),
    LOW_COLOR_CONTRAST(
        displayName = "Contrasto colore insufficiente",
        wcagRef = "WCAG 1.4.3",
        severity = ViolationSeverity.CRITICAL,
    ),
    LOW_NON_TEXT_CONTRAST(
        displayName = "Contrasto componenti non testuali",
        wcagRef = "WCAG 1.4.11",
        severity = ViolationSeverity.MODERATE,
    ),
    IMAGE_MISSING_ALT(
        displayName = "Immagine senza testo alternativo",
        wcagRef = "WCAG 1.1.1",
        severity = ViolationSeverity.CRITICAL,
    ),
    LINK_NOT_DESCRIPTIVE(
        displayName = "Link non descrittivo",
        wcagRef = "WCAG 2.4.4",
        severity = ViolationSeverity.SERIOUS,
    ),
    DUPLICATE_ACCESSIBLE_NAME(
        displayName = "Nome accessibile duplicato",
        wcagRef = "WCAG 4.1.2",
        severity = ViolationSeverity.MODERATE,
    ),
    REDUNDANT_ACCESSIBLE_NAME(
        displayName = "Nome accessibile ridondante",
        wcagRef = "WCAG 4.1.2",
        severity = ViolationSeverity.MINOR,
    ),
    SCROLLABLE_WITHOUT_LABEL(
        displayName = "Area scrollabile senza etichetta",
        wcagRef = "WCAG 4.1.2 / 2.4.6",
        severity = ViolationSeverity.MODERATE,
    ),
    DISABLED_WITHOUT_INDICATION(
        displayName = "Stato disabilitato non esposto",
        wcagRef = "WCAG 4.1.2",
        severity = ViolationSeverity.MODERATE,
    ),
    EXPANDABLE_STATE_MISSING(
        displayName = "Stato espansione mancante",
        wcagRef = "WCAG 4.1.2",
        severity = ViolationSeverity.MODERATE,
    ),
    PASSWORD_NOT_MASKED(
        displayName = "Campo password non mascherato",
        wcagRef = "WCAG 2.2.4",
        severity = ViolationSeverity.CRITICAL,
    ),
    MODAL_WITHOUT_TITLE(
        displayName = "Finestra modale senza titolo",
        wcagRef = "WCAG 2.4.2",
        severity = ViolationSeverity.SERIOUS,
    ),
    COLLECTION_WITHOUT_STRUCTURE(
        displayName = "Lista/tabella senza struttura",
        wcagRef = "WCAG 1.3.1",
        severity = ViolationSeverity.MODERATE,
    ),
    ROLE_UNDEFINED(
        displayName = "Ruolo semantico indefinito",
        wcagRef = "WCAG 4.1.2",
        severity = ViolationSeverity.SERIOUS,
    ),
    SCREEN_READER_ANNOUNCEMENT(
        displayName = "Annuncio screen reader assente",
        wcagRef = "WCAG 4.1.2",
        severity = ViolationSeverity.CRITICAL,
    ),
}

data class AccessibilityViolation(
    val type: ViolationType,
    val viewClassName: String,
    val screenTitle: String,
    val packageName: String,
    val details: String,
    val viewId: String? = null,
    val bounds: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val dedupeKey: String
        get() = "${type.name}|$packageName|$screenTitle|$viewClassName|$details|$viewId|$bounds"
}

data class ScreenReaderFinding(
    val packageName: String,
    val screenTitle: String,
    val nodeClassName: String,
    val announcedText: String?,
    val issue: String,
    val viewId: String? = null,
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
)

data class ScanSessionState(
    val isScanning: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val violations: List<AccessibilityViolation> = emptyList(),
    val screenReaderFindings: List<ScreenReaderFinding> = emptyList(),
    val scannedScreens: Int = 0,
    val lastPdfPath: String? = null,
    val errorMessage: String? = null,
)
