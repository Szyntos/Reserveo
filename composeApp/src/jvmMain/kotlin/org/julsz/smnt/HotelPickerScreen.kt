package org.julsz.smnt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

@Composable
fun HotelPickerScreen(
    client: HttpClient,
    currentUser: UserDto,
    onHotelSelected: (UserHotelRoleDto) -> Unit,
    onLogout: () -> Unit
) {
    var hotels  by remember { mutableStateOf<List<UserHotelRoleDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser.id) {
        loading = true
        try {
            hotels = client.get("$BASE_URL/api/users/${currentUser.id}/hotels").body()
        } catch (e: Exception) {
            error = e.message ?: "Failed to load hotels"
        } finally {
            loading = false
        }
    }

    val s = LocalStrings.current
    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(s.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    currentUser.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onLogout) { Text(s.logout) }
            }
        }
        HorizontalDivider()

        // Content
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator()
                error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(s.errorMsg(error!!), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { error = null; loading = true }) { Text(s.retry) }
                }
                hotels.isEmpty() -> Text(
                    s.noHotelsAssigned,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            s.welcomeBack(currentUser.name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            s.selectHotelToManage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(hotels) { hotel ->
                            HotelCard(hotel = hotel, onClick = { onHotelSelected(hotel) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HotelCard(hotel: UserHotelRoleDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                hotel.hotelName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            AssistChip(
                onClick = {},
                label = { Text(hotel.role, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
