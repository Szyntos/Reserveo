package org.julsz.smnt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    val s  = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surface)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                s.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        currentUser.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer
                    )
                }
                Text(
                    currentUser.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                TextButton(onClick = onLogout) { Text(s.logout) }
            }
        }
        HorizontalDivider(color = cs.outlineVariant)

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
                    val gridState = rememberLazyGridState()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 280.dp),
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(end = 12.dp)
                        ) {
                            items(hotels) { hotel ->
                                HotelCard(hotel = hotel, onClick = { onHotelSelected(hotel) })
                            }
                        }
                        AppVerticalScrollbar(
                            state    = gridState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HotelCard(hotel: UserHotelRoleDto, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, hoveredElevation = 0.dp),
        border    = BorderStroke(1.dp, cs.outlineVariant),
        colors    = CardDefaults.cardColors(containerColor = cs.surface),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    hotel.hotelName.firstOrNull()?.uppercaseChar()?.toString() ?: "H",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimaryContainer
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    hotel.hotelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AssistChip(
                    onClick = {},
                    label = { Text(hotel.role, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}
