package org.julsz.smnt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*

private enum class AppScreen(val label: String) {
    Dashboard("Dashboard"),
    Reservations("Reservations"),
    Config("Config"),
}

@Composable
fun MainApp(
    client: HttpClient,
    currentUser: UserDto,
    selectedHotel: UserHotelRoleDto,
    onSwitchHotel: () -> Unit,
    onLogout: () -> Unit,
    isDark: Boolean = true,
    onThemeToggle: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(AppScreen.Dashboard) }

    Row(Modifier.fillMaxSize()) {
        AppSidebar(
            currentUser   = currentUser,
            selectedHotel = selectedHotel,
            currentScreen = currentScreen,
            onScreenChange = { currentScreen = it },
            onSwitchHotel = onSwitchHotel,
            onLogout      = onLogout,
            isDark        = isDark,
            onThemeToggle = onThemeToggle
        )
        VerticalDivider()
        Box(Modifier.fillMaxSize().padding(28.dp)) {
            when (currentScreen) {
                AppScreen.Dashboard    -> DashboardPage(selectedHotel)
                AppScreen.Reservations -> ReservationsCalendarPage(client, selectedHotel)
                AppScreen.Config       -> ConfigPage(client, selectedHotel)
            }
        }
    }
}

// ─── Sidebar ──────────────────────────────────────────────────────────────────

@Composable
private fun AppSidebar(
    currentUser: UserDto,
    selectedHotel: UserHotelRoleDto,
    currentScreen: AppScreen,
    onScreenChange: (AppScreen) -> Unit,
    onSwitchHotel: () -> Unit,
    onLogout: () -> Unit,
    isDark: Boolean,
    onThemeToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Hotel header
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 4.dp)
            ) {
                Text(
                    selectedHotel.hotelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(selectedHotel.role, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
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
            // User info
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    currentUser.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    currentUser.email,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            HorizontalDivider()
            SidebarItem(label = "Switch Hotel", selected = false, onClick = onSwitchHotel)
            SidebarItem(label = "Logout", selected = false, onClick = onLogout)
            // Theme toggle pill
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    listOf(true to "Dark", false to "Light").forEach { (dark, label) ->
                        val selected = isDark == dark
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable(enabled = !selected, onClick = onThemeToggle)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
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
private fun DashboardPage(hotel: UserHotelRoleDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            hotel.hotelName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Dashboard — stats, arrivals and quick actions will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

