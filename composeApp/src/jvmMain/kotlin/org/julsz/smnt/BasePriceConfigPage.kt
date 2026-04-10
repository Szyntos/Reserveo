package org.julsz.smnt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun BasePriceConfigPage(client: HttpClient, hotel: UserHotelRoleDto, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var rules   by remember { mutableStateOf<List<PriceRuleDto>>(emptyList()) }
    var rooms   by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }

    var showAddDialog  by remember { mutableStateOf(false) }
    var editingRule    by remember { mutableStateOf<PriceRuleDto?>(null) }

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

    fun reload() = scope.launch { loadData() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // ── Breadcrumb ────────────────────────────────────────────────────────
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text("← Config", style = MaterialTheme.typography.labelMedium)
        }

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Base Price", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${rules.size} rule${if (rules.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let {
                    Text("Error: $it", color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { showAddDialog = true }, enabled = rooms.isNotEmpty()) {
                    Text("Add Rule")
                }
            }
        }

        // ── Table header ──────────────────────────────────────────────────────
        if (!loading && rules.isNotEmpty()) {
            PriceRuleTableHeader()
            HorizontalDivider()
        }

        // ── Content ───────────────────────────────────────────────────────────
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (rooms.isEmpty()) "Add rooms first before setting price rules."
                    else "No price rules yet. Click \"Add Rule\" to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    PriceRuleRow(
                        rule     = rule,
                        onEdit   = { editingRule = rule },
                        onDelete = {
                            scope.launch {
                                try {
                                    client.delete("$BASE_URL/api/price-rules/${rule.id}")
                                    loadData()
                                } catch (e: Exception) { error = e.message }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showAddDialog) {
        PriceRuleDialog(
            title     = "Add Price Rule",
            rooms     = rooms,
            initial   = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { req ->
                scope.launch {
                    try {
                        client.post("$BASE_URL/api/price-rules") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        showAddDialog = false
                        loadData()
                    } catch (e: Exception) { error = e.message }
                }
            }
        )
    }

    editingRule?.let { rule ->
        PriceRuleDialog(
            title     = "Edit Price Rule",
            rooms     = rooms,
            initial   = rule,
            onDismiss = { editingRule = null },
            onConfirm = { req ->
                scope.launch {
                    try {
                        client.put("$BASE_URL/api/price-rules/${rule.id}") {
                            contentType(ContentType.Application.Json)
                            setBody(UpdatePriceRuleRequest(
                                fromDate              = req.fromDate,
                                toDate                = req.toDate,
                                minNights             = req.minNights,
                                maxNights             = req.maxNights,
                                pricePerPersonPerNight = req.pricePerPersonPerNight,
                                currency              = req.currency
                            ))
                        }
                        editingRule = null
                        loadData()
                    } catch (e: Exception) { error = e.message }
                }
            }
        )
    }
}

// ─── Table ────────────────────────────────────────────────────────────────────

@Composable
private fun PriceRuleTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Room",        Modifier.width(80.dp),  style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("From",        Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("To",          Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("Nights",      Modifier.width(90.dp),  style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
        Text(rule.roomNumber, Modifier.width(80.dp),  style = MaterialTheme.typography.bodySmall)
        Text(rule.fromDate,   Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
        Text(rule.toDate,     Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
        Text(nightsLabel,     Modifier.width(90.dp),  style = MaterialTheme.typography.bodySmall)
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
    onDismiss: () -> Unit,
    onConfirm: (CreatePriceRuleRequest) -> Unit
) {
    var selectedRoom by remember { mutableStateOf(
        if (initial != null) rooms.firstOrNull { it.id == initial.roomId } else rooms.firstOrNull()
    ) }
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
                modifier = Modifier.width(360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Room picker
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
                        enabled       = initial == null  // can't change room when editing
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

                // Date range
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        fromDate, { fromDate = it },
                        label       = { Text("From *") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine  = true,
                        modifier    = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        toDate, { toDate = it },
                        label       = { Text("To *") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine  = true,
                        modifier    = Modifier.weight(1f)
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
                        roomId                = selectedRoom!!.id,
                        fromDate              = fromDate.trim(),
                        toDate                = toDate.trim(),
                        minNights             = minNights.trim().toInt(),
                        maxNights             = maxNights.trim().toIntOrNull(),
                        pricePerPersonPerNight = price.trim().toDouble(),
                        currency              = currency.trim().ifBlank { "PLN" }
                    ))
                },
                enabled = valid
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
