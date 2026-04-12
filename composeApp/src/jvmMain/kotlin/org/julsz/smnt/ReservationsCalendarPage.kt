package org.julsz.smnt

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
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

// ─── Status colors ────────────────────────────────────────────────────────────

private val STATUS_PALETTE: Map<String, Pair<Color, Color>> = mapOf(
    "pending"     to (Color(0xFFFFF9C4) to Color(0xFFE65100)),
    "confirmed"   to (Color(0xFFBBDEFB) to Color(0xFF0D47A1)),
    "checked_in"  to (Color(0xFFC8E6C9) to Color(0xFF1B5E20)),
    "checked_out" to (Color(0xFFEEEEEE) to Color(0xFF424242)),
    "cancelled"   to (Color(0xFFFFCDD2) to Color(0xFFB71C1C)),
    "no_show"     to (Color(0xFFE1BEE7) to Color(0xFF4A148C)),
)

private val RESERVATION_STATUSES = listOf(
    "pending", "confirmed", "checked_in", "checked_out", "cancelled", "no_show"
)

private enum class ResView { Calendar, Timeline }

// ─── Calendar helpers (same pattern as price rules) ───────────────────────────

private data class RCalDay(val date: LocalDate, val inMonth: Boolean)

private fun buildResWeeks(year: Int, month: Int): List<List<RCalDay>> {
    val first = LocalDate.of(year, month, 1)
    val last  = first.with(TemporalAdjusters.lastDayOfMonth())
    val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end   = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val weeks = mutableListOf<List<RCalDay>>()
    var ws = start
    while (!ws.isAfter(end)) {
        weeks += (0..6).map { i ->
            val d = ws.plusDays(i.toLong())
            RCalDay(d, d.monthValue == month && d.year == year)
        }
        ws = ws.plusWeeks(1)
    }
    return weeks
}

// ─── Entry point ──────────────────────────────────────────────────────────────

