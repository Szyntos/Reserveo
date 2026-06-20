package org.julsz.smnt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ─── Period color palette (background to text) ────────────────────────────────

private val PERIOD_PALETTES: List<Pair<Color, Color>> = listOf(
    Color(0xFFBBDEFB) to Color(0xFF0D47A1),
    Color(0xFFC8E6C9) to Color(0xFF1B5E20),
    Color(0xFFFFCDD2) to Color(0xFFB71C1C),
    Color(0xFFFFF9C4) to Color(0xFFE65100),
    Color(0xFFE1BEE7) to Color(0xFF4A148C),
    Color(0xFFB2EBF2) to Color(0xFF006064),
    Color(0xFFFFE0B2) to Color(0xFFBF360C),
    Color(0xFFCFD8DC) to Color(0xFF263238),
    Color(0xFFDCEDC8) to Color(0xFF33691E),
    Color(0xFFF8BBD0) to Color(0xFF880E4F),
)

// ─── Calendar helpers ─────────────────────────────────────────────────────────

private data class CalDay(val date: LocalDate, val inMonth: Boolean)

private fun buildWeeks(year: Int, month: Int): List<List<CalDay>> {
    val first = LocalDate.of(year, month, 1)
    val last  = first.with(TemporalAdjusters.lastDayOfMonth())
    val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end   = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val weeks = mutableListOf<List<CalDay>>()
    var ws = start
    while (!ws.isAfter(end)) {
        weeks += (0..6).map { i ->
            val d = ws.plusDays(i.toLong())
            CalDay(d, d.monthValue == month && d.year == year)
        }
        ws = ws.plusWeeks(1)
    }
    return weeks
}

// ─── Entry point ──────────────────────────────────────────────────────────────

