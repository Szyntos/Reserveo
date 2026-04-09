package org.julsz.smnt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch

private const val BASE_URL = "http://localhost:8080"

@Composable
fun DbViewerApp() {
    val tabs = listOf("Hotels", "Rooms", "Guests", "Reservations")
    var selectedTab by remember { mutableStateOf(0) }

    var hotels       by remember { mutableStateOf<List<HotelDto>>(emptyList()) }
    var rooms        by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var guests       by remember { mutableStateOf<List<GuestDto>>(emptyList()) }
    var reservations by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf<String?>(null) }

    val client = remember {
        HttpClient(CIO) { install(ContentNegotiation) { json() } }
    }
    DisposableEffect(Unit) { onDispose { client.close() } }

    val scope = rememberCoroutineScope()

    fun reload() = scope.launch {
        loading = true
        error = null
        try {
            hotels       = client.get("$BASE_URL/api/hotels").body()
            rooms        = client.get("$BASE_URL/api/rooms").body()
            guests       = client.get("$BASE_URL/api/guests").body()
            reservations = client.get("$BASE_URL/api/reservations").body()
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    MaterialTheme {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reserveo DB Viewer", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    error?.let {
                        Text("Error: $it", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Button(onClick = { reload() }, enabled = !loading,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("Refresh")
                    }
                }
            }

            // ── Tabs ─────────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    val count = when (i) {
                        0 -> hotels.size
                        1 -> rooms.size
                        2 -> guests.size
                        3 -> reservations.size
                        else -> 0
                    }
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(if (count > 0) "$title ($count)" else title) }
                    )
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            when (selectedTab) {
                0 -> HotelsTab(hotels)
                1 -> RoomsTab(rooms)
                2 -> GuestsTab(guests)
                3 -> ReservationsTab(reservations)
            }
        }
    }
}

// ─── Tab content ──────────────────────────────────────────────────────────────

@Composable
private fun HotelsTab(rows: List<HotelDto>) = DataTable(
    headers = listOf("ID" to 45.dp, "Name" to 200.dp, "Address" to 280.dp,
                     "Phone" to 160.dp, "Email" to 220.dp),
    rows = rows
) { h -> listOf(h.id.toString(), h.name, h.address.d(), h.phone.d(), h.email.d()) }

@Composable
private fun RoomsTab(rows: List<RoomDto>) = DataTable(
    headers = listOf("ID" to 45.dp, "Hotel" to 160.dp, "Room" to 65.dp, "Floor" to 55.dp,
                     "Max Guests" to 85.dp, "Status" to 120.dp, "Description" to 240.dp),
    rows = rows
) { r -> listOf(r.id.toString(), r.hotelName, r.number, r.floor?.toString().d(),
                r.maxGuests.toString(), r.status, r.description.d()) }

@Composable
private fun GuestsTab(rows: List<GuestDto>) = DataTable(
    headers = listOf("ID" to 45.dp, "First Name" to 120.dp, "Last Name" to 140.dp,
                     "Email" to 220.dp, "Phone" to 155.dp, "Nationality" to 95.dp,
                     "Blacklisted" to 85.dp),
    rows = rows
) { g -> listOf(g.id.toString(), g.firstName, g.lastName, g.email.d(), g.phone.d(),
                g.nationality.d(), if (g.blacklisted) "YES" else "no") }

@Composable
private fun ReservationsTab(rows: List<ReservationDto>) = DataTable(
    headers = listOf("ID" to 45.dp, "Hotel" to 155.dp, "Room" to 55.dp, "Guest" to 170.dp,
                     "Check-in" to 95.dp, "Check-out" to 95.dp, "Status" to 105.dp,
                     "Pax" to 45.dp, "Total PLN" to 90.dp),
    rows = rows
) { r -> listOf(r.id.toString(), r.hotelName, r.roomNumber, r.guestName,
                r.checkInDate, r.checkOutDate, r.status,
                "${r.adults}+${r.children}",
                r.totalAmount?.let { "%.2f".format(it) }.d()) }

// ─── Generic table ────────────────────────────────────────────────────────────

@Composable
private fun <T : Any> DataTable(
    headers: List<Pair<String, Dp>>,
    rows: List<T>,
    cells: (T) -> List<String>
) {
    val stripe = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Column(Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            headers.forEach { (label, width) ->
                Text(label,
                    modifier = Modifier.width(width),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        HorizontalDivider(thickness = 1.dp)

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows) { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 1) stripe else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        cells(row).zip(headers).forEach { (value, header) ->
                            Text(value,
                                modifier = Modifier.width(header.second),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

private fun String?.d() = this ?: "—"
