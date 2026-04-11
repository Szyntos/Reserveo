package org.julsz.smnt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun BasePriceConfigPage(client: HttpClient, hotel: UserHotelRoleDto, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var rules   by remember { mutableStateOf<List<PriceRuleDto>>(emptyList()) }
    var rooms   by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }

    // null = tile grid; non-null = rules for that room
    var selectedRoom by remember { mutableStateOf<RoomDto?>(null) }

    suspend fun loadData() {
        loading = true; error = null
        try {
            rules = client.get("$BASE_URL/api/hotels/${hotel.hotelId}/price-rules").body()
            rooms = client.get("$BASE_URL/api/rooms?hotelId=${hotel.hotelId}").body<List<RoomDto>>()
                .filter { it.archivedAt == null }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(hotel.hotelId) { loadData() }

    val current = selectedRoom
    if (current == null) {
        RoomTileGrid(
            rooms   = rooms,
            rules   = rules,
            loading = loading,
            error   = error,
            onBack  = onBack,
            onSelect = { selectedRoom = it }
        )
    } else {
        RoomRulesView(
            client   = client,
            room     = current,
            rules    = rules.filter { it.roomId == current.id },
            allRooms = rooms,
            error    = error,
            onBack   = { selectedRoom = null },
            onError  = { error = it },
            onReload = { scope.launch { loadData() } }
        )
    }
}

// ─── Level 1: Room tile grid ──────────────────────────────────────────────────

@Composable
private fun RoomTileGrid(
    rooms: List<RoomDto>,
    rules: List<PriceRuleDto>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSelect: (RoomDto) -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text("← Config", style = MaterialTheme.typography.labelMedium)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Base Price", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Select a room to manage its price rules",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error?.let {
                Text("Error: $it", color = MaterialTheme.colorScheme.error,
                     style = MaterialTheme.typography.bodySmall)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (rooms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Add rooms first before setting price rules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val ruleCountByRoom = rules.groupBy { it.roomId }.mapValues { it.value.size }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(rooms, key = { it.id }) { room ->
                    RoomPriceTile(
                        room       = room,
                        ruleCount  = ruleCountByRoom[room.id] ?: 0,
                        onClick    = { onSelect(room) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomPriceTile(room: RoomDto, ruleCount: Int, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Room ${room.number}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                room.typeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (ruleCount == 0) "No rules" else "$ruleCount rule${if (ruleCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ruleCount == 0)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary
                )
                Text(
                    "Manage →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─── Level 2: Rules for one room ─────────────────────────────────────────────

@Composable
private fun RoomRulesView(
    client: HttpClient,
    room: RoomDto,
    rules: List<PriceRuleDto>,
    allRooms: List<RoomDto>,
    error: String?,
    onBack: () -> Unit,
    onError: (String?) -> Unit,
    onReload: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule   by remember { mutableStateOf<PriceRuleDto?>(null) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Breadcrumb
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text("← Base Price", style = MaterialTheme.typography.labelMedium)
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Room ${room.number}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${room.typeName} · ${rules.size} rule${if (rules.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let {
                    Text("Error: $it", color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { showAddDialog = true }) { Text("Add Rule") }
            }
        }

        // Table
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No rules yet for this room. Click \"Add Rule\" to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            PriceRuleTableHeader()
            HorizontalDivider()
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(rules, key = { it.id }) { rule ->
                    PriceRuleRow(
                        rule     = rule,
                        onEdit   = { editingRule = rule },
                        onDelete = {
                            scope.launch {
                                try {
                                    client.delete("$BASE_URL/api/price-rules/${rule.id}")
                                    onReload()
                                } catch (e: Exception) { onError(e.message) }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Dialogs
    if (showAddDialog) {
        PriceRuleDialog(
            title      = "Add Rule — Room ${room.number}",
            rooms      = allRooms,
            initial    = null,
            fixedRoom  = room,
            onDismiss  = { showAddDialog = false },
            onConfirm  = { req ->
                scope.launch {
                    try {
                        client.post("$BASE_URL/api/price-rules") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        showAddDialog = false
                        onReload()
                    } catch (e: Exception) { onError(e.message) }
                }
            }
        )
    }

    editingRule?.let { rule ->
        PriceRuleDialog(
            title     = "Edit Rule — Room ${room.number}",
            rooms     = allRooms,
            initial   = rule,
            fixedRoom = room,
            onDismiss = { editingRule = null },
            onConfirm = { req ->
                scope.launch {
                    try {
                        client.put("$BASE_URL/api/price-rules/${rule.id}") {
                            contentType(ContentType.Application.Json)
                            setBody(UpdatePriceRuleRequest(
                                fromDate               = req.fromDate,
                                toDate                 = req.toDate,
                                minNights              = req.minNights,
                                maxNights              = req.maxNights,
                                pricePerPersonPerNight = req.pricePerPersonPerNight,
                                currency               = req.currency
                            ))
                        }
                        editingRule = null
                        onReload()
                    } catch (e: Exception) { onError(e.message) }
                }
            }
        )
    }
}

// ─── Table components ─────────────────────────────────────────────────────────

@Composable
private fun PriceRuleTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("From",   Modifier.width(110.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("To",     Modifier.width(110.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("Nights", Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("Price / person / night", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PriceRuleRow(rule: PriceRuleDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    val nightsLabel = when {
        rule.maxNights == null -> "${rule.minNights}+ nights"
        rule.minNights == rule.maxNights -> "${rule.minNights} night${if (rule.minNights != 1) "s" else ""}"
        else -> "${rule.minNights}–${rule.maxNights} nights"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rule.fromDate,   Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
        Text(rule.toDate,     Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
        Text(nightsLabel,     Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
        Text(
            "%.2f %s".format(rule.pricePerPersonPerNight, rule.currency),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = onEdit,   contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Edit") }
        TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}

// ─── Dialog ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceRuleDialog(
    title: String,
    rooms: List<RoomDto>,
    initial: PriceRuleDto?,
    fixedRoom: RoomDto?,           // pre-selected, picker hidden
    onDismiss: () -> Unit,
    onConfirm: (CreatePriceRuleRequest) -> Unit
) {
    val resolvedRoom = fixedRoom ?: (if (initial != null) rooms.firstOrNull { it.id == initial.roomId } else rooms.firstOrNull())
    var selectedRoom by remember { mutableStateOf(resolvedRoom) }
    var roomExpanded by remember { mutableStateOf(false) }

    var fromDate  by remember { mutableStateOf(initial?.fromDate ?: "") }
    var toDate    by remember { mutableStateOf(initial?.toDate ?: "") }
    var minNights by remember { mutableStateOf(initial?.minNights?.toString() ?: "1") }
    var maxNights by remember { mutableStateOf(initial?.maxNights?.toString() ?: "") }
    var price     by remember { mutableStateOf(initial?.pricePerPersonPerNight?.toString() ?: "") }
    var currency  by remember { mutableStateOf(initial?.currency ?: "PLN") }

    val valid = selectedRoom != null &&
        fromDate.isNotBlank() && toDate.isNotBlank() &&
        minNights.toIntOrNull() != null &&
        price.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.width(380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Room picker — only shown when not fixed to a specific room
                if (fixedRoom == null) {
                    ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = { roomExpanded = it }) {
                        OutlinedTextField(
                            value         = selectedRoom?.number?.let { "Room $it" } ?: "Select room",
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Room *") },
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
                            modifier      = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            singleLine    = true,
                            enabled       = initial == null
                        )
                        ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { roomExpanded = false }) {
                            rooms.forEach { room ->
                                DropdownMenuItem(
                                    text    = { Text("Room ${room.number} · ${room.typeName}") },
                                    onClick = { selectedRoom = room; roomExpanded = false }
                                )
                            }
                        }
                    }
                }

                // Date range
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(
                        label          = "From *",
                        dateString     = fromDate,
                        onDateSelected = { fromDate = it },
                        modifier       = Modifier.weight(1f)
                    )
                    DatePickerField(
                        label          = "To *",
                        dateString     = toDate,
                        onDateSelected = { toDate = it },
                        modifier       = Modifier.weight(1f)
                    )
                }

                // Stay duration bracket
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        minNights, { minNights = it },
                        label      = { Text("Min nights *") },
                        singleLine = true,
                        modifier   = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        maxNights, { maxNights = it },
                        label       = { Text("Max nights") },
                        placeholder = { Text("blank = no limit") },
                        singleLine  = true,
                        modifier    = Modifier.weight(1f)
                    )
                }

                // Price + currency
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        price, { price = it },
                        label      = { Text("Price / person / night *") },
                        singleLine = true,
                        modifier   = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        currency, { currency = it },
                        label      = { Text("Currency") },
                        singleLine = true,
                        modifier   = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(CreatePriceRuleRequest(
                        roomId                 = selectedRoom!!.id,
                        fromDate               = fromDate.trim(),
                        toDate                 = toDate.trim(),
                        minNights              = minNights.trim().toInt(),
                        maxNights              = maxNights.trim().toIntOrNull(),
                        pricePerPersonPerNight = price.trim().toDouble(),
                        currency               = currency.trim().ifBlank { "PLN" }
                    ))
                },
                enabled = valid
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Date Picker Field ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    dateString: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    val initialMillis = remember(dateString) {
        if (dateString.isNotBlank()) {
            try { LocalDate.parse(dateString).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            catch (_: Exception) { null }
        } else null
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    OutlinedTextField(
        value         = dateString,
        onValueChange = {},
        readOnly      = true,
        label         = { Text(label) },
        trailingIcon  = {
            TextButton(
                onClick = { showPicker = true },
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { Text("📅") }
        },
        modifier   = modifier,
        singleLine = true
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onDateSelected(date.toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
