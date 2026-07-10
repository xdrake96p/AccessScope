package dev.accessscope.scanner.ui.viewmodel

import android.app.Application
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.ui.selection.AppSelectionPolicy
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.AppIconCache
import dev.accessscope.scanner.util.FavoriteAppsStore
import dev.accessscope.scanner.util.PackageHelper
import dev.accessscope.scanner.util.ScanSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestisce elenco app installate, preferiti e selezione per la scansione.
 */
internal class ScanAppListController(
    private val application: Application,
    private val favoriteAppsStore: FavoriteAppsStore,
    private val scanSettingsStore: ScanSettingsStore,
    private val uiState: MutableStateFlow<HomeUiState>,
    private val scope: CoroutineScope,
    private val onSelectionChanged: (Set<String>) -> Unit,
) {

    fun loadApps() {
        scope.launch {
            val includeSystem = uiState.value.includeSystemApps
            uiState.update { it.copy(isLoadingApps = true) }
            val apps = withContext(Dispatchers.IO) {
                PackageHelper.loadInstalledApps(application, includeSystem)
            }
            val storedFavorites = favoriteAppsStore.getFavorites()
            val installedPackages = apps.map { it.packageName }.toSet()
            val beforeSelected = uiState.value.selectedPackages
            val primaryFavorite = apps.firstOrNull { it.packageName in storedFavorites }?.packageName
            val (favorites, selected) = AppSelectionPolicy.sanitizeAgainstInstalled(
                selected = beforeSelected,
                favorites = storedFavorites,
                installed = installedPackages,
                preferredPrimary = primaryFavorite,
            )
            if (favorites != storedFavorites) {
                favoriteAppsStore.setFavorites(favorites)
            }
            val enriched = apps.map { app ->
                app.copy(isFavorite = app.packageName in favorites)
            }.sortedWith(
                compareByDescending<InstalledAppInfo> { it.isFavorite }
                    .thenBy { !it.isSystemApp }
                    .thenBy { it.label.lowercase() },
            )
            val pm = application.packageManager
            withContext(Dispatchers.Default) {
                enriched.chunked(48).forEach { chunk ->
                    AppIconCache.preload(pm, chunk.map { it.packageName })
                }
            }
            uiState.update { state ->
                state.copy(
                    apps = enriched,
                    favoritePackages = favorites,
                    selectedPackages = selected,
                    isLoadingApps = false,
                )
            }
            logSelection(
                "load_apps_done",
                mapOf(
                    "appCount" to enriched.size,
                    "favoriteCount" to favorites.size,
                    "selected" to uiState.value.selectedPackages.joinToString(),
                ),
            )
        }
    }

    fun toggleFavorite(packageName: String) {
        if (uiState.value.scanState.isScanning) return
        val stateBefore = uiState.value
        val wasFavorite = packageName in stateBefore.favoritePackages
        val favorites = favoriteAppsStore.toggle(packageName)
        val isNowFavorite = packageName in favorites
        uiState.update { state ->
            val selected = when {
                isNowFavorite -> AppSelectionPolicy.selectOnFavoriteAdded(packageName)
                else -> AppSelectionPolicy.selectOnFavoriteRemoved(state.selectedPackages, packageName)
            }
            state.copy(
                favoritePackages = favorites,
                selectedPackages = selected,
                selectionLimitDialog = null,
                apps = state.apps.map { app ->
                    app.copy(isFavorite = app.packageName in favorites)
                }.sortedWith(
                    compareByDescending<InstalledAppInfo> { it.isFavorite }
                        .thenBy { !it.isSystemApp }
                        .thenBy { it.label.lowercase() },
                ),
            )
        }
        logSelection(
            "toggle_favorite",
            mapOf(
                "package" to packageName,
                "wasFavorite" to wasFavorite,
                "isFavorite" to isNowFavorite,
                "selectedAfter" to uiState.value.selectedPackages.joinToString(),
            ),
        )
        onSelectionChanged(uiState.value.selectedPackages)
    }

    fun toggleIncludeSystemApps() {
        uiState.update { it.copy(includeSystemApps = !it.includeSystemApps) }
        loadApps()
    }

    fun toggleApp(packageName: String) {
        if (uiState.value.scanState.isScanning) return
        val stateBefore = uiState.value
        if (packageName in stateBefore.selectedPackages &&
            AppSelectionPolicy.isFavoriteProtectedFromDeselect(packageName, stateBefore.favoritePackages)
        ) {
            logSelection(
                "toggle_app_blocked_favorite",
                mapOf("package" to packageName),
            )
            uiState.update {
                it.copy(
                    selectionLimitDialog = AppSelectionLimitDialog(
                        message = AppSelectionPolicy.favoriteDeselectBlockedMessage(),
                        blockedPackage = packageName,
                    ),
                )
            }
            return
        }
        uiState.update { state ->
            when (
                val result = AppSelectionPolicy.toggleSelection(
                    current = state.selectedPackages,
                    packageName = packageName,
                    replaceOnLimit = state.autoLaunchEnabled,
                )
            ) {
                is AppSelectionPolicy.ToggleResult.Updated ->
                    state.copy(selectedPackages = result.selected, selectionLimitDialog = null)
                is AppSelectionPolicy.ToggleResult.LimitReached ->
                    state.copy(
                        selectionLimitDialog = AppSelectionLimitDialog(
                            message = result.message,
                            blockedPackage = result.blockedPackage,
                        ),
                    )
            }
        }
        logSelection(
            "toggle_app",
            mapOf(
                "package" to packageName,
                "selectedAfter" to uiState.value.selectedPackages.joinToString(),
                "dialog" to (uiState.value.selectionLimitDialog != null),
            ),
        )
        onSelectionChanged(uiState.value.selectedPackages)
    }

    fun dismissSelectionLimitDialog() {
        uiState.update { it.copy(selectionLimitDialog = null) }
        logSelection("dismiss_selection_dialog")
    }

    fun selectAllVisible() {
        uiState.update { state ->
            val first = state.apps.firstOrNull()?.packageName
            when {
                first == null -> state
                state.selectedPackages.isNotEmpty() -> state.copy(
                    selectionLimitDialog = AppSelectionLimitDialog(autoLaunchLimitMessage()),
                )
                else -> state.copy(selectedPackages = setOf(first))
            }
        }
        logSelection("select_all_visible", mapOf("selected" to uiState.value.selectedPackages.joinToString()))
    }

    fun clearSelection() {
        uiState.update { state ->
            state.copy(selectedPackages = emptySet())
        }
        logSelection("clear_selection", mapOf("selected" to uiState.value.selectedPackages.joinToString()))
        onSelectionChanged(uiState.value.selectedPackages)
    }

    fun toggleAutoLaunch() {
        val state = uiState.value
        val enabled = !state.autoLaunchEnabled
        if (enabled) {
            val trimmed = AppSelectionPolicy.enforceMax(state.selectedPackages)
            scanSettingsStore.autoLaunchEnabled = true
            uiState.update {
                it.copy(
                    autoLaunchEnabled = true,
                    selectedPackages = trimmed,
                    statusMessage = if (trimmed.size < state.selectedPackages.size) {
                        "Lancio automatico attivo: selezione limitata a ${ScanViewModel.MAX_APPS_WITH_AUTO_LAUNCH} app."
                    } else {
                        it.statusMessage
                    },
                )
            }
            logSelection("toggle_auto_launch", mapOf("enabled" to true, "trimmed" to trimmed.joinToString()))
            return
        }
        scanSettingsStore.autoLaunchEnabled = enabled
        uiState.update { it.copy(autoLaunchEnabled = enabled) }
        logSelection("toggle_auto_launch", mapOf("enabled" to enabled))
    }

    fun appIconBitmap(packageName: String): androidx.compose.ui.graphics.ImageBitmap? =
        AppIconCache.getOrLoad(application.packageManager, packageName)

    fun autoLaunchLimitMessage() = AppSelectionPolicy.limitMessage()

    internal fun logSelectionForStart(event: String, extras: Map<String, Any?> = emptyMap()) {
        logSelection(event, extras)
    }

    private fun logSelection(event: String, extras: Map<String, Any?> = emptyMap()) {
        val state = uiState.value
        AppFileLogger.log(
            hypothesisId = "SEL",
            location = "AppSelection",
            message = event,
            data = extras + mapOf(
                "selected" to state.selectedPackages.joinToString().ifBlank { "-" },
                "favorites" to state.favoritePackages.joinToString().ifBlank { "-" },
                "autoLaunch" to state.autoLaunchEnabled,
                "scanning" to state.scanState.isScanning,
            ),
        )
    }
}
