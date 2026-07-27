/**
 * Top app bar coerente con il design system AccessScope.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.R
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily

/**
 * Barra superiore con titolo e pulsante indietro o menu opzionale.
 *
 * @param title Titolo mostrato nella barra.
 * @param onBack Callback per tornare indietro; se null, non mostra l'icona indietro.
 * @param onMenuClick Se impostato (e [onBack] null), mostra hamburger drawer.
 * @param actions Slot azioni a destra (es. chip Beta).
 * @param modifier Modifier esterno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessScopeTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro")
                }
                onMenuClick != null -> IconButton(onClick = onMenuClick) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Apri menu")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * Top bar della home con hamburger (drawer), logo e wordmark AccessScope.
 *
 * @param onMenuClick Apre il navigation drawer laterale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(onMenuClick: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_access_scope_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "AccessScope",
                    fontFamily = HankenGroteskFamily,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Outlined.Menu, contentDescription = "Apri menu")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
