package org.julsz.smnt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.http.*
import kotlinx.coroutines.launch

private val ROOM_STATUSES = listOf("free", "occupied", "out_of_order")

@Composable
fun RoomsConfigPage(client: HttpClient, hotel: UserHotelRoleDto, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var rooms        by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    var error        by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var showAddDialog   by remember { mutableStateOf(false) }
    var editingRoom     by remember { mutableStateOf<RoomDto?>(null) }

    suspend fun loadRooms() {
        loading = true; error = null
        try {
            rooms = client.get("$BASE_URL/api/rooms?hotelId=${hotel.hotelId}").body()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(hotel.hotelId) { loadRooms() }

    fun reload() = scope.launch { loadRooms() }

    fun archiveRoom(room: RoomDto) = scope.launch {
        try {
            val action = if (room.archivedAt == null) "archive" else "unarchive"
            client.patch("$BASE_URL/api/rooms/${room.id}/$action")
            loadRooms()
        } catch (e: Exception) { error = e.message }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // ── Breadcrumb ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("← Config", style = MaterialTheme.typography.labelMedium)
            }
        }

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Rooms", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${rooms.count { it.archivedAt == null }} active · ${rooms.count { it.archivedAt != null }} archived",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let {
                    Text("Error: $it", color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = showArchived, onCheckedChange = { showArchived = it })
                    Text("Show archived", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { showAddDialog = true }) { Text("Add Room") }
            }
        }

        // ── Grid ──────────────────────────────────────────────────────────────
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val visible = if (showArchived) rooms else rooms.filter { it.archivedAt == null }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (rooms.isEmpty()) "No rooms yet. Add the first room."
                        else "All rooms are archived.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(visible, key = { it.id }) { room ->
                        RoomCard(
                            room      = room,
                            onEdit    = { editingRoom = room },
                            onArchive = { archiveRoom(room) }
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showAddDialog) {
        AddRoomDialog(
            hotelId   = hotel.hotelId,
            onDismiss = { showAddDialog = false },
            onCreate  = { req ->
                scope.launch {
                    try {
                        client.post("$BASE_URL/api/rooms") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        loadRooms()
                    } catch (e: Exception) { error = e.message }
                }
            }
        )
    }

    editingRoom?.let { room ->
        EditRoomDialog(
            room      = room,
            onDismiss = { editingRoom = null },
            onSave    = { req ->
                scope.launch {
                    try {
                        client.put("$BASE_URL/api/rooms/${room.id}") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        loadRooms()
                    } catch (e: Exception) { error = e.message }
                }
            }
        )
    }
}

// ─── Room Card ────────────────────────────────────────────────────────────────

