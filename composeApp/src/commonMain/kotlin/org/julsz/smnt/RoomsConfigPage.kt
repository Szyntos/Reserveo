package org.julsz.smnt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.http.*
import kotlinx.coroutines.delay
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

    fun archiveRoom(room: RoomDto) = scope.launch {
        try {
            val action = if (room.archivedAt == null) "archive" else "unarchive"
            client.patch("$BASE_URL/api/rooms/${room.id}/$action")
            loadRooms()
        } catch (e: Exception) { error = e.message }
    }

    val s = LocalStrings.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // ── Breadcrumb ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text(s.breadcrumbConfig, style = MaterialTheme.typography.labelMedium)
            }
        }

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(s.roomsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    s.roomsStats(rooms.count { it.archivedAt == null }, rooms.count { it.archivedAt != null }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let {
                    Text(s.errorMsg(it), color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = showArchived, onCheckedChange = { showArchived = it })
                    Text(s.showArchived, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { showAddDialog = true }) { Text(s.addRoomBtn) }
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
                        if (rooms.isEmpty()) s.noRoomsYet else s.allRoomsArchived,
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
            client    = client,
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
            client    = client,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomCard(room: RoomDto, onEdit: () -> Unit, onArchive: () -> Unit) {
    val s        = LocalStrings.current
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s.roomLabel(room.number),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                if (archived) {
                    SuggestionChip(onClick = {}, label = { Text(s.archivedChip, style = MaterialTheme.typography.labelSmall) })
                } else {
                    StatusChip(room.status)
                }
            }

            Text(
                buildString {
                    append(room.typeName)
                    room.floor?.let { append(" · Floor $it") }
                    append(" · ${room.maxGuests} guest${if (room.maxGuests != 1) "s" else ""}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )

            room.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (room.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    room.tags.forEach { tag -> TagChip(tag, alpha = alpha) }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!archived) {
                    TextButton(onClick = onEdit) { Text(s.edit) }
                }
                TextButton(onClick = onArchive) {
                    Text(if (archived) s.unarchive else s.archive)
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: String, alpha: Float = 1f) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha * 0.7f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha)
        )
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
    client: HttpClient,
    hotelId: Int,
    onDismiss: () -> Unit,
    onCreate: (CreateRoomRequest) -> Unit
) {
    var typeName    by remember { mutableStateOf("") }
    var number      by remember { mutableStateOf("") }
    var floor       by remember { mutableStateOf("") }
    var maxGuests   by remember { mutableStateOf("2") }
    var description by remember { mutableStateOf("") }
    var tags        by remember { mutableStateOf<List<String>>(emptyList()) }

    val s = LocalStrings.current
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.addRoomTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomFormFields(
                    client = client, hotelId = hotelId,
                    typeName = typeName, onTypeName = { typeName = it },
                    number = number, onNumber = { number = it },
                    floor = floor, onFloor = { floor = it },
                    maxGuests = maxGuests, onMaxGuests = { maxGuests = it },
                    description = description, onDescription = { description = it },
                    status = null, onStatus = {},
                    tags = tags,
                    onAddTag = { tag -> if (tag !in tags) tags = tags + tag },
                    onRemoveTag = { tag -> tags = tags - tag }
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
                        description = description.trim().ifBlank { null },
                        tags        = tags
                    ))
                    onDismiss()
                },
                enabled = typeName.isNotBlank() && number.isNotBlank() && maxGuests.trim().toIntOrNull() != null
            ) { Text(s.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

// ─── Edit Dialog ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRoomDialog(
    client: HttpClient,
    room: RoomDto,
    onDismiss: () -> Unit,
    onSave: (UpdateRoomRequest) -> Unit
) {
    var typeName    by remember { mutableStateOf(room.typeName) }
    var number      by remember { mutableStateOf(room.number) }
    var floor       by remember { mutableStateOf(room.floor?.toString() ?: "") }
    var maxGuests   by remember { mutableStateOf(room.maxGuests.toString()) }
    var description by remember { mutableStateOf(room.description ?: "") }
    var tags        by remember { mutableStateOf(room.tags) }

    val s = LocalStrings.current
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.editRoomTitle(room.number)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomFormFields(
                    client = client, hotelId = room.hotelId,
                    typeName = typeName, onTypeName = { typeName = it },
                    number = number, onNumber = { number = it },
                    floor = floor, onFloor = { floor = it },
                    maxGuests = maxGuests, onMaxGuests = { maxGuests = it },
                    description = description, onDescription = { description = it },
                    status = null, onStatus = {},
                    tags = tags,
                    onAddTag = { tag -> if (tag !in tags) tags = tags + tag },
                    onRemoveTag = { tag -> tags = tags - tag }
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
                        description = description.trim().ifBlank { null },
                        tags        = tags
                    ))
                    onDismiss()
                },
                enabled = typeName.isNotBlank() && number.isNotBlank() && maxGuests.trim().toIntOrNull() != null
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

// ─── Shared form fields ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun RoomFormFields(
    client: HttpClient,
    hotelId: Int,
    typeName: String, onTypeName: (String) -> Unit,
    number: String, onNumber: (String) -> Unit,
    floor: String, onFloor: (String) -> Unit,
    maxGuests: String, onMaxGuests: (String) -> Unit,
    description: String, onDescription: (String) -> Unit,
    status: String?, onStatus: (String) -> Unit,
    tags: List<String>, onAddTag: (String) -> Unit, onRemoveTag: (String) -> Unit
) {
    val s = LocalStrings.current
    OutlinedTextField(typeName, onTypeName, label = { Text(s.roomTypeLabel) },
        placeholder = { Text(s.roomTypePlaceholder) },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(number, onNumber, label = { Text(s.numberLabel) },
            singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(floor, onFloor, label = { Text(s.floorLabel) },
            singleLine = true, modifier = Modifier.weight(1f))
    }
    OutlinedTextField(maxGuests, onMaxGuests, label = { Text(s.maxGuestsLabel) },
        singleLine = true, modifier = Modifier.fillMaxWidth())

    if (status != null) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = status.replace('_', ' '),
                onValueChange = {},
                readOnly = true,
                label = { Text(s.statusLabel) },
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

    OutlinedTextField(description, onDescription, label = { Text(s.descriptionLabel) },
        singleLine = true, modifier = Modifier.fillMaxWidth())

    // ── Tag input with prefix-search suggestions ──────────────────────────────
    // searchKey tracks what the user actually typed; tagInput may be overwritten
    // by Tab/arrows with a suggestion without triggering a new search.
    var tagInput   by remember { mutableStateOf("") }
    var searchKey  by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<TagDto>>(emptyList()) }
    var expanded   by remember { mutableStateOf(false) }
    var selIndex   by remember { mutableStateOf(-1) }

    LaunchedEffect(searchKey) {
        selIndex = -1
        if (searchKey.isBlank()) { suggestions = emptyList(); expanded = false; return@LaunchedEffect }
        delay(150)
        try {
            val result: List<TagDto> = client.get("$BASE_URL/api/tags") {
                parameter("hotelId", hotelId)
                parameter("prefix", searchKey.trim())
            }.body()
            suggestions = result.filter { it.name !in tags }
            expanded = suggestions.isNotEmpty()
        } catch (_: Exception) {
            suggestions = emptyList(); expanded = false
        }
    }

    fun cycleTo(index: Int) {
        selIndex = index
        tagInput = suggestions[index].name
    }

    fun commitTag(name: String) {
        val cleaned = name.trim().lowercase()
        if (cleaned.isNotBlank()) onAddTag(cleaned)
        tagInput = ""; searchKey = ""
        suggestions = emptyList()
        expanded = false; selIndex = -1
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it && suggestions.isNotEmpty() },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { v -> tagInput = v; searchKey = v; if (v.isBlank()) expanded = false },
                    label = { Text(s.tagsLabel) },
                    placeholder = { Text(s.tagInputHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .onPreviewKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyDown && event.key == Key.Tab -> {
                                    if (suggestions.isNotEmpty()) {
                                        cycleTo((selIndex + 1) % suggestions.size)
                                        expanded = true
                                    }
                                    true
                                }
event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                    commitTag(tagInput); true
                                }
                                else -> false
                            }
                        }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    suggestions.forEachIndexed { index, tag ->
                        DropdownMenuItem(
                            text = { Text(tag.name) },
                            onClick = { commitTag(tag.name) },
                            modifier = if (index == selIndex)
                                Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                            else Modifier
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = { commitTag(tagInput) },
                enabled = tagInput.isNotBlank(),
                modifier = Modifier.padding(top = 4.dp)
            ) { Text("+") }
        }

        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveTag(tag) },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { Text("×", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}
