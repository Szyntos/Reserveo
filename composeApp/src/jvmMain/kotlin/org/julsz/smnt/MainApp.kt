package org.julsz.smnt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*

private enum class AppScreen(val label: String) {
    Dashboard("Dashboard"),
    HotelConfig("Hotel Config"),
}

@Composable
fun MainApp(client: HttpClient, currentUser: UserDto, onLogout: () -> Unit) {
    var currentScreen by remember { mutableStateOf(AppScreen.Dashboard) }

    Row(Modifier.fillMaxSize()) {
        AppSidebar(
            currentUser   = currentUser,
            currentScreen = currentScreen,
            onScreenChange = { currentScreen = it },
            onLogout      = onLogout
        )
        VerticalDivider()
        Box(Modifier.fillMaxSize().padding(28.dp)) {
            when (currentScreen) {
                AppScreen.Dashboard  -> DashboardPage()
                AppScreen.HotelConfig -> HotelConfigPage()
            }
        }
    }
}

// ─── Sidebar ──────────────────────────────────────────────────────────────────

@Composable
private fun AppSidebar(
    currentUser: UserDto,
    currentScreen: AppScreen,
    onScreenChange: (AppScreen) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Brand + user
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 16.dp)
            ) {
                Text(
                    "Reserveo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    currentUser.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    currentUser.email,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            AppScreen.entries.forEach { screen ->
                SidebarItem(
                    label    = screen.label,
                    selected = currentScreen == screen,
                    onClick  = { onScreenChange(screen) }
                )
            }
        }

        Column {
            HorizontalDivider()
            SidebarItem(label = "Logout", selected = false, onClick = onLogout)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SidebarItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg        = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}

// ─── Pages ────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardPage() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Stats, arrivals and quick actions will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HotelConfigPage() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Hotel Config",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Hotel settings and configuration will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
