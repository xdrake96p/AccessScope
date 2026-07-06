package dev.accessscope.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.accessscope.scanner.ui.screen.HomeScreen
import dev.accessscope.scanner.ui.theme.AccessScopeTheme
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel

class MainActivity : ComponentActivity() {

    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AccessScopeTheme {
                HomeScreen(viewModel = scanViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scanViewModel.refreshPermissions()
    }
}
