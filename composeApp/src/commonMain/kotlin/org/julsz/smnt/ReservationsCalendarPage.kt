@file:OptIn(ExperimentalLayoutApi::class)

package org.julsz.smnt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
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
private enum class DragMode { Reservation, External, Block }

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
fun ReservationsCalendarPage(
    client: HttpClient,
    hotel: UserHotelRoleDto,
    centerDays: Int = 30,
    noShowAfterDays: Int = 14,
    autoCheckOutAfterDays: Int = 3,
    timelineDayWidth: Float = 40f,
    onTimelineDayWidthChange: (Float) -> Unit = {},
    timelineRowHeight: Float = 34f,
    onTimelineRowHeightChange: (Float) -> Unit = {},
    timelineLabelWidth: Float = 96f,
    onTimelineLabelWidthChange: (Float) -> Unit = {},
    timelineShowRoomType: Boolean = true,
    onCreateInvoice: ((ReservationDto) -> Unit)? = null,
    onViewInvoice: ((InvoiceDto) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    var reservations by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var rooms        by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var guests       by remember { mutableStateOf<List<GuestDto>>(emptyList()) }
    var priceRules   by remember { mutableStateOf<List<PriceRuleDto>>(emptyList()) }
    var roomBlocks   by remember { mutableStateOf<List<RoomBlockDto>>(emptyList()) }
    var holidays     by remember { mutableStateOf<List<HolidayDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    var displayYear    by remember { mutableStateOf(LocalDate.now().year) }
    var displayMonth   by remember { mutableStateOf(LocalDate.now().monthValue) }
    var currentView    by remember { mutableStateOf(ResView.Timeline) }
    var timelineScale  by remember { mutableStateOf(TimelineScale.Center) }
    var showNewDialog         by remember { mutableStateOf(false) }
    var showNewExternalDialog by remember { mutableStateOf(false) }
    var showBlockDialog       by remember { mutableStateOf(false) }
    var blockSubmitting       by remember { mutableStateOf(false) }
    var dragMode          by remember { mutableStateOf(DragMode.Reservation) }
    var optionRes         by remember { mutableStateOf<ReservationDto?>(null) }
    var editReservation   by remember { mutableStateOf<ReservationDto?>(null) }
    var editBlock         by remember { mutableStateOf<RoomBlockDto?>(null) }
    var paymentsRes       by remember { mutableStateOf<ReservationDto?>(null) }
    var prefillRoom       by remember { mutableStateOf<RoomDto?>(null) }
    var prefillCheckIn    by remember { mutableStateOf("") }
    var prefillCheckOut   by remember { mutableStateOf("") }
    var hiddenStatuses    by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editGuestRes      by remember { mutableStateOf<ReservationDto?>(null) }

    suspend fun loadData(showLoading: Boolean = true) {
        if (showLoading) loading = true
        try {
            reservations = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            rooms        = client.get("$BASE_URL/api/rooms?hotelId=${hotel.hotelId}").body<List<RoomDto>>()
                .filter { it.archivedAt == null }
            guests       = client.get("$BASE_URL/api/guests").body()
            priceRules   = client.get("$BASE_URL/api/hotels/${hotel.hotelId}/price-rules").body()
            roomBlocks   = client.get("$BASE_URL/api/room-blocks?hotelId=${hotel.hotelId}").body()
            holidays     = client.get("$BASE_URL/api/holidays?hotelId=${hotel.hotelId}").body()

            val cutoff = LocalDate.now().minusDays(noShowAfterDays.toLong())
            val autoNoShow = reservations.filter { r ->
                r.status in setOf("pending", "confirmed") &&
                r.source != "external" &&
                LocalDate.parse(r.checkInDate).isBefore(cutoff) &&
                r.paidAmount <= (r.downPaymentAmount ?: 0.0)
            }
            if (autoNoShow.isNotEmpty()) {
                autoNoShow.forEach { res ->
                    try {
                        client.put("$BASE_URL/api/reservations/${res.id}") {
                            contentType(ContentType.Application.Json)
                            setBody(UpdateReservationRequest(
                                roomId = res.roomId, guestId = res.guestId,
                                checkInDate = res.checkInDate, checkOutDate = res.checkOutDate,
                                status = "no_show", adults = res.adults,
                                totalAmount = res.totalAmount, description = res.description,
                                requiresDownPayment = res.requiresDownPayment,
                                downPaymentAmount = res.downPaymentAmount
                            ))
                        }
                    } catch (_: Exception) {}
                }
                reservations = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            }

            val checkOutCutoff = LocalDate.now().minusDays(autoCheckOutAfterDays.toLong())
            val autoCheckOut = reservations.filter { r ->
                r.status == "checked_in" &&
                LocalDate.parse(r.checkOutDate).isBefore(checkOutCutoff)
            }
            if (autoCheckOut.isNotEmpty()) {
                autoCheckOut.forEach { res ->
                    try {
                        client.put("$BASE_URL/api/reservations/${res.id}") {
                            contentType(ContentType.Application.Json)
                            setBody(UpdateReservationRequest(
                                roomId = res.roomId, guestId = res.guestId,
                                checkInDate = res.checkInDate, checkOutDate = res.checkOutDate,
                                status = "checked_out", adults = res.adults,
                                totalAmount = res.totalAmount, description = res.description,
                                requiresDownPayment = res.requiresDownPayment,
                                downPaymentAmount = res.downPaymentAmount
                            ))
                        }
                    } catch (_: Exception) {}
                }
                reservations = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            }
        } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) } finally { if (showLoading) loading = false }
    }

    LaunchedEffect(hotel.hotelId) { loadData() }

    Column(Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        val showNav = !(currentView == ResView.Timeline && timelineScale == TimelineScale.Center)
        val periodLabel = when {
            currentView == ResView.Calendar -> "$displayYear"
            timelineScale == TimelineScale.Center -> "±${centerDays}d"
            timelineScale == TimelineScale.Year -> "$displayYear"
            else -> "${Month.of(displayMonth).getDisplayName(java.time.format.TextStyle.SHORT, s.locale)} $displayYear"
        }
        val navBack: () -> Unit = {
            when {
                currentView == ResView.Calendar || timelineScale == TimelineScale.Year -> displayYear--
                displayMonth == 1 -> { displayMonth = 12; displayYear-- }
                else -> displayMonth--
            }
        }
        val navForward: () -> Unit = {
            when {
                currentView == ResView.Calendar || timelineScale == TimelineScale.Year -> displayYear++
                displayMonth == 12 -> { displayMonth = 1; displayYear++ }
                else -> displayMonth++
            }
        }
        val viewToggle: @Composable () -> Unit = {
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(ResView.Calendar to s.viewCalendar, ResView.Timeline to s.viewTimeline).forEach { (view, label) ->
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
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            val isNarrow = maxWidth < 560.dp
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Row 1: title + view toggle (+ nav on wide)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(s.reservationsTitle, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)
                        Text("${hotel.hotelName} · ${reservations.size} ${s.totalLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        viewToggle()
                        if (!isNarrow) {
                            Spacer(Modifier.width(4.dp))
                            if (showNav) {
                                IconButton(onClick = navBack, Modifier.size(32.dp)) {
                                    Text("◀", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Text(periodLabel, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            if (showNav) {
                                IconButton(onClick = navForward, Modifier.size(32.dp)) {
                                    Text("▶", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                // Row 2 (narrow only): navigation arrows + period label
                if (isNarrow) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showNav) {
                            IconButton(onClick = navBack, Modifier.size(32.dp)) {
                                Text("◀", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Text(periodLabel, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        if (showNav) {
                            IconButton(onClick = navForward, Modifier.size(32.dp)) {
                                Text("▶", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                // Last row: action buttons — horizontal scroll so they never stack vertically
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showNewDialog = true },
                        enabled = rooms.isNotEmpty(),
                        contentPadding = if (isNarrow) PaddingValues(horizontal = 8.dp, vertical = 4.dp) else ButtonDefaults.ContentPadding
                    ) {
                        Text(s.newReservationBtn,
                            style = if (isNarrow) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = { showNewExternalDialog = true },
                        enabled = rooms.isNotEmpty(),
                        contentPadding = if (isNarrow) PaddingValues(horizontal = 8.dp, vertical = 4.dp) else ButtonDefaults.ContentPadding
                    ) {
                        Text(s.newExternalBtn,
                            style = if (isNarrow) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = { showBlockDialog = true },
                        enabled = rooms.isNotEmpty(),
                        contentPadding = if (isNarrow) PaddingValues(horizontal = 8.dp, vertical = 4.dp) else ButtonDefaults.ContentPadding
                    ) {
                        Text(s.blockRoomBtn,
                            style = if (isNarrow) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val visibleReservations = remember(reservations, hiddenStatuses) {
                if (hiddenStatuses.isEmpty()) reservations
                else reservations.filter { it.status !in hiddenStatuses }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (currentView) {
                ResView.Calendar -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    (1..12).forEach { m ->
                        item(key = "month_${displayYear}_$m") {
                            ResMonthCalendar(
                                year         = displayYear,
                                month        = m,
                                reservations = visibleReservations,
                                holidays     = holidays,
                                onResClick   = { optionRes = it }
                            )
                            if (m < 12) HorizontalDivider(Modifier.padding(vertical = 16.dp))
                            else Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                ResView.Timeline -> ReservationsTimelineView(
                    rooms            = rooms.sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number })),
                    reservations     = reservations,
                    blocks           = roomBlocks,
                    holidays         = holidays,
                    year             = displayYear,
                    month            = displayMonth,
                    scale            = timelineScale,
                    onScaleChange    = { timelineScale = it },
                    centerDays       = centerDays,
                    onEditRequest    = { optionRes = it },
                    onBlockClick     = { editBlock = it },
                    dragMode         = dragMode,
                    onDragModeChange = { dragMode = it },
                    hiddenStatuses         = hiddenStatuses,
                    onHiddenStatusesChange = { hiddenStatuses = it },
                    dayWidthPx          = timelineDayWidth,
                    onDayWidthPxChange  = onTimelineDayWidthChange,
                    rowHeightPx         = timelineRowHeight,
                    onRowHeightPxChange = onTimelineRowHeightChange,
                    labelWidthPx        = timelineLabelWidth,
                    onLabelWidthPxChange = onTimelineLabelWidthChange,
                    showRoomType        = timelineShowRoomType,
                    onBlockRequest   = { room, cin, cout ->
                        scope.launch {
                            try {
                                client.post("$BASE_URL/api/room-blocks") {
                                    contentType(ContentType.Application.Json)
                                    setBody(CreateRoomBlockRequest(
                                        roomId   = room.id,
                                        fromDate = cin.toString(),
                                        toDate   = cout.toString(),
                                        reason   = null
                                    ))
                                }
                                loadData(showLoading = false)
                            } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                        }
                    },
                    onCreateRequest  = { room, cin, cout ->
                        prefillRoom     = room
                        prefillCheckIn  = cin.toString()
                        prefillCheckOut = cout.toString()
                        showNewDialog   = true
                    },
                    onCreateExternalRequest = { room, cin, cout ->
                        prefillRoom     = room
                        prefillCheckIn  = cin.toString()
                        prefillCheckOut = cout.toString()
                        showNewExternalDialog = true
                    }
                )
                }
            }
        }
    }

    // ── New Reservation dialog ─────────────────────────────────────────────────
    if (showNewDialog) {
        NewReservationDialog(
            hotel           = hotel,
            rooms           = rooms,
            guests          = guests,
            reservations    = reservations,
            blocks          = roomBlocks,
            priceRules      = priceRules,
            prefillRoom     = prefillRoom,
            prefillCheckIn  = prefillCheckIn,
            prefillCheckOut = prefillCheckOut,
            onDismiss = {
                showNewDialog = false
                prefillRoom = null; prefillCheckIn = ""; prefillCheckOut = ""
            },
            onConfirm = { req, pendingAdjustments ->
                scope.launch {
                    try {
                        val response = client.post("$BASE_URL/api/reservations") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        if (!response.status.isSuccess()) {
                            snackbar.showSnackbar(
                                if (response.status == HttpStatusCode.Conflict) s.conflictError
                                else s.serverError(response.status)
                            )
                            return@launch
                        }
                        if (pendingAdjustments.isNotEmpty()) {
                            val created: ReservationDto = response.body()
                            pendingAdjustments.forEach { adj ->
                                try {
                                    client.post("$BASE_URL/api/reservations/${created.id}/price-adjustments") {
                                        contentType(ContentType.Application.Json)
                                        setBody(adj)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        showNewDialog = false
                        prefillRoom = null; prefillCheckIn = ""; prefillCheckOut = ""
                        loadData(showLoading = false)
                    } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
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
                } catch (e: Exception) {
                    scope.launch { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                    null
                }
            }
        )
    }

    // ── New External Reservation dialog ───────────────────────────────────────
    if (showNewExternalDialog) {
        NewExternalReservationDialog(
            hotel           = hotel,
            rooms           = rooms,
            guests          = guests,
            reservations    = reservations,
            blocks          = roomBlocks,
            prefillRoom     = prefillRoom,
            prefillCheckIn  = prefillCheckIn,
            prefillCheckOut = prefillCheckOut,
            onDismiss = {
                showNewExternalDialog = false
                prefillRoom = null; prefillCheckIn = ""; prefillCheckOut = ""
            },
            onConfirm    = { req ->
                scope.launch {
                    try {
                        val response = client.post("$BASE_URL/api/reservations") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        if (!response.status.isSuccess()) {
                            snackbar.showSnackbar(
                                if (response.status == HttpStatusCode.Conflict) s.conflictError
                                else s.serverError(response.status)
                            )
                            return@launch
                        }
                        showNewExternalDialog = false
                        prefillRoom = null; prefillCheckIn = ""; prefillCheckOut = ""
                        loadData(showLoading = false)
                    } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
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
                } catch (e: Exception) {
                    scope.launch { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                    null
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
            reservations = reservations,
            blocks     = roomBlocks,
            priceRules = priceRules,
            client     = client,
            onDismiss  = { editReservation = null },
            onConfirm  = { req ->
                scope.launch {
                    try {
                        val response = client.put("$BASE_URL/api/reservations/${res.id}") {
                            contentType(ContentType.Application.Json)
                            setBody(req)
                        }
                        if (!response.status.isSuccess()) {
                            snackbar.showSnackbar(
                                if (response.status == HttpStatusCode.Conflict) s.conflictError
                                else s.serverError(response.status)
                            )
                            return@launch
                        }
                        editReservation = null
                        loadData(showLoading = false)
                    } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                }
            },
            onDelete = {
                scope.launch {
                    try {
                        client.delete("$BASE_URL/api/reservations/${res.id}")
                        reservations = reservations.filter { it.id != res.id }
                        editReservation = null
                        loadData(showLoading = false)
                    } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
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
                } catch (e: Exception) {
                    scope.launch { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                    null
                }
            },
            onAdjustmentsChanged = { scope.launch { loadData(showLoading = false) } }
        )
    }

    // ── Reservation detail dialog ──────────────────────────────────────────────
    optionRes?.let { res ->
        ReservationDetailDialog(
            res       = res,
            rooms     = rooms,
            guests    = guests,
            client    = client,
            onDismiss = { optionRes = null },
            onEdit    = { editReservation = res; optionRes = null },
            onPayments = { paymentsRes = res; optionRes = null },
            onEditGuest = { editGuestRes = res; optionRes = null },
            onInvoice  = onCreateInvoice?.let { cb -> { cb(res); optionRes = null } },
            onOpenInvoice = { inv -> onViewInvoice?.invoke(inv); optionRes = null },
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
                        loadData(showLoading = false)
                    } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
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
            onPaymentsChanged = { scope.launch { loadData(showLoading = false) } },
            onViewInvoice     = onViewInvoice?.let { cb -> { inv -> paymentsRes = null; cb(inv) } }
        )
    }

    // ── Block Room dialog ──────────────────────────────────────────────────────
    if (showBlockDialog) {
        BlockRoomDialog(
            rooms        = rooms,
            reservations = reservations,
            isSubmitting = blockSubmitting,
            onDismiss    = { showBlockDialog = false; blockSubmitting = false },
            onConfirm    = { requests ->
                scope.launch {
                    blockSubmitting = true
                    try {
                        requests.forEach { req ->
                            client.post("$BASE_URL/api/room-blocks") {
                                contentType(ContentType.Application.Json)
                                setBody(req)
                            }
                        }
                        showBlockDialog = false
                        loadData(showLoading = false)
                    } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                    finally { blockSubmitting = false }
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
                    } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                }
            }
        )
    }

    // ── Edit Guest dialog ──────────────────────────────────────────────────────
    editGuestRes?.let { res ->
        val guest = guests.find { it.id == res.guestId }
        if (guest != null) {
            EditGuestDialog(
                guest     = guest,
                onDismiss = { editGuestRes = null },
                onConfirm = { req ->
                    scope.launch {
                        try {
                            val updated: GuestDto = client.put("$BASE_URL/api/guests/${guest.id}") {
                                contentType(ContentType.Application.Json)
                                setBody(req)
                            }.body()
                            guests = guests.map { if (it.id == updated.id) updated else it }
                            editGuestRes = null
                            loadData(showLoading = false)
                        } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                    }
                }
            )
        }
    }
}

// ─── Month grid ───────────────────────────────────────────────────────────────

@Composable
private fun ResMonthCalendar(
    year: Int,
    month: Int,
    reservations: List<ReservationDto>,
    holidays: List<HolidayDto> = emptyList(),
    onResClick: (ReservationDto) -> Unit
) {
    val s = LocalStrings.current
    val weeks = remember(year, month) { buildResWeeks(year, month) }
    val monthName = Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, s.locale)

    Column(Modifier.fillMaxWidth()) {
        Text("$monthName $year", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))

        Row(Modifier.fillMaxWidth()) {
            s.dowLabels.forEach { d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(d, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        weeks.forEach { week ->
            ResWeekRow(week, reservations, holidays, onResClick)
        }
    }
}

// ─── Week row ─────────────────────────────────────────────────────────────────

@Composable
private fun ResWeekRow(
    week: List<RCalDay>,
    reservations: List<ReservationDto>,
    holidays: List<HolidayDto> = emptyList(),
    onResClick: (ReservationDto) -> Unit
) {
    val weekStart = week.first().date
    val weekEnd   = week.last().date
    val today     = LocalDate.now()
    val holidayMap: Map<LocalDate, HolidayDto> = remember(holidays, weekStart, weekEnd) {
        buildMap {
            holidays.forEach { h ->
                val from = maxOf(java.time.LocalDate.parse(h.fromDate), weekStart)
                val to   = minOf(java.time.LocalDate.parse(h.toDate), weekEnd)
                if (!from.isAfter(to)) {
                    var d = from
                    while (!d.isAfter(to)) {
                        putIfAbsent(d, h)
                        d = d.plusDays(1)
                    }
                }
            }
        }
    }

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
                val isHoliday = cal.inMonth && cal.date in holidayMap
                Box(
                    Modifier.width(dayW).height(CELL_H)
                        .background(if (isHoliday) Color(0xFFFFF176).copy(alpha = 0.50f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
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
            val s = LocalStrings.current
            val noteHead = res.description?.takeIf { it.isNotBlank() }?.replace("\n", "; ")
            val label = buildString {
                append("${s.roomAbbr} ${res.roomNumber} · ${res.guestName}")
                if (noteHead != null) append(" · $noteHead")
            }
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

// ─── Shared price helpers ─────────────────────────────────────────────────────

private data class PriceSegment(
    val fromDate: LocalDate,
    val toDate: LocalDate,   // exclusive (= checkout of this segment)
    val rule: PriceRuleDto?
) {
    val nights: Int get() = ChronoUnit.DAYS.between(fromDate, toDate).toInt()
}

private fun buildPriceSegments(
    priceRules: List<PriceRuleDto>,
    roomId: Int,
    checkIn: String,
    checkOut: String
): List<PriceSegment> {
    val cin  = runCatching { LocalDate.parse(checkIn.trim())  }.getOrNull() ?: return emptyList()
    val cout = runCatching { LocalDate.parse(checkOut.trim()) }.getOrNull() ?: return emptyList()
    if (!cout.isAfter(cin)) return emptyList()
    val totalNights = ChronoUnit.DAYS.between(cin, cout).toInt()
    val roomRules   = priceRules.filter { it.roomId == roomId }

    fun ruleForDay(day: LocalDate): PriceRuleDto? = roomRules
        .filter { r ->
            !LocalDate.parse(r.fromDate).isAfter(day) &&
            !LocalDate.parse(r.toDate).isBefore(day) &&
            totalNights >= r.minNights &&
            (r.maxNights == null || totalNights <= r.maxNights!!)
        }
        .maxByOrNull { it.minNights }

    val segments = mutableListOf<PriceSegment>()
    var segStart    = cin
    var currentRule = ruleForDay(cin)
    var day         = cin.plusDays(1)
    while (day.isBefore(cout)) {
        val rule = ruleForDay(day)
        if (rule?.id != currentRule?.id) {
            segments += PriceSegment(segStart, day, currentRule)
            segStart    = day
            currentRule = rule
        }
        day = day.plusDays(1)
    }
    segments += PriceSegment(segStart, cout, currentRule)
    return segments
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

private fun hasBlockConflict(
    blocks: List<RoomBlockDto>,
    roomId: Int,
    checkIn: String,
    checkOut: String
): Boolean {
    val cin  = runCatching { LocalDate.parse(checkIn.trim())  }.getOrNull() ?: return false
    val cout = runCatching { LocalDate.parse(checkOut.trim()) }.getOrNull() ?: return false
    if (!cout.isAfter(cin)) return false
    return blocks.any { b ->
        b.roomId == roomId &&
        LocalDate.parse(b.fromDate).isBefore(cout) &&
        LocalDate.parse(b.toDate).isAfter(cin)
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
    client: HttpClient,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onPayments: () -> Unit,
    onEditGuest: () -> Unit,
    onInvoice: (() -> Unit)? = null,
    onOpenInvoice: ((InvoiceDto) -> Unit)? = null,
    onSaveDescription: (String?) -> Unit
) {
    val nights = remember(res.checkInDate, res.checkOutDate) {
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(res.checkInDate), LocalDate.parse(res.checkOutDate)).toInt() }.getOrDefault(0)
    }
    val room  = rooms.find { it.id == res.roomId }
    val guest = guests.find { it.id == res.guestId }
    var description       by remember(res.id) { mutableStateOf(res.description ?: "") }
    var editingNote       by remember(res.id) { mutableStateOf(false) }
    var reservationInvoice by remember(res.id) { mutableStateOf<InvoiceDto?>(null) }
    val s = LocalStrings.current

    LaunchedEffect(res.id) {
        try {
            val invoices = client.get("$BASE_URL/api/invoices?hotelId=${res.hotelId}").body<List<InvoiceDto>>()
            reservationInvoice = invoices.firstOrNull { it.reservationId == res.id }
        } catch (_: Exception) {}
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.reservationDetailTitle(res.id)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
                    if (g.blacklisted) {
                        Text(
                            s.blacklistedWarning,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                // Dates — shown prominently
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(s.checkIn, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(res.checkInDate, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text(s.checkOut, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(res.checkOutDate, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text(s.nightsLabel, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(nights.toString(), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                SummaryRow(s.roomDetailLabel, room?.let { "${s.roomLabel(it.number)} · ${it.typeName}" } ?: s.roomLabel(res.roomNumber))
                SummaryRow(s.adultsLabel, formatAdults(res.adults))
                SummaryRow(s.statusLabel, s.statusName(res.status))
                if (dpPending(res)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(s.downPmtLabel, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp))
                        Text(
                            res.downPaymentAmount?.let { "${"%.2f".format(it)} ${s.plnRequired}" } ?: s.plnRequired,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
                res.totalAmount?.let { total ->
                    SummaryRow(s.totalLabel, "${"%.2f".format(total)} PLN")
                    SummaryRow(s.paidLabel,  "${"%.2f".format(res.paidAmount)} PLN")
                    val remaining = total - res.paidAmount
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.remainingLabel, style = MaterialTheme.typography.labelSmall,
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

                reservationInvoice?.let { inv ->
                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.invoiceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp))
                        Text(
                            inv.invoiceNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenInvoice?.invoke(inv) }
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.notesLabel, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!editingNote) {
                        TextButton(
                            onClick = { editingNote = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(s.editNoteBtn, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (editingNote) {
                    val editScrollState = rememberScrollState()
                    val noteFieldState = rememberTextFieldState(description)
                    LaunchedEffect(noteFieldState.text) { description = noteFieldState.text.toString() }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 160.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    ) {
                        BasicTextField(
                            state = noteFieldState,
                            scrollState = editScrollState,
                            lineLimits = TextFieldLineLimits.MultiLine(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .padding(end = 8.dp),
                            decorator = { innerTextField ->
                                if (noteFieldState.text.isEmpty()) {
                                    Text(
                                        s.notesPlaceholder,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        AppVerticalScrollbar(
                            state    = editScrollState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        )
                    }
                } else {
                    val scrollState = rememberScrollState()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp, max = 160.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .padding(end = 8.dp)
                                .verticalScroll(scrollState)
                        ) {
                            if (description.isBlank()) {
                                Text(
                                    s.notesPlaceholder,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            } else {
                                Text(description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        AppVerticalScrollbar(
                            state    = scrollState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (editingNote) {
                    Button(
                        onClick = {
                            onSaveDescription(description.trim().ifBlank { null })
                            editingNote = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(s.saveNotesBtn) }
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(s.editReservationBtn) }
                OutlinedButton(onClick = onPayments, modifier = Modifier.fillMaxWidth()) { Text(s.managePaymentsBtn) }
                OutlinedButton(onClick = onEditGuest, modifier = Modifier.fillMaxWidth()) { Text(s.editGuestBtn) }
                onInvoice?.let { createAction ->
                    val inv = reservationInvoice
                    OutlinedButton(
                        onClick  = { if (inv == null) createAction() },
                        enabled  = inv == null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(s.createInvoiceBtn) }
                    if (inv != null) {
                        OutlinedButton(
                            onClick  = { onOpenInvoice?.invoke(inv) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(s.viewInvoiceBtn) }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(s.close) }
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
    onPaymentsChanged: () -> Unit = {},
    onViewInvoice: ((InvoiceDto) -> Unit)? = null
) {
    val scope    = rememberCoroutineScope()
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    var payments          by remember { mutableStateOf<List<PaymentDto>>(emptyList()) }
    var reservationInvoice by remember { mutableStateOf<InvoiceDto?>(null) }
    var loading  by remember { mutableStateOf(true) }

    // Add-payment form state
    var isDeposit             by remember { mutableStateOf(false) }
    var amountInput           by remember { mutableStateOf("") }
    var paidAtInput           by remember { mutableStateOf(LocalDate.now().toString()) }
    var notesInput            by remember { mutableStateOf("") }
    var receiptType           by remember { mutableStateOf("") }
    var receiptNumberInput    by remember { mutableStateOf("") }
    var useCustomInvoiceNumber by remember { mutableStateOf(false) }
    var showDatePicker        by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        try {
            payments = client.get("$BASE_URL/api/reservations/${res.id}/payments").body()
            val invoices = client.get("$BASE_URL/api/invoices?hotelId=${res.hotelId}").body<List<InvoiceDto>>()
            reservationInvoice = invoices.firstOrNull { it.reservationId == res.id }
        }
        catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
        loading = false
    }

    LaunchedEffect(res.id) { reload() }

    LaunchedEffect(reservationInvoice) {
        if (reservationInvoice != null && receiptType.isBlank()) {
            receiptType = "invoice"
        }
    }

    val totalPaid = remember(payments) { payments.sumOf { it.amount } }
    val remaining = res.totalAmount?.let { it - totalPaid }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.paymentsTitle(res.id)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary row
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    res.totalAmount?.let {
                        Column {
                            Text(s.totalLabel, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.2f".format(it)} PLN", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column {
                        Text(s.paidLabel, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.2f".format(totalPaid)} PLN", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    remaining?.let {
                        Column {
                            Text(s.remainingLabel, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.2f".format(it)} PLN", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (it > 0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (res.requiresDownPayment && res.downPaymentAmount != null) {
                    val dpCovered = totalPaid >= res.downPaymentAmount!!
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (dpCovered) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (dpCovered) "✓" else "⚠",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dpCovered) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            s.downPaymentNeeded("%.2f".format(res.downPaymentAmount)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dpCovered) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                HorizontalDivider()

                // Payments table
                if (loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else if (payments.isEmpty()) {
                    Text(s.noPayments,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    var expandedId by remember { mutableStateOf<Int?>(null) }
                    // Header
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(s.typeCol,   style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.3f))
                        Text(s.amountCol, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                        Text(s.dateCol,   style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.1f))
                        Text(s.docCol,    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.5f))
                        Spacer(Modifier.width(28.dp))
                    }
                    payments.forEach { p ->
                        val expanded = expandedId == p.id
                        val docLabel = s.docLabel(p.receiptType, p.receiptNumber)
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { expandedId = if (expanded) null else p.id }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (p.isDeposit) s.downPaymentName else s.paymentName,
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
                                val inv = reservationInvoice
                                val isAppInvoiceLink = p.receiptType == "invoice" && inv != null && onViewInvoice != null
                                Text(docLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1.5f).then(
                                        if (isAppInvoiceLink) Modifier.clickable { onViewInvoice!!(inv!!) } else Modifier
                                    ),
                                    color = if (p.receiptType != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                client.delete("$BASE_URL/api/payments/${p.id}")
                                                reload()
                                                // Revert to pending if down payment is no longer covered
                                                if (res.status == "confirmed" && res.requiresDownPayment) {
                                                    val required = res.downPaymentAmount
                                                    val newTotalPaid = payments.sumOf { it.amount }
                                                    if (required != null && newTotalPaid < required) {
                                                        try {
                                                            client.put("$BASE_URL/api/reservations/${res.id}") {
                                                                contentType(ContentType.Application.Json)
                                                                setBody(UpdateReservationRequest(
                                                                    roomId = res.roomId, guestId = res.guestId,
                                                                    checkInDate = res.checkInDate, checkOutDate = res.checkOutDate,
                                                                    status = "pending", adults = res.adults,
                                                                    totalAmount = res.totalAmount, description = res.description,
                                                                    requiresDownPayment = res.requiresDownPayment,
                                                                    downPaymentAmount = res.downPaymentAmount
                                                                ))
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                                onPaymentsChanged()
                                            } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
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
                                    SummaryRow(s.typeRow,   if (p.isDeposit) s.downPaymentName else s.paymentName)
                                    SummaryRow(s.amountRow, "${"%.2f".format(p.amount)} ${p.currency ?: "PLN"}")
                                    SummaryRow(s.dateRow,   p.paidAt ?: "—")
                                    if (!p.notes.isNullOrBlank()) SummaryRow(s.notesLabel, p.notes!!)
                                    val rType = p.receiptType
                                    if (rType != null) {
                                        SummaryRow(s.docTypeRow, rType.replaceFirstChar { it.uppercaseChar() })
                                        val appInv = reservationInvoice
                                        if (rType == "invoice" && appInv != null && onViewInvoice != null) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(s.docNumberRow, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.width(72.dp))
                                                Text(appInv.invoiceNumber,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.clickable { onViewInvoice(appInv) })
                                            }
                                        } else {
                                            SummaryRow(s.docNumberRow, p.receiptNumber ?: "—")
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }


                HorizontalDivider()
                Text(s.addPaymentSection, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)

                // Type toggle
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    listOf(false to s.paymentName, true to s.downPaymentName).forEach { (deposit, label) ->
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
                        label = { Text(s.amountFieldLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = paidAtInput,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s.dateFieldLabel) },
                        trailingIcon = {
                            TextButton(onClick = { showDatePicker = true },
                                contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(s.pick, style = MaterialTheme.typography.labelSmall)
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
                    AppDatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                dpState.selectedDateMillis?.let { millis ->
                                    paidAtInput = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneOffset.UTC).toLocalDate().toString()
                                }
                                showDatePicker = false
                            }) { Text(s.ok) }
                        },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(s.cancel) } }
                    ) { DatePicker(state = dpState) }
                }
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text(s.notesLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Receipt/invoice type toggle
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    listOf("" to s.nothing, "receipt" to s.receiptLabel, "invoice" to s.invoiceLabel).forEach { (value, label) ->
                        val sel = receiptType == value
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                .clickable { receiptType = value; receiptNumberInput = ""; useCustomInvoiceNumber = false }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium,
                                color = if (sel) MaterialTheme.colorScheme.onSecondary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (receiptType == "receipt") {
                    OutlinedTextField(
                        value = receiptNumberInput,
                        onValueChange = { receiptNumberInput = it },
                        label = { Text(s.receiptNumberLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (receiptType == "invoice") {
                    Row(
                        Modifier.fillMaxWidth().clickable { useCustomInvoiceNumber = !useCustomInvoiceNumber },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = useCustomInvoiceNumber, onCheckedChange = { useCustomInvoiceNumber = it })
                        Text(s.customInvoiceNumberLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (useCustomInvoiceNumber) {
                        OutlinedTextField(
                            value = receiptNumberInput,
                            onValueChange = { receiptNumberInput = it },
                            label = { Text(s.invoiceNumberLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val appInv = reservationInvoice
                        if (appInv != null) {
                            LaunchedEffect(appInv.invoiceNumber) { receiptNumberInput = appInv.invoiceNumber }
                            Surface(
                                shape    = RoundedCornerShape(6.dp),
                                color    = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(s.invoiceNumberLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Text(appInv.invoiceNumber,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                    if (onViewInvoice != null) {
                                        TextButton(onClick = { onViewInvoice(appInv) }) {
                                            Text(s.viewInvoiceBtn)
                                        }
                                    }
                                }
                            }
                        } else {
                            LaunchedEffect(Unit) { receiptNumberInput = "" }
                            Surface(
                                shape    = RoundedCornerShape(6.dp),
                                color    = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    s.noInvoiceForReservation,
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                                // Auto-confirm if pending down payment is now covered
                                if (res.status == "pending" && res.requiresDownPayment) {
                                    val required = res.downPaymentAmount
                                    val newTotalPaid = payments.sumOf { it.amount }
                                    if (required != null && newTotalPaid >= required) {
                                        try {
                                            client.put("$BASE_URL/api/reservations/${res.id}") {
                                                contentType(ContentType.Application.Json)
                                                setBody(UpdateReservationRequest(
                                                    roomId = res.roomId, guestId = res.guestId,
                                                    checkInDate = res.checkInDate, checkOutDate = res.checkOutDate,
                                                    status = "confirmed", adults = res.adults,
                                                    totalAmount = res.totalAmount, description = res.description,
                                                    requiresDownPayment = res.requiresDownPayment,
                                                    downPaymentAmount = res.downPaymentAmount
                                                ))
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                                onPaymentsChanged()
                            } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                        }
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(s.addPaymentBtn) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close) } }
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
    blocks: List<RoomBlockDto>,
    priceRules: List<PriceRuleDto>,
    prefillRoom: RoomDto? = null,
    prefillCheckIn: String = "",
    prefillCheckOut: String = "",
    onDismiss: () -> Unit,
    onConfirm: (CreateReservationRequest, List<CreatePriceAdjustmentRequest>) -> Unit,
    onCreateGuest: suspend (CreateGuestRequest) -> GuestDto?
) {
    var selectedRoom    by remember { mutableStateOf(prefillRoom ?: rooms.firstOrNull()) }
    var roomExpanded    by remember { mutableStateOf(false) }
    var checkIn   by remember { mutableStateOf(prefillCheckIn) }
    var checkOut  by remember { mutableStateOf(prefillCheckOut) }
    var adults    by remember { mutableStateOf(selectedRoom?.maxGuests?.toString() ?: "1") }
    var requiresDownPayment    by remember { mutableStateOf(false) }
    var downPaymentAmountInput by remember { mutableStateOf("") }
    var guestFirstName   by remember { mutableStateOf("") }
    var guestLastName    by remember { mutableStateOf("") }
    var guestCountryCode by remember { mutableStateOf("") }
    var guestPhoneNumber by remember { mutableStateOf("") }
    var selectedGuest    by remember { mutableStateOf<GuestDto?>(null) }
    var pendingAdjustments by remember { mutableStateOf<List<CreatePriceAdjustmentRequest>>(emptyList()) }
    var adjAmountInput   by remember { mutableStateOf("") }
    var adjDescInput     by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current

    LaunchedEffect(selectedRoom?.id) {
        selectedRoom?.let { adults = it.maxGuests.toString() }
    }
    val segments = remember(selectedRoom?.id, checkIn, checkOut, priceRules) {
        buildPriceSegments(priceRules, selectedRoom?.id ?: -1, checkIn, checkOut)
    }
    var segmentPpns by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(segments) {
        segmentPpns = segments.map { it.rule?.pricePerPersonPerNight?.let { p -> "%.2f".format(p) } ?: "" }
    }
    val totalGuests = adults.toDoubleOrNull() ?: 1.0
    val computedTotal = segments.indices
        .mapNotNull { i -> segmentPpns.getOrNull(i)?.toDoubleOrNull()?.let { it * segments[i].nights * totalGuests } }
        .takeIf { it.size == segments.size && segments.isNotEmpty() }
        ?.sum()
    val adjustmentsSum = pendingAdjustments.sumOf { it.amount }
    val guestReady = selectedGuest != null || (guestFirstName.isNotBlank() && guestLastName.isNotBlank())
    val conflict = remember(selectedRoom?.id, checkIn, checkOut, reservations, blocks) {
        selectedRoom != null && checkIn.isNotBlank() && checkOut.isNotBlank() &&
        (hasReservationConflict(reservations, selectedRoom!!.id, checkIn, checkOut) ||
         hasBlockConflict(blocks, selectedRoom!!.id, checkIn, checkOut))
    }
    val valid = selectedRoom != null && guestReady &&
        checkIn.isNotBlank() && checkOut.isNotBlank() && adults.toDoubleOrNull() != null && !conflict

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.newReservationTitle) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = { roomExpanded = it }) {
                    OutlinedTextField(
                        value = selectedRoom?.let { "${s.roomLabel(it.number)} · ${it.typeName}" } ?: s.selectRoomHint,
                        onValueChange = {}, readOnly = true, label = { Text(s.roomFieldLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
                    )
                    ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { roomExpanded = false }) {
                        rooms.forEach { room ->
                            DropdownMenuItem(
                                text = { Text("${s.roomLabel(room.number)} · ${room.typeName} (max ${room.maxGuests})") },
                                onClick = { selectedRoom = room; roomExpanded = false }
                            )
                        }
                    }
                }
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
                ResDateRangePickerFields(
                    checkInLabel = s.checkInLabel, checkIn = checkIn, onCheckInChange = { checkIn = it },
                    checkOutLabel = s.checkOutLabel, checkOut = checkOut, onCheckOutChange = { checkOut = it }
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
                        Text(s.roomAlreadyReserved,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                OutlinedTextField(adults, { adults = it }, label = { Text(s.adultsLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                PriceBreakdownSection(
                    segments     = segments,
                    segmentPpns  = segmentPpns,
                    onPpnChange  = { i, v -> segmentPpns = segmentPpns.toMutableList().also { it[i] = v } },
                    totalGuests  = totalGuests
                )
                // ── Price Adjustments ─────────────────────────────────────────
                HorizontalDivider()
                Text(s.priceAdjustmentsTitle, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (pendingAdjustments.isEmpty()) {
                            Text(s.noAdjustments, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            pendingAdjustments.forEachIndexed { idx, adj ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("%.2f PLN".format(adj.amount),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (adj.amount < 0) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.primary)
                                        if (!adj.description.isNullOrBlank()) {
                                            Text(
                                                adj.description!!, style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(
                                        onClick = { pendingAdjustments = pendingAdjustments.toMutableList().also { it.removeAt(idx) } },
                                        modifier = Modifier.size(28.dp)
                                    ) { Text("×", style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = adjAmountInput, onValueChange = { adjAmountInput = it },
                                label = { Text(s.adjustmentAmountLabel, style = MaterialTheme.typography.labelSmall) },
                                singleLine = true, modifier = Modifier.widthIn(min = 90.dp, max = 130.dp)
                            )
                            OutlinedTextField(
                                value = adjDescInput, onValueChange = { adjDescInput = it },
                                label = { Text(s.adjustmentDescriptionLabel, style = MaterialTheme.typography.labelSmall) },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val amount = adjAmountInput.toDoubleOrNull() ?: return@Button
                                    pendingAdjustments = pendingAdjustments + CreatePriceAdjustmentRequest(
                                        amount = amount,
                                        description = adjDescInput.trim().ifBlank { null }
                                    )
                                    adjAmountInput = ""; adjDescInput = ""
                                },
                                enabled = adjAmountInput.toDoubleOrNull() != null
                            ) { Text(s.add) }
                        }
                        if (pendingAdjustments.isNotEmpty() && computedTotal != null) {
                            HorizontalDivider(thickness = 0.5.dp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.segmentsBaseTotal, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${"%.2f".format(computedTotal)} PLN", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.adjustmentsTotal, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("%+.2f PLN".format(adjustmentsSum), style = MaterialTheme.typography.labelSmall,
                                    color = if (adjustmentsSum < 0) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.effectiveTotalLabel, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("${"%.2f".format(computedTotal + adjustmentsSum)} PLN",
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { requiresDownPayment = !requiresDownPayment },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = requiresDownPayment, onCheckedChange = { requiresDownPayment = it })
                    Text(s.requiresDownPayment, style = MaterialTheme.typography.bodyMedium)
                }
                if (requiresDownPayment) {
                    OutlinedTextField(
                        value = downPaymentAmountInput,
                        onValueChange = { downPaymentAmountInput = it },
                        label = { Text(s.downPaymentAmountLabel) },
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
                        val builtSegments = if (segments.size == segmentPpns.size && segments.isNotEmpty()) {
                            segments.indices.mapNotNull { i ->
                                val ppn = segmentPpns.getOrNull(i)?.toDoubleOrNull() ?: return@mapNotNull null
                                CreatePriceSegmentRequest(
                                    fromDate               = segments[i].fromDate.toString(),
                                    toDate                 = segments[i].toDate.toString(),
                                    pricePerPersonPerNight = ppn,
                                    adults                 = totalGuests,
                                    currency               = "PLN",
                                    priceRuleId            = segments[i].rule?.id
                                )
                            }.takeIf { it.size == segments.size }
                        } else null
                        onConfirm(
                            CreateReservationRequest(
                                hotelId = hotel.hotelId, roomId = selectedRoom!!.id, guestId = guestId,
                                checkInDate = checkIn.trim(), checkOutDate = checkOut.trim(),
                                status = if (requiresDownPayment) "pending" else "confirmed",
                                adults = adults.trim().toDoubleOrNull() ?: 1.0,
                                totalAmount = computedTotal?.let { it + adjustmentsSum } ?: computedTotal,
                                requiresDownPayment = requiresDownPayment,
                                downPaymentAmount   = if (requiresDownPayment) downPaymentAmountInput.toDoubleOrNull() else null,
                                priceSegments       = builtSegments ?: emptyList()
                            ),
                            pendingAdjustments
                        )
                    }
                }, enabled = valid
            ) { Text(s.create) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

// ─── New External Reservation dialog ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewExternalReservationDialog(
    hotel: UserHotelRoleDto,
    rooms: List<RoomDto>,
    guests: List<GuestDto>,
    reservations: List<ReservationDto>,
    blocks: List<RoomBlockDto>,
    prefillRoom: RoomDto? = null,
    prefillCheckIn: String = "",
    prefillCheckOut: String = "",
    onDismiss: () -> Unit,
    onConfirm: (CreateReservationRequest) -> Unit,
    onCreateGuest: suspend (CreateGuestRequest) -> GuestDto?
) {
    var selectedRoom     by remember { mutableStateOf(prefillRoom ?: rooms.firstOrNull()) }
    var roomExpanded     by remember { mutableStateOf(false) }
    var statusExpanded   by remember { mutableStateOf(false) }
    var checkIn          by remember { mutableStateOf(prefillCheckIn) }
    var checkOut         by remember { mutableStateOf(prefillCheckOut) }
    var adults           by remember { mutableStateOf(selectedRoom?.maxGuests?.toString() ?: "1") }
    var status           by remember { mutableStateOf("confirmed") }
    var totalAmountInput by remember { mutableStateOf("") }
    var externalRefInput by remember { mutableStateOf("") }
    var guestFirstName   by remember { mutableStateOf("") }
    var guestLastName    by remember { mutableStateOf("") }
    var guestCountryCode by remember { mutableStateOf("") }
    var guestPhoneNumber by remember { mutableStateOf("") }
    var selectedGuest    by remember { mutableStateOf<GuestDto?>(null) }
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current

    LaunchedEffect(selectedRoom?.id) {
        selectedRoom?.let { adults = it.maxGuests.toString() }
    }

    val conflict = remember(selectedRoom?.id, checkIn, checkOut, reservations, blocks) {
        selectedRoom != null && checkIn.isNotBlank() && checkOut.isNotBlank() &&
        (hasReservationConflict(reservations, selectedRoom!!.id, checkIn, checkOut) ||
         hasBlockConflict(blocks, selectedRoom!!.id, checkIn, checkOut))
    }
    val guestReady = selectedGuest != null || (guestFirstName.isNotBlank() && guestLastName.isNotBlank())
    val valid = selectedRoom != null && guestReady &&
        checkIn.isNotBlank() && checkOut.isNotBlank() && adults.toDoubleOrNull() != null && !conflict

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.newExternalReservationTitle) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Booking.com",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(s.externalSourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                }
                ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = { roomExpanded = it }) {
                    OutlinedTextField(
                        value = selectedRoom?.let { "${s.roomLabel(it.number)} · ${it.typeName}" } ?: s.selectRoomHint,
                        onValueChange = {}, readOnly = true, label = { Text(s.roomFieldLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
                    )
                    ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { roomExpanded = false }) {
                        rooms.forEach { room ->
                            DropdownMenuItem(
                                text = { Text("${s.roomLabel(room.number)} · ${room.typeName} (max ${room.maxGuests})") },
                                onClick = { selectedRoom = room; roomExpanded = false }
                            )
                        }
                    }
                }
                GuestInputSection(
                    guests = guests, selectedGuest = selectedGuest,
                    onSelectedGuestChange = { selectedGuest = it },
                    firstName = guestFirstName, onFirstNameChange = { guestFirstName = it; selectedGuest = null },
                    lastName = guestLastName, onLastNameChange = { guestLastName = it; selectedGuest = null },
                    countryCode = guestCountryCode, onCountryCodeChange = { guestCountryCode = it },
                    phoneNumber = guestPhoneNumber, onPhoneNumberChange = { guestPhoneNumber = it }
                )
                HorizontalDivider()
                ResDateRangePickerFields(
                    checkInLabel = s.checkInLabel, checkIn = checkIn, onCheckInChange = { checkIn = it },
                    checkOutLabel = s.checkOutLabel, checkOut = checkOut, onCheckOutChange = { checkOut = it }
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
                        Text(s.roomAlreadyReserved, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = adults, onValueChange = { adults = it },
                        label = { Text(s.adultsLabel) }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it },
                        modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = status.replace('_', ' '), onValueChange = {}, readOnly = true,
                            label = { Text(s.statusLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
                        )
                        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                            RESERVATION_STATUSES.forEach { code ->
                                DropdownMenuItem(text = { Text(s.statusName(code)) },
                                    onClick = { status = code; statusExpanded = false })
                            }
                        }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = totalAmountInput, onValueChange = { totalAmountInput = it },
                    label = { Text(s.bookingTotalLabel) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = externalRefInput, onValueChange = { externalRefInput = it },
                    label = { Text(s.bookingRefLabel) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
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
                            hotelId      = hotel.hotelId,
                            roomId       = selectedRoom!!.id,
                            guestId      = guestId,
                            checkInDate  = checkIn.trim(),
                            checkOutDate = checkOut.trim(),
                            status       = status,
                            adults       = adults.trim().toDoubleOrNull() ?: 1.0,
                            totalAmount  = totalAmountInput.toDoubleOrNull(),
                            source       = "external",
                            sourceName   = "Booking.com",
                            externalRef  = externalRefInput.trim().ifBlank { null }
                        ))
                    }
                }, enabled = valid
            ) { Text(s.create) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
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
    blocks: List<RoomBlockDto>,
    priceRules: List<PriceRuleDto>,
    client: HttpClient,
    onDismiss: () -> Unit,
    onConfirm: (UpdateReservationRequest) -> Unit,
    onDelete: () -> Unit,
    onCreateGuest: suspend (CreateGuestRequest) -> GuestDto?,
    onAdjustmentsChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val s = LocalStrings.current
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
    val isExternal       = existing.source == "external"
    var externalRefInput    by remember { mutableStateOf(existing.externalRef ?: "") }
    var externalTotalInput  by remember { mutableStateOf(existing.totalAmount?.let { "%.2f".format(it) } ?: "") }
    var adjustments by remember(existing.id) { mutableStateOf(existing.priceAdjustments) }
    var adjAmountInput by remember { mutableStateOf("") }
    var adjDescInput   by remember { mutableStateOf("") }

    val totalGuests = adults.toDoubleOrNull() ?: 1.0
    // Use baked segments when room+dates haven't changed and segments exist
    val useBaked = existing.priceSegments.isNotEmpty() &&
        selectedRoom?.id == existing.roomId &&
        checkIn == existing.checkInDate &&
        checkOut == existing.checkOutDate
    val segments: List<PriceSegment> = remember(selectedRoom?.id, checkIn, checkOut, priceRules, useBaked) {
        if (useBaked) {
            existing.priceSegments.map { seg ->
                PriceSegment(
                    fromDate = LocalDate.parse(seg.fromDate),
                    toDate   = LocalDate.parse(seg.toDate),
                    rule     = seg.priceRuleId?.let { rId -> priceRules.find { it.id == rId } }
                )
            }
        } else {
            buildPriceSegments(priceRules, selectedRoom?.id ?: -1, checkIn, checkOut)
        }
    }
    var segmentPpns by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(segments, useBaked) {
        segmentPpns = if (useBaked) {
            existing.priceSegments.map { "%.2f".format(it.pricePerPersonPerNight) }
        } else {
            segments.map { it.rule?.pricePerPersonPerNight?.let { p -> "%.2f".format(p) } ?: "" }
        }
    }
    // bakedPpns: the ppn saved at booking time, for change detection
    val bakedPpns: List<Double?> = if (useBaked)
        existing.priceSegments.map { it.pricePerPersonPerNight }
    else
        emptyList()
    val computedTotal = segments.indices
        .mapNotNull { i -> segmentPpns.getOrNull(i)?.toDoubleOrNull()?.let { it * segments[i].nights * totalGuests } }
        .takeIf { it.size == segments.size && segments.isNotEmpty() }
        ?.sum()
    val adjustmentsSum = adjustments.sumOf { it.amount }
    val origAdjSum = existing.priceAdjustments.sumOf { it.amount }
    val effectiveTotal = computedTotal?.let { it + adjustmentsSum }
        ?: existing.totalAmount?.let { base -> (base - origAdjSum) + adjustmentsSum }

    val conflict = remember(selectedRoom?.id, checkIn, checkOut, reservations, blocks) {
        selectedRoom != null && checkIn.isNotBlank() && checkOut.isNotBlank() &&
        (hasReservationConflict(reservations, selectedRoom!!.id, checkIn, checkOut, excludeId = existing.id) ||
         hasBlockConflict(blocks, selectedRoom!!.id, checkIn, checkOut))
    }
    val guestReady = selectedGuest != null || (guestFirstName.isNotBlank() && guestLastName.isNotBlank())
    val valid = selectedRoom != null && guestReady &&
        checkIn.isNotBlank() && checkOut.isNotBlank() && adults.toDoubleOrNull() != null && !conflict

    if (showDeleteConfirm) {
        AppAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(s.deleteReservationTitle(existing.id)) },
            text  = { Text(s.cannotBeUndone) },
            confirmButton = { Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(s.delete) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(s.cancel) } }
        )
        return
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.editReservationTitle(existing.id)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isExternal) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Booking.com",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(s.externalSourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                    }
                }
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
                        Text(s.roomAlreadyReserved,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                if (isExternal) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = externalRefInput, onValueChange = { externalRefInput = it },
                        label = { Text(s.bookingRefLabel) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = externalTotalInput, onValueChange = { externalTotalInput = it },
                        label = { Text(s.bookingTotalLabel) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                } else {
                HorizontalDivider()
                PriceBreakdownSection(
                    segments    = segments,
                    segmentPpns = segmentPpns,
                    onPpnChange = { i, v -> segmentPpns = segmentPpns.toMutableList().also { it[i] = v } },
                    totalGuests = totalGuests,
                    bakedPpns   = bakedPpns
                )
                // ── Price Adjustments section ─────────────────────────────────
                HorizontalDivider()
                Text(s.priceAdjustmentsTitle, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (adjustments.isEmpty()) {
                            Text(s.noAdjustments,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            adjustments.forEach { adj ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "%.2f PLN".format(adj.amount),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (adj.amount < 0) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.primary
                                        )
                                        if (!adj.description.isNullOrBlank()) {
                                            Text(
                                                adj.description!!,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            try {
                                                client.delete("$BASE_URL/api/reservations/${existing.id}/price-adjustments/${adj.id}")
                                                adjustments = adjustments.filter { it.id != adj.id }
                                                onAdjustmentsChanged()
                                            } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                                        }
                                    }, modifier = Modifier.size(28.dp)) {
                                        Text("×", style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        // Add adjustment form
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = adjAmountInput,
                                onValueChange = { adjAmountInput = it },
                                label = { Text(s.adjustmentAmountLabel, style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier.width(130.dp)
                            )
                            OutlinedTextField(
                                value = adjDescInput,
                                onValueChange = { adjDescInput = it },
                                label = { Text(s.adjustmentDescriptionLabel, style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val amount = adjAmountInput.toDoubleOrNull() ?: return@Button
                                    scope.launch {
                                        try {
                                            val created: ReservationPriceAdjustmentDto = client.post("$BASE_URL/api/reservations/${existing.id}/price-adjustments") {
                                                contentType(ContentType.Application.Json)
                                                setBody(CreatePriceAdjustmentRequest(amount = amount, description = adjDescInput.trim().ifBlank { null }))
                                            }.body()
                                            adjustments = adjustments + created
                                            adjAmountInput = ""; adjDescInput = ""
                                            onAdjustmentsChanged()
                                        } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                                    }
                                },
                                enabled = adjAmountInput.toDoubleOrNull() != null
                            ) { Text(s.add) }
                        }
                        // Totals summary
                        if (computedTotal != null || existing.totalAmount != null) {
                            HorizontalDivider(thickness = 0.5.dp)
                            if (computedTotal != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(s.segmentsBaseTotal, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${"%.2f".format(computedTotal)} PLN",
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (adjustments.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(s.adjustmentsTotal, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("%+.2f PLN".format(adjustmentsSum),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (adjustmentsSum < 0) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.primary)
                                }
                                val displayTotal = computedTotal?.let { it + adjustmentsSum }
                                    ?: existing.totalAmount?.let { base ->
                                        val origAdj = existing.priceAdjustments.sumOf { it.amount }
                                        base - origAdj + adjustmentsSum
                                    }
                                if (displayTotal != null) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(s.effectiveTotalLabel, style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold)
                                        Text("${"%.2f".format(displayTotal)} PLN",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { requiresDownPayment = !requiresDownPayment },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = requiresDownPayment, onCheckedChange = { requiresDownPayment = it })
                    Text(s.requiresDownPayment, style = MaterialTheme.typography.bodyMedium)
                }
                if (requiresDownPayment) {
                    OutlinedTextField(
                        value = downPaymentAmountInput,
                        onValueChange = { downPaymentAmountInput = it },
                        label = { Text(s.downPaymentAmountLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                } // end else (not external)
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
                        val builtSegments = if (!isExternal && segments.size == segmentPpns.size && segments.isNotEmpty()) {
                            segments.indices.mapNotNull { i ->
                                val ppn = segmentPpns.getOrNull(i)?.toDoubleOrNull() ?: return@mapNotNull null
                                val ruleId = if (useBaked) existing.priceSegments.getOrNull(i)?.priceRuleId
                                             else segments[i].rule?.id
                                CreatePriceSegmentRequest(
                                    fromDate               = segments[i].fromDate.toString(),
                                    toDate                 = segments[i].toDate.toString(),
                                    pricePerPersonPerNight = ppn,
                                    adults                 = totalGuests,
                                    currency               = "PLN",
                                    priceRuleId            = ruleId
                                )
                            }.takeIf { it.size == segments.size }
                        } else null
                        onConfirm(UpdateReservationRequest(
                            roomId = selectedRoom!!.id, guestId = guestId,
                            checkInDate = checkIn.trim(), checkOutDate = checkOut.trim(), status = status,
                            adults = adults.trim().toDoubleOrNull() ?: 1.0,
                            totalAmount = if (isExternal) externalTotalInput.toDoubleOrNull() else effectiveTotal,
                            description = existing.description,
                            requiresDownPayment = if (isExternal) false else requiresDownPayment,
                            downPaymentAmount   = if (isExternal) null else if (requiresDownPayment) downPaymentAmountInput.toDoubleOrNull() else null,
                            priceSegments       = if (isExternal) emptyList() else (builtSegments ?: emptyList()),
                            externalRef         = if (isExternal) externalRefInput.trim().ifBlank { null } else null
                        ))
                    }
                }, enabled = valid
            ) { Text(s.save) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(s.delete) }
                TextButton(onClick = onDismiss) { Text(s.cancel) }
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
    val s = LocalStrings.current
    ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = onRoomExpandChange) {
        OutlinedTextField(
            value = selectedRoom?.let { "${s.roomLabel(it.number)} · ${it.typeName}" } ?: s.selectRoomHint,
            onValueChange = {}, readOnly = true, label = { Text(s.roomFieldLabel) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
        )
        ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { onRoomExpandChange(false) }) {
            rooms.forEach { room ->
                DropdownMenuItem(
                    text = { Text("${s.roomLabel(room.number)} · ${room.typeName} (max ${room.maxGuests})") },
                    onClick = { onRoomChange(room); onRoomExpandChange(false) }
                )
            }
        }
    }
    ResDateRangePickerFields(
        checkInLabel = s.checkInLabel, checkIn = checkIn, onCheckInChange = onCheckInChange,
        checkOutLabel = s.checkOutLabel, checkOut = checkOut, onCheckOutChange = onCheckOutChange
    )
    OutlinedTextField(adults, onAdultsChange, label = { Text(s.adultsLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = onStatusExpandChange) {
        OutlinedTextField(
            value = status.replace('_', ' '), onValueChange = {}, readOnly = true, label = { Text(s.statusLabel) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true
        )
        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { onStatusExpandChange(false) }) {
            RESERVATION_STATUSES.forEach { code ->
                DropdownMenuItem(text = { Text(s.statusName(code)) }, onClick = { onStatusChange(code); onStatusExpandChange(false) })
            }
        }
    }
}

@Composable
private fun PriceBreakdownSection(
    segments: List<PriceSegment>,
    segmentPpns: List<String>,
    onPpnChange: (index: Int, value: String) -> Unit,
    totalGuests: Double,
    bakedPpns: List<Double?> = emptyList()   // original saved ppns — non-null means "show change indicator"
) {
    val s = LocalStrings.current
    if (segments.isEmpty()) return
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", s.locale)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEachIndexed { i, seg ->
            val ppn      = segmentPpns.getOrElse(i) { "" }
            val ppnValue = ppn.toDoubleOrNull()
            val subtotal = if (ppnValue != null) ppnValue * seg.nights * totalGuests else null
            val bakedPpn = bakedPpns.getOrNull(i)
            val rulePpn  = seg.rule?.pricePerPersonPerNight
            // Baked differs from rule → rule was updated since booking
            val ruleChanged = bakedPpn != null && rulePpn != null &&
                Math.abs(rulePpn - bakedPpn) > 0.001

            if (i > 0) HorizontalDivider(thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${seg.fromDate.format(fmt)} – ${seg.toDate.minusDays(1).format(fmt)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${seg.nights} ${s.nightsAbbr}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (seg.rule != null) {
                        Text(
                            s.priceRuleLine(seg.rule.minNights, seg.rule.maxNights),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (bakedPpn != null) {
                        // baked segment whose rule was deleted
                        Text(s.noMatchingRule,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(s.noMatchingRule,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (ruleChanged) {
                        Text(
                            "rule: ${"%.2f".format(rulePpn)} PLN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    OutlinedTextField(
                        value         = ppn,
                        onValueChange = { onPpnChange(i, it) },
                        label         = { Text(s.plnPerPersonPerNight) },
                        singleLine    = true,
                        modifier      = Modifier.width(160.dp),
                        isError       = ppn.isNotBlank() && ppnValue == null
                    )
                    if (subtotal != null) {
                        Text(
                            s.priceTotalLine(subtotal),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        val grandTotal = segments.indices
            .mapNotNull { i -> segmentPpns.getOrNull(i)?.toDoubleOrNull()?.let { it * segments[i].nights * totalGuests } }
            .takeIf { it.size == segments.size }
            ?.sum()
        if (grandTotal != null && segments.size > 1) {
            HorizontalDivider()
            Text(
                s.priceTotalLine(grandTotal),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
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
    holidays: List<HolidayDto> = emptyList(),
    year: Int,
    month: Int,
    scale: TimelineScale,
    onScaleChange: (TimelineScale) -> Unit,
    centerDays: Int,
    onEditRequest: (ReservationDto) -> Unit,
    onBlockClick: (RoomBlockDto) -> Unit,
    dragMode: DragMode = DragMode.Reservation,
    onDragModeChange: (DragMode) -> Unit = {},
    hiddenStatuses: Set<String> = emptySet(),
    onHiddenStatusesChange: (Set<String>) -> Unit = {},
    dayWidthPx: Float = 40f,
    onDayWidthPxChange: (Float) -> Unit = {},
    rowHeightPx: Float = 34f,
    onRowHeightPxChange: (Float) -> Unit = {},
    labelWidthPx: Float = 96f,
    onLabelWidthPxChange: (Float) -> Unit = {},
    showRoomType: Boolean = true,
    onBlockRequest: (room: RoomDto, checkIn: LocalDate, checkOut: LocalDate) -> Unit = { _, _, _ -> },
    onCreateRequest: (room: RoomDto, checkIn: LocalDate, checkOut: LocalDate) -> Unit,
    onCreateExternalRequest: (room: RoomDto, checkIn: LocalDate, checkOut: LocalDate) -> Unit = { _, _, _ -> }
) {
    val s             = LocalStrings.current
    val scope         = rememberCoroutineScope()
    var dragState     by remember { mutableStateOf<TimelineDragState?>(null) }
    var showOptions   by remember { mutableStateOf(false) }
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
    val monthSegments: List<Pair<String, Int>> = remember(year, month, scale, centerDays, s.locale) {
        when (scale) {
            TimelineScale.Year -> buildList {
                add(Month.DECEMBER.getDisplayName(java.time.format.TextStyle.SHORT, s.locale) to LocalDate.of(year - 1, 12, 1).lengthOfMonth())
                (1..12).forEach { m -> add(Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, s.locale) to LocalDate.of(year, m, 1).lengthOfMonth()) }
                add(Month.JANUARY.getDisplayName(java.time.format.TextStyle.SHORT, s.locale) to LocalDate.of(year + 1, 1, 1).lengthOfMonth())
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
                    segments += Month.of(d.monthValue).getDisplayName(java.time.format.TextStyle.SHORT, s.locale) to count
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
    val ROW_H        = rowHeightPx.dp * fs
    val LANE_H       = (rowHeightPx - 4f).dp * fs
    val SPLIT_ROW_H  = LANE_H * 2
    val MONTH_ROW_H  = 18.dp * fs
    val DAY_ROW_H    = 46.dp * fs
    val HEAD_H       = if (scale == TimelineScale.Month) DAY_ROW_H else MONTH_ROW_H + DAY_ROW_H
    val LABEL_W      = labelWidthPx.dp * fs
    val BAND_H       = (rowHeightPx - 12f).dp * fs

    val visibleReservations = remember(reservations, hiddenStatuses) {
        if (hiddenStatuses.isEmpty()) reservations
        else reservations.filter { it.status !in hiddenStatuses }
    }
    val resByRoom    = remember(visibleReservations) { visibleReservations.groupBy { it.roomId } }
    val blocksByRoom = remember(blocks) { blocks.groupBy { it.roomId } }
    val holidayMap: Map<LocalDate, HolidayDto> = remember(holidays, dateStart, dateEnd) {
        buildMap {
            holidays.forEach { h ->
                val from = maxOf(LocalDate.parse(h.fromDate), dateStart)
                val to   = minOf(LocalDate.parse(h.toDate), dateEnd)
                if (!from.isAfter(to)) {
                    var d = from
                    while (!d.isAfter(to)) {
                        putIfAbsent(d, h)
                        d = d.plusDays(1)
                    }
                }
            }
        }
    }
    var expandedRoomId  by remember { mutableStateOf<Int?>(null) }
    // Per-room expanded band height: base content + 15dp per optional row (DP, note)
    val expandedBandHByRoom = remember(reservations, rooms) {
        val cancelSet = setOf("cancelled", "no_show")
        rooms.associate { room ->
            val activeRoomRes = reservations.filter { it.roomId == room.id && it.status !in cancelSet }
            val hasDP   = activeRoomRes.any { it.requiresDownPayment }
            val hasNote = activeRoomRes.any { it.description?.isNotBlank() == true }
            room.id to (84 + ((if (hasDP) 1 else 0) + (if (hasNote) 1 else 0)) * 15)
        }
    }
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
        // ── Controls ──────────────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Always-visible row: today, cancelled, drag mode, options toggle
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    Text(s.today, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Show/hide cancelled
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
                        if (showCancelled) s.hideCancelled else s.showCancelled,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (showCancelled) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Drag mode (desktop only)
                if (!IS_ANDROID) {
                    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        listOf(
                            DragMode.Reservation to s.dragModeReservation,
                            DragMode.External    to s.dragModeExternal,
                            DragMode.Block       to s.dragModeBlock
                        ).forEach { (mode, label) ->
                            val sel = dragMode == mode
                            val bg = when {
                                !sel                      -> Color.Transparent
                                mode == DragMode.Block    -> Color(0xFF607D8B)
                                mode == DragMode.External -> MaterialTheme.colorScheme.tertiary
                                else                      -> MaterialTheme.colorScheme.primary
                            }
                            val fg = when {
                                !sel                      -> MaterialTheme.colorScheme.onSurfaceVariant
                                mode == DragMode.Block    -> Color.White
                                mode == DragMode.External -> MaterialTheme.colorScheme.onTertiary
                                else                      -> MaterialTheme.colorScheme.onPrimary
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(bg)
                                    .clickable { onDragModeChange(mode) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // Options toggle
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (showOptions) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { showOptions = !showOptions }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (showOptions) "▲" else "▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (showOptions) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Collapsible options panel: scale, half-shift, width, height
            if (showOptions) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Scale pill
                    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        listOf(TimelineScale.Center to s.scaleCenter, TimelineScale.Month to s.scaleMonth, TimelineScale.Year to s.scaleYear).forEach { (sc, label) ->
                            val sel = scale == sc
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                    .clickable { onScaleChange(sc) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium,
                                    color = if (sel) MaterialTheme.colorScheme.onSecondary
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // Half-shift pill
                    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        listOf(false to s.fullDay, true to s.halfShiftLabel).forEach { (v, label) ->
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
                }
                // Width / height / label-width sliders
                listOf(
                    Triple(s.widthLabel,      dayWidthPx,   onDayWidthPxChange)   to (16f..80f),
                    Triple(s.heightLabel,     rowHeightPx,  onRowHeightPxChange)  to (24f..72f),
                    Triple(s.labelWidthLabel, labelWidthPx, onLabelWidthPxChange) to (40f..160f)
                ).forEach { (triple, range) ->
                    val (label, value, onChange) = triple
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(48.dp))
                        Slider(value = value, onValueChange = onChange,
                            valueRange = range, modifier = Modifier.weight(1f))
                        Text("${value.toInt()}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp))
                    }
                }
                // Status filter chips
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    STATUS_PALETTE.entries.forEach { (status, colors) ->
                        val (bg, fg) = colors
                        val hidden = status in hiddenStatuses
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (hidden) Color.Transparent else bg)
                                .border(
                                    width = 1.dp,
                                    color = fg.copy(alpha = if (hidden) 0.35f else 0f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    onHiddenStatusesChange(
                                        if (hidden) hiddenStatuses - status
                                        else        hiddenStatuses + status
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                s.statusName(status),
                                style = MaterialTheme.typography.labelSmall,
                                color = fg.copy(alpha = if (hidden) 0.35f else 1f)
                            )
                        }
                    }
                }
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
                            val isToday      = day == today
                            val isWeekend    = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
                            val holidayForDay = holidayMap[day]
                            val isHoliday = holidayForDay != null

                            AppTooltipArea(
                                tooltip = {
                                    if (holidayForDay != null) {
                                        Surface(
                                            shape           = RoundedCornerShape(4.dp),
                                            shadowElevation = 8.dp,
                                            color           = MaterialTheme.colorScheme.inverseSurface
                                        ) {
                                            Text(
                                                holidayForDay.name,
                                                style    = MaterialTheme.typography.labelSmall,
                                                color    = MaterialTheme.colorScheme.inverseOnSurface,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            ) {
                                Column(
                                    Modifier.width(DAY_W).fillMaxHeight()
                                        .background(when {
                                            isToday   -> MaterialTheme.colorScheme.primaryContainer
                                            isHoliday -> Color(0xFFFFF176).copy(alpha = 0.50f)
                                            isWeekend -> MaterialTheme.colorScheme.surfaceVariant
                                            else      -> Color.Transparent
                                        })
                                        .drawBehind {
                                            drawLine(divColor, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), strokeWidth = 1f)
                                        }
                                        .padding(top = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, s.locale).take(
                                            if (scale == TimelineScale.Year) 1 else 2
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isToday   -> MaterialTheme.colorScheme.onPrimaryContainer
                                            isWeekend -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                                            else      -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(Modifier.height(2.dp))
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
        }
        HorizontalDivider()

        // ── Room rows ─────────────────────────────────────────────────────────
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // Sticky left labels (vertical scroll only)
            Column(Modifier.width(LABEL_W).fillMaxHeight().verticalScroll(vScroll)) {
                rooms.forEach { room ->
                    val isExpanded = expandedRoomId == room.id
                    val expandedBandH = (expandedBandHByRoom[room.id] ?: 84).dp * fs
                    val expandedRowH  = expandedBandH + 12.dp * fs
                    val rowH by animateDpAsState(
                        targetValue = when {
                            isExpanded    -> expandedRowH
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
                                Text("${s.roomAbbr} ${room.number}", style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium, maxLines = 1)
                                if (showRoomType) {
                                    Text(room.typeName, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
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
                        .run {
                            if (IS_ANDROID) {
                                pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        hScroll.dispatchRawDelta(-dragAmount.x)
                                        vScroll.dispatchRawDelta(-dragAmount.y)
                                    }
                                }
                                    .verticalScroll(vScroll, enabled = false)
                                    .horizontalScroll(hScroll, enabled = false)
                            } else {
                                verticalScroll(vScroll).horizontalScroll(hScroll)
                            }
                        }
                ) {
                    val latestReservations = rememberUpdatedState(reservations)
                    val latestBlocks = rememberUpdatedState(blocks)
                    Column(Modifier.width(totalWidth)) {
                        rooms.forEach { room ->
                            val isExpanded = expandedRoomId == room.id
                            val expandedBandH = (expandedBandHByRoom[room.id] ?: 84).dp * fs
                            val expandedRowH  = expandedBandH + 12.dp * fs
                            val rowH by animateDpAsState(
                                targetValue = when {
                                    isExpanded    -> expandedRowH
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
                                    .pointerInput(room.id, DAY_W, days.size, halfShift, dragMode) {
                                        if (IS_ANDROID) return@pointerInput
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
                                                    val s        = minOf(ds.startIdx, ds.endIdx)
                                                    val e        = maxOf(ds.startIdx, ds.endIdx)
                                                    val checkIn  = days[s]
                                                    val checkOut = days[e].plusDays(1)
                                                    val curRes   = latestReservations.value
                                                    val curBlks  = latestBlocks.value
                                                    when (dragMode) {
                                                        DragMode.Block -> {
                                                            val noResConflict = !hasReservationConflict(
                                                                curRes, ds.room.id,
                                                                checkIn.toString(), checkOut.toString()
                                                            )
                                                            val noBlockConflict = !hasBlockConflict(
                                                                curBlks, ds.room.id,
                                                                checkIn.toString(), checkOut.toString()
                                                            )
                                                            if (noResConflict && noBlockConflict)
                                                                onBlockRequest(ds.room, checkIn, checkOut)
                                                        }
                                                        DragMode.External -> {
                                                            val noConflict = !hasReservationConflict(
                                                                curRes, ds.room.id,
                                                                checkIn.toString(), checkOut.toString()
                                                            ) && !hasBlockConflict(
                                                                curBlks, ds.room.id,
                                                                checkIn.toString(), checkOut.toString()
                                                            )
                                                            if (noConflict) onCreateExternalRequest(ds.room, checkIn, checkOut)
                                                        }
                                                        DragMode.Reservation -> {
                                                            val noConflict = !hasReservationConflict(
                                                                curRes, ds.room.id,
                                                                checkIn.toString(), checkOut.toString()
                                                            ) && !hasBlockConflict(
                                                                curBlks, ds.room.id,
                                                                checkIn.toString(), checkOut.toString()
                                                            )
                                                            if (noConflict) onCreateRequest(ds.room, checkIn, checkOut)
                                                        }
                                                    }
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
                                        val isHoliday = day in holidayMap
                                        Box(Modifier.width(DAY_W).fillMaxHeight()
                                            .background(when {
                                                isToday   -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                isHoliday -> Color(0xFFFFF176).copy(alpha = 0.35f)
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
                                    val blockBandH = if (isExpanded) expandedBandH else BAND_H
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
                                            block.reason ?: s.blocked,
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
                                    val hasNoteC   = res.description?.isNotBlank() == true
                                    val noteLabelC = res.description?.takeIf { it.isNotBlank() }?.replace("\n", "; ")
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
                                        val endPaddingC = when {
                                            dotColorC != null && hasNoteC && rightRound -> 21.dp
                                            dotColorC != null && rightRound             -> 12.dp
                                            hasNoteC && rightRound                      -> 12.dp
                                            else                                        -> 4.dp
                                        }
                                        Text(
                                            if (noteLabelC != null) "${res.guestName} · $noteLabelC" else res.guestName,
                                            modifier = Modifier.padding(start = if (showDpC) 18.dp else 4.dp,
                                                end = endPaddingC)
                                                .align(Alignment.CenterStart),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        if (rightRound) {
                                            if (hasNoteC) {
                                                Box(Modifier.align(Alignment.CenterEnd)
                                                    .padding(end = if (dotColorC != null) 12.dp else 3.dp)
                                                    .size(7.dp).clip(RoundedCornerShape(1.dp))
                                                    .background(fg.copy(alpha = 0.5f)))
                                            }
                                            if (dotColorC != null) {
                                                Box(Modifier.align(Alignment.CenterEnd).padding(end = 3.dp)
                                                    .size(6.dp).clip(CircleShape).background(dotColorC))
                                            }
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
                                    val bandH      = if (isExpanded) expandedBandH else BAND_H
                                    val bTop = when {
                                        isExpanded                   -> (rowH - bandH) / 2
                                        showCancelled                -> LANE_H + (LANE_H - bandH) / 2
                                        else                         -> (rowH - bandH) / 2
                                    }
                                    val (bg, fg) = STATUS_PALETTE[res.status] ?: (Color(0xFFE0E0E0) to Color(0xFF424242))
                                    val dotColorA   = paymentDotColor(res.totalAmount, res.paidAmount)
                                    val showDpA     = dpPending(res) && leftRound
                                    val hasNoteA    = res.description?.isNotBlank() == true
                                    val noteLabelA  = res.description?.takeIf { it.isNotBlank() }?.replace("\n", "; ")
                                    val isExternalA = res.source == "external"
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
                                            val leftBadgeWidth = when {
                                                showDpA && isExternalA && leftRound -> 34.dp
                                                showDpA && leftRound                -> 18.dp
                                                isExternalA && leftRound            -> 18.dp
                                                else                                -> 4.dp
                                            }
                                            if (showDpA && leftRound) {
                                                Box(Modifier.align(Alignment.CenterStart).padding(start = 2.dp)
                                                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE65100))
                                                    .padding(horizontal = 2.dp, vertical = 1.dp)) {
                                                    Text("DP", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp), color = Color.White)
                                                }
                                            }
                                            if (isExternalA && leftRound) {
                                                Box(Modifier.align(Alignment.CenterStart)
                                                    .padding(start = if (showDpA) 20.dp else 2.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(MaterialTheme.colorScheme.tertiary)
                                                    .padding(horizontal = 2.dp, vertical = 1.dp)) {
                                                    Text("B", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                                        color = MaterialTheme.colorScheme.onTertiary)
                                                }
                                            }
                                            val endPaddingA = when {
                                                dotColorA != null && hasNoteA && rightRound -> 21.dp
                                                dotColorA != null && rightRound             -> 12.dp
                                                hasNoteA && rightRound                      -> 12.dp
                                                else                                        -> 4.dp
                                            }
                                            Text(
                                                if (noteLabelA != null) "${res.guestName} · $noteLabelA" else res.guestName,
                                                modifier = Modifier.padding(start = leftBadgeWidth,
                                                    top = 4.dp,
                                                    end = endPaddingA),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                            if (rightRound) {
                                                if (hasNoteA) {
                                                    Box(Modifier.align(Alignment.TopEnd)
                                                        .padding(top = 4.dp, end = if (dotColorA != null) 12.dp else 3.dp)
                                                        .size(7.dp).clip(RoundedCornerShape(1.dp))
                                                        .background(fg.copy(alpha = 0.5f)))
                                                }
                                                if (dotColorA != null) {
                                                    Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 3.dp)
                                                        .size(6.dp).clip(CircleShape).background(dotColorA))
                                                }
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
                                                            s.statusName(res.status),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            color = fg
                                                        )
                                                    }
                                                    if (isExternalA) {
                                                        Box(
                                                            Modifier
                                                                .clip(RoundedCornerShape(3.dp))
                                                                .background(MaterialTheme.colorScheme.tertiary)
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text("Booking",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                                color = MaterialTheme.colorScheme.onTertiary)
                                                        }
                                                    }
                                                    Text(
                                                        res.guestName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = fg, maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (hasNoteA) {
                                                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp))
                                                            .background(fg.copy(alpha = 0.5f)))
                                                    }
                                                    if (dotColorA != null) {
                                                        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColorA))
                                                    }
                                                }
                                                // Check-in date + nights only
                                                Text(
                                                    "${res.checkInDate}  ($nights ${s.nightsAbbr})",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = fg.copy(alpha = 0.85f),
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                                val people = s.adultsStr(res.adults)
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
                                                if (noteLabelA != null) {
                                                    Text(
                                                        noteLabelA,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        color = fg.copy(alpha = 0.75f),
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(Modifier.weight(1f))
                                                TextButton(
                                                    onClick = { onEditRequest(res) },
                                                    modifier = Modifier.height(22.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                ) {
                                                    Text(s.edit, style = MaterialTheme.typography.labelSmall, color = fg)
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
                                    val previewLeft  = if (halfShift) DAY_W * (s + 0.5f) + 1.dp else DAY_W * s + 1.dp
                                    val previewWidth = DAY_W * (e - s + 1) - 2.dp
                                    val bTop  = when {
                                        showCancelled && !isExpanded -> LANE_H + (LANE_H - BAND_H) / 2
                                        else -> (rowH - BAND_H) / 2
                                    }
                                    val cin  = days[s].toString()
                                    val cout = days[e].plusDays(1).toString()
                                    val hasResConflict   = hasReservationConflict(reservations, room.id, cin, cout)
                                    val hasBlkConflict   = hasBlockConflict(blocks, room.id, cin, cout)
                                    val isConflict = hasResConflict || hasBlkConflict
                                    val previewColor = when {
                                        isConflict              -> MaterialTheme.colorScheme.error
                                        dragMode == DragMode.Block    -> Color(0xFF607D8B)
                                        dragMode == DragMode.External -> MaterialTheme.colorScheme.tertiary
                                        else                    -> MaterialTheme.colorScheme.primary
                                    }
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
                AppVerticalScrollbar(
                    state    = vScroll,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
                // Horizontal scrollbar (bottom edge)
                AppHorizontalScrollbar(
                    state    = hScroll,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(end = 8.dp)
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

    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedGuest != null) {
            // Selected guest card
            val selectedInteraction = remember(selectedGuest.id) { MutableInteractionSource() }
            val selectedHovered by selectedInteraction.collectIsHoveredAsState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .hoverable(selectedInteraction)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "${selectedGuest.firstName} ${selectedGuest.lastName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (!selectedGuest.blacklisted && !selectedGuest.notes.isNullOrBlank()) {
                                Text(
                                    "note",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
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
                        Text(s.change, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                if (selectedGuest.blacklisted) {
                    Text(
                        s.blacklistedWarning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (selectedHovered && !selectedGuest.blacklisted && !selectedGuest.notes.isNullOrBlank()) {
                    Text(
                        selectedGuest.notes!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        } else {
            // Input fields
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(firstName, onFirstNameChange, label = { Text(s.firstNameLabel) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(lastName,  onLastNameChange,  label = { Text(s.lastNameLabel) },  singleLine = true, modifier = Modifier.weight(1f))
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = countryCode, onValueChange = onCountryCodeChange,
                    label = { Text(s.codeLabel) },
                    placeholder = { Text("48") },
                    prefix = { Text("+") },
                    singleLine = true,
                    modifier = Modifier.width(90.dp)
                )
                OutlinedTextField(phoneNumber, onPhoneNumberChange, label = { Text(s.phoneNumberLabel) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            // Suggestions
            if (suggestions.isNotEmpty()) {
                Text(
                    s.didYouMean,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                suggestions.forEach { guest ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()
                    val cardBg = if (guest.blacklisted) MaterialTheme.colorScheme.errorContainer
                                 else MaterialTheme.colorScheme.surface
                    val cardBorder = if (guest.blacklisted) MaterialTheme.colorScheme.error
                                     else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(6.dp))
                            .hoverable(interactionSource)
                            .clickable { onSelectedGuestChange(guest) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "${guest.firstName} ${guest.lastName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (guest.blacklisted) MaterialTheme.colorScheme.onErrorContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!guest.blacklisted && !guest.notes.isNullOrBlank()) {
                                        Text(
                                            "note",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                val gPhone = listOfNotNull(guest.countryCode?.let { "+$it" }, guest.phoneNumber).joinToString(" ").ifBlank { null }
                                val detail = listOfNotNull(gPhone, guest.nationality).joinToString(" · ")
                                if (detail.isNotBlank()) Text(
                                    detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (guest.blacklisted) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(s.select, style = MaterialTheme.typography.labelSmall,
                                color = if (guest.blacklisted) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary)
                        }
                        if (isHovered && !guest.blacklisted && !guest.notes.isNullOrBlank()) {
                            Text(
                                guest.notes!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
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
    reservations: List<ReservationDto>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<CreateRoomBlockRequest>) -> Unit
) {
    var selectedRoom  by remember { mutableStateOf(rooms.firstOrNull()) }
    var roomExpanded  by remember { mutableStateOf(false) }
    var blockAllRooms by remember { mutableStateOf(false) }
    var fromDate      by remember { mutableStateOf("") }
    var toDate        by remember { mutableStateOf("") }
    var reason        by remember { mutableStateOf("") }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }
    val s = LocalStrings.current

    val conflictingRooms = remember(blockAllRooms, selectedRoom, fromDate, toDate, reservations) {
        val cin  = runCatching { LocalDate.parse(fromDate.trim()) }.getOrNull() ?: return@remember emptyList()
        val cout = runCatching { LocalDate.parse(toDate.trim()) }.getOrNull()  ?: return@remember emptyList()
        if (!cout.isAfter(cin)) return@remember emptyList()
        val toCheck = if (blockAllRooms) rooms else listOfNotNull(selectedRoom)
        toCheck.filter { room ->
            reservations.any { r ->
                r.roomId == room.id &&
                r.status !in listOf("cancelled", "no_show") &&
                LocalDate.parse(r.checkInDate).isBefore(cout) &&
                LocalDate.parse(r.checkOutDate).isAfter(cin)
            }
        }
    }
    val hasConflict = conflictingRooms.isNotEmpty()
    val datesOk     = fromDate.isNotBlank() && toDate.isNotBlank()
    val roomOk      = blockAllRooms || selectedRoom != null
    val valid       = roomOk && datesOk && !hasConflict && !isSubmitting

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.blockRoomTitle) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // All-rooms checkbox
                Row(
                    Modifier.fillMaxWidth().clickable { blockAllRooms = !blockAllRooms },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = blockAllRooms, onCheckedChange = { blockAllRooms = it })
                    Text(s.blockAllRoomsLabel, style = MaterialTheme.typography.bodyMedium)
                }
                // Room picker — disabled when blocking all
                ExposedDropdownMenuBox(
                    expanded = roomExpanded && !blockAllRooms,
                    onExpandedChange = { if (!blockAllRooms) roomExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when {
                            blockAllRooms -> s.allRoomsLabel
                            else -> selectedRoom?.let { "${s.roomLabel(it.number)} · ${it.typeName}" } ?: s.selectRoomHint
                        },
                        onValueChange = {}, readOnly = true, label = { Text(s.roomFieldLabel) },
                        trailingIcon  = { if (!blockAllRooms) ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
                        enabled  = !blockAllRooms,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = roomExpanded && !blockAllRooms, onDismissRequest = { roomExpanded = false }) {
                        rooms.forEach { room ->
                            DropdownMenuItem(
                                text  = { Text("${s.roomLabel(room.number)} · ${room.typeName}") },
                                onClick = { selectedRoom = room; roomExpanded = false }
                            )
                        }
                    }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fromDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s.fromLabel) },
                        trailingIcon = {
                            TextButton(onClick = { showFromPicker = true },
                                contentPadding = PaddingValues(horizontal = 4.dp)) { Text("📅") }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = toDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s.toLabel) },
                        trailingIcon = {
                            TextButton(onClick = { showToPicker = true },
                                contentPadding = PaddingValues(horizontal = 4.dp)) { Text("📅") }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    reason, { reason = it },
                    label       = { Text(s.reasonLabel) },
                    placeholder = { Text(s.reasonPlaceholder) },
                    singleLine  = true,
                    modifier    = Modifier.fillMaxWidth()
                )
                if (hasConflict) {
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
                        Text(
                            s.blockReservationConflict(conflictingRooms.joinToString { s.roomAbbr + " " + it.number }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val toBlock = if (blockAllRooms) rooms else listOfNotNull(selectedRoom)
                    onConfirm(toBlock.map { room ->
                        CreateRoomBlockRequest(
                            roomId   = room.id,
                            fromDate = fromDate.trim(),
                            toDate   = toDate.trim(),
                            reason   = reason.trim().ifBlank { null }
                        )
                    })
                },
                enabled = valid
            ) { Text(s.blockBtn) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )

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
                        fromDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
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
                        toDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showToPicker = false
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text(s.cancel) } }
        ) { DatePicker(state = dpState) }
    }
}

// ─── Block delete dialog ──────────────────────────────────────────────────────

@Composable
private fun EditGuestDialog(
    guest: GuestDto,
    onDismiss: () -> Unit,
    onConfirm: (UpdateGuestRequest) -> Unit
) {
    val s = LocalStrings.current
    var firstName   by remember(guest.id) { mutableStateOf(guest.firstName) }
    var lastName    by remember(guest.id) { mutableStateOf(guest.lastName) }
    var countryCode by remember(guest.id) { mutableStateOf(guest.countryCode ?: "") }
    var phoneNumber by remember(guest.id) { mutableStateOf(guest.phoneNumber ?: "") }
    var nationality by remember(guest.id) { mutableStateOf(guest.nationality ?: "") }
    var blacklisted by remember(guest.id) { mutableStateOf(guest.blacklisted) }
    var notes       by remember(guest.id) { mutableStateOf(guest.notes ?: "") }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${guest.firstName} ${guest.lastName}") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(firstName, { firstName = it }, label = { Text(s.firstNameLabel) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(lastName,  { lastName  = it }, label = { Text(s.lastNameLabel) },  singleLine = true, modifier = Modifier.weight(1f))
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countryCode, onValueChange = { countryCode = it },
                        label = { Text(s.codeLabel) }, prefix = { Text("+") },
                        singleLine = true, modifier = Modifier.width(90.dp)
                    )
                    OutlinedTextField(phoneNumber, { phoneNumber = it }, label = { Text(s.phoneNumberLabel) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(nationality, { nationality = it }, label = { Text(s.nationalityLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = blacklisted, onCheckedChange = { blacklisted = it })
                    Text(s.blacklistedLabel, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(s.guestNotesLabel) },
                    placeholder = { Text(s.guestNotesPlaceholder) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        onConfirm(UpdateGuestRequest(
                            firstName   = firstName.trim(),
                            lastName    = lastName.trim(),
                            countryCode = countryCode.trim().ifBlank { null },
                            phoneNumber = phoneNumber.trim().ifBlank { null },
                            nationality = nationality.trim().ifBlank { null },
                            blacklisted = blacklisted,
                            notes       = notes.trim().ifBlank { null }
                        ))
                    },
                    enabled = firstName.isNotBlank() && lastName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(s.saveGuestBtn) }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(s.cancel) }
            }
        }
    )
}

@Composable
private fun RoomBlockDeleteDialog(
    block: RoomBlockDto,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val s = LocalStrings.current
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.removeBlockTitle) },
        text  = { Text(s.removeBlockConfirm(block.roomNumber, block.fromDate, block.toDate, block.reason)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(s.remove) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

// ─── Date range picker fields ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResDateRangePickerFields(
    checkInLabel: String, checkIn: String, onCheckInChange: (String) -> Unit,
    checkOutLabel: String, checkOut: String, onCheckOutChange: (String) -> Unit
) {
    var showCheckInPicker  by remember { mutableStateOf(false) }
    var showCheckOutPicker by remember { mutableStateOf(false) }
    val s = LocalStrings.current

    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = checkIn, onValueChange = {}, readOnly = true,
            label = { Text(checkInLabel) },
            trailingIcon = {
                TextButton(onClick = { showCheckInPicker = true },
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("📅") }
            },
            modifier = Modifier.weight(1f), singleLine = true
        )
        OutlinedTextField(
            value = checkOut, onValueChange = {}, readOnly = true,
            label = { Text(checkOutLabel) },
            trailingIcon = {
                TextButton(onClick = { showCheckOutPicker = true },
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("📅") }
            },
            modifier = Modifier.weight(1f), singleLine = true
        )
    }

    if (showCheckInPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = checkIn.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            }
        )
        AppDatePickerDialog(
            onDismissRequest = { showCheckInPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        onCheckInChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showCheckInPicker = false
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { showCheckInPicker = false }) { Text(s.cancel) } }
        ) { DatePicker(state = dpState) }
    }

    if (showCheckOutPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = checkOut.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            }
        )
        AppDatePickerDialog(
            onDismissRequest = { showCheckOutPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        onCheckOutChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showCheckOutPicker = false
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { showCheckOutPicker = false }) { Text(s.cancel) } }
        ) { DatePicker(state = dpState) }
    }
}