@Composable
fun ReservationsCalendarPage(client: HttpClient, hotel: UserHotelRoleDto) {
    val scope = rememberCoroutineScope()

    var reservations by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var rooms        by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var guests       by remember { mutableStateOf<List<GuestDto>>(emptyList()) }
    var priceRules   by remember { mutableStateOf<List<PriceRuleDto>>(emptyList()) }
    var roomBlocks   by remember { mutableStateOf<List<RoomBlockDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    var error        by remember { mutableStateOf<String?>(null) }
    var displayYear  by remember { mutableStateOf(LocalDate.now().year) }
    var displayMonth by remember { mutableStateOf(LocalDate.now().monthValue) }
    var currentView  by remember { mutableStateOf(ResView.Calendar) }
    var showNewDialog     by remember { mutableStateOf(false) }
    var showBlockDialog   by remember { mutableStateOf(false) }
    var editReservation   by remember { mutableStateOf<ReservationDto?>(null) }
    var editBlock         by remember { mutableStateOf<RoomBlockDto?>(null) }
    var prefillRoom       by remember { mutableStateOf<RoomDto?>(null) }
    var prefillCheckIn    by remember { mutableStateOf("") }
    var prefillCheckOut   by remember { mutableStateOf("") }

    suspend fun loadData() {
        loading = true; error = null
        try {
            reservations = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            rooms        = client.get("$BASE_URL/api/rooms?hotelId=${hotel.hotelId}").body<List<RoomDto>>()
                .filter { it.archivedAt == null }
            guests       = client.get("$BASE_URL/api/guests").body()
            priceRules   = client.get("$BASE_URL/api/hotels/${hotel.hotelId}/price-rules").body()
            roomBlocks   = client.get("$BASE_URL/api/room-blocks?hotelId=${hotel.hotelId}").body()
        } catch (e: Exception) { error = e.message } finally { loading = false }
    }

    LaunchedEffect(hotel.hotelId) { loadData() }

    Column(Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Reservations", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text("${hotel.hotelName} · ${reservations.size} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                error?.let {
                    Text("Error: $it", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                // View toggle
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    listOf(ResView.Calendar to "Calendar", ResView.Timeline to "Timeline").forEach { (view, label) ->
                        val sel = currentView == view
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { currentView = view }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                // Navigation
                IconButton(onClick = {
                    if (currentView == ResView.Calendar) displayYear--
                    else if (displayMonth == 1) { displayMonth = 12; displayYear-- } else displayMonth--
                }, Modifier.size(32.dp)) { Text("◀", style = MaterialTheme.typography.labelLarge) }
                Text(
                    if (currentView == ResView.Calendar) "$displayYear"
                    else "${Month.of(displayMonth).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)} $displayYear",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    if (currentView == ResView.Calendar) displayYear++
                    else if (displayMonth == 12) { displayMonth = 1; displayYear++ } else displayMonth++
                }, Modifier.size(32.dp)) { Text("▶", style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { showBlockDialog = true }, enabled = rooms.isNotEmpty()) {
                    Text("Block Room")
                }
                Button(onClick = { showNewDialog = true }, enabled = rooms.isNotEmpty() && guests.isNotEmpty()) {
                    Text("New Reservation")
                }
            }
        }

        // ── Status legend ─────────────────────────────────────────────────────
        Row(
            Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            STATUS_PALETTE.entries.forEach { (status, colors) ->
                val (bg, fg) = colors
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(bg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(status.replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall, color = fg)
                }
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else when (currentView) {
            ResView.Calendar -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                (1..12).forEach { m ->
                    item(key = "month_${displayYear}_$m") {
                        ResMonthCalendar(
                            year         = displayYear,
                            month        = m,
                            reservations = reservations,
                            onResClick   = { editReservation = it }
                        )
                        if (m < 12) HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        else Spacer(Modifier.height(16.dp))
                    }
                }
            }
            ResView.Timeline -> ReservationsTimelineView(
                rooms           = rooms.sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number })),
                reservations    = reservations,
                blocks          = roomBlocks,
                year            = displayYear,
                month           = displayMonth,
                onEditRequest   = { editReservation = it },
                onBlockClick    = { editBlock = it },
                onCreateRequest = { room, cin, cout ->
                    prefillRoom     = room
                    prefillCheckIn  = cin.toString()
                    prefillCheckOut = cout.toString()
                    showNewDialog   = true
                }
            )
        }
    }

    // ── New Reservation dialog ─────────────────────────────────────────────────
    if (showNewDialog) {
        NewReservationDialog(
            hotel           = hotel,
            rooms           = rooms,
            guests          = guests,
            priceRules      = priceRules,
            prefillRoom     = prefillRoom,
            prefillCheckIn  = prefillCheckIn,
            prefillCheckOut = prefillCheckOut,
            onDismiss = {
                showNewDialog = false
                prefillRoom = null; prefillCheckIn = ""; prefillCheckOut = ""
            },
            onConfirm = { req ->
                scope.launch {
                    try {
                        val response = client.post("$BASE_URL/api/reservations") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        if (!response.status.isSuccess()) {
                            error = if (response.status == HttpStatusCode.Conflict)
                                "Double booking: room already reserved for these dates"
                            else
                                "Server error: ${response.status}"
                            return@launch
                        }
                        showNewDialog = false
                        prefillRoom = null; prefillCheckIn = ""; prefillCheckOut = ""
                        loadData()
                    } catch (e: Exception) { error = e.message }
                }
            }
        )
    }

    // ── Edit Reservation dialog ────────────────────────────────────────────────
    editReservation?.let { res ->
        ReservationEditDialog(
            existing   = res,
            rooms      = rooms,
            guests     = guests,
            priceRules = priceRules,
            onDismiss  = { editReservation = null },
            onConfirm  = { req ->
                scope.launch {
                    try {
                        val response = client.put("$BASE_URL/api/reservations/${res.id}") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        if (!response.status.isSuccess()) {
                            error = if (response.status == HttpStatusCode.Conflict)
                                "Double booking: room already reserved for these dates"
                            else
                                "Server error: ${response.status}"
                            return@launch
                        }
                        editReservation = null
                        loadData()
                    } catch (e: Exception) { error = e.message }
                }
            },
            onDelete = {
                scope.launch {
                    try {
                        client.delete("$BASE_URL/api/reservations/${res.id}")
                        editReservation = null
                        loadData()
                    } catch (e: Exception) { error = e.message }
                }
            }
        )
    }

    // ── Block Room dialog ──────────────────────────────────────────────────────
    if (showBlockDialog) {
        BlockRoomDialog(
            rooms     = rooms,
            onDismiss = { showBlockDialog = false },
            onConfirm = { req ->
                scope.launch {
                    try {
                        client.post("$BASE_URL/api/room-blocks") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        showBlockDialog = false
                        loadData()
                    } catch (e: Exception) { error = e.message }
                }
            }
        )
    }

    // ── Block delete dialog ────────────────────────────────────────────────────
    editBlock?.let { block ->
        RoomBlockDeleteDialog(
            block     = block,
            onDismiss = { editBlock = null },
            onConfirm = {
                scope.launch {
                    try {
                        client.delete("$BASE_URL/api/room-blocks/${block.id}")
                        editBlock = null
                        loadData()
                    } catch (e: Exception) { error = e.message }
                }
            }
        )
    }
}

// ─── Month grid ───────────────────────────────────────────────────────────────