@Composable
private fun RoomCard(room: RoomDto, onEdit: () -> Unit, onArchive: () -> Unit) {
    val archived = room.archivedAt != null
    val alpha    = if (archived) 0.5f else 1f

    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (archived) 0.dp else 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (archived)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Room number + status
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Room ${room.number}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                if (archived) {
                    SuggestionChip(onClick = {}, label = { Text("archived", style = MaterialTheme.typography.labelSmall) })
                } else {
                    StatusChip(room.status)
                }
            }

            // Meta row
            Text(
                buildString {
                    append(room.typeName)
                    room.floor?.let { append(" · Floor $it") }
                    append(" · ${room.maxGuests} guest${if (room.maxGuests != 1) "s" else ""}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )

            // Description
            room.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!archived) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                }
                TextButton(onClick = onArchive) {
                    Text(if (archived) "Unarchive" else "Archive")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (bg, fg) = statusColors(status)
    Box(
        modifier = Modifier
            .background(bg, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(status.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

private val statusColorMap: Map<String, Pair<Color, Color>> = mapOf(
    "free"         to (Color(0xFFDCF5E0) to Color(0xFF1B5E20)),
    "occupied"     to (Color(0xFFD0E4FF) to Color(0xFF0D3B7A)),
    "out_of_order" to (Color(0xFFEEEEEE) to Color(0xFF424242)),
)

@Composable
private fun statusColors(status: String): Pair<Color, Color> =
    statusColorMap[status] ?: (MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant)

// ─── Add Dialog ───────────────────────────────────────────────────────────────

@Composable
private fun AddRoomDialog(
    hotelId: Int,
    onDismiss: () -> Unit,
    onCreate: (CreateRoomRequest) -> Unit
) {
    var typeName    by remember { mutableStateOf("") }
    var number      by remember { mutableStateOf("") }
    var floor       by remember { mutableStateOf("") }
    var maxGuests   by remember { mutableStateOf("2") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomFormFields(
                    typeName = typeName, onTypeName = { typeName = it },
                    number = number, onNumber = { number = it },
                    floor = floor, onFloor = { floor = it },
                    maxGuests = maxGuests, onMaxGuests = { maxGuests = it },
                    description = description, onDescription = { description = it },
                    status = null, onStatus = {}
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(CreateRoomRequest(
                        hotelId     = hotelId,
                        typeName    = typeName.trim(),
                        number      = number.trim(),
                        floor       = floor.trim().toIntOrNull(),
                        maxGuests   = maxGuests.trim().toIntOrNull() ?: 2,
                        description = description.trim().ifBlank { null }
                    ))
                    onDismiss()
                },
                enabled = typeName.isNotBlank() && number.isNotBlank() && maxGuests.trim().toIntOrNull() != null
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Edit Dialog ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRoomDialog(
    room: RoomDto,
    onDismiss: () -> Unit,
    onSave: (UpdateRoomRequest) -> Unit
) {
    var typeName    by remember { mutableStateOf(room.typeName) }
    var number      by remember { mutableStateOf(room.number) }
    var floor       by remember { mutableStateOf(room.floor?.toString() ?: "") }
    var maxGuests   by remember { mutableStateOf(room.maxGuests.toString()) }
    var description by remember { mutableStateOf(room.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Room ${room.number}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomFormFields(
                    typeName = typeName, onTypeName = { typeName = it },
                    number = number, onNumber = { number = it },
                    floor = floor, onFloor = { floor = it },
                    maxGuests = maxGuests, onMaxGuests = { maxGuests = it },
                    description = description, onDescription = { description = it },
                    status = null, onStatus = {}
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(UpdateRoomRequest(
                        typeName    = typeName.trim(),
                        number      = number.trim(),
                        floor       = floor.trim().toIntOrNull(),
                        maxGuests   = maxGuests.trim().toIntOrNull() ?: room.maxGuests,
                        description = description.trim().ifBlank { null }
                    ))
                    onDismiss()
                },
                enabled = typeName.isNotBlank() && number.isNotBlank() && maxGuests.trim().toIntOrNull() != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Shared form fields ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomFormFields(
    typeName: String, onTypeName: (String) -> Unit,
    number: String, onNumber: (String) -> Unit,
    floor: String, onFloor: (String) -> Unit,
    maxGuests: String, onMaxGuests: (String) -> Unit,
    description: String, onDescription: (String) -> Unit,
    status: String?, onStatus: (String) -> Unit
) {
    OutlinedTextField(typeName, onTypeName, label = { Text("Room Type *") },
        placeholder = { Text("Single / Double / Suite …") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(number, onNumber, label = { Text("Number *") },
            singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(floor, onFloor, label = { Text("Floor") },
            singleLine = true, modifier = Modifier.weight(1f))
    }
    OutlinedTextField(maxGuests, onMaxGuests, label = { Text("Max Guests *") },
        singleLine = true, modifier = Modifier.fillMaxWidth())

    if (status != null) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = status.replace('_', ' '),
                onValueChange = {},
                readOnly = true,
                label = { Text("Status") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                singleLine = true
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ROOM_STATUSES.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.replace('_', ' ')) },
                        onClick = { onStatus(s); expanded = false }
                    )
                }
            }
        }
    }

    OutlinedTextField(description, onDescription, label = { Text("Description") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
}
