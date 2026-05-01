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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
private enum class TimelineScale { Center, Month, Year }

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
fun ReservationsCalendarPage(client: HttpClient, hotel: UserHotelRoleDto, centerDays: Int = 30) {
    val scope = rememberCoroutineScope()

    var reservations by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var rooms        by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var guests       by remember { mutableStateOf<List<GuestDto>>(emptyList()) }
    var priceRules   by remember { mutableStateOf<List<PriceRuleDto>>(emptyList()) }
    var roomBlocks   by remember { mutableStateOf<List<RoomBlockDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    var error        by remember { mutableStateOf<String?>(null) }
    var displayYear    by remember { mutableStateOf(LocalDate.now().year) }
    var displayMonth   by remember { mutableStateOf(LocalDate.now().monthValue) }
    var currentView    by remember { mutableStateOf(ResView.Timeline) }
    var timelineScale  by remember { mutableStateOf(TimelineScale.Center) }
    var showNewDialog     by remember { mutableStateOf(false) }
    var showBlockDialog   by remember { mutableStateOf(false) }
    var optionRes         by remember { mutableStateOf<ReservationDto?>(null) }
    var editReservation   by remember { mutableStateOf<ReservationDto?>(null) }
    var editBlock         by remember { mutableStateOf<RoomBlockDto?>(null) }
    var paymentsRes       by remember { mutableStateOf<ReservationDto?>(null) }
    var prefillRoom       by remember { mutableStateOf<RoomDto?>(null) }
    var prefillCheckIn    by remember { mutableStateOf("") }
    var prefillCheckOut   by remember { mutableStateOf("") }

    suspend fun loadData(showLoading: Boolean = true) {
        if (showLoading) loading = true
        error = null
        try {
            reservations = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            rooms        = client.get("$BASE_URL/api/rooms?hotelId=${hotel.hotelId}").body<List<RoomDto>>()
                .filter { it.archivedAt == null }
            guests       = client.get("$BASE_URL/api/guests").body()
            priceRules   = client.get("$BASE_URL/api/hotels/${hotel.hotelId}/price-rules").body()
            roomBlocks   = client.get("$BASE_URL/api/room-blocks?hotelId=${hotel.hotelId}").body()
        } catch (e: Exception) { error = e.message } finally { if (showLoading) loading = false }
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
                // Navigation — hidden for center scale (no anchor to navigate)
                val showNav = !(currentView == ResView.Timeline && timelineScale == TimelineScale.Center)
                if (showNav) {
                    IconButton(onClick = {
                        when {
                            currentView == ResView.Calendar || timelineScale == TimelineScale.Year -> displayYear--
                            displayMonth == 1 -> { displayMonth = 12; displayYear-- }
                            else -> displayMonth--
                        }
                    }, Modifier.size(32.dp)) { Text("◀", style = MaterialTheme.typography.labelLarge) }
                }
                Text(
                    when {
                        currentView == ResView.Calendar -> "$displayYear"
                        timelineScale == TimelineScale.Center -> "±${centerDays}d"
                        timelineScale == TimelineScale.Year -> "$displayYear"
                        else -> "${Month.of(displayMonth).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)} $displayYear"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (showNav) {
                    IconButton(onClick = {
                        when {
                            currentView == ResView.Calendar || timelineScale == TimelineScale.Year -> displayYear++
                            displayMonth == 12 -> { displayMonth = 1; displayYear++ }
                            else -> displayMonth++
                        }
                    }, Modifier.size(32.dp)) { Text("▶", style = MaterialTheme.typography.labelLarge) }
                }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { showBlockDialog = true }, enabled = rooms.isNotEmpty()) {
                    Text("Block Room")
                }
                Button(onClick = { showNewDialog = true }, enabled = rooms.isNotEmpty()) {
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
                            onResClick   = { optionRes = it }
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
                scale           = timelineScale,
                onScaleChange   = { timelineScale = it },
                centerDays      = centerDays,
                onEditRequest   = { optionRes = it },
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
            reservations    = reservations,
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
                        loadData(showLoading = false)
                    } catch (e: Exception) { error = e.message }
                }
            },
            onCreateGuest = { req ->
                try {
                    val created: GuestDto = client.post("$BASE_URL/api/guests") {
                        contentType(ContentType.Application.Json)
                        setBody(req)
                    }.body()
                    guests = guests + created
                    created
                } catch (e: Exception) { error = e.message; null }
            }
        )
    }

    // ── Edit Reservation dialog ────────────────────────────────────────────────
    editReservation?.let { res ->
        ReservationEditDialog(
            existing   = res,
            rooms      = rooms,
            guests     = guests,
            reservations = reservations,
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
                        loadData(showLoading = false)
                    } catch (e: Exception) { error = e.message }
                }
            },
            onDelete = {
                scope.launch {
                    try {
                        client.delete("$BASE_URL/api/reservations/${res.id}")
                        editReservation = null
                        loadData(showLoading = false)
                    } catch (e: Exception) { error = e.message }
                }
            },
            onCreateGuest = { req ->
                try {
                    val created: GuestDto = client.post("$BASE_URL/api/guests") {
                        contentType(ContentType.Application.Json)
                        setBody(req)
                    }.body()
                    guests = guests + created
                    created
                } catch (e: Exception) { error = e.message; null }
            }
        )
    }

    // ── Reservation detail dialog ──────────────────────────────────────────────
    optionRes?.let { res ->
        ReservationDetailDialog(
            res       = res,
            rooms     = rooms,
            guests    = guests,
            onDismiss = { optionRes = null },
            onEdit    = { editReservation = res; optionRes = null },
            onPayments = { paymentsRes = res; optionRes = null },
            onSaveDescription = { desc ->
                scope.launch {
                    try {
                        client.put("$BASE_URL/api/reservations/${res.id}") {
                            contentType(ContentType.Application.Json)
                            setBody(UpdateReservationRequest(
                                roomId = res.roomId, guestId = res.guestId,
                                checkInDate = res.checkInDate, checkOutDate = res.checkOutDate,
                                status = res.status, adults = res.adults,
                                totalAmount = res.totalAmount, description = desc,
                                requiresDownPayment = res.requiresDownPayment,
                                downPaymentAmount   = res.downPaymentAmount
                            ))
                        }
                        optionRes = null
                        loadData(showLoading = false)
                    } catch (e: Exception) { error = e.message }
                }
            }
        )
    }

    // ── Manage Payments dialog ─────────────────────────────────────────────────
    paymentsRes?.let { res ->
        ManagePaymentsDialog(
            res               = res,
            client            = client,
            onDismiss         = { paymentsRes = null },
            onPaymentsChanged = { scope.launch { loadData(showLoading = false) } }
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
                        loadData(showLoading = false)
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
                        loadData(showLoading = false)
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
            val dotColor = paymentDotColor(res.totalAmount, res.paidAmount)
            val showDpBadge = dpPending(res) && clampFrom == from

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
                    .clickable { onResClick(res) }
            ) {
                if (showDpBadge) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE65100))
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                    ) {
                        Text("DP", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                            color = Color.White)
                    }
                }
                if (showLabel) {
                    Text(
                        label,
                        modifier = Modifier.padding(start = if (showDpBadge) 18.dp else 4.dp,
                            end = if (dotColor != null) 12.dp else 4.dp)
                            .align(Alignment.CenterStart),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (dotColor != null && clampTo == to) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 3.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
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

// ─── Booking conflict helper ─────────────────────────────────────────────────

private fun hasReservationConflict(
    reservations: List<ReservationDto>,
    roomId: Int,
    checkIn: String,
    checkOut: String,
    excludeId: Int? = null
): Boolean {
    val cin  = runCatching { LocalDate.parse(checkIn.trim())  }.getOrNull() ?: return false
    val cout = runCatching { LocalDate.parse(checkOut.trim()) }.getOrNull() ?: return false
    if (!cout.isAfter(cin)) return false
    return reservations.any { r ->
        r.roomId == roomId &&
        r.id != excludeId &&
        r.status !in listOf("cancelled", "no_show") &&
        LocalDate.parse(r.checkInDate).isBefore(cout) &&
        LocalDate.parse(r.checkOutDate).isAfter(cin)
    }
}

private fun formatAdults(a: Double): String =
    if (a % 1.0 == 0.0) a.toLong().toString() else "%.1f".format(a)

private fun dpPending(res: ReservationDto): Boolean {
    if (!res.requiresDownPayment) return false
    val required = res.downPaymentAmount ?: return true
    return res.paidAmount < required
}

private fun paymentDotColor(totalAmount: Double?, paidAmount: Double): Color? = when {
    totalAmount == null || totalAmount <= 0 -> null
    paidAmount >= totalAmount               -> Color(0xFF4CAF50)
    paidAmount > 0                          -> Color(0xFFFFC107)
    else                                    -> Color(0xFFF44336)
}

// ─── Detail dialog (shown when clicking a reservation) ───────────────────────

@Composable
private fun ReservationDetailDialog(
    res: ReservationDto,
    rooms: List<RoomDto>,
    guests: List<GuestDto>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onPayments: () -> Unit,
    onSaveDescription: (String?) -> Unit
) {
    val nights = remember(res.checkInDate, res.checkOutDate) {
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(res.checkInDate), LocalDate.parse(res.checkOutDate)).toInt() }.getOrDefault(0)
    }
    val room  = rooms.find { it.id == res.roomId }
    val guest = guests.find { it.id == res.guestId }
    var description by remember(res.id) { mutableStateOf(res.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reservation #${res.id}") },
        text = {
            Column(
                Modifier.width(440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Guest header
                Text(res.guestName, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                guest?.let { g ->
                    val phone = listOfNotNull(g.countryCode?.let { "+$it" }, g.phoneNumber).joinToString(" ").ifBlank { null }
                    val contact = listOfNotNull(phone, g.nationality).joinToString(" · ")
                    if (contact.isNotBlank())
                        Text(contact, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                // Dates — shown prominently
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Check-in", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(res.checkInDate, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text("Check-out", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(res.checkOutDate, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text("Nights", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(nights.toString(), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                SummaryRow("Room", room?.let { "Room ${it.number} · ${it.typeName}" } ?: "Room ${res.roomNumber}")
                SummaryRow("Adults", formatAdults(res.adults))
                SummaryRow("Status", res.status.replace('_', ' ').replaceFirstChar { it.uppercaseChar() })
                if (dpPending(res)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Down Pmt", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp))
                        Text(
                            res.downPaymentAmount?.let { "${"%.2f".format(it)} PLN required" } ?: "Required",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
                res.totalAmount?.let { total ->
                    SummaryRow("Total", "${"%.2f".format(total)} PLN")
                    SummaryRow("Paid",  "${"%.2f".format(res.paidAmount)} PLN")
                    val remaining = total - res.paidAmount
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Remaining", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp))
                        Text(
                            "${"%.2f".format(remaining)} PLN",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (remaining <= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                Text("Notes", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    placeholder = { Text("Add notes about this reservation…") },
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onSaveDescription(description.trim().ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth()) { Text("Save Notes") }
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit Reservation") }
                OutlinedButton(onClick = onPayments, modifier = Modifier.fillMaxWidth()) { Text("Manage Payments") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    )
}

// ─── Manage Payments dialog ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagePaymentsDialog(
    res: ReservationDto,
    client: HttpClient,
    onDismiss: () -> Unit,
    onPaymentsChanged: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var payments    by remember { mutableStateOf<List<PaymentDto>>(emptyList()) }
    var loading     by remember { mutableStateOf(true) }
    var error       by remember { mutableStateOf<String?>(null) }

    // Add-payment form state
    var isDeposit          by remember { mutableStateOf(false) }
    var amountInput        by remember { mutableStateOf("") }
    var paidAtInput        by remember { mutableStateOf(LocalDate.now().toString()) }
    var notesInput         by remember { mutableStateOf("") }
    var receiptType        by remember { mutableStateOf("") }
    var receiptNumberInput by remember { mutableStateOf("") }
    var showDatePicker     by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        try { payments = client.get("$BASE_URL/api/reservations/${res.id}/payments").body() }
        catch (e: Exception) { error = e.message }
        loading = false
    }

    LaunchedEffect(res.id) { reload() }

    val totalPaid = remember(payments) { payments.sumOf { it.amount } }
    val remaining = res.totalAmount?.let { it - totalPaid }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payments · Reservation #${res.id}") },
        text = {
            Column(
                Modifier.width(460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    res.totalAmount?.let {
                        Column {
                            Text("Total", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.2f".format(it)} PLN", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column {
                        Text("Paid", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.2f".format(totalPaid)} PLN", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    remaining?.let {
                        Column {
                            Text("Remaining", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.2f".format(it)} PLN", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (it > 0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                HorizontalDivider()

                // Payments table
                if (loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else if (payments.isEmpty()) {
                    Text("No payments recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    var expandedId by remember { mutableStateOf<Int?>(null) }
                    // Header
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Type",    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.3f))
                        Text("Amount", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                        Text("Date",   style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.1f))
                        Text("Doc",    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.5f))
                        Spacer(Modifier.width(28.dp))
                    }
                    payments.forEach { p ->
                        val expanded = expandedId == p.id
                        val docLabel = when (p.receiptType) {
                            "receipt" -> "R: ${p.receiptNumber ?: "—"}"
                            "invoice" -> "I: ${p.receiptNumber ?: "—"}"
                            else      -> "—"
                        }
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { expandedId = if (expanded) null else p.id }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (p.isDeposit) "Down Payment" else "Payment",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1.3f)
                                )
                                Text("${"%.2f".format(p.amount)} ${p.currency ?: "PLN"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1.2f))
                                Text(p.paidAt ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1.1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(docLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1.5f),
                                    color = if (p.receiptType != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                client.delete("$BASE_URL/api/payments/${p.id}")
                                                reload()
                                                onPaymentsChanged()
                                            } catch (e: Exception) { error = e.message }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("✕", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (expanded) {
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    SummaryRow("Type",  if (p.isDeposit) "Down Payment" else "Payment")
                                    SummaryRow("Amount","${"%.2f".format(p.amount)} ${p.currency ?: "PLN"}")
                                    SummaryRow("Date",  p.paidAt ?: "—")
                                    if (!p.notes.isNullOrBlank()) SummaryRow("Notes", p.notes!!)
                                    val rType = p.receiptType
                                    if (rType != null) {
                                        SummaryRow("Doc type", rType.replaceFirstChar { it.uppercaseChar() })
                                        SummaryRow("Doc #",    p.receiptNumber ?: "—")
                                    }
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                error?.let {
                    Text("Error: $it", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall)
                }

                HorizontalDivider()
                Text("Add Payment", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)

                // Type toggle
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    listOf(false to "Payment", true to "Down Payment").forEach { (deposit, label) ->
                        val sel = isDeposit == deposit
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { isDeposit = deposit }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Amount (PLN) *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = paidAtInput,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date") },
                        trailingIcon = {
                            TextButton(onClick = { showDatePicker = true },
                                contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Pick", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (showDatePicker) {
                    val initMillis = remember(paidAtInput) {
                        runCatching { LocalDate.parse(paidAtInput).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                            .getOrElse { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                    }
                    val dpState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                dpState.selectedDateMillis?.let { millis ->
                                    paidAtInput = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneOffset.UTC).toLocalDate().toString()
                                }
                                showDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
                    ) { DatePicker(state = dpState) }
                }
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Receipt/invoice type toggle
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    listOf("" to "Nothing", "receipt" to "Receipt", "invoice" to "Invoice").forEach { (value, label) ->
                        val sel = receiptType == value
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                .clickable { receiptType = value; if (value.isEmpty()) receiptNumberInput = "" }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium,
                                color = if (sel) MaterialTheme.colorScheme.onSecondary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (receiptType.isNotEmpty()) {
                    OutlinedTextField(
                        value = receiptNumberInput,
                        onValueChange = { receiptNumberInput = it },
                        label = { Text("${receiptType.replaceFirstChar { it.uppercaseChar() }} number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val canAdd = amountInput.toDoubleOrNull() != null && amountInput.toDouble() > 0
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                client.post("$BASE_URL/api/reservations/${res.id}/payments") {
                                    contentType(ContentType.Application.Json)
                                    setBody(CreatePaymentRequest(
                                        isDeposit     = isDeposit,
                                        amount        = amountInput.toDouble(),
                                        paidAt        = paidAtInput.trim().ifBlank { null },
                                        notes         = notesInput.trim().ifBlank { null },
                                        receiptType   = receiptType.ifBlank { null },
                                        receiptNumber = receiptNumberInput.trim().ifBlank { null }
                                    ))
                                }
                                amountInput        = ""
                                paidAtInput        = LocalDate.now().toString()
                                notesInput         = ""
                                isDeposit          = false
                                receiptType        = ""
                                receiptNumberInput = ""
                                reload()
                                onPaymentsChanged()
                            } catch (e: Exception) { error = e.message }
                        }
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Payment") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

// ─── New Reservation dialog ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewReservationDialog(
    hotel: UserHotelRoleDto,
    rooms: List<RoomDto>,
    guests: List<GuestDto>,
    reservations: List<ReservationDto>,
    priceRules: List<PriceRuleDto>,
    prefillRoom: RoomDto? = null,
    prefillCheckIn: String = "",
    prefillCheckOut: String = "",
    onDismiss: () -> Unit,
    onConfirm: (CreateReservationRequest) -> Unit,
    onCreateGuest: suspend (CreateGuestRequest) -> GuestDto?
) {
    var selectedRoom    by remember { mutableStateOf(prefillRoom ?: rooms.firstOrNull()) }
    var roomExpanded    by remember { mutableStateOf(false) }
    var statusExpanded  by remember { mutableStateOf(false) }
    var checkIn   by remember { mutableStateOf(prefillCheckIn) }
    var checkOut  by remember { mutableStateOf(prefillCheckOut) }
    var adults    by remember { mutableStateOf(selectedRoom?.maxGuests?.toString() ?: "1") }
    var status    by remember { mutableStateOf("confirmed") }
    var ppnInput  by remember { mutableStateOf("") }
    var requiresDownPayment    by remember { mutableStateOf(false) }
    var downPaymentAmountInput by remember { mutableStateOf("") }
    // Guest form
    var guestFirstName   by remember { mutableStateOf("") }
    var guestLastName    by remember { mutableStateOf("") }
    var guestCountryCode by remember { mutableStateOf("") }
    var guestPhoneNumber by remember { mutableStateOf("") }
    var selectedGuest    by remember { mutableStateOf<GuestDto?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedRoom?.id) {
        selectedRoom?.let { adults = it.maxGuests.toString() }
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
    val totalGuests = adults.toDoubleOrNull() ?: 1.0
    val computedTotal = if (ppn != null && nights > 0) ppn * nights * totalGuests else null
    val guestReady = selectedGuest != null || (guestFirstName.isNotBlank() && guestLastName.isNotBlank())
    val conflict = remember(selectedRoom?.id, checkIn, checkOut) {
        selectedRoom != null && checkIn.isNotBlank() && checkOut.isNotBlank() &&
        hasReservationConflict(reservations, selectedRoom!!.id, checkIn, checkOut)
    }
    val valid = selectedRoom != null && guestReady &&
        checkIn.isNotBlank() && checkOut.isNotBlank() && adults.toDoubleOrNull() != null && !conflict

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Reservation") },
        text = {
            Column(Modifier.width(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Room
                ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = { roomExpanded = it }) {
                    OutlinedTextField(
                        value = selectedRoom?.let { "Room ${it.number} · ${it.typeName}" } ?: "Select room",
                        onValueChange = {}, readOnly = true, label = { Text("Room *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
                    )
                    ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { roomExpanded = false }) {
                        rooms.forEach { room ->
                            DropdownMenuItem(
                                text = { Text("Room ${room.number} · ${room.typeName} (max ${room.maxGuests})") },
                                onClick = { selectedRoom = room; roomExpanded = false }
                            )
                        }
                    }
                }
                // Guest search / create
                GuestInputSection(
                    guests = guests,
                    selectedGuest = selectedGuest,
                    onSelectedGuestChange = { selectedGuest = it },
                    firstName = guestFirstName,
                    onFirstNameChange = { guestFirstName = it; selectedGuest = null },
                    lastName = guestLastName,
                    onLastNameChange = { guestLastName = it; selectedGuest = null },
                    countryCode = guestCountryCode,
                    onCountryCodeChange = { guestCountryCode = it },
                    phoneNumber = guestPhoneNumber,
                    onPhoneNumberChange = { guestPhoneNumber = it }
                )
                HorizontalDivider()
                // Dates
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResDatePickerField("Check-in *",  checkIn,  { checkIn  = it }, Modifier.weight(1f))
                    ResDatePickerField("Check-out *", checkOut, { checkOut = it }, Modifier.weight(1f))
                }
                if (conflict) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Room already reserved for these dates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                OutlinedTextField(adults, { adults = it }, label = { Text("Adults") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }) {
                    OutlinedTextField(
                        value = status.replace('_', ' '), onValueChange = {}, readOnly = true, label = { Text("Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
                    )
                    ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        RESERVATION_STATUSES.forEach { s ->
                            DropdownMenuItem(text = { Text(s.replace('_', ' ')) }, onClick = { status = s; statusExpanded = false })
                        }
                    }
                }
                HorizontalDivider()
                PricePpnSection(
                    ppnInput = ppnInput, onPpnChange = { ppnInput = it },
                    matchingRule = matchingRule, nights = nights,
                    totalGuests = totalGuests, computedTotal = computedTotal
                )
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { requiresDownPayment = !requiresDownPayment },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = requiresDownPayment, onCheckedChange = { requiresDownPayment = it })
                    Text("Requires down payment", style = MaterialTheme.typography.bodyMedium)
                }
                if (requiresDownPayment) {
                    OutlinedTextField(
                        value = downPaymentAmountInput,
                        onValueChange = { downPaymentAmountInput = it },
                        label = { Text("Down payment amount (PLN)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val guestId = selectedGuest?.id ?: run {
                            onCreateGuest(CreateGuestRequest(
                                firstName   = guestFirstName.trim(),
                                lastName    = guestLastName.trim(),
                                countryCode = guestCountryCode.trim().ifBlank { null },
                                phoneNumber = guestPhoneNumber.trim().ifBlank { null }
                            ))?.id
                        } ?: return@launch
                        onConfirm(CreateReservationRequest(
                            hotelId = hotel.hotelId, roomId = selectedRoom!!.id, guestId = guestId,
                            checkInDate = checkIn.trim(), checkOutDate = checkOut.trim(), status = status,
                            adults = adults.trim().toDoubleOrNull() ?: 1.0,
                            totalAmount = computedTotal,
                            requiresDownPayment = requiresDownPayment,
                            downPaymentAmount   = if (requiresDownPayment) downPaymentAmountInput.toDoubleOrNull() else null
                        ))
                    }
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
    reservations: List<ReservationDto>,
    priceRules: List<PriceRuleDto>,
    onDismiss: () -> Unit,
    onConfirm: (UpdateReservationRequest) -> Unit,
    onDelete: () -> Unit,
    onCreateGuest: suspend (CreateGuestRequest) -> GuestDto?
) {
    val scope = rememberCoroutineScope()
    var selectedRoom     by remember { mutableStateOf(rooms.find { it.id == existing.roomId } ?: rooms.firstOrNull()) }
    var selectedGuest    by remember { mutableStateOf(guests.find { it.id == existing.guestId }) }
    var guestFirstName   by remember { mutableStateOf("") }
    var guestLastName    by remember { mutableStateOf("") }
    var guestCountryCode by remember { mutableStateOf("") }
    var guestPhoneNumber by remember { mutableStateOf("") }
    var roomExpanded   by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var checkIn  by remember { mutableStateOf(existing.checkInDate) }
    var checkOut by remember { mutableStateOf(existing.checkOutDate) }
    var adults   by remember { mutableStateOf(formatAdults(existing.adults)) }
    var status   by remember { mutableStateOf(existing.status) }
    var requiresDownPayment    by remember(existing.id) { mutableStateOf(existing.requiresDownPayment) }
    var downPaymentAmountInput by remember(existing.id) { mutableStateOf(existing.downPaymentAmount?.let { "%.2f".format(it) } ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Initialise ppn: from back-computed existing total, rule will override via LaunchedEffect
    var ppnInput by remember(existing.id) {
        val cin  = runCatching { LocalDate.parse(existing.checkInDate) }.getOrNull()
        val cout = runCatching { LocalDate.parse(existing.checkOutDate) }.getOrNull()
        val n    = if (cin != null && cout != null && cout.isAfter(cin)) ChronoUnit.DAYS.between(cin, cout).toInt() else 0
        val g    = existing.adults
        val back = existing.totalAmount?.let { if (n > 0 && g > 0.0) "%.2f".format(it / (n * g)) else null } ?: ""
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
    val totalGuests = adults.toDoubleOrNull() ?: 1.0
    val computedTotal = if (ppn != null && nights > 0) ppn * nights * totalGuests else null
    val conflict = remember(selectedRoom?.id, checkIn, checkOut) {
        selectedRoom != null && checkIn.isNotBlank() && checkOut.isNotBlank() &&
        hasReservationConflict(reservations, selectedRoom!!.id, checkIn, checkOut, excludeId = existing.id)
    }
    val guestReady = selectedGuest != null || (guestFirstName.isNotBlank() && guestLastName.isNotBlank())
    val valid = selectedRoom != null && guestReady &&
        checkIn.isNotBlank() && checkOut.isNotBlank() && adults.toDoubleOrNull() != null && !conflict

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
            Column(Modifier.width(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuestInputSection(
                    guests = guests,
                    selectedGuest = selectedGuest,
                    onSelectedGuestChange = { selectedGuest = it },
                    firstName = guestFirstName, onFirstNameChange = { guestFirstName = it; selectedGuest = null },
                    lastName = guestLastName, onLastNameChange = { guestLastName = it; selectedGuest = null },
                    countryCode = guestCountryCode, onCountryCodeChange = { guestCountryCode = it },
                    phoneNumber = guestPhoneNumber, onPhoneNumberChange = { guestPhoneNumber = it }
                )
                HorizontalDivider()
                ReservationFormFields(
                    rooms = rooms,
                    selectedRoom = selectedRoom, onRoomChange = { selectedRoom = it },
                    roomExpanded = roomExpanded, onRoomExpandChange = { roomExpanded = it },
                    statusExpanded = statusExpanded, onStatusExpandChange = { statusExpanded = it },
                    checkIn = checkIn, onCheckInChange = { checkIn = it },
                    checkOut = checkOut, onCheckOutChange = { checkOut = it },
                    adults = adults, onAdultsChange = { adults = it },
                    status = status, onStatusChange = { status = it }
                )
                if (conflict) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Room already reserved for these dates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                HorizontalDivider()
                PricePpnSection(
                    ppnInput = ppnInput, onPpnChange = { ppnInput = it },
                    matchingRule = matchingRule, nights = nights,
                    totalGuests = totalGuests, computedTotal = computedTotal
                )
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { requiresDownPayment = !requiresDownPayment },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = requiresDownPayment, onCheckedChange = { requiresDownPayment = it })
                    Text("Requires down payment", style = MaterialTheme.typography.bodyMedium)
                }
                if (requiresDownPayment) {
                    OutlinedTextField(
                        value = downPaymentAmountInput,
                        onValueChange = { downPaymentAmountInput = it },
                        label = { Text("Down payment amount (PLN)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val guestId = selectedGuest?.id ?: run {
                            onCreateGuest(CreateGuestRequest(
                                firstName   = guestFirstName.trim(),
                                lastName    = guestLastName.trim(),
                                countryCode = guestCountryCode.trim().ifBlank { null },
                                phoneNumber = guestPhoneNumber.trim().ifBlank { null }
                            ))?.id
                        } ?: return@launch
                        onConfirm(UpdateReservationRequest(
                            roomId = selectedRoom!!.id, guestId = guestId,
                            checkInDate = checkIn.trim(), checkOutDate = checkOut.trim(), status = status,
                            adults = adults.trim().toDoubleOrNull() ?: 1.0,
                            totalAmount = computedTotal,
                            description = existing.description,
                            requiresDownPayment = requiresDownPayment,
                            downPaymentAmount   = if (requiresDownPayment) downPaymentAmountInput.toDoubleOrNull() else null
                        ))
                    }
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
    rooms: List<RoomDto>,
    selectedRoom: RoomDto?, onRoomChange: (RoomDto) -> Unit,
    roomExpanded: Boolean, onRoomExpandChange: (Boolean) -> Unit,
    statusExpanded: Boolean, onStatusExpandChange: (Boolean) -> Unit,
    checkIn: String, onCheckInChange: (String) -> Unit,
    checkOut: String, onCheckOutChange: (String) -> Unit,
    adults: String, onAdultsChange: (String) -> Unit,
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResDatePickerField("Check-in *",  checkIn,  onCheckInChange,  Modifier.weight(1f))
        ResDatePickerField("Check-out *", checkOut, onCheckOutChange, Modifier.weight(1f))
    }
    OutlinedTextField(adults, onAdultsChange, label = { Text("Adults") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
    totalGuests: Double,
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
                    "$nights night${if (nights != 1) "s" else ""} × ${formatAdults(totalGuests)} person${if (totalGuests != 1.0) "s" else ""}",
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

private data class TimelineDragState(val room: RoomDto, val startIdx: Int, val endIdx: Int)

@Composable
private fun ReservationsTimelineView(
    rooms: List<RoomDto>,
    reservations: List<ReservationDto>,
    blocks: List<RoomBlockDto>,
    year: Int,
    month: Int,
    scale: TimelineScale,
    onScaleChange: (TimelineScale) -> Unit,
    centerDays: Int,
    onEditRequest: (ReservationDto) -> Unit,
    onBlockClick: (RoomBlockDto) -> Unit,
    onCreateRequest: (room: RoomDto, checkIn: LocalDate, checkOut: LocalDate) -> Unit
) {
    val scope         = rememberCoroutineScope()
    var dragState     by remember { mutableStateOf<TimelineDragState?>(null) }
    var dayWidthPx    by remember { mutableStateOf(40f) }
    var viewportWidthPx by remember { mutableStateOf(0) }
    var halfShift     by remember { mutableStateOf(false) }
    var showCancelled by remember { mutableStateOf(false) }
    // Round to an integer pixel so that (DAY_W * n) == n individual DAY_W cells,
    // preventing month-header dividers from drifting out of sync with day columns.
    val density      = LocalDensity.current
    val DAY_W        = with(density) { dayWidthPx.dp.roundToPx().toDp() }
    val today        = LocalDate.now()

    // All days in view
    val days: List<LocalDate> = remember(year, month, scale, centerDays) {
        when (scale) {
            TimelineScale.Month -> {
                val n = LocalDate.of(year, month, 1).lengthOfMonth()
                (1..n).map { LocalDate.of(year, month, it) }
            }
            TimelineScale.Year  -> buildList {
                val decLen = LocalDate.of(year - 1, 12, 1).lengthOfMonth()
                addAll((1..decLen).map { LocalDate.of(year - 1, 12, it) })
                (1..12).forEach { m ->
                    val n = LocalDate.of(year, m, 1).lengthOfMonth()
                    addAll((1..n).map { LocalDate.of(year, m, it) })
                }
                val janLen = LocalDate.of(year + 1, 1, 1).lengthOfMonth()
                addAll((1..janLen).map { LocalDate.of(year + 1, 1, it) })
            }
            TimelineScale.Center -> {
                val start = today.minusDays(centerDays.toLong())
                val totalDays = centerDays * 2 + 1
                (0 until totalDays).map { start.plusDays(it.toLong()) }
            }
        }
    }
    // Month label segments — shown for Year and Center scales
    val monthSegments: List<Pair<String, Int>> = remember(year, month, scale, centerDays) {
        when (scale) {
            TimelineScale.Year -> buildList {
                add(Month.DECEMBER.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) to LocalDate.of(year - 1, 12, 1).lengthOfMonth())
                (1..12).forEach { m -> add(Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) to LocalDate.of(year, m, 1).lengthOfMonth()) }
                add(Month.JANUARY.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) to LocalDate.of(year + 1, 1, 1).lengthOfMonth())
            }
            TimelineScale.Center -> {
                val start = today.minusDays(centerDays.toLong())
                val end   = today.plusDays(centerDays.toLong())
                val segments = mutableListOf<Pair<String, Int>>()
                var d = start
                while (!d.isAfter(end)) {
                    val monthEnd   = d.with(TemporalAdjusters.lastDayOfMonth())
                    val clampedEnd = if (monthEnd.isAfter(end)) end else monthEnd
                    val count      = ChronoUnit.DAYS.between(d, clampedEnd).toInt() + 1
                    segments += Month.of(d.monthValue).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) to count
                    d = clampedEnd.plusDays(1)
                }
                segments
            }
            else -> emptyList()
        }
    }

    val dateStart    = days.first()
    val dateEnd      = days.last()
    val totalWidth   = DAY_W * days.size

    val fs           = density.fontScale
    val ROW_H        = 34.dp * fs
    val LANE_H       = 30.dp * fs
    val SPLIT_ROW_H  = LANE_H * 2
    val MONTH_ROW_H  = 18.dp * fs
    val DAY_ROW_H    = 46.dp * fs
    val HEAD_H       = if (scale == TimelineScale.Month) DAY_ROW_H else MONTH_ROW_H + DAY_ROW_H
    val LABEL_W      = 96.dp * fs
    val BAND_H       = 22.dp * fs

    val resByRoom    = remember(reservations) { reservations.groupBy { it.roomId } }
    val blocksByRoom = remember(blocks) { blocks.groupBy { it.roomId } }
    var expandedRoomId  by remember { mutableStateOf<Int?>(null) }
    val EXPANDED_ROW_H  = 100.dp * fs
    val EXPANDED_BAND_H = 88.dp * fs
    val hScroll      = rememberScrollState()
    val vScroll      = rememberScrollState()
    val divColor     = MaterialTheme.colorScheme.outlineVariant

    // Scroll to today when scale or period changes
    LaunchedEffect(scale, year, month, centerDays) {
        val dayPx = with(density) { dayWidthPx.dp.roundToPx() }
        if (!today.isBefore(dateStart) && !today.isAfter(dateEnd)) {
            val dayOffset = ChronoUnit.DAYS.between(dateStart, today).toInt()
            val centered  = (dayOffset * dayPx - viewportWidthPx / 2 + dayPx / 2).coerceAtLeast(0)
            hScroll.scrollTo(centered)
        } else {
            hScroll.scrollTo(0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Controls row ──────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scale pill
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(TimelineScale.Center to "Center", TimelineScale.Month to "Month", TimelineScale.Year to "Year").forEach { (s, label) ->
                    val sel = scale == s
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) MaterialTheme.colorScheme.secondary else Color.Transparent)
                            .clickable { onScaleChange(s) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium,
                            color = if (sel) MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Today button
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        scope.launch {
                            val dayPx = with(density) { dayWidthPx.dp.roundToPx() }
                            if (!today.isBefore(dateStart) && !today.isAfter(dateEnd)) {
                                val dayOffset = ChronoUnit.DAYS.between(dateStart, today).toInt()
                                val centered  = (dayOffset * dayPx - viewportWidthPx / 2 + dayPx / 2).coerceAtLeast(0)
                                hScroll.animateScrollTo(centered)
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("Today", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Spacer(Modifier.width(4.dp))
            // Half-shift pill
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(false to "Full day", true to "Half shift").forEach { (v, label) ->
                    val sel = halfShift == v
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) MaterialTheme.colorScheme.tertiary else Color.Transparent)
                            .clickable { halfShift = v }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium,
                            color = if (sel) MaterialTheme.colorScheme.onTertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            // Show/hide cancellations button
            val cancelBtnColor = if (showCancelled) MaterialTheme.colorScheme.errorContainer
                                 else MaterialTheme.colorScheme.surfaceVariant
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(cancelBtnColor)
                    .clickable { showCancelled = !showCancelled }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    if (showCancelled) "Hide cancelled" else "Show cancelled",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (showCancelled) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Date header (sticky top) ──────────────────────────────────────────
        Row(Modifier.fillMaxWidth().height(HEAD_H)) {
            Box(Modifier.width(LABEL_W).fillMaxHeight())
            Box(Modifier.width(1.dp).fillMaxHeight().background(divColor))
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                Column(Modifier.width(totalWidth)) {
                    // Month label row (year and center scales)
                    if (scale != TimelineScale.Month) {
                        Row(Modifier.fillMaxWidth().height(MONTH_ROW_H)) {
                            monthSegments.forEach { (name, count) ->
                                Box(
                                    Modifier
                                        .width(DAY_W * count)
                                        .fillMaxHeight()
                                        .drawBehind {
                                            drawLine(divColor, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), strokeWidth = 1f)
                                            drawLine(divColor, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        name,
                                        style     = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        maxLines  = 1,
                                        overflow  = TextOverflow.Ellipsis,
                                        modifier  = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                                    )
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
                                    .drawBehind {
                                        drawLine(divColor, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), strokeWidth = 1f)
                                    }
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
                    val isExpanded = expandedRoomId == room.id
                    val rowH by animateDpAsState(
                        targetValue = when {
                            isExpanded    -> EXPANDED_ROW_H
                            showCancelled -> SPLIT_ROW_H
                            else          -> ROW_H
                        },
                        label = "label_${room.id}"
                    )
                    Box(
                        Modifier
                            .height(rowH)
                            .fillMaxWidth()
                            .clickable { expandedRoomId = if (expandedRoomId == room.id) null else room.id },
                        contentAlignment = Alignment.TopStart
                    ) {
                        Row(
                            Modifier.height(ROW_H).fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Rm ${room.number}", style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(room.typeName, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                if (isExpanded) "▲" else "▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(divColor))

            // Scrollable content with scrollbars overlaid
            Box(Modifier.weight(1f).fillMaxHeight()
                .onGloballyPositioned { viewportWidthPx = it.size.width }) {
                // Main scrollable content
                Box(
                    Modifier.fillMaxSize()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                ) {
                    Column(Modifier.width(totalWidth)) {
                        rooms.forEach { room ->
                            val isExpanded = expandedRoomId == room.id
                            val rowH by animateDpAsState(
                                targetValue = when {
                                    isExpanded    -> EXPANDED_ROW_H
                                    showCancelled -> SPLIT_ROW_H
                                    else          -> ROW_H
                                },
                                label = "content_${room.id}"
                            )

                            // ── Filter reservations for this room ──────────────
                            val cancelStatuses = setOf("cancelled", "no_show")
                            val allRoomRes = (resByRoom[room.id] ?: emptyList()).filter { res ->
                                val co = LocalDate.parse(res.checkOutDate)
                                val ci = LocalDate.parse(res.checkInDate)
                                // Without half-shift, checkout on dateStart = nothing shown
                                val coVisible = if (halfShift) !co.isBefore(dateStart) else co.isAfter(dateStart)
                                coVisible && !ci.isAfter(dateEnd)
                            }
                            val activeRes    = allRoomRes.filter { it.status !in cancelStatuses }
                            val cancelledRes = if (showCancelled) allRoomRes.filter { it.status in cancelStatuses }
                                              else emptyList()

                            // ── Band geometry helper (halfShift-aware) ─────────
                            // Returns (bLeft, bWidth, leftRounded, rightRounded) or null if invisible
                            fun bandGeo(from: LocalDate, to: LocalDate): Array<Any>? {
                                return if (halfShift) {
                                    val inOff  = ChronoUnit.DAYS.between(dateStart, from).toInt()
                                    val outOff = ChronoUnit.DAYS.between(dateStart, to).toInt()
                                    val startFrac = (inOff + 0.5f).coerceAtLeast(0f)
                                    val endFrac   = (outOff + 0.5f).coerceAtMost(days.size.toFloat())
                                    if (endFrac <= startFrac) null
                                    else arrayOf(
                                        DAY_W * startFrac + 1.dp,
                                        DAY_W * (endFrac - startFrac) - 2.dp,
                                        inOff >= 0,                 // left cap: checkIn visible
                                        outOff <= days.size - 1     // right cap: checkOut visible
                                    )
                                } else {
                                    // Full-day mode: band covers check-in (inclusive) to
                                    // check-out (exclusive). Last visible column = checkOut - 1.
                                    val lastNight = to.minusDays(1)
                                    val clampFrom = if (from.isBefore(dateStart)) dateStart else from
                                    val clampTo   = if (lastNight.isAfter(dateEnd)) dateEnd else lastNight
                                    if (clampTo.isBefore(clampFrom)) return@bandGeo null
                                    val startOff  = ChronoUnit.DAYS.between(dateStart, clampFrom).toInt()
                                    val endOff    = ChronoUnit.DAYS.between(dateStart, clampTo).toInt()
                                    arrayOf(
                                        DAY_W * startOff + 1.dp,
                                        DAY_W * (endOff - startOff + 1) - 2.dp,
                                        from == clampFrom,
                                        lastNight == clampTo  // right cap: last night is within view
                                    )
                                }
                            }

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(rowH)
                                    .pointerInput(room.id, DAY_W, days.size, halfShift) {
                                        fun toIdx(x: Float): Int {
                                            val raw = if (halfShift)
                                                ((x / DAY_W.toPx()) - 0.5f).toInt()
                                            else
                                                (x / DAY_W.toPx()).toInt()
                                            return raw.coerceIn(0, days.size - 1)
                                        }
                                        detectHorizontalDragGestures(
                                            onDragStart = { offset ->
                                                dragState = TimelineDragState(room, toIdx(offset.x), toIdx(offset.x))
                                            },
                                            onHorizontalDrag = { change, _ ->
                                                change.consume()
                                                dragState = dragState?.copy(endIdx = toIdx(change.position.x))
                                            },
                                            onDragEnd = {
                                                dragState?.let { ds ->
                                                    val s       = minOf(ds.startIdx, ds.endIdx)
                                                    val e       = maxOf(ds.startIdx, ds.endIdx)
                                                    // checkout = day after last selected square → nights = e-s+1
                                                    val checkIn  = days[s]
                                                    val checkOut = days[e].plusDays(1)
                                                    val noConflict = !hasReservationConflict(
                                                        reservations, ds.room.id,
                                                        checkIn.toString(), checkOut.toString()
                                                    )
                                                    if (noConflict) onCreateRequest(ds.room, checkIn, checkOut)
                                                }
                                                dragState = null
                                            },
                                            onDragCancel = { dragState = null }
                                        )
                                    }
                            ) {
                                // Background stripes
                                Row(Modifier.fillMaxWidth().height(rowH)) {
                                    days.forEach { day ->
                                        val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
                                        val isToday   = day == today
                                        Box(Modifier.width(DAY_W).fillMaxHeight()
                                            .background(when {
                                                isToday   -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                isWeekend -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                else      -> Color.Transparent
                                            })
                                            .drawBehind {
                                                drawLine(divColor, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), strokeWidth = 1f)
                                            })
                                    }
                                }
                                // Lane divider line when split
                                if (showCancelled && !isExpanded) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).offset(y = LANE_H)
                                        .background(divColor.copy(alpha = 0.5f)))
                                }
                                // Room block bands
                                val roomBlocksInView = (blocksByRoom[room.id] ?: emptyList()).filter { block ->
                                    !LocalDate.parse(block.toDate).isBefore(dateStart) &&
                                    !LocalDate.parse(block.fromDate).isAfter(dateEnd)
                                }
                                roomBlocksInView.forEach { block ->
                                    val from = LocalDate.parse(block.fromDate)
                                    val to   = LocalDate.parse(block.toDate)
                                    val geo  = bandGeo(from, to) ?: return@forEach
                                    val bLeft      = geo[0] as Dp
                                    val bWidth     = geo[1] as Dp
                                    val leftRound  = geo[2] as Boolean
                                    val rightRound = geo[3] as Boolean
                                    val blockBandH = if (isExpanded) EXPANDED_BAND_H else BAND_H
                                    val bTop = if (showCancelled && !isExpanded)
                                        LANE_H + (LANE_H - blockBandH) / 2
                                    else
                                        (rowH - blockBandH) / 2
                                    Box(
                                        Modifier
                                            .offset(x = bLeft, y = bTop)
                                            .width(bWidth).height(blockBandH)
                                            .clip(RoundedCornerShape(
                                                topStart    = if (leftRound)  4.dp else 0.dp,
                                                bottomStart = if (leftRound)  4.dp else 0.dp,
                                                topEnd      = if (rightRound) 4.dp else 0.dp,
                                                bottomEnd   = if (rightRound) 4.dp else 0.dp
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
                                // Cancelled/no-show bands — top lane when split, otherwise centered
                                cancelledRes.forEach { res ->
                                    val from = LocalDate.parse(res.checkInDate)
                                    val to   = LocalDate.parse(res.checkOutDate)
                                    val geo  = bandGeo(from, to) ?: return@forEach
                                    val bLeft      = geo[0] as Dp
                                    val bWidth     = geo[1] as Dp
                                    val leftRound  = geo[2] as Boolean
                                    val rightRound = geo[3] as Boolean
                                    val bandH      = BAND_H
                                    val bTop       = (LANE_H - bandH) / 2
                                    val (bg, fg)   = STATUS_PALETTE[res.status] ?: (Color(0xFFE0E0E0) to Color(0xFF424242))
                                    val dotColorC  = paymentDotColor(res.totalAmount, res.paidAmount)
                                    val showDpC    = dpPending(res) && leftRound
                                    Box(
                                        Modifier
                                            .offset(x = bLeft, y = bTop)
                                            .width(bWidth).height(bandH)
                                            .clip(RoundedCornerShape(
                                                topStart    = if (leftRound)  4.dp else 0.dp,
                                                bottomStart = if (leftRound)  4.dp else 0.dp,
                                                topEnd      = if (rightRound) 4.dp else 0.dp,
                                                bottomEnd   = if (rightRound) 4.dp else 0.dp
                                            ))
                                            .background(bg)
                                            .clickable { onEditRequest(res) }
                                    ) {
                                        if (showDpC) {
                                            Box(Modifier.align(Alignment.CenterStart).padding(start = 2.dp)
                                                .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE65100))
                                                .padding(horizontal = 2.dp, vertical = 1.dp)) {
                                                Text("DP", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp), color = Color.White)
                                            }
                                        }
                                        Text(
                                            res.guestName,
                                            modifier = Modifier.padding(start = if (showDpC) 18.dp else 4.dp,
                                                end = if (dotColorC != null) 12.dp else 4.dp)
                                                .align(Alignment.CenterStart),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        if (dotColorC != null && rightRound) {
                                            Box(Modifier.align(Alignment.CenterEnd).padding(end = 3.dp)
                                                .size(6.dp).clip(CircleShape).background(dotColorC))
                                        }
                                    }
                                }
                                // Active reservation bands — bottom lane when split, otherwise centered
                                activeRes.forEach { res ->
                                    val from = LocalDate.parse(res.checkInDate)
                                    val to   = LocalDate.parse(res.checkOutDate)
                                    val geo  = bandGeo(from, to) ?: return@forEach
                                    val bLeft      = geo[0] as Dp
                                    val bWidth     = geo[1] as Dp
                                    val leftRound  = geo[2] as Boolean
                                    val rightRound = geo[3] as Boolean
                                    val bandH      = if (isExpanded) EXPANDED_BAND_H else BAND_H
                                    val bTop = when {
                                        isExpanded                   -> (rowH - bandH) / 2
                                        showCancelled                -> LANE_H + (LANE_H - bandH) / 2
                                        else                         -> (rowH - bandH) / 2
                                    }
                                    val (bg, fg) = STATUS_PALETTE[res.status] ?: (Color(0xFFE0E0E0) to Color(0xFF424242))
                                    val dotColorA = paymentDotColor(res.totalAmount, res.paidAmount)
                                    val showDpA   = dpPending(res) && leftRound
                                    val nights = remember(res.checkInDate, res.checkOutDate) {
                                        runCatching {
                                            ChronoUnit.DAYS.between(
                                                LocalDate.parse(res.checkInDate),
                                                LocalDate.parse(res.checkOutDate)
                                            ).toInt()
                                        }.getOrDefault(0)
                                    }
                                    Box(
                                        Modifier
                                            .offset(x = bLeft, y = bTop)
                                            .width(bWidth).height(bandH)
                                            .clip(RoundedCornerShape(
                                                topStart    = if (leftRound)  4.dp else 0.dp,
                                                bottomStart = if (leftRound)  4.dp else 0.dp,
                                                topEnd      = if (rightRound) 4.dp else 0.dp,
                                                bottomEnd   = if (rightRound) 4.dp else 0.dp
                                            ))
                                            .background(bg)
                                            .clickable { onEditRequest(res) }
                                    ) {
                                        if (!isExpanded) {
                                            if (showDpA) {
                                                Box(Modifier.align(Alignment.CenterStart).padding(start = 2.dp)
                                                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE65100))
                                                    .padding(horizontal = 2.dp, vertical = 1.dp)) {
                                                    Text("DP", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp), color = Color.White)
                                                }
                                            }
                                            Text(
                                                res.guestName,
                                                modifier = Modifier.padding(start = if (showDpA) 18.dp else 4.dp,
                                                    top = 4.dp,
                                                    end = if (dotColorA != null && rightRound) 12.dp else 4.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                            if (dotColorA != null && rightRound) {
                                                Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 3.dp)
                                                    .size(6.dp).clip(CircleShape).background(dotColorA))
                                            }
                                        } else {
                                            Column(
                                                Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(fg.copy(alpha = 0.2f))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            res.status.replace('_', ' '),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            color = fg
                                                        )
                                                    }
                                                    Text(
                                                        res.guestName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = fg, maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (dotColorA != null) {
                                                        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColorA))
                                                    }
                                                }
                                                // Check-in date + nights only
                                                Text(
                                                    "${res.checkInDate}  ($nights n.)",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = fg.copy(alpha = 0.85f),
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                                val people = "${formatAdults(res.adults)} adult${if (res.adults != 1.0) "s" else ""}"
                                                val amountStr = res.totalAmount?.let { " · ${"%.0f".format(it)} PLN" } ?: ""
                                                Text(
                                                    "$people$amountStr",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = fg.copy(alpha = 0.85f),
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                                if (res.requiresDownPayment) {
                                                    Text(
                                                        "DP: ${res.downPaymentAmount?.let { "${"%.0f".format(it)} PLN" } ?: "required"}",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        color = Color(0xFFE65100),
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(Modifier.weight(1f))
                                                TextButton(
                                                    onClick = { onEditRequest(res) },
                                                    modifier = Modifier.height(22.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                ) {
                                                    Text("Edit", style = MaterialTheme.typography.labelSmall, color = fg)
                                                }
                                            }
                                        }
                                    }
                                }
                                // Drag preview band
                                if (dragState?.room?.id == room.id) {
                                    val ds    = dragState!!
                                    val s     = minOf(ds.startIdx, ds.endIdx)
                                    val e     = maxOf(ds.startIdx, ds.endIdx)
                                    // Both modes: checkout = days[e]+1, so (e-s+1) nights visible
                                    // Half-shift shifts the left edge by +0.5 columns; width is the same
                                    val previewLeft  = if (halfShift) DAY_W * (s + 0.5f) + 1.dp else DAY_W * s + 1.dp
                                    val previewWidth = DAY_W * (e - s + 1) - 2.dp
                                    val bTop  = when {
                                        showCancelled && !isExpanded -> LANE_H + (LANE_H - BAND_H) / 2
                                        else -> (rowH - BAND_H) / 2
                                    }
                                    val dragConflict = hasReservationConflict(
                                        reservations, room.id,
                                        days[s].toString(), days[e].plusDays(1).toString()
                                    )
                                    val previewColor = if (dragConflict)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                    Box(
                                        Modifier
                                            .offset(x = previewLeft, y = bTop)
                                            .width(previewWidth).height(BAND_H)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(previewColor.copy(alpha = 0.3f))
                                            .border(1.dp, previewColor, RoundedCornerShape(4.dp))
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


// ─── Guest input section (search existing or fill in new) ────────────────────

@Composable
private fun GuestInputSection(
    guests: List<GuestDto>,
    selectedGuest: GuestDto?,
    onSelectedGuestChange: (GuestDto?) -> Unit,
    firstName: String,    onFirstNameChange: (String) -> Unit,
    lastName: String,     onLastNameChange: (String) -> Unit,
    countryCode: String,  onCountryCodeChange: (String) -> Unit,
    phoneNumber: String,  onPhoneNumberChange: (String) -> Unit
) {
    val suggestions = remember(firstName, lastName, guests) {
        val fn = firstName.trim().lowercase()
        val ln = lastName.trim().lowercase()
        if (fn.isBlank() && ln.isBlank()) emptyList()
        else guests.filter { g ->
            (fn.isEmpty() || g.firstName.lowercase().contains(fn)) &&
            (ln.isEmpty() || g.lastName.lowercase().contains(ln))
        }.take(3)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedGuest != null) {
            // Selected guest card
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${selectedGuest.firstName} ${selectedGuest.lastName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val phone = listOfNotNull(selectedGuest.countryCode?.let { "+$it" }, selectedGuest.phoneNumber).joinToString(" ").ifBlank { null }
                    val detail = listOfNotNull(phone, selectedGuest.nationality).joinToString(" · ")
                    if (detail.isNotBlank()) Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { onSelectedGuestChange(null) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Change", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        } else {
            // Input fields
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(firstName, onFirstNameChange, label = { Text("First name *") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(lastName,  onLastNameChange,  label = { Text("Last name *") },  singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = countryCode, onValueChange = onCountryCodeChange,
                    label = { Text("Code") },
                    placeholder = { Text("48") },
                    prefix = { Text("+") },
                    singleLine = true,
                    modifier = Modifier.width(90.dp)
                )
                OutlinedTextField(phoneNumber, onPhoneNumberChange, label = { Text("Phone number") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            // Suggestions
            if (suggestions.isNotEmpty()) {
                Text(
                    "Did you mean?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                suggestions.forEach { guest ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { onSelectedGuestChange(guest) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${guest.firstName} ${guest.lastName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            val gPhone = listOfNotNull(guest.countryCode?.let { "+$it" }, guest.phoneNumber).joinToString(" ").ifBlank { null }
                            val detail = listOfNotNull(gPhone, guest.nationality).joinToString(" · ")
                            if (detail.isNotBlank()) Text(
                                detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("Select", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
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