private val DOW = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
private fun ResMonthCalendar(
    year: Int,
    month: Int,
    reservations: List<ReservationDto>,
    onResClick: (ReservationDto) -> Unit
) {
    val weeks = remember(year, month) { buildResWeeks(year, month) }
    val monthName = Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)

    Column(Modifier.fillMaxWidth()) {
        Text("$monthName $year", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))

        Row(Modifier.fillMaxWidth()) {
            DOW.forEach { d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(d, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        weeks.forEach { week ->
            ResWeekRow(week, reservations, onResClick)
        }
    }
}

// ─── Week row ─────────────────────────────────────────────────────────────────

@Composable
private fun ResWeekRow(
    week: List<RCalDay>,
    reservations: List<ReservationDto>,
    onResClick: (ReservationDto) -> Unit
) {
    val weekStart = week.first().date
    val weekEnd   = week.last().date
    val today     = LocalDate.now()

    // Reservations overlapping this week, sorted for consistent stacking
    val overlapping = reservations
        .filter {
            !LocalDate.parse(it.checkInDate).isAfter(weekEnd) &&
            !LocalDate.parse(it.checkOutDate).isBefore(weekStart)
        }
        .sortedWith(compareBy({ it.checkInDate }, { it.roomNumber }))

    val CELL_H   = 26.dp
    val BAND_H   = 15.dp
    val BAND_GAP = 2.dp
    val totalH   = CELL_H + (BAND_H + BAND_GAP) * overlapping.size + if (overlapping.isEmpty()) 0.dp else 3.dp

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

        // Reservation bands
        overlapping.forEachIndexed { idx, res ->
            val from      = LocalDate.parse(res.checkInDate)
            val to        = LocalDate.parse(res.checkOutDate)
            val clampFrom = if (from.isBefore(weekStart)) weekStart else from
            val clampTo   = if (to.isAfter(weekEnd))     weekEnd   else to

            val startOff = ChronoUnit.DAYS.between(weekStart, clampFrom).toInt()
            val endOff   = ChronoUnit.DAYS.between(weekStart, clampTo).toInt()

            val bLeft  = dayW * startOff + 1.dp
            val bWidth = dayW * (endOff - startOff + 1) - 2.dp
            val bTop   = CELL_H + (BAND_H + BAND_GAP) * idx

            val (bg, fg) = STATUS_PALETTE[res.status] ?: (Color(0xFFE0E0E0) to Color(0xFF424242))

            val showLabel = clampFrom == from || clampFrom == weekStart
            val label = "Rm ${res.roomNumber} · ${res.guestName}"

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
                    .clickable { onResClick(res) },
                contentAlignment = Alignment.CenterStart
            ) {
                if (showLabel) {
                    Text(
                        label,
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

// ─── Shared price helper ──────────────────────────────────────────────────────

private fun matchPriceRule(
    priceRules: List<PriceRuleDto>,
    roomId: Int,
    checkIn: String,
    checkOut: String
): PriceRuleDto? {
    val cin  = runCatching { LocalDate.parse(checkIn.trim())  }.getOrNull() ?: return null
    val cout = runCatching { LocalDate.parse(checkOut.trim()) }.getOrNull() ?: return null
    if (!cout.isAfter(cin)) return null
    val nights = ChronoUnit.DAYS.between(cin, cout).toInt()
    return priceRules.filter { r ->
        r.roomId == roomId &&
        !LocalDate.parse(r.fromDate).isAfter(cin) &&
        !LocalDate.parse(r.toDate).isBefore(cin) &&
        nights >= r.minNights &&
        (r.maxNights == null || nights <= r.maxNights!!)
    }.maxByOrNull { it.minNights }
}

// ─── New Reservation dialog ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewReservationDialog(
    hotel: UserHotelRoleDto,
    rooms: List<RoomDto>,
    guests: List<GuestDto>,
    priceRules: List<PriceRuleDto>,
    prefillRoom: RoomDto? = null,
    prefillCheckIn: String = "",
    prefillCheckOut: String = "",
    onDismiss: () -> Unit,
    onConfirm: (CreateReservationRequest) -> Unit
) {
    var selectedRoom   by remember { mutableStateOf(prefillRoom ?: rooms.firstOrNull()) }
    var selectedGuest  by remember { mutableStateOf(guests.firstOrNull()) }
    var roomExpanded   by remember { mutableStateOf(false) }
    var guestExpanded  by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var checkIn  by remember { mutableStateOf(prefillCheckIn) }
    var checkOut by remember { mutableStateOf(prefillCheckOut) }
    var adults   by remember { mutableStateOf("1") }
    var children by remember { mutableStateOf("0") }
    var status   by remember { mutableStateOf("confirmed") }
    var ppnInput by remember { mutableStateOf("") }

    val nights = remember(checkIn, checkOut) {
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(checkIn.trim()), LocalDate.parse(checkOut.trim())).toInt() }.getOrDefault(0)
    }
    val matchingRule = remember(selectedRoom?.id, checkIn, checkOut, priceRules) {
        selectedRoom?.let { matchPriceRule(priceRules, it.id, checkIn, checkOut) }
    }
    LaunchedEffect(matchingRule, selectedRoom?.id) {
        matchingRule?.let { ppnInput = "%.2f".format(it.pricePerPersonPerNight) }
    }
    val ppn = ppnInput.toDoubleOrNull()
    val totalGuests = (adults.toIntOrNull() ?: 1) + (children.toIntOrNull() ?: 0)
    val computedTotal = if (ppn != null && nights > 0) ppn * nights * totalGuests else null
    val valid = selectedRoom != null && selectedGuest != null &&
        checkIn.isNotBlank() && checkOut.isNotBlank() && adults.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Reservation") },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReservationFormFields(
                    rooms = rooms, guests = guests,
                    selectedRoom = selectedRoom, onRoomChange = { selectedRoom = it },
                    selectedGuest = selectedGuest, onGuestChange = { selectedGuest = it },
                    roomExpanded = roomExpanded, onRoomExpandChange = { roomExpanded = it },
                    guestExpanded = guestExpanded, onGuestExpandChange = { guestExpanded = it },
                    statusExpanded = statusExpanded, onStatusExpandChange = { statusExpanded = it },
                    checkIn = checkIn, onCheckInChange = { checkIn = it },
                    checkOut = checkOut, onCheckOutChange = { checkOut = it },
                    adults = adults, onAdultsChange = { adults = it },
                    children = children, onChildrenChange = { children = it },
                    status = status, onStatusChange = { status = it }
                )
                HorizontalDivider()
                PricePpnSection(
                    ppnInput = ppnInput, onPpnChange = { ppnInput = it },
                    matchingRule = matchingRule, nights = nights,
                    totalGuests = totalGuests, computedTotal = computedTotal
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(CreateReservationRequest(
                        hotelId = hotel.hotelId, roomId = selectedRoom!!.id, guestId = selectedGuest!!.id,
                        checkInDate = checkIn.trim(), checkOutDate = checkOut.trim(), status = status,
                        adults = adults.trim().toIntOrNull() ?: 1,
                        children = children.trim().toIntOrNull() ?: 0,
                        totalAmount = computedTotal
                    ))
                }, enabled = valid
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Edit Reservation dialog ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservationEditDialog(
    existing: ReservationDto,
    rooms: List<RoomDto>,
    guests: List<GuestDto>,
    priceRules: List<PriceRuleDto>,
    onDismiss: () -> Unit,
    onConfirm: (UpdateReservationRequest) -> Unit,
    onDelete: () -> Unit
) {
    var selectedRoom   by remember { mutableStateOf(rooms.find { it.id == existing.roomId } ?: rooms.firstOrNull()) }
    var selectedGuest  by remember { mutableStateOf(guests.find { it.id == existing.guestId } ?: guests.firstOrNull()) }
    var roomExpanded   by remember { mutableStateOf(false) }
    var guestExpanded  by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var checkIn  by remember { mutableStateOf(existing.checkInDate) }
    var checkOut by remember { mutableStateOf(existing.checkOutDate) }
    var adults   by remember { mutableStateOf(existing.adults.toString()) }
    var children by remember { mutableStateOf(existing.children.toString()) }
    var status   by remember { mutableStateOf(existing.status) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Initialise ppn: from back-computed existing total, rule will override via LaunchedEffect
    var ppnInput by remember(existing.id) {
        val cin  = runCatching { LocalDate.parse(existing.checkInDate) }.getOrNull()
        val cout = runCatching { LocalDate.parse(existing.checkOutDate) }.getOrNull()
        val n    = if (cin != null && cout != null && cout.isAfter(cin)) ChronoUnit.DAYS.between(cin, cout).toInt() else 0
        val g    = existing.adults + existing.children
        val back = existing.totalAmount?.let { if (n > 0 && g > 0) "%.2f".format(it / (n * g)) else null } ?: ""
        mutableStateOf(back)
    }

    val nights = remember(checkIn, checkOut) {
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(checkIn.trim()), LocalDate.parse(checkOut.trim())).toInt() }.getOrDefault(0)
    }
    val matchingRule = remember(selectedRoom?.id, checkIn, checkOut, priceRules) {
        selectedRoom?.let { matchPriceRule(priceRules, it.id, checkIn, checkOut) }
    }
    LaunchedEffect(matchingRule, selectedRoom?.id) {
        matchingRule?.let { ppnInput = "%.2f".format(it.pricePerPersonPerNight) }
    }
    val ppn = ppnInput.toDoubleOrNull()
    val totalGuests = (adults.toIntOrNull() ?: 1) + (children.toIntOrNull() ?: 0)
    val computedTotal = if (ppn != null && nights > 0) ppn * nights * totalGuests else null
    val valid = selectedRoom != null && selectedGuest != null &&
        checkIn.isNotBlank() && checkOut.isNotBlank() && adults.toIntOrNull() != null

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete reservation #${existing.id}?") },
            text  = { Text("This cannot be undone.") },
            confirmButton = { Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Reservation #${existing.id}") },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReservationFormFields(
                    rooms = rooms, guests = guests,
                    selectedRoom = selectedRoom, onRoomChange = { selectedRoom = it },
                    selectedGuest = selectedGuest, onGuestChange = { selectedGuest = it },
                    roomExpanded = roomExpanded, onRoomExpandChange = { roomExpanded = it },
                    guestExpanded = guestExpanded, onGuestExpandChange = { guestExpanded = it },
                    statusExpanded = statusExpanded, onStatusExpandChange = { statusExpanded = it },
                    checkIn = checkIn, onCheckInChange = { checkIn = it },
                    checkOut = checkOut, onCheckOutChange = { checkOut = it },
                    adults = adults, onAdultsChange = { adults = it },
                    children = children, onChildrenChange = { children = it },
                    status = status, onStatusChange = { status = it }
                )
                HorizontalDivider()
                PricePpnSection(
                    ppnInput = ppnInput, onPpnChange = { ppnInput = it },
                    matchingRule = matchingRule, nights = nights,
                    totalGuests = totalGuests, computedTotal = computedTotal
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(UpdateReservationRequest(
                        roomId = selectedRoom!!.id, guestId = selectedGuest!!.id,
                        checkInDate = checkIn.trim(), checkOutDate = checkOut.trim(), status = status,
                        adults = adults.trim().toIntOrNull() ?: 1,
                        children = children.trim().toIntOrNull() ?: 0,
                        totalAmount = computedTotal
                    ))
                }, enabled = valid
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// ─── Shared form composables ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservationFormFields(
    rooms: List<RoomDto>, guests: List<GuestDto>,
    selectedRoom: RoomDto?, onRoomChange: (RoomDto) -> Unit,
    selectedGuest: GuestDto?, onGuestChange: (GuestDto) -> Unit,
    roomExpanded: Boolean, onRoomExpandChange: (Boolean) -> Unit,
    guestExpanded: Boolean, onGuestExpandChange: (Boolean) -> Unit,
    statusExpanded: Boolean, onStatusExpandChange: (Boolean) -> Unit,
    checkIn: String, onCheckInChange: (String) -> Unit,
    checkOut: String, onCheckOutChange: (String) -> Unit,
    adults: String, onAdultsChange: (String) -> Unit,
    children: String, onChildrenChange: (String) -> Unit,
    status: String, onStatusChange: (String) -> Unit
) {
    ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = onRoomExpandChange) {
        OutlinedTextField(
            value = selectedRoom?.let { "Room ${it.number} · ${it.typeName}" } ?: "Select room",
            onValueChange = {}, readOnly = true, label = { Text("Room *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
        )
        ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { onRoomExpandChange(false) }) {
            rooms.forEach { room ->
                DropdownMenuItem(
                    text = { Text("Room ${room.number} · ${room.typeName} (max ${room.maxGuests})") },
                    onClick = { onRoomChange(room); onRoomExpandChange(false) }
                )
            }
        }
    }
    ExposedDropdownMenuBox(expanded = guestExpanded, onExpandedChange = onGuestExpandChange) {
        OutlinedTextField(
            value = selectedGuest?.let { "${it.firstName} ${it.lastName}" } ?: "Select guest",
            onValueChange = {}, readOnly = true, label = { Text("Guest *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(guestExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
        )
        ExposedDropdownMenu(expanded = guestExpanded, onDismissRequest = { onGuestExpandChange(false) }) {
            guests.forEach { guest ->
                DropdownMenuItem(
                    text = { Text("${guest.firstName} ${guest.lastName}${guest.nationality?.let { " · $it" } ?: ""}") },
                    onClick = { onGuestChange(guest); onGuestExpandChange(false) }
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResDatePickerField("Check-in *",  checkIn,  onCheckInChange,  Modifier.weight(1f))
        ResDatePickerField("Check-out *", checkOut, onCheckOutChange, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(adults,   onAdultsChange,   label = { Text("Adults *") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(children, onChildrenChange, label = { Text("Children") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = onStatusExpandChange) {
        OutlinedTextField(
            value = status.replace('_', ' '), onValueChange = {}, readOnly = true, label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
        )
        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { onStatusExpandChange(false) }) {
            RESERVATION_STATUSES.forEach { s ->
                DropdownMenuItem(text = { Text(s.replace('_', ' ')) }, onClick = { onStatusChange(s); onStatusExpandChange(false) })
            }
        }
    }
}

@Composable
private fun PricePpnSection(
    ppnInput: String,
    onPpnChange: (String) -> Unit,
    matchingRule: PriceRuleDto?,
    nights: Int,
    totalGuests: Int,
    computedTotal: Double?
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value         = ppnInput,
            onValueChange = onPpnChange,
            label         = { Text("PLN / person / night") },
            singleLine    = true,
            modifier      = Modifier.width(180.dp),
            isError       = ppnInput.isNotBlank() && ppnInput.toDoubleOrNull() == null
        )
        Column {
            if (computedTotal != null) {
                Text(
                    "$nights night${if (nights != 1) "s" else ""} × $totalGuests person${if (totalGuests != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "= ${"%.2f".format(computedTotal)} PLN total",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (matchingRule != null) {
                Text(
                    "Rule: ${matchingRule.minNights}${matchingRule.maxNights?.let { "–$it" } ?: "+"} nights",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (nights > 0) {
                Text("No matching rule", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Timeline view ────────────────────────────────────────────────────────────

private enum class TimelineScale { Month, Year }

private data class TimelineDragState(val room: RoomDto, val startIdx: Int, val endIdx: Int)

@Composable
private fun ReservationsTimelineView(
    rooms: List<RoomDto>,
    reservations: List<ReservationDto>,
    blocks: List<RoomBlockDto>,
    year: Int,
    month: Int,
    onEditRequest: (ReservationDto) -> Unit,
    onBlockClick: (RoomBlockDto) -> Unit,
    onCreateRequest: (room: RoomDto, checkIn: LocalDate, checkOut: LocalDate) -> Unit
) {
    var dragState by remember { mutableStateOf<TimelineDragState?>(null) }
    var scale        by remember { mutableStateOf(TimelineScale.Month) }
    var dayWidthPx   by remember { mutableStateOf(40f) }
    val DAY_W        = dayWidthPx.dp

    // All days in view
    val days: List<LocalDate> = remember(year, month, scale) {
        when (scale) {
            TimelineScale.Month -> {
                val n = LocalDate.of(year, month, 1).lengthOfMonth()
                (1..n).map { LocalDate.of(year, month, it) }
            }
            TimelineScale.Year  -> (1..12).flatMap { m ->
                val n = LocalDate.of(year, m, 1).lengthOfMonth()
                (1..n).map { LocalDate.of(year, m, it) }
            }
        }
    }
    // Month label segments for year-scale header
    val monthSegments: List<Pair<String, Int>> = remember(year, scale) {
        if (scale == TimelineScale.Year) (1..12).map { m ->
            Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) to
                LocalDate.of(year, m, 1).lengthOfMonth()
        } else emptyList()
    }

    val dateStart    = days.first()
    val dateEnd      = days.last()
    val today        = LocalDate.now()
    val totalWidth   = DAY_W * days.size

    val ROW_H        = 34.dp
    val MONTH_ROW_H  = 18.dp
    val DAY_ROW_H    = 46.dp
    val HEAD_H       = if (scale == TimelineScale.Year) MONTH_ROW_H + DAY_ROW_H else DAY_ROW_H
    val LABEL_W      = 96.dp
    val BAND_H       = 22.dp

    val resByRoom    = remember(reservations) { reservations.groupBy { it.roomId } }
    val blocksByRoom = remember(blocks) { blocks.groupBy { it.roomId } }
    val hScroll      = rememberScrollState()
    val vScroll      = rememberScrollState()
    val divColor     = MaterialTheme.colorScheme.outlineVariant

    // Reset horizontal scroll when scale or period changes
    LaunchedEffect(scale, year, month) { hScroll.scrollTo(0) }

    Column(Modifier.fillMaxSize()) {
        // ── Controls row ──────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scale pill
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(TimelineScale.Month to "Month", TimelineScale.Year to "Year").forEach { (s, label) ->
                    val sel = scale == s
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) MaterialTheme.colorScheme.secondary else Color.Transparent)
                            .clickable { scale = s }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium,
                            color = if (sel) MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Day width slider
            Text("Width:", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value         = dayWidthPx,
                onValueChange = { dayWidthPx = it },
                valueRange    = 16f..80f,
                modifier      = Modifier.width(160.dp)
            )
            Text("${dayWidthPx.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp))
        }

        // ── Date header (sticky top) ──────────────────────────────────────────
        Row(Modifier.fillMaxWidth().height(HEAD_H)) {
            Box(Modifier.width(LABEL_W).fillMaxHeight())
            Box(Modifier.width(1.dp).fillMaxHeight().background(divColor))
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                Column(Modifier.width(totalWidth)) {
                    // Month label row (year scale only)
                    if (scale == TimelineScale.Year) {
                        Row(Modifier.fillMaxWidth().height(MONTH_ROW_H)) {
                            monthSegments.forEach { (name, count) ->
                                Box(
                                    Modifier
                                        .width(DAY_W * count)
                                        .fillMaxHeight()
                                        .border(0.5.dp, divColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    // Day number row
                    Row(Modifier.fillMaxWidth().height(DAY_ROW_H)) {
                        days.forEach { day ->
                            val isToday   = day == today
                            val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
                            Column(
                                Modifier
                                    .width(DAY_W).fillMaxHeight()
                                    .background(when {
                                        isToday   -> MaterialTheme.colorScheme.primaryContainer
                                        isWeekend -> MaterialTheme.colorScheme.surfaceVariant
                                        else      -> Color.Transparent
                                    })
                                    .padding(top = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (scale == TimelineScale.Month) {
                                    Text(
                                        day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).take(2),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isToday   -> MaterialTheme.colorScheme.onPrimaryContainer
                                            isWeekend -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                                            else      -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                                Text(
                                    day.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isToday   -> MaterialTheme.colorScheme.onPrimaryContainer
                                        isWeekend -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        else      -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()

        // ── Room rows ─────────────────────────────────────────────────────────
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // Sticky left labels (vertical scroll only)
            Column(Modifier.width(LABEL_W).fillMaxHeight().verticalScroll(vScroll)) {
                rooms.forEach { room ->
                    Box(Modifier.height(ROW_H).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Column(Modifier.padding(horizontal = 8.dp)) {
                            Text("Rm ${room.number}", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(room.typeName, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider()
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(divColor))

            // Scrollable content with scrollbars overlaid
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // Main scrollable content
                Box(
                    Modifier.fillMaxSize()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                ) {
                    Column(Modifier.width(totalWidth)) {
                        rooms.forEach { room ->
                            val roomRes = (resByRoom[room.id] ?: emptyList()).filter { res ->
                                !LocalDate.parse(res.checkOutDate).isBefore(dateStart) &&
                                !LocalDate.parse(res.checkInDate).isAfter(dateEnd)
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(ROW_H)
                                    .pointerInput(room.id, DAY_W, days.size) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { offset ->
                                                val idx = (offset.x / DAY_W.toPx()).toInt().coerceIn(0, days.size - 1)
                                                dragState = TimelineDragState(room, idx, idx)
                                            },
                                            onHorizontalDrag = { change, _ ->
                                                change.consume()
                                                val idx = (change.position.x / DAY_W.toPx()).toInt().coerceIn(0, days.size - 1)
                                                dragState = dragState?.copy(endIdx = idx)
                                            },
                                            onDragEnd = {
                                                dragState?.let { ds ->
                                                    val s = minOf(ds.startIdx, ds.endIdx)
                                                    val e = maxOf(ds.startIdx, ds.endIdx)
                                                    onCreateRequest(ds.room, days[s], days[e])
                                                }
                                                dragState = null
                                            },
                                            onDragCancel = { dragState = null }
                                        )
                                    }
                            ) {
                                // Background stripes
                                Row(Modifier.fillMaxSize()) {
                                    days.forEach { day ->
                                        val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
                                        val isToday   = day == today
                                        Box(Modifier.width(DAY_W).fillMaxHeight().background(when {
                                            isToday   -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                            isWeekend -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            else      -> Color.Transparent
                                        }))
                                    }
                                }
                                // Room block bands
                                val roomBlocksInView = (blocksByRoom[room.id] ?: emptyList()).filter { block ->
                                    !LocalDate.parse(block.toDate).isBefore(dateStart) &&
                                    !LocalDate.parse(block.fromDate).isAfter(dateEnd)
                                }
                                roomBlocksInView.forEach { block ->
                                    val from      = LocalDate.parse(block.fromDate)
                                    val to        = LocalDate.parse(block.toDate)
                                    val clampFrom = if (from.isBefore(dateStart)) dateStart else from
                                    val clampTo   = if (to.isAfter(dateEnd))     dateEnd   else to
                                    val startOff  = ChronoUnit.DAYS.between(dateStart, clampFrom).toInt()
                                    val endOff    = ChronoUnit.DAYS.between(dateStart, clampTo).toInt()
                                    val bLeft     = DAY_W * startOff + 1.dp
                                    val bWidth    = DAY_W * (endOff - startOff + 1) - 2.dp
                                    val bTop      = (ROW_H - BAND_H) / 2
                                    Box(
                                        Modifier
                                            .offset(x = bLeft, y = bTop)
                                            .width(bWidth).height(BAND_H)
                                            .clip(RoundedCornerShape(
                                                topStart    = if (clampFrom == from) 4.dp else 0.dp,
                                                bottomStart = if (clampFrom == from) 4.dp else 0.dp,
                                                topEnd      = if (clampTo   == to)   4.dp else 0.dp,
                                                bottomEnd   = if (clampTo   == to)   4.dp else 0.dp
                                            ))
                                            .background(Color(0xFF607D8B))
                                            .clickable { onBlockClick(block) },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            block.reason ?: "Blocked",
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                // Existing reservation bands
                                roomRes.forEach { res ->
                                    val from      = LocalDate.parse(res.checkInDate)
                                    val to        = LocalDate.parse(res.checkOutDate)
                                    val clampFrom = if (from.isBefore(dateStart)) dateStart else from
                                    val clampTo   = if (to.isAfter(dateEnd))     dateEnd   else to
                                    val startOff  = ChronoUnit.DAYS.between(dateStart, clampFrom).toInt()
                                    val endOff    = ChronoUnit.DAYS.between(dateStart, clampTo).toInt()
                                    val bLeft     = DAY_W * startOff + 1.dp
                                    val bWidth    = DAY_W * (endOff - startOff + 1) - 2.dp
                                    val bTop      = (ROW_H - BAND_H) / 2
                                    val (bg, fg)  = STATUS_PALETTE[res.status] ?: (Color(0xFFE0E0E0) to Color(0xFF424242))
                                    Box(
                                        Modifier
                                            .offset(x = bLeft, y = bTop)
                                            .width(bWidth).height(BAND_H)
                                            .clip(RoundedCornerShape(
                                                topStart    = if (clampFrom == from) 4.dp else 0.dp,
                                                bottomStart = if (clampFrom == from) 4.dp else 0.dp,
                                                topEnd      = if (clampTo   == to)   4.dp else 0.dp,
                                                bottomEnd   = if (clampTo   == to)   4.dp else 0.dp
                                            ))
                                            .background(bg)
                                            .clickable { onEditRequest(res) },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(res.guestName, modifier = Modifier.padding(horizontal = 4.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                // Drag preview band
                                if (dragState?.room?.id == room.id) {
                                    val ds    = dragState!!
                                    val s     = minOf(ds.startIdx, ds.endIdx)
                                    val e     = maxOf(ds.startIdx, ds.endIdx)
                                    val bLeft = DAY_W * s + 1.dp
                                    val bW    = DAY_W * (e - s + 1) - 2.dp
                                    val bTop  = (ROW_H - BAND_H) / 2
                                    Box(
                                        Modifier
                                            .offset(x = bLeft, y = bTop)
                                            .width(bW).height(BAND_H)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                // Vertical scrollbar (right edge)
                VerticalScrollbar(
                    adapter  = rememberScrollbarAdapter(vScroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
                // Horizontal scrollbar (bottom edge)
                HorizontalScrollbar(
                    adapter  = rememberScrollbarAdapter(hScroll),
                    modifier = Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(end = 8.dp)  // leave space for vertical scrollbar
                )
            }
        }
    }
}

// ─── Block Room dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockRoomDialog(
    rooms: List<RoomDto>,
    onDismiss: () -> Unit,
    onConfirm: (CreateRoomBlockRequest) -> Unit
) {
    var selectedRoom by remember { mutableStateOf(rooms.firstOrNull()) }
    var roomExpanded by remember { mutableStateOf(false) }
    var fromDate     by remember { mutableStateOf("") }
    var toDate       by remember { mutableStateOf("") }
    var reason       by remember { mutableStateOf("") }

    val valid = selectedRoom != null && fromDate.isNotBlank() && toDate.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block Room") },
        text = {
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = { roomExpanded = it }) {
                    OutlinedTextField(
                        value = selectedRoom?.let { "Room ${it.number} · ${it.typeName}" } ?: "Select room",
                        onValueChange = {}, readOnly = true, label = { Text("Room *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { roomExpanded = false }) {
                        rooms.forEach { room ->
                            DropdownMenuItem(
                                text  = { Text("Room ${room.number} · ${room.typeName}") },
                                onClick = { selectedRoom = room; roomExpanded = false }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResDatePickerField("From *", fromDate, { fromDate = it }, Modifier.weight(1f))
                    ResDatePickerField("To *",   toDate,   { toDate   = it }, Modifier.weight(1f))
                }
                OutlinedTextField(
                    reason, { reason = it },
                    label       = { Text("Reason") },
                    placeholder = { Text("Maintenance, cleaning…") },
                    singleLine  = true,
                    modifier    = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(CreateRoomBlockRequest(
                        roomId   = selectedRoom!!.id,
                        fromDate = fromDate.trim(),
                        toDate   = toDate.trim(),
                        reason   = reason.trim().ifBlank { null }
                    ))
                },
                enabled = valid
            ) { Text("Block") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Block delete dialog ──────────────────────────────────────────────────────

@Composable
private fun RoomBlockDeleteDialog(
    block: RoomBlockDto,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Block") },
        text  = {
            Text("Remove block for Room ${block.roomNumber} from ${block.fromDate} to ${block.toDate}" +
                 (block.reason?.let { " ($it)" } ?: "") + "?")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Date picker field (local copy) ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResDatePickerField(
    label: String,
    dateString: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val initialMillis = remember(dateString) {
        if (dateString.isNotBlank()) runCatching {
            LocalDate.parse(dateString).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull() else null
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    OutlinedTextField(
        value         = dateString,
        onValueChange = {},
        readOnly      = true,
        label         = { Text(label) },
        trailingIcon  = {
            TextButton(onClick = { showPicker = true },
                contentPadding = PaddingValues(horizontal = 4.dp)) { Text("📅") }
        },
        modifier   = modifier,
        singleLine = true
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onDateSelected(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}
