/**
 * Activity principale dell'applicazione AccessScope.
 *
 * Configura tema Compose, navigation drawer e bottom bar contestuali a due zone
 * (principale / sessione) attorno al grafo di navigazione.
 */
package dev.accessscope.scanner

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.content.Context
import dev.accessscope.scanner.ui.components.AccessScopeDrawerContent
import dev.accessscope.scanner.ui.components.BottomZone
import dev.accessscope.scanner.ui.components.MainBottomBar
import dev.accessscope.scanner.ui.components.MaestroDrawerContent
import dev.accessscope.scanner.ui.components.SessionBottomBar
import dev.accessscope.scanner.ui.components.SessionTab
import dev.accessscope.scanner.ui.components.zoneForRoute
import dev.accessscope.scanner.util.FeedbackIssueBuilder
import dev.accessscope.scanner.ui.screen.DynamicReportScreen
import dev.accessscope.scanner.ui.screen.FavoritesScreen
import dev.accessscope.scanner.ui.screen.FeedbackScreen
import dev.accessscope.scanner.ui.screen.FlowEditScreen
import dev.accessscope.scanner.ui.screen.FlowsScreen
import dev.accessscope.scanner.ui.screen.HomeScreen
import dev.accessscope.scanner.ui.screen.LogCheckerScreen
import dev.accessscope.scanner.ui.screen.ReportScreen
import dev.accessscope.scanner.ui.screen.ScanHistoryScreen
import dev.accessscope.scanner.ui.screen.SettingsScreen
import dev.accessscope.scanner.ui.screen.ViolationDetailScreen
import dev.accessscope.scanner.ui.screen.onboarding.OnboardingScreen
import dev.accessscope.scanner.ui.screen.onboarding.SplashScreen
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.AccessScopeTheme
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import dev.accessscope.scanner.util.OnboardingStore
import dev.accessscope.scanner.util.PdfHelper
import kotlinx.coroutines.launch

/**
 * Activity host dell'interfaccia utente AccessScope.
 *
 * Avvia il tema Compose, il [ScanViewModel] e la shell di navigazione con
 * drawer e bottom bar a due zone.
 */
