package dev.accessscope.scanner.ui.viewmodel

import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ScanSessionState
import dev.accessscope.scanner.report.SessionComparison
import dev.accessscope.scanner.ui.theme.AppThemeMode

/**
 * Dialog mostrato quando si supera il limite di app monitorabili.
 */
data class AppSelectionLimitDialog(
    val message: String,
    val blockedPackage: String? = null,
)

/**
 * Stato immutabile dell'interfaccia della schermata Home.
 *
 * @param apps Elenco delle app installate sul dispositivo, arricchite con metadati e ordinamento.
 * @param selectedPackages Set dei package name selezionati per il monitoraggio durante la scansione.
 * @param favoritePackages Set dei package name contrassegnati come preferiti.
 * @param scanState Stato corrente della sessione di scansione (violazioni, schermate, PDF, ecc.).
 * @param accessibilityGranted Indica se il servizio di accessibilità AccessScope è abilitato nelle impostazioni di sistema.
 * @param accessibilityConnected Indica se il servizio di accessibilità è attualmente connesso e in esecuzione.
 * @param overlayGranted Indica se è stato concesso il permesso di disegnare sopra le altre app.
 * @param isLoadingApps True mentre l'elenco app viene caricato in background.
 * @param includeSystemApps Se true, include anche le app di sistema nell'elenco.
 * @param autoLaunchEnabled Se true, all'avvio della scansione viene aperta automaticamente la prima app selezionata.
 * @param scanScope Ambiti di analisi attivi per la prossima sessione (etichette, contrasto, TalkBack, ecc.).
 * @param statusMessage Messaggio temporaneo da mostrare all'utente (es. errori, conferme); null se assente.
 * @param themeMode Preferenza tema interfaccia (chiaro, scuro o sistema).
 * @param reliabilityReportEnabled Se true, genera report Markdown di affidabilità a fine scansione.
 * @param includeLowConfidenceFindings Se true, il report include anche finding sotto soglia (più rumore).
 * @param latestArchivedSession Ultima sessione archiviata per l'app principale selezionata.
 * @param sessionComparison Confronto numerico ultima vs penultima sessione archiviata.
 * @param historyPackageName Package usato per cronologia e confronto.
 * @param selectionLimitDialog Dialog da mostrare quando si supera il limite di app monitorabili.
 */
data class HomeUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val favoritePackages: Set<String> = emptySet(),
    val scanState: ScanSessionState = ScanSessionState(),
    val accessibilityGranted: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val overlayGranted: Boolean = false,
    val isLoadingApps: Boolean = true,
    val includeSystemApps: Boolean = false,
    val autoLaunchEnabled: Boolean = false,
    val scanScope: ScanScope = ScanScope.FULL,
    val statusMessage: String? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val reliabilityReportEnabled: Boolean = false,
    val includeLowConfidenceFindings: Boolean = false,
    val latestArchivedSession: ArchivedScanSession? = null,
    val sessionComparison: SessionComparison? = null,
    val historyPackageName: String? = null,
    val selectionLimitDialog: AppSelectionLimitDialog? = null,
)

/**
 * Slice UI per l'elenco app — non include [ScanSessionState] per evitare recomposition durante la scansione.
 */
data class AppListUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val isLoadingApps: Boolean = true,
    val includeSystemApps: Boolean = false,
    val autoLaunchEnabled: Boolean = false,
)

/**
 * Slice UI per dashboard e barra azioni legata alla sessione di scansione.
 */
data class ScanDashboardUiState(
    val scanState: ScanSessionState = ScanSessionState(),
    val selectedPackages: Set<String> = emptySet(),
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val latestArchivedSession: ArchivedScanSession? = null,
    val sessionComparison: SessionComparison? = null,
    val historyPackageName: String? = null,
)
