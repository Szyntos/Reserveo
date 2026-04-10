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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val BASE_URL = "http://localhost:8080"

@Composable
fun DbViewerApp() {
    val tabs = listOf("Hotels", "Rooms", "Guests", "Reservations", "Users")
    var selectedTab by remember { mutableStateOf(0) }

    var hotels       by remember { mutableStateOf<List<HotelDto>>(emptyList()) }
    var rooms        by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var guests       by remember { mutableStateOf<List<GuestDto>>(emptyList()) }
    var reservations by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var users        by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf<String?>(null) }

    val client = remember { HttpClient(CIO) { install(ContentNegotiation) { json() } } }
    DisposableEffect(Unit) { onDispose { client.close() } }
    val scope = rememberCoroutineScope()

    fun reload() = scope.launch {
        loading = true; error = null
        try {
            hotels       = client.get("$BASE_URL/api/hotels").body()
            rooms        = client.get("$BASE_URL/api/rooms").body()
            guests       = client.get("$BASE_URL/api/guests").body()
            reservations = client.get("$BASE_URL/api/reservations").body()
            users        = client.get("$BASE_URL/api/users").body()
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        } finally { loading = false }
    }

    fun createHotel(req: CreateHotelRequest) = scope.postAndReload(client, "hotels", req,
        onError = { error = it }, onSuccess = { reload() })

    fun createRoom(req: CreateRoomRequest) = scope.postAndReload(client, "rooms", req,
        onError = { error = it }, onSuccess = { reload() })

    fun createUser(req: CreateUserRequest) = scope.postAndReload(client, "users", req,
        onError = { error = it }, onSuccess = { reload() })

    LaunchedEffect(Unit) { reload() }

    val sansSerifTypography = remember {
        val ff = FontFamily.SansSerif
        Typography(
            displayLarge  = Typography().displayLarge.copy(fontFamily = ff),
            displayMedium = Typography().displayMedium.copy(fontFamily = ff),
            displaySmall  = Typography().displaySmall.copy(fontFamily = ff),
            headlineLarge = Typography().headlineLarge.copy(fontFamily = ff),
            headlineMedium= Typography().headlineMedium.copy(fontFamily = ff),
            headlineSmall = Typography().headlineSmall.copy(fontFamily = ff),
            titleLarge    = Typography().titleLarge.copy(fontFamily = ff),
            titleMedium   = Typography().titleMedium.copy(fontFamily = ff),
            titleSmall    = Typography().titleSmall.copy(fontFamily = ff),
            bodyLarge     = Typography().bodyLarge.copy(fontFamily = ff),
            bodyMedium    = Typography().bodyMedium.copy(fontFamily = ff),
            bodySmall     = Typography().bodySmall.copy(fontFamily = ff),
            labelLarge    = Typography().labelLarge.copy(fontFamily = ff),
            labelMedium   = Typography().labelMedium.copy(fontFamily = ff),
            labelSmall    = Typography().labelSmall.copy(fontFamily = ff),
        )
    }
    MaterialTheme(typography = sansSerifTypography) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
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

            // ── Tabs ──────────────────────────────────────────────────────────
            val counts = listOf(hotels.size, rooms.size, guests.size, reservations.size, users.size)
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(if (counts[i] > 0) "$title (${counts[i]})" else title) })
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            when (selectedTab) {
                0 -> HotelsTab(hotels,       onCreate = ::createHotel)
                1 -> RoomsTab(rooms, hotels, onCreate = ::createRoom)
                2 -> GuestsTab(guests)
                3 -> ReservationsTab(reservations)
                4 -> UsersTab(users,         onCreate = ::createUser)
            }
        }
    }
}

// ─── Hotels ───────────────────────────────────────────────────────────────────

@Composable
private fun HotelsTab(rows: List<HotelDto>, onCreate: (CreateHotelRequest) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TabToolbar { Button(onClick = { showDialog = true }) { Text("New Hotel") } }
        DataTable(
            headers = listOf("ID" to 45.dp, "Name" to 200.dp, "Address" to 280.dp,
                             "Phone" to 160.dp, "Email" to 220.dp),
            rows = rows
        ) { h -> listOf(h.id.toString(), h.name, h.address.d(), h.phone.d(), h.email.d()) }
    }
    if (showDialog) CreateHotelDialog(onDismiss = { showDialog = false }, onCreate = onCreate)
}