class MainActivity : ComponentActivity() {

    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by scanViewModel.uiState.collectAsStateWithLifecycle()
            AccessScopeTheme(themeMode = uiState.themeMode) {
                AccessScopeAppRoot(viewModel = scanViewModel)
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
 * Shell di navigazione con splash, onboarding, drawer e bottom bar contestuali.
 *
 * Zona principale (tab Home/Preferiti/Settings) e zona sessione
 * (tab Scansione/Dettagli/Report/Storico) mostrate in base alla route corrente.
 *
 * @param viewModel ViewModel condiviso che gestisce lo stato della scansione e del report.
 */
@Composable
private fun AccessScopeAppRoot(viewModel: ScanViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val zone = zoneForRoute(currentRoute)

    var lastDetailRoute by remember { mutableStateOf<String?>(null) }
    var maestroImportRequest by remember { mutableStateOf(0) }
    var maestroCreateRequest by remember { mutableStateOf(0) }

    val sessionPackage = uiState.historyPackageName
        ?: uiState.scanState.selectedPackages.firstOrNull()
        ?: uiState.latestArchivedSession?.targetPackages?.firstOrNull()

    val isMaestroRoute = currentRoute == "maestro" || currentRoute?.startsWith("maestro/") == true

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = zone == BottomZone.MAIN,
        drawerContent = {
            ModalDrawerSheet {
                if (isMaestroRoute) {
                    MaestroDrawerContent(
                        onImportYaml = {
                            scope.launch {
                                drawerState.close()
                                if (currentRoute?.startsWith("maestro/edit") == true) {
                                    navController.popBackStack("maestro", inclusive = false)
                                }
                                maestroImportRequest++
                            }
                        },
                        onCreateYaml = {
                            scope.launch {
                                drawerState.close()
                                if (currentRoute?.startsWith("maestro/edit") == true) {
                                    navController.popBackStack("maestro", inclusive = false)
                                }
                                maestroCreateRequest++
                            }
                        },
                        onMaestroBug = {
                            scope.launch { drawerState.close() }
                            openMaestroGithubIssue(context, bug = true)
                        },
                        onMaestroSuggestion = {
                            scope.launch { drawerState.close() }
                            openMaestroGithubIssue(context, bug = false)
                        },
                    )
                } else {
                    AccessScopeDrawerContent(
                        versionName = BuildConfig.VERSION_NAME,
                        historyEnabled = sessionPackage != null,
                        onCronologia = {
                            scope.launch { drawerState.close() }
                            sessionPackage?.let { pkg -> navigateTab("history/$pkg") }
                        },
                        onUltimaSessione = {
                            scope.launch { drawerState.close() }
                            navigateTab("report")
                        },
                        onFeedback = {
                            scope.launch { drawerState.close() }
                            navController.navigate("feedback")
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            bottomBar = {
                when (zone) {
                    BottomZone.MAIN -> MainBottomBar(
                        currentRoute = currentRoute,
                        onSelect = ::navigateTab,
                    )
                    BottomZone.SESSION -> SessionBottomBar(
                        currentRoute = currentRoute,
                        storicoEnabled = sessionPackage != null,
                        onSelect = { tab ->
                            when (tab) {
                                SessionTab.SCANSIONE -> navigateTab("report")
                                SessionTab.DETTAGLI -> navigateTab(lastDetailRoute ?: "report")
                                SessionTab.REPORT -> navigateTab("dynamic_report")
                                SessionTab.STORICO -> sessionPackage?.let { pkg -> navigateTab("history/$pkg") }
                            }
                        },
                    )
                    BottomZone.NONE -> Unit
                }
            },
        ) { padding ->
            AccessScopeNavHost(
                viewModel = viewModel,
                navController = navController,
                modifier = Modifier.padding(padding),
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onViolationDetailOpened = { route -> lastDetailRoute = route },
                maestroImportRequest = maestroImportRequest,
                maestroCreateRequest = maestroCreateRequest,
            )
        }
    }
}

/**
 * Grafo di navigazione Compose per le schermate di AccessScope.
 *
 * @param onViolationDetailOpened Notifica la route del dettaglio aperto (tab Dettagli).
 */
@Composable
private fun AccessScopeNavHost(
    viewModel: ScanViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onViolationDetailOpened: (String) -> Unit = {},
    maestroImportRequest: Int = 0,
    maestroCreateRequest: Int = 0,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fadeInSpec = fadeIn(animationSpec = AccessScopeMotion.screenEnterTween)
    val fadeOutSpec = fadeOut(animationSpec = AccessScopeMotion.screenExitTween)
    val enter = slideInHorizontally(animationSpec = AccessScopeMotion.navSpring) { it } + fadeInSpec
    val exit = slideOutHorizontally(animationSpec = AccessScopeMotion.navSpring) { it } + fadeOutSpec
    val popEnter = slideInHorizontally(animationSpec = AccessScopeMotion.navSpring) { -it } + fadeInSpec
    val popExit = slideOutHorizontally(animationSpec = AccessScopeMotion.navSpring) { -it } + fadeOutSpec

    fun openViolationDetail(dedupeKey: String) {
        val encoded = Base64.encodeToString(
            dedupeKey.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP,
        )
        val route = "violation_detail/$encoded"
        onViolationDetailOpened(route)
        navController.navigate(route)
    }

    NavHost(
        navController = navController,
        startDestination = "splash",
        modifier = modifier,
    ) {
        composable("splash") {
            SplashScreen(
                onFinished = {
                    val store = OnboardingStore(context)
                    val destination = if (store.isOnboardingCompleted()) "home" else "onboarding"
                    navController.navigate(destination) {
                        popUpTo("splash") { inclusive = true }
                    }
                },
            )
        }
        composable("onboarding") {
            OnboardingScreen(
                onFinish = { dontShowAgain ->
                    OnboardingStore(context).setOnboardingCompleted(dontShowAgain)
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenReport = { navController.navigate("report") },
                onOpenDynamicReport = { navController.navigate("dynamic_report") },
                onOpenHistory = { packageName -> navController.navigate("history/$packageName") },
                onOpenDrawer = onOpenDrawer,
            )
        }
        composable("favorites") {
            FavoritesScreen(viewModel = viewModel)
        }
        composable("maestro") {
            FlowsScreen(
                viewModel = viewModel,
                onOpenDrawer = onOpenDrawer,
                onEditFlow = { flowId -> navController.navigate("maestro/edit/$flowId") },
                importYamlRequest = maestroImportRequest,
                createYamlRequest = maestroCreateRequest,
            )
        }
        composable(
            route = "maestro/edit/{flowId}",
            arguments = listOf(navArgument("flowId") { type = NavType.StringType }),
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) { entry ->
            val flowId = entry.arguments?.getString("flowId").orEmpty()
            FlowEditScreen(
                flowId = flowId,
                onBack = { navController.popBackStack() },
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
                onOpenViolationDetail = ::openViolationDetail,
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
                onOpenViolationDetail = ::openViolationDetail,
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

/**
 * Apre GitHub Issues precompilata per feedback Maestro.
 */
private fun openMaestroGithubIssue(context: Context, bug: Boolean) {
    val device = FeedbackIssueBuilder.formatDeviceInfo(
        model = Build.MODEL,
        apiLevel = Build.VERSION.SDK_INT,
        appVersion = BuildConfig.VERSION_NAME,
    )
    val url = FeedbackIssueBuilder.buildMaestroUrl(bug = bug, deviceInfo = device)
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
