package org.julsz.smnt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*

private enum class ConfigSection(val title: String, val description: String) {
    Rooms("Rooms", "Manage rooms, statuses and availability"),
    BasePrice("Base Price", "Set pricing rules by room, period and stay length"),
}

@Composable
fun ConfigPage(client: HttpClient, hotel: UserHotelRoleDto) {
    var section by remember { mutableStateOf<ConfigSection?>(null) }

    when (section) {
        null ->
            ConfigHub(hotel, onNavigate = { section = it })
        ConfigSection.Rooms ->
            RoomsConfigPage(client, hotel, onBack = { section = null })
        ConfigSection.BasePrice ->
            BasePriceConfigPage(client, hotel, onBack = { section = null })
    }
}

// ─── Hub ──────────────────────────────────────────────────────────────────────

@Composable
private fun ConfigHub(hotel: UserHotelRoleDto, onNavigate: (ConfigSection) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Config",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                hotel.hotelName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ConfigSection.entries.forEach { entity ->
                ConfigCard(
                    title       = entity.title,
                    description = entity.description,
                    onClick     = { onNavigate(entity) },
                    modifier    = Modifier.width(220.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfigCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Manage →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