@Composable
fun BasePriceConfigPage(client: HttpClient, hotel: UserHotelRoleDto, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var rules         by remember { mutableStateOf<List<PriceRuleDto>>(emptyList()) }
    var rooms         by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var loading       by remember { mutableStateOf(true) }
    var error         by remember { mutableStateOf<String?>(null) }
    var selectedRoom  by remember { mutableStateOf<RoomDto?>(null) }
    var showGenerator by remember { mutableStateOf(false) }

    suspend fun loadData() {
        loading = true; error = null
        try {
            rules = client.get("$BASE_URL/api/hotels/${hotel.hotelId}/price-rules").body()
            rooms = client.get("$BASE_URL/api/rooms?hotelId=${hotel.hotelId}").body<List<RoomDto>>()
                .filter { it.archivedAt == null }
        } catch (e: Exception) { error = e.message } finally { loading = false }
    }

    LaunchedEffect(hotel.hotelId) { loadData() }

    when {
        showGenerator -> RuleGeneratorPage(
            client        = client,
            rooms         = rooms,
            existingRules = rules,
            onBack        = { showGenerator = false },
            onDone        = { showGenerator = false; scope.launch { loadData() } }
        )
        selectedRoom != null -> {
            val current = selectedRoom!!
            RoomCalendarView(
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
        else -> RoomTileGrid(
            rooms       = rooms,
            rules       = rules,
            loading     = loading,
            error       = error,
            onBack      = onBack,
            onSelect    = { selectedRoom = it },
            onGenerate  = { showGenerator = true }
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
    onSelect: (RoomDto) -> Unit,
    onGenerate: () -> Unit
) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize()) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) { Text(s.breadcrumbConfig, style = MaterialTheme.typography.labelMedium) }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(s.basePriceTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(s.basePriceSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                error?.let {
                    Text(s.errorMsg(it), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onGenerate) { Text(s.generateRulesBtn) }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            rooms.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.addRoomsFirst,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val countByRoom = rules.groupBy { it.roomId }.mapValues { it.value.size }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(rooms, key = { it.id }) { room ->
                        RoomPriceTile(room, countByRoom[room.id] ?: 0) { onSelect(room) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomPriceTile(room: RoomDto, ruleCount: Int, onClick: () -> Unit) {
    val s = LocalStrings.current
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.roomLabel(room.number), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(room.typeName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ruleCount == 0) s.noRules else s.rulesCount(ruleCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ruleCount == 0) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                )
                Text(s.configManage, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─── Level 2: Calendar view ───────────────────────────────────────────────────

@Composable
private fun RoomCalendarView(
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
    var displayYear  by remember { mutableStateOf(LocalDate.now().year) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule   by remember { mutableStateOf<PriceRuleDto?>(null) }

    // Assign a stable color to each unique (fromDate, toDate) period
    val periodPalette: Map<String, Pair<Color, Color>> = remember(rules) {
        rules.map { it.fromDate to it.toDate }.distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
            .mapIndexed { i, (f, t) -> "$f|$t" to PERIOD_PALETTES[i % PERIOD_PALETTES.size] }
            .toMap()
    }

    val s = LocalStrings.current
    Column(Modifier.fillMaxSize()) {
        // Breadcrumb
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) { Text(s.breadcrumbBasePrice, style = MaterialTheme.typography.labelMedium) }

        // Header row
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(s.roomLabel(room.number), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text("${room.typeName} · ${s.rulesCount(rules.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                error?.let {
                    Text(s.errorMsg(it), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                // Year navigation
                IconButton(onClick = { displayYear-- }, modifier = Modifier.size(32.dp)) {
                    Text("◀", style = MaterialTheme.typography.labelLarge)
                }
                Text("$displayYear", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                IconButton(onClick = { displayYear++ }, modifier = Modifier.size(32.dp)) {
                    Text("▶", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showAddDialog = true }) { Text(s.addRule) }
            }
        }

        // 12-month calendar
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            (1..12).forEach { m ->
                item(key = "month_${displayYear}_$m") {
                    MonthCalendar(
                        year          = displayYear,
                        month         = m,
                        rules         = rules,
                        periodPalette = periodPalette,
                        onRuleClick   = { editingRule = it }
                    )
                    if (m < 12) HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    else Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        PriceRuleDialog(
            title     = "${s.addRule} — ${s.roomLabel(room.number)}",
            rooms     = allRooms,
            initial   = null,
            fixedRoom = room,
            onDismiss = { showAddDialog = false },
            onDelete  = null,
            onConfirm = { req ->
                scope.launch {
                    try {
                        client.post("$BASE_URL/api/price-rules") {
                            contentType(ContentType.Application.Json); setBody(req)
                        }
                        showAddDialog = false; onReload()
                    } catch (e: Exception) { onError(e.message) }
                }
            }
        )
    }

    // Edit dialog
    editingRule?.let { rule ->
        PriceRuleDialog(
            title     = "${s.edit} — ${s.roomLabel(room.number)}",
            rooms     = allRooms,
            initial   = rule,
            fixedRoom = room,
            onDismiss = { editingRule = null },
            onDelete  = {
                scope.launch {
                    try {
                        client.delete("$BASE_URL/api/price-rules/${rule.id}")
                        editingRule = null; onReload()
                    } catch (e: Exception) { onError(e.message) }
                }
            },
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
                        editingRule = null; onReload()
                    } catch (e: Exception) { onError(e.message) }
                }
            }
        )
    }
}

// ─── Month grid ───────────────────────────────────────────────────────────────

// DOW labels come from LocalStrings at render time

@Composable
private fun MonthCalendar(
    year: Int,
    month: Int,
    rules: List<PriceRuleDto>,
    periodPalette: Map<String, Pair<Color, Color>>,
    onRuleClick: (PriceRuleDto) -> Unit
) {
    val s = LocalStrings.current
    val weeks = remember(year, month) { buildWeeks(year, month) }
    val monthName = Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, s.locale)

    Column(Modifier.fillMaxWidth()) {
        Text(
            "$monthName $year",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        // Day-of-week header
        Row(Modifier.fillMaxWidth()) {
            s.dowLabels.forEach { dow ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(dow, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        weeks.forEach { week ->
            WeekRow(week, rules, periodPalette, onRuleClick)
        }
    }
}

// ─── Week row with calendar bands ─────────────────────────────────────────────

@Composable
private fun WeekRow(
    week: List<CalDay>,
    rules: List<PriceRuleDto>,
    periodPalette: Map<String, Pair<Color, Color>>,
    onRuleClick: (PriceRuleDto) -> Unit
) {
    val weekStart = week.first().date
    val weekEnd   = week.last().date
    val today     = LocalDate.now()

    // Rules overlapping this week, sorted for consistent stacking
    val overlapping = rules
        .filter { !LocalDate.parse(it.fromDate).isAfter(weekEnd) && !LocalDate.parse(it.toDate).isBefore(weekStart) }
        .sortedWith(compareBy({ it.fromDate }, { it.minNights }))

    val CELL_H  = 26.dp
    val BAND_H  = 15.dp
    val BAND_GAP = 2.dp
    val totalH  = CELL_H + (BAND_H + BAND_GAP) * overlapping.size + if (overlapping.isEmpty()) 0.dp else 3.dp

    BoxWithConstraints(Modifier.fillMaxWidth().height(totalH)) {
        val dayW = maxWidth / 7

        // Day number cells
        Row(Modifier.fillMaxWidth().height(CELL_H)) {
            week.forEach { cal ->
                val isToday = cal.date == today
                Box(Modifier.width(dayW).height(CELL_H), contentAlignment = Alignment.Center) {
                    if (isToday) {
                        Box(Modifier.size(20.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary))
                    }
                    Text(
                        cal.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            !cal.inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            cal.date.dayOfWeek == DayOfWeek.SATURDAY ||
                            cal.date.dayOfWeek == DayOfWeek.SUNDAY ->
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Price-rule bands
        overlapping.forEachIndexed { idx, rule ->
            val from      = LocalDate.parse(rule.fromDate)
            val to        = LocalDate.parse(rule.toDate)
            val clampFrom = if (from.isBefore(weekStart)) weekStart else from
            val clampTo   = if (to.isAfter(weekEnd))     weekEnd   else to

            val startOff = ChronoUnit.DAYS.between(weekStart, clampFrom).toInt()
            val endOff   = ChronoUnit.DAYS.between(weekStart, clampTo).toInt()

            val bLeft  = dayW * startOff + 1.dp
            val bWidth = dayW * (endOff - startOff + 1) - 2.dp
            val bTop   = CELL_H + (BAND_H + BAND_GAP) * idx

            val (bg, fg) = periodPalette["${rule.fromDate}|${rule.toDate}"]
                ?: (Color(0xFFE0E0E0) to Color(0xFF424242))

            val nightsLabel = when {
                rule.maxNights == null -> "${rule.minNights}+n"
                rule.minNights == rule.maxNights -> "${rule.minNights}n"
                else -> "${rule.minNights}–${rule.maxNights}n"
            }
            val priceStr = if (rule.pricePerPersonPerNight > 0.0)
                " · ${"%.0f".format(rule.pricePerPersonPerNight)}" else ""
            val showLabel = clampFrom == from || clampFrom == weekStart

            Box(
                Modifier
                    .offset(x = bLeft, y = bTop)
                    .width(bWidth)
                    .height(BAND_H)
                    .clip(RoundedCornerShape(
                        topStart    = if (clampFrom == from) 3.dp else 0.dp,
                        bottomStart = if (clampFrom == from) 3.dp else 0.dp,
                        topEnd      = if (clampTo   == to)   3.dp else 0.dp,
                        bottomEnd   = if (clampTo   == to)   3.dp else 0.dp
                    ))
                    .background(bg)
                    .clickable { onRuleClick(rule) },
                contentAlignment = Alignment.CenterStart
            ) {
                if (showLabel) {
                    Text(
                        "$nightsLabel$priceStr",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Add / Edit dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PriceRuleDialog(
    title: String,
    rooms: List<RoomDto>,
    initial: PriceRuleDto?,
    fixedRoom: RoomDto?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onConfirm: (CreatePriceRuleRequest) -> Unit
) {
    val resolvedRoom = fixedRoom
        ?: (if (initial != null) rooms.firstOrNull { it.id == initial.roomId } else rooms.firstOrNull())
    var selectedRoom by remember { mutableStateOf(resolvedRoom) }
    var roomExpanded by remember { mutableStateOf(false) }

    var fromDate  by remember { mutableStateOf(initial?.fromDate ?: "") }
    var toDate    by remember { mutableStateOf(initial?.toDate ?: "") }
    var minNights by remember { mutableStateOf(initial?.minNights?.toString() ?: "1") }
    var maxNights by remember { mutableStateOf(initial?.maxNights?.toString() ?: "") }
    var price     by remember { mutableStateOf(initial?.pricePerPersonPerNight?.toString() ?: "") }
    var currency  by remember { mutableStateOf(initial?.currency ?: "PLN") }

    val valid = selectedRoom != null && fromDate.isNotBlank() && toDate.isNotBlank()
        && minNights.toIntOrNull() != null && price.toDoubleOrNull() != null

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val s = LocalStrings.current
                if (fixedRoom == null) {
                    ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = { roomExpanded = it }) {
                        OutlinedTextField(
                            value         = selectedRoom?.number?.let { s.roomLabel(it) } ?: s.selectRoomHint,
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text(s.roomFieldLabel) },
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
                            modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            singleLine    = true,
                            enabled       = initial == null
                        )
                        ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { roomExpanded = false }) {
                            rooms.forEach { room ->
                                DropdownMenuItem(
                                    text    = { Text("${s.roomLabel(room.number)} · ${room.typeName}") },
                                    onClick = { selectedRoom = room; roomExpanded = false }
                                )
                            }
                        }
                    }
                }
                DateRangePickerFields(
                    fromLabel = s.fromLabel, fromDate = fromDate, onFromChange = { fromDate = it },
                    toLabel = s.toLabel, toDate = toDate, onToChange = { toDate = it }
                )
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minNights, { minNights = it }, label = { Text(s.minNightsLabel) },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(maxNights, { maxNights = it }, label = { Text(s.maxNightsLabel) },
                        placeholder = { Text(s.blankNoLimit) }, singleLine = true,
                        modifier = Modifier.weight(1f))
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(price, { price = it }, label = { Text(s.priceLabel) },
                        singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(currency, { currency = it }, label = { Text(s.currencyLabel) },
                        singleLine = true, modifier = Modifier.weight(1f))
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
            ) { Text(if (initial == null) LocalStrings.current.add else LocalStrings.current.save) }
        },
        dismissButton = {
            val s = LocalStrings.current
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(s.delete, color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(s.cancel) }
            }
        }
    )
}

// ─── Date range picker fields ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DateRangePickerFields(
    fromLabel: String, fromDate: String, onFromChange: (String) -> Unit,
    toLabel: String, toDate: String, onToChange: (String) -> Unit
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }
    val s = LocalStrings.current

    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = fromDate, onValueChange = {}, readOnly = true,
            label = { Text(fromLabel) },
            trailingIcon = {
                TextButton(onClick = { showFromPicker = true },
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("📅") }
            },
            modifier = Modifier.weight(1f), singleLine = true
        )
        OutlinedTextField(
            value = toDate, onValueChange = {}, readOnly = true,
            label = { Text(toLabel) },
            trailingIcon = {
                TextButton(onClick = { showToPicker = true },
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("📅") }
            },
            modifier = Modifier.weight(1f), singleLine = true
        )
    }

    if (showFromPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = fromDate.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            }
        )
        AppDatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        onFromChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showFromPicker = false
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text(s.cancel) } }
        ) { DatePicker(state = dpState) }
    }

    if (showToPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = toDate.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            }
        )
        AppDatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        onToChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showToPicker = false
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text(s.cancel) } }
        ) { DatePicker(state = dpState) }
    }
}
