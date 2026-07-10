/**
 * Activity principale dell'applicazione AccessScope.
 *
 * Configura l'interfaccia Compose con navigazione tra home, impostazioni e report,
 * e aggiorna lo stato dei permessi al ritorno in primo piano.
 */
package dev.accessscope.scanner

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.accessscope.scanner.ui.screen.DynamicReportScreen
import dev.accessscope.scanner.ui.screen.FeedbackScreen
import dev.accessscope.scanner.ui.screen.HomeScreen
import dev.accessscope.scanner.ui.screen.LogCheckerScreen
import dev.accessscope.scanner.ui.screen.ReportScreen
import dev.accessscope.scanner.ui.screen.ScanHistoryScreen
import dev.accessscope.scanner.ui.screen.SettingsScreen
import dev.accessscope.scanner.ui.screen.ViolationDetailScreen
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.AccessScopeTheme
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import dev.accessscope.scanner.util.PdfHelper

/**
 * Activity host dell'interfaccia utente AccessScope.
 *
 * Avvia il tema Compose, il [ScanViewModel] e il grafo di navigazione tra le schermate
 * principali dell'app.
 */
class MainActivity : ComponentActivity() {

    private val scanViewModel: ScanViewModel by viewModels()

    /**
     * Configura l'edge-to-edge e imposta il contenuto Compose con tema e navigazione.
     *
     * @param savedInstanceState Stato salvato dell'activity, se presente.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by scanViewModel.uiState.collectAsStateWithLifecycle()
            AccessScopeTheme(themeMode = uiState.themeMode) {
                AccessScopeNavHost(viewModel = scanViewModel)
            }
        }
    }

    /**
     * Aggiorna lo stato dei permessi (accessibilità, overlay, ecc.) al ritorno in primo piano.
     */
    override fun onResume() {
        super.onResume()
        scanViewModel.refreshPermissions()
    }
}

/**
 * Grafo di navigazione Compose per le schermate principali di AccessScope.
 *
 * Definisce le rotte `home`, `settings` e `report` con transizioni animate
 * coerenti con il design system dell'app.
 *
 * @param viewModel ViewModel condiviso che gestisce lo stato della scansione e del report.
 */
@Composable
private fun AccessScopeNavHost(viewModel: ScanViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fadeInSpec = fadeIn(animationSpec = AccessScopeMotion.screenEnterTween)
    val fadeOutSpec = fadeOut(animationSpec = AccessScopeMotion.screenExitTween)
    val enter = slideInHorizontally(animationSpec = AccessScopeMotion.navSpring) { it } + fadeInSpec
    val exit = slideOutHorizontally(animationSpec = AccessScopeMotion.navSpring) { it } + fadeOutSpec
    val popEnter = slideInHorizontally(animationSpec = AccessScopeMotion.navSpring) { -it } + fadeInSpec
    val popExit = slideOutHorizontally(animationSpec = AccessScopeMotion.navSpring) { -it } + fadeOutSpec

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenReport = { navController.navigate("report") },
                onOpenDynamicReport = { navController.navigate("dynamic_report") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenHistory = { packageName ->
                    navController.navigate("history/$packageName")
                },
            )
        }
        composable(
            route = "settings",
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenLogChecker = { navController.navigate("log_checker") },
                onOpenFeedback = { navController.navigate("feedback") },
            )
        }
        composable(
            route = "feedback",
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) {
            FeedbackScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "log_checker",
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) {
            LogCheckerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "dynamic_report",
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) {
            DynamicReportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenViolationDetail = { dedupeKey ->
                    val encoded = Base64.encodeToString(
                        dedupeKey.toByteArray(Charsets.UTF_8),
                        Base64.URL_SAFE or Base64.NO_WRAP,
                    )
                    navController.navigate("violation_detail/$encoded")
                },
            )
        }
        composable(
            route = "report",
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) {
            ReportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPdf = { path -> PdfHelper.openPdf(context, path) },
                onOpenViolationDetail = { dedupeKey ->
                    val encoded = Base64.encodeToString(
                        dedupeKey.toByteArray(Charsets.UTF_8),
                        Base64.URL_SAFE or Base64.NO_WRAP,
                    )
                    navController.navigate("violation_detail/$encoded")
                },
            )
        }
        composable(
            route = "violation_detail/{encodedKey}",
            arguments = listOf(navArgument("encodedKey") { type = NavType.StringType }),
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("encodedKey").orEmpty()
            val dedupeKey = runCatching {
                String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault(encoded)
            val evidenceSessionId = uiState.scanState.sessionId
                ?: viewModel.currentSessionId()
                ?: uiState.scanState.selectedPackages.firstOrNull()
                    ?.let { pkg -> viewModel.getScanHistory(pkg).lastOrNull()?.id }
            ViolationDetailScreen(
                dedupeKey = dedupeKey,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                sessionId = evidenceSessionId,
            )
        }
        composable(
            route = "history/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName").orEmpty()
            val appLabel = uiState.apps.find { it.packageName == packageName }?.label ?: packageName
            ScanHistoryScreen(
                packageName = packageName,
                appLabel = appLabel,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPdf = { path -> PdfHelper.openPdf(context, path) },
            )
        }
    }
}