@Composable
private fun CreateHotelDialog(onDismiss: () -> Unit, onCreate: (CreateHotelRequest) -> Unit) {
    var name    by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone   by remember { mutableStateOf("") }
    var email   by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Hotel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField("Name *", name, { name = it })
                FormField("Address", address, { address = it })
                FormField("Phone", phone, { phone = it })
                FormField("Email", email, { email = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(CreateHotelRequest(name.trim(),
                        address.trim().ifBlank { null },
                        phone.trim().ifBlank { null },
                        email.trim().ifBlank { null }))
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Rooms ────────────────────────────────────────────────────────────────────

@Composable
private fun RoomsTab(rows: List<RoomDto>, hotels: List<HotelDto>, onCreate: (CreateRoomRequest) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TabToolbar { Button(onClick = { showDialog = true }) { Text("New Room") } }
        DataTable(
            headers = listOf("ID" to 45.dp, "Hotel" to 160.dp, "Room" to 65.dp, "Floor" to 55.dp,
                             "Max Guests" to 85.dp, "Status" to 120.dp, "Description" to 240.dp),
            rows = rows
        ) { r -> listOf(r.id.toString(), r.hotelName, r.number, r.floor?.toString().d(),
                        r.maxGuests.toString(), r.status, r.description.d()) }
    }
    if (showDialog) CreateRoomDialog(hotels, onDismiss = { showDialog = false }, onCreate = onCreate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRoomDialog(
    hotels: List<HotelDto>,
    onDismiss: () -> Unit,
    onCreate: (CreateRoomRequest) -> Unit
) {
    var selectedHotel by remember { mutableStateOf(hotels.firstOrNull()) }
    var hotelExpanded by remember { mutableStateOf(false) }
    var typeName      by remember { mutableStateOf("") }
    var number        by remember { mutableStateOf("") }
    var floor         by remember { mutableStateOf("") }
    var maxGuests     by remember { mutableStateOf("2") }
    var description   by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = hotelExpanded, onExpandedChange = { hotelExpanded = it }) {
                    OutlinedTextField(
                        value = selectedHotel?.name ?: "Select hotel",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hotel *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(hotelExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = hotelExpanded, onDismissRequest = { hotelExpanded = false }) {
                        hotels.forEach { hotel ->
                            DropdownMenuItem(
                                text = { Text(hotel.name) },
                                onClick = { selectedHotel = hotel; hotelExpanded = false }
                            )
                        }
                    }
                }
                FormField("Room Type *", typeName, { typeName = it }, placeholder = "Single / Double / Suite …")
                FormField("Room Number *", number, { number = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(floor, { floor = it }, label = { Text("Floor") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(maxGuests, { maxGuests = it }, label = { Text("Max Guests *") },
                        singleLine = true, modifier = Modifier.weight(1f))
                }
                FormField("Description", description, { description = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(CreateRoomRequest(
                        hotelId     = selectedHotel!!.id,
                        typeName    = typeName.trim(),
                        number      = number.trim(),
                        floor       = floor.trim().toIntOrNull(),
                        maxGuests   = maxGuests.trim().toIntOrNull() ?: 2,
                        description = description.trim().ifBlank { null }
                    ))
                    onDismiss()
                },
                enabled = selectedHotel != null && typeName.isNotBlank() &&
                          number.isNotBlank() && maxGuests.trim().toIntOrNull() != null
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Guests ───────────────────────────────────────────────────────────────────

@Composable
private fun GuestsTab(rows: List<GuestDto>) = DataTable(
    headers = listOf("ID" to 45.dp, "First Name" to 120.dp, "Last Name" to 140.dp,
                     "Email" to 220.dp, "Phone" to 155.dp, "Nationality" to 95.dp,
                     "Blacklisted" to 85.dp),
    rows = rows
) { g -> listOf(g.id.toString(), g.firstName, g.lastName, g.email.d(), g.phone.d(),
                g.nationality.d(), if (g.blacklisted) "YES" else "no") }

// ─── Reservations ─────────────────────────────────────────────────────────────

@Composable
private fun ReservationsTab(rows: List<ReservationDto>) = DataTable(
    headers = listOf("ID" to 45.dp, "Hotel" to 155.dp, "Room" to 55.dp, "Guest" to 170.dp,
                     "Check-in" to 95.dp, "Check-out" to 95.dp, "Status" to 105.dp,
                     "Pax" to 45.dp, "Total PLN" to 90.dp),
    rows = rows
) { r -> listOf(r.id.toString(), r.hotelName, r.roomNumber, r.guestName,
                r.checkInDate, r.checkOutDate, r.status, "${r.adults}+${r.children}",
                r.totalAmount?.let { "%.2f".format(it) }.d()) }

// ─── Users ────────────────────────────────────────────────────────────────────

@Composable
private fun UsersTab(rows: List<UserDto>, onCreate: (CreateUserRequest) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TabToolbar { Button(onClick = { showDialog = true }) { Text("Register User") } }
        DataTable(
            headers = listOf("ID" to 45.dp, "Name" to 200.dp, "Email" to 280.dp, "Created" to 160.dp),
            rows = rows
        ) { u -> listOf(u.id.toString(), u.name, u.email, u.createdAt) }
    }
    if (showDialog) CreateUserDialog(onDismiss = { showDialog = false }, onCreate = onCreate)
}

@Composable
private fun CreateUserDialog(onDismiss: () -> Unit, onCreate: (CreateUserRequest) -> Unit) {
    var name  by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField("Name *", name, { name = it })
                FormField("Email *", email, { email = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(CreateUserRequest(name.trim(), email.trim()))
                    onDismiss()
                },
                enabled = name.isNotBlank() && email.isNotBlank()
            ) { Text("Register") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Shared components ────────────────────────────────────────────────────────

@Composable
private fun TabToolbar(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End, content = content)
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun <T : Any> DataTable(
    headers: List<Pair<String, Dp>>,
    rows: List<T>,
    cells: (T) -> List<String>
) {
    val stripe = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            headers.forEach { (label, width) ->
                Text(label, modifier = Modifier.width(width), fontWeight = FontWeight.SemiBold,
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
                            Text(value, modifier = Modifier.width(header.second),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun String?.d() = this ?: "—"

private inline fun <reified T : Any> CoroutineScope.postAndReload(
    client: HttpClient,
    endpoint: String,
    body: T,
    crossinline onError: (String) -> Unit,
    crossinline onSuccess: () -> Unit
) = launch {
    try {
        client.post("$BASE_URL/api/$endpoint") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        onSuccess()
    } catch (e: Exception) {
        onError(e.message ?: "Unknown error")
    }
}
