package dev.accessscope.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.accessscope.scanner.ui.screen.HomeScreen
import dev.accessscope.scanner.ui.screen.ReportScreen
import dev.accessscope.scanner.ui.screen.SettingsScreen
import dev.accessscope.scanner.ui.theme.AccessScopeTheme
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import dev.accessscope.scanner.util.PdfHelper

class MainActivity : ComponentActivity() {

    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AccessScopeTheme {
                AccessScopeNavHost(viewModel = scanViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scanViewModel.refreshPermissions()
    }
}

@Composable
private fun AccessScopeNavHost(viewModel: ScanViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenReport = { navController.navigate("report") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable("report") {
            ReportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPdf = { path -> PdfHelper.openPdf(context, path) },
            )
        }
    }
}
