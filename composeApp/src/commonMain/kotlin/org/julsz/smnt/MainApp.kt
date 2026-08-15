package org.julsz.smnt

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import kotlin.math.roundToInt

private enum class AppScreen { Dashboard, Reservations, Statistics, Payouts, Config, Invoices, Settings }

private enum class StatBucket { Week, Month, Quarter }

/** [endExclusive] is the day after the period's last day. */
private data class StatPeriod(val start: LocalDate, val endExclusive: LocalDate, val label: String) {
    val isProvisional: Boolean get() = endExclusive.isAfter(LocalDate.now())
}

/** Rounds [date] down to the start of the calendar bucket it falls in (Monday for weeks, 1st for months/quarters). */
private fun alignBucketStart(date: LocalDate, bucket: StatBucket): LocalDate = when (bucket) {
    StatBucket.Week    -> date.with(DayOfWeek.MONDAY)
    StatBucket.Month   -> date.withDayOfMonth(1)
    StatBucket.Quarter -> LocalDate.of(date.year, ((date.monthValue - 1) / 3) * 3 + 1, 1)
}

/** Walks calendar-aligned buckets covering [rangeStart, rangeEndExclusive), clipping the first/last to the range. */
private fun buildPeriods(
    rangeStart: LocalDate,
    rangeEndExclusive: LocalDate,
    bucket: StatBucket,
    locale: java.util.Locale
): List<StatPeriod> {
    if (!rangeStart.isBefore(rangeEndExclusive)) return emptyList()
    val periods = mutableListOf<StatPeriod>()
    var cursor = alignBucketStart(rangeStart, bucket)
    while (cursor.isBefore(rangeEndExclusive)) {
        val bucketEnd = when (bucket) {
            StatBucket.Week    -> cursor.plusWeeks(1)
            StatBucket.Month   -> cursor.plusMonths(1)
            StatBucket.Quarter -> cursor.plusMonths(3)
        }
        val label = when (bucket) {
            StatBucket.Week    -> "${cursor.dayOfMonth} ${cursor.month.getDisplayName(TextStyle.SHORT, locale)}"
            StatBucket.Month   -> "${cursor.month.getDisplayName(TextStyle.SHORT, locale)} ${cursor.year}"
            StatBucket.Quarter -> "Q${(cursor.monthValue - 1) / 3 + 1} ${cursor.year}"
        }
        periods.add(StatPeriod(maxOf(cursor, rangeStart), minOf(bucketEnd, rangeEndExclusive), label))
        cursor = bucketEnd
    }
    return periods
}

/** Overlap nights and prorated revenue for [res] within [spanStart, spanEndExclusive). */
private fun overlapNightsAndRevenue(
    res: ReservationDto,
    spanStart: LocalDate,
    spanEndExclusive: LocalDate
): Pair<Long, Double> {
    val ci = LocalDate.parse(res.checkInDate)
    val co = LocalDate.parse(res.checkOutDate)
    val totalNights = (co.toEpochDay() - ci.toEpochDay()).coerceAtLeast(1)
    val overlap = (minOf(co, spanEndExclusive).toEpochDay() - maxOf(ci, spanStart).toEpochDay()).coerceAtLeast(0)
    val revenue = if (overlap > 0) (res.totalAmount ?: 0.0) * overlap.toDouble() / totalNights.toDouble() else 0.0
    return overlap to revenue
}

private enum class HistGroup { All, ByType, OneRoom }

private data class BarData(val total: Int, val segments: List<Pair<String, Int>>)

private val HIST_COLORS = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350),
    Color(0xFFAB47BC), Color(0xFFFF7043), Color(0xFF66BB6A),
    Color(0xFF29B6F6), Color(0xFFFFCA28)
)

val LocalSnackbar = compositionLocalOf { SnackbarHostState() }

@Composable
fun MainApp(
    client: HttpClient,
    currentUser: UserDto,
    selectedHotel: UserHotelRoleDto,
    onSwitchHotel: () -> Unit,
    onLogout: () -> Unit,
    isDark: Boolean = true,
    onThemeToggle: () -> Unit = {},
    fontScale: Float = 1.0f,
    onFontScaleChange: (Float) -> Unit = {},
    centerDays: Int = 30,
    onCenterDaysChange: (Int) -> Unit = {},
    noShowAfterDays: Int = 14,
    onNoShowAfterDaysChange: (Int) -> Unit = {},
    autoCheckOutAfterDays: Int = 3,
    onAutoCheckOutAfterDaysChange: (Int) -> Unit = {},
    language: AppLanguage = AppLanguage.English,
    onLanguageChange: (AppLanguage) -> Unit = {},
    timelineDayWidth: Float = 40f,
    onTimelineDayWidthChange: (Float) -> Unit = {},
    timelineRowHeight: Float = 34f,
    onTimelineRowHeightChange: (Float) -> Unit = {},
    timelineLabelWidth: Float = 96f,
    onTimelineLabelWidthChange: (Float) -> Unit = {},
    timelineShowRoomType: Boolean = true,
    onTimelineShowRoomTypeChange: (Boolean) -> Unit = {},
    serverMode: String = "localhost",
    onServerModeChange: (String) -> Unit = {},
    customServerUrl: String = "",
    onCustomServerUrlChange: (String) -> Unit = {},
    appVersionName: String? = null,
    appVersionCode: Int? = null,
    updateInfo: AppUpdateInfo? = null,
    updateChecking: Boolean = false,
    updateError: String? = null,
    updateDownloadProgress: Float? = null,
    updateManualOnly: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onDownloadAndInstallUpdate: () -> Unit = {}
) {
    var currentScreen          by remember { mutableStateOf(AppScreen.Dashboard) }
    var invoiceForReservation  by remember { mutableStateOf<ReservationDto?>(null) }
    val snackbarState = remember { SnackbarHostState() }
    var sidebarOpen            by remember { mutableStateOf(false) }

    // Hotel-scoped role gates — mirrors the server's per-hotel authorization
    // (admin: full access, manager: reservations only, viewer: read-only).
    val isHotelAdmin           = selectedHotel.role == "admin"
    val canManageReservations  = selectedHotel.role == "admin" || selectedHotel.role == "manager"

    LaunchedEffect(isHotelAdmin, currentScreen) {
        if (currentScreen == AppScreen.Config && !isHotelAdmin) currentScreen = AppScreen.Dashboard
    }

    CompositionLocalProvider(LocalSnackbar provides snackbarState) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Landscape phones / short windows: shrink the outer chrome so pages
            // (esp. the reservations timeline) keep most of the vertical space.
            val isCompactHeight = maxHeight < 480.dp
            // On the reservations timeline in compact height, the sidebar toggle moves
            // into the timeline's own top-left corner button — the outer bar would be redundant.
            val hideTopBar = isCompactHeight && currentScreen == AppScreen.Reservations
            // Main content column (full width, behind the overlay)
            Column(Modifier.fillMaxSize()) {
                if (!hideTopBar) {
                    // Top bar with hamburger button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .statusBarsPadding()
                            .height(if (isCompactHeight) 32.dp else 48.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isCompactHeight) 28.dp else 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { sidebarOpen = !sidebarOpen },
                            contentAlignment = Alignment.Center
                        ) {
                            HamburgerIcon(tint = MaterialTheme.colorScheme.onSurface)
                        }
                        if (!canManageReservations) {
                            val s = LocalStrings.current
                            Box(
                                Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    s.viewOnlyBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (!isCompactHeight) HorizontalDivider()
                }
                Box(Modifier.fillMaxSize().padding(if (hideTopBar) 4.dp else if (isCompactHeight) 8.dp else 28.dp)) {
                    when (currentScreen) {
                        AppScreen.Dashboard    -> DashboardPage(client = client, hotel = selectedHotel, noShowAfterDays = noShowAfterDays, autoCheckOutAfterDays = autoCheckOutAfterDays, canEdit = canManageReservations)
                        AppScreen.Reservations -> ReservationsCalendarPage(
                            client, selectedHotel, centerDays, noShowAfterDays, autoCheckOutAfterDays,
                            timelineDayWidth          = timelineDayWidth,
                            onTimelineDayWidthChange  = onTimelineDayWidthChange,
                            timelineRowHeight         = timelineRowHeight,
                            onTimelineRowHeightChange = onTimelineRowHeightChange,
                            timelineLabelWidth        = timelineLabelWidth,
                            onTimelineLabelWidthChange = onTimelineLabelWidthChange,
                            timelineShowRoomType      = timelineShowRoomType,
                            onCreateInvoice = { res -> invoiceForReservation = res; currentScreen = AppScreen.Invoices },
                            onViewInvoice   = { currentScreen = AppScreen.Invoices },
                            readOnly        = !canManageReservations,
                            compact         = isCompactHeight,
                            onOpenSidebar   = { sidebarOpen = !sidebarOpen }
                        )
                        AppScreen.Statistics   -> StatisticsPage(client = client, hotel = selectedHotel)
                        AppScreen.Payouts      -> ChannelPayoutsPage(client = client, hotel = selectedHotel, canEdit = canManageReservations)
                        AppScreen.Config       -> if (isHotelAdmin) ConfigPage(client, selectedHotel)
                        AppScreen.Invoices     -> InvoicePage(
                            client             = client,
                            hotel              = selectedHotel,
                            initialReservation = invoiceForReservation,
                            onInitialConsumed  = { invoiceForReservation = null },
                            fontScale          = fontScale
                        )
                        AppScreen.Settings     -> SettingsPage(
                            fontScale = fontScale, onFontScaleChange = onFontScaleChange,
                            centerDays = centerDays, onCenterDaysChange = onCenterDaysChange,
                            noShowAfterDays = noShowAfterDays, onNoShowAfterDaysChange = onNoShowAfterDaysChange,
                            autoCheckOutAfterDays = autoCheckOutAfterDays, onAutoCheckOutAfterDaysChange = onAutoCheckOutAfterDaysChange,
                            language = language, onLanguageChange = onLanguageChange,
                            timelineDayWidth = timelineDayWidth, onTimelineDayWidthChange = onTimelineDayWidthChange,
                            timelineRowHeight = timelineRowHeight, onTimelineRowHeightChange = onTimelineRowHeightChange,
                            timelineLabelWidth = timelineLabelWidth, onTimelineLabelWidthChange = onTimelineLabelWidthChange,
                            timelineShowRoomType = timelineShowRoomType, onTimelineShowRoomTypeChange = onTimelineShowRoomTypeChange,
                            serverMode = serverMode, onServerModeChange = onServerModeChange,
                            customServerUrl = customServerUrl, onCustomServerUrlChange = onCustomServerUrlChange,
                            appVersionName = appVersionName, appVersionCode = appVersionCode,
                            updateInfo = updateInfo, updateChecking = updateChecking, updateError = updateError,
                            updateDownloadProgress = updateDownloadProgress, updateManualOnly = updateManualOnly,
                            onCheckForUpdate = onCheckForUpdate, onDownloadAndInstallUpdate = onDownloadAndInstallUpdate
                        )
                    }
                }
            }

            // Scrim — dims content when sidebar is open
            AnimatedVisibility(
                visible = sidebarOpen,
                enter = fadeIn(),
                exit  = fadeOut()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { sidebarOpen = false }
                )
            }

            // Sidebar slides in from the left
            AnimatedVisibility(
                visible = sidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit  = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                AppSidebar(
                    currentUser    = currentUser,
                    selectedHotel  = selectedHotel,
                    showConfig     = isHotelAdmin,
                    currentScreen  = currentScreen,
                    onScreenChange = { screen -> currentScreen = screen; sidebarOpen = false },
                    onSwitchHotel  = { sidebarOpen = false; onSwitchHotel() },
                    onLogout       = { sidebarOpen = false; onLogout() },
                    isDark         = isDark,
                    onThemeToggle  = onThemeToggle,
                    modifier       = Modifier.shadow(elevation = 16.dp)
                )
            }

            SnackbarHost(
                hostState = snackbarState,
                modifier  = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            )
        }
    }
}

// ─── Sidebar ──────────────────────────────────────────────────────────────────

@Composable
private fun AppSidebar(
    currentUser: UserDto,
    selectedHotel: UserHotelRoleDto,
    showConfig: Boolean,
    currentScreen: AppScreen,
    onScreenChange: (AppScreen) -> Unit,
    onSwitchHotel: () -> Unit,
    onLogout: () -> Unit,
    isDark: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s  = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(cs.surfaceVariant),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Hotel header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        selectedHotel.hotelName.firstOrNull()?.uppercaseChar()?.toString() ?: "H",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        selectedHotel.hotelName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        selectedHotel.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = cs.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))

            AppScreen.entries.filter { it != AppScreen.Config || showConfig }.forEach { screen ->
                val label = when (screen) {
                    AppScreen.Dashboard    -> s.navDashboard
                    AppScreen.Reservations -> s.navReservations
                    AppScreen.Statistics   -> s.statsTitle
                    AppScreen.Payouts      -> s.navPayouts
                    AppScreen.Config       -> s.navConfig
                    AppScreen.Invoices     -> s.navInvoices
                    AppScreen.Settings     -> s.navSettings
                }
                SidebarItem(
                    label    = label,
                    selected = currentScreen == screen,
                    onClick  = { onScreenChange(screen) }
                )
            }
        }

        Column {
            HorizontalDivider(color = cs.outline.copy(alpha = 0.5f))
            // User info with avatar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        currentUser.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        currentUser.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        currentUser.email,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(color = cs.outline.copy(alpha = 0.5f))
            SidebarItem(label = s.switchHotel, selected = false, onClick = onSwitchHotel)
            SidebarItem(label = s.logout,      selected = false, onClick = onLogout)
            // Theme toggle pill
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surface)
                        .padding(2.dp)
                ) {
                    listOf(true to s.themeDark, false to s.themeLight).forEach { (dark, label) ->
                        val selected = isDark == dark
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (selected) cs.primary
                                    else Color.Transparent
                                )
                                .clickable(enabled = !selected, onClick = onThemeToggle)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) cs.onPrimary else cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SidebarItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) cs.primary.copy(alpha = 0.10f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(40.dp)
                .background(
                    if (selected) cs.primary else Color.Transparent,
                    RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
                )
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
        )
    }
}

@Composable
fun HamburgerIcon(tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.size(20.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(tint, RoundedCornerShape(1.dp)))
        }
    }
}

// ─── Pages ────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardPage(client: HttpClient, hotel: UserHotelRoleDto, noShowAfterDays: Int = 14, autoCheckOutAfterDays: Int = 3, canEdit: Boolean = true) {
    val scope    = rememberCoroutineScope()
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    var reservations by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var rooms        by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    val today    = remember { LocalDate.now() }
    val todayStr = remember { today.toString() }

    suspend fun reload() {
        loading = true
        try {
            reservations = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            rooms        = client.get("$BASE_URL/api/rooms?hotelId=${hotel.hotelId}").body<List<RoomDto>>()
                .filter { it.archivedAt == null }

            val cutoff = LocalDate.now().minusDays(noShowAfterDays.toLong())
            val autoNoShow = reservations.filter { r ->
                r.status in setOf("pending", "confirmed") &&
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
        } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
        loading = false
    }

    LaunchedEffect(hotel.hotelId) { reload() }

    val arrivals   = remember(reservations, todayStr) { reservations.filter { it.checkInDate  == todayStr } }
    val departures = remember(reservations, todayStr) { reservations.filter { it.checkOutDate == todayStr } }
    val overdue    = remember(reservations, todayStr) {
        reservations
            .filter { r ->
                r.status in setOf("pending", "confirmed") &&
                LocalDate.parse(r.checkInDate).isBefore(today)
            }
            .sortedBy { it.checkInDate }
    }
    val overdueCheckOuts = remember(reservations, todayStr) {
        reservations
            .filter { r ->
                r.status == "checked_in" &&
                LocalDate.parse(r.checkOutDate).isBefore(today)
            }
            .sortedBy { it.checkOutDate }
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────
    val activeStatuses = setOf("pending", "confirmed", "checked_in")
    val checkedInCount = remember(reservations) { reservations.count { it.status == "checked_in" } }
    val totalRooms     = rooms.size
    val occupancyPct   = if (totalRooms > 0) (checkedInCount * 100) / totalRooms else 0

    val thisYear  = today.year
    val thisMonth = today.monthValue
    val monthStart = LocalDate.of(thisYear, thisMonth, 1)
    val monthEnd   = monthStart.plusMonths(1)
    val monthCollected = remember(reservations, thisYear, thisMonth) {
        reservations
            .filter { r ->
                r.status !in setOf("cancelled", "no_show") &&
                LocalDate.parse(r.checkInDate).isBefore(monthEnd) &&
                LocalDate.parse(r.checkOutDate).isAfter(monthStart)
            }
            .sumOf { it.paidAmount }
    }

    val next7 = remember(reservations, todayStr) {
        val end = today.plusDays(7)
        reservations.count { r ->
            r.status in activeStatuses &&
            LocalDate.parse(r.checkInDate).let { !it.isBefore(today) && it.isBefore(end) }
        }
    }

    val pendingDpCount = remember(reservations) {
        reservations.count { r ->
            r.status !in setOf("cancelled", "no_show") &&
            r.requiresDownPayment &&
            r.paidAmount < (r.downPaymentAmount ?: Double.MAX_VALUE)
        }
    }

    fun updateStatus(res: ReservationDto, newStatus: String) {
        if (!canEdit) return
        scope.launch {
            try {
                client.put("$BASE_URL/api/reservations/${res.id}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateReservationRequest(
                        roomId = res.roomId, guestId = res.guestId,
                        checkInDate = res.checkInDate, checkOutDate = res.checkOutDate,
                        status = newStatus, adults = res.adults,
                        totalAmount = res.totalAmount, description = res.description
                    ))
                }
                reload()
            } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (isWide) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(hotel.hotelName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                // ── Stat cards ────────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title      = s.statCheckedIn,
                        value      = if (totalRooms > 0) "$checkedInCount / $totalRooms" else "$checkedInCount",
                        subtitle   = if (totalRooms > 0) "$occupancyPct% ${s.statOccupancy}" else "",
                        valueColor = if (checkedInCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier   = Modifier.weight(1f)
                    )
                    StatCard(
                        title    = s.statMonthRevenue,
                        value    = "${"%.0f".format(monthCollected)} PLN",
                        subtitle = Month.of(thisMonth).getDisplayName(TextStyle.FULL, s.locale),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title    = s.statUpcoming7d,
                        value    = "$next7",
                        subtitle = s.arrivals,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title      = s.statPendingDp,
                        value      = "$pendingDpCount",
                        subtitle   = s.reservationsTitle,
                        valueColor = if (pendingDpCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier   = Modifier.weight(1f)
                    )
                }
                // ── Arrivals / Departures / Overdue ───────────────────────────
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DashboardTile(
                        title        = s.arrivals,
                        date         = todayStr,
                        pending      = arrivals.filter { it.status in listOf("confirmed", "pending") },
                        done         = arrivals.filter { it.status == "checked_in" },
                        pendingLabel = s.notArrived,
                        doneLabel    = s.arrived,
                        emptyLabel   = s.noArrivalsToday,
                        onAction     = { updateStatus(it, "checked_in") },
                        canEdit      = canEdit,
                        modifier     = Modifier.weight(1f)
                    )
                    DashboardTile(
                        title        = s.departures,
                        date         = todayStr,
                        pending      = departures.filter { it.status == "checked_in" },
                        done         = departures.filter { it.status == "checked_out" },
                        pendingLabel = s.notDeparted,
                        doneLabel    = s.departed,
                        emptyLabel   = s.noDeparturesToday,
                        onAction     = { updateStatus(it, "checked_out") },
                        canEdit      = canEdit,
                        modifier     = Modifier.weight(1f)
                    )
                    OverdueTile(
                        reservations = overdue,
                        onSetStatus  = { res, newStatus -> updateStatus(res, newStatus) },
                        modifier     = Modifier.weight(1f),
                        canEdit      = canEdit
                    )
                    OverdueCheckOutsTile(
                        reservations = overdueCheckOuts,
                        onSetStatus  = { res, newStatus -> updateStatus(res, newStatus) },
                        modifier     = Modifier.weight(1f),
                        canEdit      = canEdit
                    )
                }
            }
        } else {
            // ── Narrow (portrait / mobile) — scrollable stacked layout ────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Text(hotel.hotelName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title      = s.statCheckedIn,
                                value      = if (totalRooms > 0) "$checkedInCount / $totalRooms" else "$checkedInCount",
                                subtitle   = if (totalRooms > 0) "$occupancyPct% ${s.statOccupancy}" else "",
                                valueColor = if (checkedInCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier   = Modifier.weight(1f)
                            )
                            StatCard(
                                title    = s.statMonthRevenue,
                                value    = "${"%.0f".format(monthCollected)} PLN",
                                subtitle = Month.of(thisMonth).getDisplayName(TextStyle.FULL, s.locale),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                title    = s.statUpcoming7d,
                                value    = "$next7",
                                subtitle = s.arrivals,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title      = s.statPendingDp,
                                value      = "$pendingDpCount",
                                subtitle   = s.reservationsTitle,
                                valueColor = if (pendingDpCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier   = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item {
                    DashboardTile(
                        title        = s.arrivals,
                        date         = todayStr,
                        pending      = arrivals.filter { it.status in listOf("confirmed", "pending") },
                        done         = arrivals.filter { it.status == "checked_in" },
                        pendingLabel = s.notArrived,
                        doneLabel    = s.arrived,
                        emptyLabel   = s.noArrivalsToday,
                        onAction     = { updateStatus(it, "checked_in") },
                        canEdit      = canEdit,
                        modifier     = Modifier.fillMaxWidth()
                    )
                }
                item {
                    DashboardTile(
                        title        = s.departures,
                        date         = todayStr,
                        pending      = departures.filter { it.status == "checked_in" },
                        done         = departures.filter { it.status == "checked_out" },
                        pendingLabel = s.notDeparted,
                        doneLabel    = s.departed,
                        emptyLabel   = s.noDeparturesToday,
                        onAction     = { updateStatus(it, "checked_out") },
                        canEdit      = canEdit,
                        modifier     = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OverdueTile(
                        reservations = overdue,
                        onSetStatus  = { res, newStatus -> updateStatus(res, newStatus) },
                        modifier     = Modifier.fillMaxWidth(),
                        scrollable   = false,
                        canEdit      = canEdit
                    )
                }
                item {
                    OverdueCheckOutsTile(
                        reservations = overdueCheckOuts,
                        onSetStatus  = { res, newStatus -> updateStatus(res, newStatus) },
                        modifier     = Modifier.fillMaxWidth(),
                        scrollable   = false,
                        canEdit      = canEdit
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier  = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = BorderStroke(1.dp, cs.outlineVariant),
        colors    = CardDefaults.cardColors(containerColor = cs.surface),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardTile(
    title: String,
    date: String,
    pending: List<ReservationDto>,
    done: List<ReservationDto>,
    pendingLabel: String,
    doneLabel: String,
    emptyLabel: String,
    onAction: (ReservationDto) -> Unit,
    modifier: Modifier = Modifier,
    canEdit: Boolean = true
) {
    val s  = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Card(
        modifier  = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = BorderStroke(1.dp, cs.outlineVariant),
        colors    = CardDefaults.cardColors(containerColor = cs.surface),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(date, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            HorizontalDivider(color = cs.outlineVariant)

            if (pending.isEmpty() && done.isEmpty()) {
                Text(emptyLabel, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            } else {
                if (pending.isNotEmpty()) {
                    Text("$pendingLabel (${pending.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurfaceVariant)
                    pending.forEach { res ->
                        val noteSnippet = res.description?.takeIf { it.isNotBlank() }?.replace("\n", "; ")
                        val isExternal = res.source == "external"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(res.guestName, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                                Text(
                                    buildString {
                                        append(s.roomShort(res.roomNumber))
                                        if (isExternal) append(" · ${s.dragModeExternal}")
                                        res.totalAmount?.let { append(" · ${"%.0f".format(it)} PLN") }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                                if (noteSnippet != null) {
                                    Text(noteSnippet, style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (canEdit) {
                                IconButton(onClick = { onAction(res) }, modifier = Modifier.size(36.dp)) {
                                    Text("✓", style = MaterialTheme.typography.titleSmall, color = cs.primary)
                                }
                            }
                        }
                    }
                }
                if (done.isNotEmpty()) {
                    if (pending.isNotEmpty()) HorizontalDivider(color = cs.outlineVariant)
                    Text("$doneLabel (${done.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurfaceVariant)
                    done.forEach { res ->
                        val noteSnippetDone = res.description?.takeIf { it.isNotBlank() }?.replace("\n", "; ")
                        val isExternalDone = res.source == "external"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(res.guestName, style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant)
                                Text(
                                    buildString {
                                        append(s.roomShort(res.roomNumber))
                                        if (isExternalDone) append(" · ${s.dragModeExternal}")
                                        res.totalAmount?.let { append(" · ${"%.0f".format(it)} PLN") }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                if (noteSnippetDone != null) {
                                    Text(noteSnippetDone, style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text("✓", style = MaterialTheme.typography.bodyMedium,
                                color = cs.primary.copy(alpha = 0.5f),
                                modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverdueTile(
    reservations: List<ReservationDto>,
    onSetStatus: (ReservationDto, String) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    canEdit: Boolean = true
) {
    val s        = LocalStrings.current
    val cs       = MaterialTheme.colorScheme
    val hasItems = reservations.isNotEmpty()

    Card(
        modifier  = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = BorderStroke(1.dp, if (hasItems) cs.error.copy(alpha = 0.45f) else cs.outlineVariant),
        colors    = CardDefaults.cardColors(
            containerColor = if (hasItems) cs.errorContainer.copy(alpha = 0.12f) else cs.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s.overdueCheckIns,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (hasItems) {
                    Surface(shape = RoundedCornerShape(10.dp), color = cs.error) {
                        Text(
                            "${reservations.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            HorizontalDivider(color = if (hasItems) cs.error.copy(alpha = 0.2f) else cs.outlineVariant)

            if (reservations.isEmpty()) {
                Text(s.noOverdueCheckIns, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            } else {
                Column(
                    modifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    reservations.forEach { res ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(res.guestName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                "${s.roomShort(res.roomNumber)} · ${res.checkInDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                            if (canEdit) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("checked_in", "no_show", "cancelled").forEach { code ->
                                        OutlinedButton(
                                            onClick = { onSetStatus(res, code) },
                                            modifier = Modifier.weight(1f).height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(s.statusName(code), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverdueCheckOutsTile(
    reservations: List<ReservationDto>,
    onSetStatus: (ReservationDto, String) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    canEdit: Boolean = true
) {
    val s        = LocalStrings.current
    val cs       = MaterialTheme.colorScheme
    val hasItems = reservations.isNotEmpty()

    Card(
        modifier  = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = BorderStroke(1.dp, if (hasItems) cs.tertiary.copy(alpha = 0.45f) else cs.outlineVariant),
        colors    = CardDefaults.cardColors(
            containerColor = if (hasItems) cs.tertiaryContainer.copy(alpha = 0.12f) else cs.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s.overdueCheckOuts,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (hasItems) {
                    Surface(shape = RoundedCornerShape(10.dp), color = cs.tertiary) {
                        Text(
                            "${reservations.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            HorizontalDivider(color = if (hasItems) cs.tertiary.copy(alpha = 0.2f) else cs.outlineVariant)

            if (reservations.isEmpty()) {
                Text(s.noOverdueCheckOuts, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            } else {
                Column(
                    modifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    reservations.forEach { res ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(res.guestName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                "${s.roomShort(res.roomNumber)} · ${res.checkOutDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                            if (canEdit) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("checked_out", "cancelled").forEach { code ->
                                        OutlinedButton(
                                            onClick = { onSetStatus(res, code) },
                                            modifier = Modifier.weight(1f).height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(s.statusName(code), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Statistics page + section ────────────────────────────────────────────────

private enum class CompareMode { PreviousPeriod, SameLastYear }
private enum class RangeAnchor { Today, MonthEnd }
private enum class StatusScope { AllActive, ConfirmedPlus, Completed }

private fun applyStatusScope(list: List<ReservationDto>, scope: StatusScope): List<ReservationDto> = when (scope) {
    StatusScope.AllActive     -> list.filter { it.status !in setOf("cancelled", "no_show") }
    StatusScope.ConfirmedPlus -> list.filter { it.status in setOf("confirmed", "checked_in", "checked_out") }
    StatusScope.Completed     -> list.filter { it.status == "checked_out" }
}

@Composable
private fun StatisticsPage(client: HttpClient, hotel: UserHotelRoleDto) {
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    var reservations by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var rooms        by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var payouts      by remember { mutableStateOf<List<ChannelPayoutDto>>(emptyList()) }
    var overrideList by remember { mutableStateOf<List<ChannelPayoutOverrideDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }

    LaunchedEffect(hotel.hotelId) {
        loading = true
        try {
            reservations = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            rooms = client.get("$BASE_URL/api/rooms?hotelId=${hotel.hotelId}").body<List<RoomDto>>()
                .filter { it.archivedAt == null }
            payouts      = client.get("$BASE_URL/api/channel-payouts?hotelId=${hotel.hotelId}").body()
            overrideList = client.get("$BASE_URL/api/channel-payout-overrides?hotelId=${hotel.hotelId}").body()
        } catch (e: Exception) {
            snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
        }
        loading = false
    }

    if (loading) {
        CircularProgressIndicator()
    } else {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            StatisticsSection(
                reservations = reservations,
                rooms        = rooms,
                payouts      = payouts,
                overrideList = overrideList
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatisticsSection(
    reservations: List<ReservationDto>,
    rooms: List<RoomDto>,
    payouts: List<ChannelPayoutDto>,
    overrideList: List<ChannelPayoutOverrideDto>
) {
    val s     = LocalStrings.current
    val today = remember { LocalDate.now() }

    var bucket          by remember { mutableStateOf(StatBucket.Month) }
    var rangeStart       by remember { mutableStateOf(alignBucketStart(today, StatBucket.Month).minusMonths(2)) }
    var rangeEnd         by remember { mutableStateOf(today) }
    var showRangePicker  by remember { mutableStateOf(false) }
    var rangeAnchor      by remember { mutableStateOf(RangeAnchor.Today) }
    var compareMode      by remember { mutableStateOf(CompareMode.PreviousPeriod) }
    var histGroup        by remember { mutableStateOf(HistGroup.All) }
    var selectedRoom     by remember { mutableStateOf<RoomDto?>(null) }
    var maxGuestsFilter  by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var singleRoomFilter by remember { mutableStateOf<RoomDto?>(null) }
    var sourceFilter     by remember { mutableStateOf<Set<String>>(emptySet()) }
    var statusScope      by remember { mutableStateOf(StatusScope.AllActive) }
    var trendMetric      by remember { mutableStateOf(TrendMetric.Occupancy) }

    val rangeEndExclusive = remember(rangeEnd) { rangeEnd.plusDays(1) }
    val anchorEnd = if (rangeAnchor == RangeAnchor.Today) today else today.withDayOfMonth(today.lengthOfMonth())

    // Re-snaps the visible window's end to the current anchor whenever the toggle changes,
    // keeping the window length fixed — so flipping the toggle updates the range picker immediately.
    LaunchedEffect(rangeAnchor) {
        val len = rangeEnd.toEpochDay() - rangeStart.toEpochDay()
        rangeEnd = anchorEnd
        rangeStart = anchorEnd.minusDays(len)
    }

    val filteredRooms = remember(rooms, maxGuestsFilter, singleRoomFilter) {
        rooms
            .filter { maxGuestsFilter.isEmpty() || it.maxGuests in maxGuestsFilter }
            .filter { singleRoomFilter == null || it.id == singleRoomFilter?.id }
            .sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number }))
    }
    val filteredRoomIds = remember(filteredRooms) { filteredRooms.map { it.id }.toSet() }

    val maxGuestsOptions = remember(rooms) { rooms.map { it.maxGuests }.distinct().sorted() }
    val sourceOptions = remember(reservations) {
        reservations.map { it.sourceName?.takeIf { n -> n.isNotBlank() } ?: it.source }.distinct().sorted()
    }
    val allRoomsSorted = remember(rooms) {
        rooms.sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number }))
    }

    // room+source filtered, all statuses — the "all" baseline used for cancel-rate math
    val roomSourceFiltered = remember(reservations, filteredRoomIds, sourceFilter) {
        reservations.filter {
            it.roomId in filteredRoomIds &&
                (sourceFilter.isEmpty() || (it.sourceName?.takeIf { n -> n.isNotBlank() } ?: it.source) in sourceFilter)
        }
    }
    val active = remember(roomSourceFiltered, statusScope) { applyStatusScope(roomSourceFiltered, statusScope) }

    val periods = remember(rangeStart, rangeEndExclusive, bucket, s.locale) {
        buildPeriods(rangeStart, rangeEndExclusive, bucket, s.locale)
    }

    val kpis = remember(roomSourceFiltered, active, filteredRooms, rangeStart, rangeEndExclusive) {
        computeKpisForWindow(roomSourceFiltered, active, filteredRooms, rangeStart, rangeEndExclusive)
    }
    val periodKpis = remember(roomSourceFiltered, active, filteredRooms, periods) {
        periods.map { PeriodKpi(it, computeKpisForWindow(roomSourceFiltered, active, filteredRooms, it.start, it.endExclusive)) }
    }
    val comparisonKpis = remember(roomSourceFiltered, active, filteredRooms, rangeStart, rangeEndExclusive, compareMode) {
        val lenDays = rangeEndExclusive.toEpochDay() - rangeStart.toEpochDay()
        val (cStart, cEnd) = when (compareMode) {
            CompareMode.PreviousPeriod -> rangeStart.minusDays(lenDays) to rangeStart
            CompareMode.SameLastYear   -> rangeStart.minusYears(1) to rangeEndExclusive.minusYears(1)
        }
        computeKpisForWindow(roomSourceFiltered, active, filteredRooms, cStart, cEnd)
    }

    val sourceCountBreakdown = remember(active, rangeStart, rangeEndExclusive) {
        active.filter {
            val ci = LocalDate.parse(it.checkInDate)
            !ci.isBefore(rangeStart) && ci.isBefore(rangeEndExclusive)
        }.groupingBy { it.sourceName?.takeIf { n -> n.isNotBlank() } ?: it.source }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }

    val revenueByCapacity = remember(active, filteredRooms, rangeStart, rangeEndExclusive, s) {
        val capacityById = filteredRooms.associate { it.id to it.maxGuests }
        val acc = mutableMapOf<Int, Double>()
        active.forEach { res ->
            val capacity = capacityById[res.roomId] ?: return@forEach
            val (_, revenue) = overlapNightsAndRevenue(res, rangeStart, rangeEndExclusive)
            if (revenue != 0.0) acc[capacity] = (acc[capacity] ?: 0.0) + revenue
        }
        acc.toList().sortedBy { it.first }.map { (n, revenue) -> s.statsMaxGuestsLabel(n) to revenue }
    }

    val revenueBySource = remember(active, rangeStart, rangeEndExclusive) {
        val acc = mutableMapOf<String, Double>()
        active.forEach { res ->
            val (_, revenue) = overlapNightsAndRevenue(res, rangeStart, rangeEndExclusive)
            if (revenue != 0.0) {
                val key = res.sourceName?.takeIf { it.isNotBlank() } ?: res.source
                acc[key] = (acc[key] ?: 0.0) + revenue
            }
        }
        acc.toList().sortedByDescending { it.second }
    }

    val channelSummaries = remember(reservations, payouts, overrideList, rangeStart, rangeEndExclusive) {
        val overrides = PayoutOverrides.from(overrideList)
        buildPayoutSummaries(reservations, payouts, overrides)
            .filter {
                val monthStart = it.month.atDay(1)
                monthStart.isBefore(rangeEndExclusive) && monthStart.plusMonths(1).isAfter(rangeStart)
            }
            .sortedBy { it.month }
    }

    val weekdayStats = remember(active, filteredRooms, rangeStart, rangeEndExclusive) {
        computeWeekdayBreakdown(active, filteredRooms, rangeStart, rangeEndExclusive)
    }

    val dailyOccupancy = remember(active, filteredRooms, rangeStart, rangeEndExclusive) {
        computeDailyOccupancy(active, filteredRooms, rangeStart, rangeEndExclusive)
    }

    val payoutIntegrity = remember(reservations, overrideList) {
        computePayoutIntegrity(reservations, PayoutOverrides.from(overrideList))
    }

    val healthAlerts = remember(kpis, comparisonKpis, payoutIntegrity, s) {
        buildList {
            if (!payoutIntegrity.isSound) {
                add(s.statsAlertUnaccounted(payoutIntegrity.unaccounted))
            }
            comparisonKpis?.let { prev ->
                if (prev.occupancyRate > 0.0) {
                    val delta = (kpis.occupancyRate - prev.occupancyRate) / prev.occupancyRate
                    if (delta <= -0.15) add(s.statsAlertMetricDrop(s.statsKpiOccupancy, (-delta * 100).roundToInt()))
                }
                if (prev.revenue > 0.0) {
                    val delta = (kpis.revenue - prev.revenue) / prev.revenue
                    if (delta <= -0.15) add(s.statsAlertMetricDrop(s.statsKpiRevenue, (-delta * 100).roundToInt()))
                }
                if (prev.cancelRate > 0.0 && kpis.cancelRate - prev.cancelRate >= 0.10) {
                    add(s.statsAlertCancelSpike((kpis.cancelRate * 100).roundToInt()))
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(s.statsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        HealthBanner(healthAlerts)

        // bucket selector
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(s.statsTimeSpan, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(StatBucket.Week to s.statsBucketWeek, StatBucket.Month to s.statsBucketMonth,
                       StatBucket.Quarter to s.statsBucketQuarter).forEach { (b, label) ->
                    val sel = bucket == b
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { bucket = b }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // range controls — presets/anchor/custom scroll horizontally on their own row so they
        // never get squeezed into a shrinking leftover width; prev/date/next get a separate row.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(1, 3, 6, 9, 12).forEach { n ->
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val alignedEnd = alignBucketStart(today, bucket)
                                rangeStart = when (bucket) {
                                    StatBucket.Week    -> alignedEnd.minusWeeks((n - 1).toLong())
                                    StatBucket.Month   -> alignedEnd.minusMonths((n - 1).toLong())
                                    StatBucket.Quarter -> alignedEnd.minusMonths((n - 1).toLong() * 3)
                                }
                                rangeEnd = anchorEnd
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(s.statsMonthsLabel(n), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(RangeAnchor.Today to s.statsQuantizeToToday, RangeAnchor.MonthEnd to s.statsQuantizeToMonthEnd)
                    .forEach { (anchor, label) ->
                        val sel = rangeAnchor == anchor
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { rangeAnchor = anchor }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
            }
            OutlinedButton(
                onClick        = { showRangePicker = true },
                modifier       = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(s.statsCustomRange, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = {
                    val len = rangeEndExclusive.toEpochDay() - rangeStart.toEpochDay()
                    val newEnd = rangeStart.minusDays(1)
                    rangeStart = rangeStart.minusDays(len)
                    rangeEnd = newEnd
                },
                modifier = Modifier.size(32.dp)
            ) {
                Text("‹", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "${rangeStart.dayOfMonth} ${rangeStart.month.getDisplayName(TextStyle.SHORT, s.locale)} ${rangeStart.year} – " +
                    "${rangeEnd.dayOfMonth} ${rangeEnd.month.getDisplayName(TextStyle.SHORT, s.locale)} ${rangeEnd.year}",
                style = MaterialTheme.typography.labelMedium, maxLines = 1
            )
            IconButton(
                onClick = {
                    val len = rangeEndExclusive.toEpochDay() - rangeStart.toEpochDay()
                    val newStart = rangeEndExclusive
                    rangeEnd = rangeEndExclusive.plusDays(len - 1)
                    rangeStart = newStart
                },
                modifier = Modifier.size(32.dp)
            ) {
                Text("›", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (showRangePicker) {
            AppRangePickerDialog(
                startDate = rangeStart.toString(),
                endDate   = rangeEnd.toString(),
                onDismiss = { showRangePicker = false },
                onConfirm = { a, b ->
                    rangeStart = LocalDate.parse(a)
                    rangeEnd   = LocalDate.parse(b)
                    showRangePicker = false
                }
            )
        }

        // filters row — dropdowns and the status-scope group each get their own scrollable
        // row instead of sharing one with a weight-spacer, which squeezes whichever comes last.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsFilterDropdown(s.statsFilterMaxGuests, maxGuestsOptions, maxGuestsFilter, s::statsMaxGuestsLabel) { maxGuestsFilter = it }
            StatsSingleRoomDropdown(s.statsFilterRoom, allRoomsSorted, singleRoomFilter) { singleRoomFilter = it }
            StatsFilterDropdown(s.statsFilterSource, sourceOptions, sourceFilter, { it }) { sourceFilter = it }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(StatusScope.AllActive to s.statsScopeActive, StatusScope.ConfirmedPlus to s.statsScopeConfirmed,
                       StatusScope.Completed to s.statsScopeCompleted).forEach { (scope, label) ->
                    val sel = statusScope == scope
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { statusScope = scope }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (periods.any { it.isProvisional }) {
            Text(s.statsProvisionalNote, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary)
        }

        // comparison toggle
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(s.statsCompareLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(CompareMode.PreviousPeriod to s.statsComparePrevious,
                       CompareMode.SameLastYear to s.statsCompareLastYear).forEach { (mode, label) ->
                    val sel = compareMode == mode
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { compareMode = mode }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        KpiTilesRow(kpis, periodKpis, comparisonKpis)

        Spacer(Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.statsTrends, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                StatsExportCsvButton(periodKpis)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    TrendMetric.entries.forEach { metric ->
                        val sel = trendMetric == metric
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { trendMetric = metric }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(metric.label(s), style = MaterialTheme.typography.labelSmall,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
        TrendLineChart(periodKpis, trendMetric)

        Spacer(Modifier.height(4.dp))

        Text(s.statsNightsTable, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NightsTable(rooms = filteredRooms, periods = periods, reservations = active)
        Spacer(Modifier.height(6.dp))
        ViridisLegend()

        Spacer(Modifier.height(4.dp))

        if (revenueByCapacity.isNotEmpty()) {
            Text(s.statsRevenueByCapacity, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BreakdownBars(revenueByCapacity) { "${"%.0f".format(it)} PLN" }
            Spacer(Modifier.height(4.dp))
        }

        if (revenueBySource.isNotEmpty()) {
            Text(s.statsRevenueBySource, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BreakdownBars(revenueBySource) { "${"%.0f".format(it)} PLN" }
            Spacer(Modifier.height(4.dp))
        }

        if (sourceCountBreakdown.isNotEmpty()) {
            Text(s.statsBySource, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BreakdownBars(sourceCountBreakdown.map { it.first to it.second.toDouble() }) { "${it.roundToInt()}" }
            Spacer(Modifier.height(4.dp))
        }

        if (dailyOccupancy.isNotEmpty()) {
            Text(s.statsCalendarHeatmap, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OccupancyCalendarHeatmap(dailyOccupancy)
            Spacer(Modifier.height(6.dp))
            ViridisLegend()
            Spacer(Modifier.height(4.dp))
        }

        if (channelSummaries.isNotEmpty()) {
            Text(s.statsChannelPayouts, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChannelPayoutTable(channelSummaries)
            Spacer(Modifier.height(4.dp))
        }

        if (weekdayStats.isNotEmpty()) {
            Text(s.statsWeekdayBreakdown, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WeekdayBreakdownBars(weekdayStats)
            Spacer(Modifier.height(4.dp))
        }

        // histogram controls — title on its own line, controls in a horizontally scrollable
        // row below, so narrow screens scroll instead of squeezing button labels onto 3 lines.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.statsHistogram, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    listOf(HistGroup.All to s.statsGroupAll, HistGroup.ByType to s.statsGroupByType,
                           HistGroup.OneRoom to s.statsGroupOneRoom).forEach { (grp, label) ->
                        val sel = histGroup == grp
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { histGroup = grp }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (histGroup == HistGroup.OneRoom) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick        = { expanded = true },
                            modifier       = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(selectedRoom?.let { s.roomShort(it.number) } ?: s.selectRoomHint,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            filteredRooms.forEach { room ->
                                DropdownMenuItem(
                                    text    = { Text("${s.roomShort(room.number)} (${room.typeName})") },
                                    onClick = { selectedRoom = room; expanded = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        NightsHistogram(
            reservations = active,
            rooms        = filteredRooms,
            spanStart    = rangeStart,
            spanEndExclusive = rangeEndExclusive,
            group        = histGroup,
            selectedRoom = if (histGroup == HistGroup.OneRoom) selectedRoom else null
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HealthBanner(alerts: List<String>) {
    if (alerts.isEmpty()) return
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        alerts.forEach { msg ->
            Text(
                "⚠ $msg",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun <T> StatsFilterDropdown(
    label: String,
    options: List<T>,
    selected: Set<T>,
    optionLabel: (T) -> String,
    onChange: (Set<T>) -> Unit
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick        = { expanded = true },
            modifier       = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(if (selected.isEmpty()) label else "$label (${selected.size})",
                style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                val checked = opt in selected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(optionLabel(opt), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    onClick = { onChange(if (checked) selected - opt else selected + opt) }
                )
            }
            if (selected.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text    = { Text(s.statsClearFilter, style = MaterialTheme.typography.labelSmall) },
                    onClick = { onChange(emptySet()) }
                )
            }
        }
    }
}

@Composable
private fun StatsSingleRoomDropdown(
    label: String,
    rooms: List<RoomDto>,
    selected: RoomDto?,
    onChange: (RoomDto?) -> Unit
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick        = { expanded = true },
            modifier       = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(selected?.let { "$label: ${s.roomShort(it.number)}" } ?: label,
                style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            rooms.forEach { room ->
                DropdownMenuItem(
                    text    = { Text("${s.roomShort(room.number)} (${room.typeName})", style = MaterialTheme.typography.labelSmall) },
                    onClick = { onChange(room); expanded = false }
                )
            }
            if (selected != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text    = { Text(s.statsClearFilter, style = MaterialTheme.typography.labelSmall) },
                    onClick = { onChange(null); expanded = false }
                )
            }
        }
    }
}

private data class StatsKpis(
    val occupancyRate: Double,
    val revenue: Double,
    val adr: Double,
    val revPar: Double,
    val avgStayNights: Double,
    val cancelRate: Double
)

private data class PeriodKpi(val period: StatPeriod, val kpis: StatsKpis)

private fun computeKpisForWindow(
    all: List<ReservationDto>,
    active: List<ReservationDto>,
    rooms: List<RoomDto>,
    spanStart: LocalDate,
    spanEnd: LocalDate
): StatsKpis {
    if (rooms.isEmpty() || !spanStart.isBefore(spanEnd)) {
        return StatsKpis(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
    val spanDays  = (spanEnd.toEpochDay() - spanStart.toEpochDay()).coerceAtLeast(1)

    var nightsInSpan = 0L
    var revenue = 0.0
    var checkInsInSpan = 0
    var nightsSumForCheckIns = 0L

    active.forEach { res ->
        val ci = LocalDate.parse(res.checkInDate)
        val co = LocalDate.parse(res.checkOutDate)
        val totalNights = (co.toEpochDay() - ci.toEpochDay()).coerceAtLeast(1)
        val (overlapNights, overlapRevenue) = overlapNightsAndRevenue(res, spanStart, spanEnd)
        if (overlapNights > 0) {
            nightsInSpan += overlapNights
            revenue += overlapRevenue
        }
        if (!ci.isBefore(spanStart) && ci.isBefore(spanEnd)) {
            checkInsInSpan += 1
            nightsSumForCheckIns += totalNights
        }
    }

    val availableRoomNights = rooms.size.toLong() * spanDays
    val occupancyRate = if (availableRoomNights > 0) nightsInSpan.toDouble() / availableRoomNights else 0.0
    val adr    = if (nightsInSpan > 0) revenue / nightsInSpan else 0.0
    val revPar = if (availableRoomNights > 0) revenue / availableRoomNights else 0.0
    val avgStay = if (checkInsInSpan > 0) nightsSumForCheckIns.toDouble() / checkInsInSpan else 0.0

    val allCheckInsInSpan = all.count {
        val ci = LocalDate.parse(it.checkInDate)
        !ci.isBefore(spanStart) && ci.isBefore(spanEnd)
    }
    val cancelledInSpan = all.count {
        it.status in setOf("cancelled", "no_show") &&
            LocalDate.parse(it.checkInDate).let { ci -> !ci.isBefore(spanStart) && ci.isBefore(spanEnd) }
    }
    val cancelRate = if (allCheckInsInSpan > 0) cancelledInSpan.toDouble() / allCheckInsInSpan else 0.0

    return StatsKpis(occupancyRate, revenue, adr, revPar, avgStay, cancelRate)
}

private data class WeekdayStat(val dow: DayOfWeek, val occupancyRate: Double, val adr: Double)

private fun computeWeekdayBreakdown(
    active: List<ReservationDto>,
    rooms: List<RoomDto>,
    spanStart: LocalDate,
    spanEndExclusive: LocalDate
): List<WeekdayStat> {
    if (rooms.isEmpty() || !spanStart.isBefore(spanEndExclusive)) return emptyList()

    val nightsByDow  = LongArray(7)
    val revenueByDow = DoubleArray(7)
    val occByDow     = LongArray(7)

    var d = spanStart
    while (d.isBefore(spanEndExclusive)) {
        occByDow[d.dayOfWeek.value - 1]++
        d = d.plusDays(1)
    }

    active.forEach { res ->
        val ci = LocalDate.parse(res.checkInDate)
        val co = LocalDate.parse(res.checkOutDate)
        val totalNights = (co.toEpochDay() - ci.toEpochDay()).coerceAtLeast(1)
        val perNight = (res.totalAmount ?: 0.0) / totalNights
        var night = maxOf(ci, spanStart)
        val end = minOf(co, spanEndExclusive)
        while (night.isBefore(end)) {
            val idx = night.dayOfWeek.value - 1
            nightsByDow[idx]++
            revenueByDow[idx] += perNight
            night = night.plusDays(1)
        }
    }

    return DayOfWeek.values().map { dow ->
        val idx = dow.value - 1
        val available = rooms.size.toLong() * occByDow[idx]
        val occRate = if (available > 0) nightsByDow[idx].toDouble() / available else 0.0
        val adr     = if (nightsByDow[idx] > 0) revenueByDow[idx] / nightsByDow[idx] else 0.0
        WeekdayStat(dow, occRate, adr)
    }
}

/** Per-day occupancy fraction (nights occupied / rooms.size) across [spanStart, spanEndExclusive). */
private fun computeDailyOccupancy(
    active: List<ReservationDto>,
    rooms: List<RoomDto>,
    spanStart: LocalDate,
    spanEndExclusive: LocalDate
): List<Pair<LocalDate, Double>> {
    val days = (spanEndExclusive.toEpochDay() - spanStart.toEpochDay()).toInt()
    if (rooms.isEmpty() || days <= 0) return emptyList()

    val counts = IntArray(days)
    active.forEach { res ->
        val ci = LocalDate.parse(res.checkInDate)
        val co = LocalDate.parse(res.checkOutDate)
        var night = maxOf(ci, spanStart)
        val end = minOf(co, spanEndExclusive)
        while (night.isBefore(end)) {
            counts[(night.toEpochDay() - spanStart.toEpochDay()).toInt()]++
            night = night.plusDays(1)
        }
    }
    return (0 until days).map { i -> spanStart.plusDays(i.toLong()) to (counts[i].toDouble() / rooms.size) }
}

// Viridis — the perceptually-uniform, colorblind-safe sequential colormap developed for
// matplotlib (Smith & van der Walt) and now the standard "magnitude" ramp across scientific
// visualization. Unlike a rainbow/jet scale it has monotonically increasing perceived
// lightness low→high, so intensity reads correctly even in grayscale or under CVD.
private val VIRIDIS_STOPS = listOf(
    Color(0xFF440154), Color(0xFF482878), Color(0xFF3E4A89), Color(0xFF31688E),
    Color(0xFF26828E), Color(0xFF1F9E89), Color(0xFF35B779), Color(0xFF6DCD59),
    Color(0xFFB4DE2C), Color(0xFFFDE725)
)

private fun viridis(frac: Float): Color {
    val f = frac.coerceIn(0f, 1f)
    val segments = VIRIDIS_STOPS.size - 1
    val pos = f * segments
    val i = pos.toInt().coerceIn(0, segments - 1)
    val t = pos - i
    return androidx.compose.ui.graphics.lerp(VIRIDIS_STOPS[i], VIRIDIS_STOPS[i + 1], t)
}

/** WCAG relative luminance, used to pick a readable black/white label over a viridis cell. */
private fun relativeLuminance(c: Color): Double {
    fun lin(v: Float): Double {
        val x = v.toDouble()
        return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
}

private fun textOn(bg: Color): Color = if (relativeLuminance(bg) > 0.42) Color.Black else Color.White

@Composable
private fun ViridisLegend() {
    val s = LocalStrings.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(s.statsGradientLegendLow, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.weight(1f).height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(VIRIDIS_STOPS))
        )
        Text(s.statsGradientLegendHigh, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OccupancyCalendarHeatmap(daily: List<Pair<LocalDate, Double>>) {
    if (daily.isEmpty()) return
    val s    = LocalStrings.current
    val cell = 14.dp
    val gap  = 3.dp

    val firstDow: Int = daily.first().first.dayOfWeek.value // 1=Mon..7=Sun
    val padded: List<Pair<LocalDate, Double>?> = List(firstDow - 1) { null } + daily
    val weeks = padded.chunked(7)

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            DayOfWeek.values().forEach { dow ->
                Box(Modifier.height(cell).width(20.dp), contentAlignment = Alignment.CenterStart) {
                    Text(dow.getDisplayName(TextStyle.NARROW, s.locale), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    week.forEach { entry ->
                        if (entry == null) {
                            Spacer(Modifier.size(cell))
                        } else {
                            val (date, occ) = entry
                            val frac = occ.toFloat().coerceIn(0f, 1f)
                            AppTooltipArea(
                                tooltip = {
                                    Surface(
                                        shape           = RoundedCornerShape(6.dp),
                                        shadowElevation = 8.dp,
                                        color           = MaterialTheme.colorScheme.inverseSurface
                                    ) {
                                        Text(
                                            "$date — ${(occ * 100).roundToInt()}%",
                                            style    = MaterialTheme.typography.bodySmall,
                                            color    = MaterialTheme.colorScheme.inverseOnSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            ) {
                                Box(
                                    Modifier.size(cell).clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (occ <= 0.0) MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                            else viridis(frac)
                                        )
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.size(cell)) }
                }
            }
        }
    }
}

@Composable
private fun KpiSparkline(points: List<Pair<Double, Boolean>>, color: Color, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val maxV = points.maxOf { it.first }
    val minV = points.minOf { it.first }
    val range = (maxV - minV).let { if (it <= 0.0) 1.0 else it }
    Canvas(modifier) {
        val stepX = size.width / (points.size - 1)
        val xy = points.mapIndexed { i, (v, prov) ->
            Triple(i * stepX, size.height - ((v - minV) / range * size.height).toFloat(), prov)
        }
        val fillPath = Path().apply {
            moveTo(xy.first().first, size.height)
            xy.forEach { (x, y, _) -> lineTo(x, y) }
            lineTo(xy.last().first, size.height)
            close()
        }
        drawPath(fillPath, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        for (i in 0 until xy.size - 1) {
            val (x0, y0, prov0) = xy[i]
            val (x1, y1, prov1) = xy[i + 1]
            val provisional = prov0 || prov1
            drawLine(
                color       = if (provisional) color.copy(alpha = 0.5f) else color,
                start       = Offset(x0, y0),
                end         = Offset(x1, y1),
                strokeWidth = 2.5.dp.toPx(),
                cap         = StrokeCap.Round,
                pathEffect  = if (provisional) PathEffect.dashPathEffect(floatArrayOf(5f, 5f)) else null
            )
        }
        xy.forEach { (x, y, prov) ->
            drawCircle(
                color  = if (prov) color.copy(alpha = 0.5f) else color,
                radius = 2.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun KpiTilesRow(kpis: StatsKpis, periodKpis: List<PeriodKpi>, comparisonKpis: StatsKpis?) {
    val s = LocalStrings.current
    data class Tile(
        val label: String,
        val desc: String,
        val value: String,
        val current: Double,
        val previous: Double?,
        val trend: List<Pair<Double, Boolean>>,
        val higherIsBetter: Boolean
    )

    val tiles = listOf(
        Tile(s.statsKpiOccupancy, s.statsKpiOccupancyDesc, "${(kpis.occupancyRate * 100).roundToInt()}%", kpis.occupancyRate,
            comparisonKpis?.occupancyRate, periodKpis.map { it.kpis.occupancyRate to it.period.isProvisional }, true),
        Tile(s.statsKpiRevenue, s.statsKpiRevenueDesc, "${"%.0f".format(kpis.revenue)} PLN", kpis.revenue,
            comparisonKpis?.revenue, periodKpis.map { it.kpis.revenue to it.period.isProvisional }, true),
        Tile(s.statsKpiAdr, s.statsKpiAdrDesc, "${"%.0f".format(kpis.adr)} PLN", kpis.adr,
            comparisonKpis?.adr, periodKpis.map { it.kpis.adr to it.period.isProvisional }, true),
        Tile(s.statsKpiRevpar, s.statsKpiRevparDesc, "${"%.0f".format(kpis.revPar)} PLN", kpis.revPar,
            comparisonKpis?.revPar, periodKpis.map { it.kpis.revPar to it.period.isProvisional }, true),
        Tile(s.statsKpiAvgStay, s.statsKpiAvgStayDesc, "%.1f".format(kpis.avgStayNights), kpis.avgStayNights,
            comparisonKpis?.avgStayNights, periodKpis.map { it.kpis.avgStayNights to it.period.isProvisional }, true),
        Tile(s.statsKpiCancelRate, s.statsKpiCancelRateDesc, "${(kpis.cancelRate * 100).roundToInt()}%", kpis.cancelRate,
            comparisonKpis?.cancelRate, periodKpis.map { it.kpis.cancelRate to it.period.isProvisional }, false)
    )

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tiles.forEach { tile ->
            AppTooltipArea(
                tooltip = {
                    Surface(
                        shape           = RoundedCornerShape(6.dp),
                        shadowElevation = 8.dp,
                        color           = MaterialTheme.colorScheme.inverseSurface
                    ) {
                        Text(
                            tile.desc,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.widthIn(max = 220.dp).padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            ) {
            Column(
                // Fixed (not min-only) width: this tile sits in a horizontalScroll Row, which hands
                // children an infinite max-width constraint — widthIn(min=) wouldn't bound it, so the
                // sparkline's fillMaxWidth() below would collapse to ~0 and render as a vertical sliver.
                Modifier.width(150.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(tile.label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(tile.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    val prev = tile.previous
                    if (prev != null) {
                        if (prev != 0.0) {
                            val delta = (tile.current - prev) / prev
                            val up   = delta >= 0
                            val good = if (tile.higherIsBetter) up else !up
                            Text(
                                (if (up) "▲" else "▼") + " ${(kotlin.math.abs(delta) * 100).roundToInt()}%",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = if (good) Color(0xFF2E7D32) else Color(0xFFC62828),
                                maxLines = 1
                            )
                        } else if (tile.current != 0.0) {
                            Text(s.statsKpiNew, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                if (tile.trend.size >= 2) {
                    KpiSparkline(
                        tile.trend, MaterialTheme.colorScheme.primary,
                        Modifier.fillMaxWidth().height(40.dp).padding(top = 4.dp)
                    )
                }
            }
            }
        }
    }
}

private enum class TrendMetric { Occupancy, Revenue, Adr, RevPar, AvgStay, CancelRate }

private fun TrendMetric.extract(k: StatsKpis): Double = when (this) {
    TrendMetric.Occupancy  -> k.occupancyRate
    TrendMetric.Revenue    -> k.revenue
    TrendMetric.Adr        -> k.adr
    TrendMetric.RevPar     -> k.revPar
    TrendMetric.AvgStay    -> k.avgStayNights
    TrendMetric.CancelRate -> k.cancelRate
}

private fun TrendMetric.format(v: Double): String = when (this) {
    TrendMetric.Occupancy, TrendMetric.CancelRate            -> "${(v * 100).roundToInt()}%"
    TrendMetric.Revenue, TrendMetric.Adr, TrendMetric.RevPar -> "${"%.0f".format(v)} PLN"
    TrendMetric.AvgStay                                      -> "%.1f".format(v)
}

private fun TrendMetric.label(s: AppStrings): String = when (this) {
    TrendMetric.Occupancy  -> s.statsKpiOccupancy
    TrendMetric.Revenue    -> s.statsKpiRevenue
    TrendMetric.Adr        -> s.statsKpiAdr
    TrendMetric.RevPar     -> s.statsKpiRevpar
    TrendMetric.AvgStay    -> s.statsKpiAvgStay
    TrendMetric.CancelRate -> s.statsKpiCancelRate
}

@Composable
private fun TrendLineChart(periodKpis: List<PeriodKpi>, metric: TrendMetric) {
    val s = LocalStrings.current
    if (periodKpis.size < 2) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(s.statsNoData, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val values = periodKpis.map { metric.extract(it.kpis) }
    val maxV   = values.max()
    val minV   = minOf(0.0, values.min())
    val range  = (maxV - minV).let { if (it <= 0.0) 1.0 else it }

    val primary          = MaterialTheme.colorScheme.primary
    val gridColor        = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val pointW = 64.dp
    val chartH = 160.dp

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Column(Modifier.width(56.dp).height(chartH), verticalArrangement = Arrangement.SpaceBetween) {
            Text(metric.format(maxV), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant, maxLines = 1)
            Text(metric.format(minV), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant, maxLines = 1)
        }
        Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(pointW * periodKpis.size).height(chartH)) {
                val gridN = 3
                for (i in 0..gridN) {
                    val y = size.height * i / gridN
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val stepX = size.width / (periodKpis.size - 1).coerceAtLeast(1)
                val points = periodKpis.mapIndexed { i, pk ->
                    val v = metric.extract(pk.kpis)
                    val x = i * stepX
                    val y = size.height - ((v - minV) / range * size.height).toFloat()
                    Triple(x, y, pk.period.isProvisional)
                }
                val fillPath = Path().apply {
                    moveTo(points.first().first, size.height)
                    points.forEach { (x, y, _) -> lineTo(x, y) }
                    lineTo(points.last().first, size.height)
                    close()
                }
                drawPath(fillPath, brush = Brush.verticalGradient(listOf(primary.copy(alpha = 0.25f), primary.copy(alpha = 0f))))
                for (i in 0 until points.size - 1) {
                    val (x0, y0, prov0) = points[i]
                    val (x1, y1, prov1) = points[i + 1]
                    val provisional = prov0 || prov1
                    drawLine(
                        color       = if (provisional) primary.copy(alpha = 0.5f) else primary,
                        start       = Offset(x0, y0),
                        end         = Offset(x1, y1),
                        strokeWidth = 3.dp.toPx(),
                        cap         = StrokeCap.Round,
                        pathEffect  = if (provisional) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
                    )
                }
                points.forEach { (x, y, prov) ->
                    drawCircle(
                        color  = if (prov) primary.copy(alpha = 0.6f) else primary,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            Row(Modifier.width(pointW * periodKpis.size)) {
                periodKpis.forEach { pk ->
                    Box(Modifier.width(pointW), contentAlignment = Alignment.Center) {
                        Text(pk.period.label, style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsExportCsvButton(periodKpis: List<PeriodKpi>) {
    val s          = LocalStrings.current
    val snackbar   = LocalSnackbar.current
    val clipboard  = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope      = rememberCoroutineScope()

    OutlinedButton(
        onClick = {
            val header = "Period,Occupancy%,Revenue,ADR,RevPAR,AvgStayNights,CancelRate%"
            val rows = periodKpis.joinToString("\n") { pk ->
                val k = pk.kpis
                listOf(
                    pk.period.label,
                    "%.1f".format(k.occupancyRate * 100),
                    "%.2f".format(k.revenue),
                    "%.2f".format(k.adr),
                    "%.2f".format(k.revPar),
                    "%.2f".format(k.avgStayNights),
                    "%.1f".format(k.cancelRate * 100)
                ).joinToString(",")
            }
            clipboard.setText(androidx.compose.ui.text.AnnotatedString("$header\n$rows"))
            scope.launch { snackbar.showSnackbar(s.statsCsvCopied) }
        },
        modifier       = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Text(s.statsExportCsv, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BreakdownBars(items: List<Pair<String, Double>>, valueLabel: (Double) -> String) {
    val primary = MaterialTheme.colorScheme.primary
    val total = items.sumOf { it.second }
    if (total <= 0.0) return
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (name, value) ->
            val frac = (value / total).toFloat()
            Row(
                Modifier.fillMaxWidth().height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.width(90.dp)) {
                    Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(frac)
                            .clip(RoundedCornerShape(3.dp))
                            .background(primary)
                    )
                }
                Box(Modifier.width(70.dp)) {
                    Text(valueLabel(value), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun WeekdayBreakdownBars(stats: List<WeekdayStat>) {
    val s = LocalStrings.current
    val primary = MaterialTheme.colorScheme.primary
    val maxOcc = stats.maxOf { it.occupancyRate }.coerceAtLeast(0.0001)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        stats.forEach { st ->
            val frac = (st.occupancyRate / maxOcc).toFloat().coerceIn(0f, 1f)
            Row(
                Modifier.fillMaxWidth().height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.width(40.dp)) {
                    Text(st.dow.getDisplayName(TextStyle.SHORT, s.locale), style = MaterialTheme.typography.labelSmall)
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(frac)
                            .clip(RoundedCornerShape(3.dp))
                            .background(primary)
                    )
                }
                Box(Modifier.width(40.dp)) {
                    Text("${(st.occupancyRate * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.width(70.dp)) {
                    Text("${"%.0f".format(st.adr)} PLN", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChannelPayoutTable(summaries: List<PayoutMonthSummary>) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(s.statsPayoutMonth, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.2f))
            Text(s.statsPayoutBooked, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text(s.statsPayoutReceived, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text(s.statsPayoutCommission, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text(s.statsPayoutRate, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
        }
        HorizontalDivider()
        summaries.forEach { sm ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${sm.month.month.getDisplayName(TextStyle.SHORT, s.locale)} ${sm.month.year}",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                Text("${"%.0f".format(sm.booked)} ${sm.currency}", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text(sm.received?.let { "${"%.0f".format(it)} ${sm.currency}" } ?: "—",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text(sm.commission?.let { "${"%.0f".format(it)} ${sm.currency}" } ?: "—",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text(sm.commissionRate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun NightsTable(
    rooms: List<RoomDto>,
    periods: List<StatPeriod>,
    reservations: List<ReservationDto>
) {
    if (rooms.isEmpty() || periods.isEmpty()) return
    val s       = LocalStrings.current
    val surfVar = MaterialTheme.colorScheme.surfaceVariant

    val nightsMap = remember(rooms, periods, reservations) {
        val map = rooms.associate { it.id to MutableList(periods.size) { 0L } }
        reservations.forEach { res ->
            val perPeriod = map[res.roomId] ?: return@forEach
            val ci = LocalDate.parse(res.checkInDate)
            val co = LocalDate.parse(res.checkOutDate)
            periods.forEachIndexed { idx, p ->
                val n = (minOf(co, p.endExclusive).toEpochDay() - maxOf(ci, p.start).toEpochDay()).coerceAtLeast(0)
                perPeriod[idx] += n
            }
        }
        map
    }

    val labelW  = 72.dp
    val colW    = 60.dp
    val headerH = 28.dp
    val cellH   = 32.dp

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(surfVar)
    ) {
        // fixed room-label column
        Column(Modifier.width(labelW)) {
            Box(Modifier.height(headerH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(s.roomAbbr, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            rooms.forEachIndexed { i, room ->
                Box(Modifier.height(cellH).fillMaxWidth().padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart) {
                    Text(room.number, style = MaterialTheme.typography.labelSmall)
                }
                if (i < rooms.lastIndex) HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
        VerticalDivider()
        // horizontally scrollable period content
        Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Row {
                periods.forEach { p ->
                    Box(Modifier.width(colW).height(headerH), contentAlignment = Alignment.Center) {
                        Text(p.label, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                }
            }
            HorizontalDivider()
            rooms.forEachIndexed { i, room ->
                Row {
                    periods.forEachIndexed { idx, p ->
                        val nights    = nightsMap[room.id]?.getOrNull(idx) ?: 0L
                        val daysInP   = (p.endExclusive.toEpochDay() - p.start.toEpochDay()).coerceAtLeast(1)
                        val intensity = (nights.toFloat() / daysInP).coerceIn(0f, 1f)
                        val cellColor = if (nights > 0) viridis(intensity) else Color.Transparent
                        Box(
                            Modifier.width(colW).height(cellH).background(cellColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (nights > 0) {
                                Text("$nights", style = MaterialTheme.typography.labelSmall,
                                    color = textOn(cellColor))
                            }
                        }
                    }
                }
                if (i < rooms.lastIndex) HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun NightsHistogram(
    reservations: List<ReservationDto>,
    rooms: List<RoomDto>,
    spanStart: LocalDate,
    spanEndExclusive: LocalDate,
    group: HistGroup,
    selectedRoom: RoomDto?
) {
    val s       = LocalStrings.current
    val primary = MaterialTheme.colorScheme.primary
    val surfVar = MaterialTheme.colorScheme.surfaceVariant

    if (group == HistGroup.OneRoom && selectedRoom == null) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(surfVar).padding(20.dp),
            contentAlignment = Alignment.Center) {
            Text(s.selectRoomHint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val barMap = remember(reservations, rooms, spanStart, spanEndExclusive, group, selectedRoom, s) {
        if (!spanStart.isBefore(spanEndExclusive)) return@remember emptyMap()
        val roomCapacity = rooms.associate { it.id to it.maxGuests }

        val counts = mutableMapOf<Int, MutableMap<String, Int>>()
        reservations.forEach { res ->
            if (selectedRoom != null && res.roomId != selectedRoom.id) return@forEach
            val ci = LocalDate.parse(res.checkInDate)
            if (ci.isBefore(spanStart) || !ci.isBefore(spanEndExclusive)) return@forEach
            val co     = LocalDate.parse(res.checkOutDate)
            val nights = (co.toEpochDay() - ci.toEpochDay()).toInt().coerceAtLeast(1)
            val key    = if (group == HistGroup.ByType) roomCapacity[res.roomId]?.let { s.statsMaxGuestsLabel(it) } ?: "?" else ""
            counts.getOrPut(nights) { mutableMapOf() }.merge(key, 1, Int::plus)
        }
        counts.mapValues { (_, v) ->
            val segs = v.entries.map { it.key to it.value }
            BarData(segs.sumOf { it.second }, segs)
        }
    }

    if (barMap.isEmpty()) {
        Text(s.statsNoData, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val maxNights = minOf(barMap.keys.max(), 30)
    val maxCount  = barMap.values.maxOf { it.total }
    val groupKeys = if (group == HistGroup.ByType)
                        barMap.values.flatMap { it.segments.map { seg -> seg.first } }.distinct()
                            .sortedBy { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE }
                    else listOf("")
    val colorMap  = groupKeys.mapIndexed { i, k -> k to HIST_COLORS[i % HIST_COLORS.size] }.toMap()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(surfVar)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (1..maxNights).forEach { n ->
                val bar   = barMap[n]
                val total = bar?.total ?: 0
                val frac  = if (maxCount > 0 && total > 0) total.toFloat() / maxCount else 0f

                Row(
                    Modifier.fillMaxWidth().height(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // nights label, right-aligned
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.CenterEnd) {
                        Text("$n", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // bar track
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        if (total > 0) {
                            Row(
                                Modifier.fillMaxHeight().fillMaxWidth(frac)
                                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                            ) {
                                (bar?.segments ?: emptyList())
                                    .sortedBy { groupKeys.indexOf(it.first) }
                                    .forEach { (key, cnt) ->
                                        Box(
                                            Modifier.fillMaxHeight().weight(cnt.toFloat())
                                                .background(colorMap[key] ?: primary)
                                        )
                                    }
                            }
                        }
                    }
                    // count label
                    Box(Modifier.width(30.dp)) {
                        if (total > 0) Text("$total", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (group == HistGroup.ByType && groupKeys.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    groupKeys.forEach { key ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))
                                .background(colorMap[key] ?: primary))
                            Text(key, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Text(s.statsNightsAxisLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Settings ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsPage(
    fontScale: Float, onFontScaleChange: (Float) -> Unit,
    centerDays: Int, onCenterDaysChange: (Int) -> Unit,
    noShowAfterDays: Int = 14, onNoShowAfterDaysChange: (Int) -> Unit = {},
    autoCheckOutAfterDays: Int = 3, onAutoCheckOutAfterDaysChange: (Int) -> Unit = {},
    language: AppLanguage = AppLanguage.English,
    onLanguageChange: (AppLanguage) -> Unit = {},
    timelineDayWidth: Float = 40f, onTimelineDayWidthChange: (Float) -> Unit = {},
    timelineRowHeight: Float = 34f, onTimelineRowHeightChange: (Float) -> Unit = {},
    timelineLabelWidth: Float = 96f, onTimelineLabelWidthChange: (Float) -> Unit = {},
    timelineShowRoomType: Boolean = true, onTimelineShowRoomTypeChange: (Boolean) -> Unit = {},
    serverMode: String = "localhost", onServerModeChange: (String) -> Unit = {},
    customServerUrl: String = "", onCustomServerUrlChange: (String) -> Unit = {},
    appVersionName: String? = null,
    appVersionCode: Int? = null,
    updateInfo: AppUpdateInfo? = null,
    updateChecking: Boolean = false,
    updateError: String? = null,
    updateDownloadProgress: Float? = null,
    updateManualOnly: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onDownloadAndInstallUpdate: () -> Unit = {}
) {
    val s = LocalStrings.current
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(s.settingsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(s.settingsAppearance, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.settingsFontSize, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${(fontScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = fontScale,
                onValueChange = onFontScaleChange,
                valueRange = 0.75f..2.5f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("75%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("100%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("150%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            Text(s.settingsLanguage, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AppLanguage.entries.forEach { lang ->
                    val selected = language == lang
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable(enabled = !selected) { onLanguageChange(lang) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            lang.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()
            Text(s.settingsTimeline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.settingsCenterViewRange, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "±${centerDays}d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = centerDays.toFloat(),
                onValueChange = { onCenterDaysChange(it.roundToInt()) },
                valueRange = 7f..90f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("7d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("30d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("90d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.settingsNoShowAfterDays, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${noShowAfterDays}d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = noShowAfterDays.toFloat(),
                onValueChange = { onNoShowAfterDaysChange(it.roundToInt()) },
                valueRange = 1f..60f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("14d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("60d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.settingsAutoCheckOutAfterDays, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${autoCheckOutAfterDays}d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = autoCheckOutAfterDays.toFloat(),
                onValueChange = { onAutoCheckOutAfterDaysChange(it.roundToInt()) },
                valueRange = 1f..30f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("7d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("30d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            Text(s.settingsTimelineDisplay, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Text(s.showRoomTypeLabel, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface)) {
                listOf(true to s.showRoomTypeLabel, false to s.hideRoomTypeLabel).forEach { (v, label) ->
                    val sel = timelineShowRoomType == v
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onTimelineShowRoomTypeChange(v) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(s.settingsServer, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            val serverOptions = listOf(
                "localhost"  to s.settingsServerLocalhost,
                "deployment" to s.settingsServerDeployment,
                "custom"     to s.settingsServerCustom
            )
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface)) {
                serverOptions.forEach { (mode, label) ->
                    val selected = serverMode == mode
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable(enabled = !selected) { onServerModeChange(mode) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (serverMode == "custom") {
                OutlinedTextField(
                    value = customServerUrl,
                    onValueChange = onCustomServerUrlChange,
                    label = { Text(s.settingsServerCustomUrl) },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (appVersionName != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(s.settingsAbout, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.settingsVersion, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$appVersionName ($appVersionCode)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    updateDownloadProgress != null ->
                        LinearProgressIndicator(
                            progress = { updateDownloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    updateInfo != null -> {
                        Text(
                            s.settingsUpdateAvailable(updateInfo.latestVersionName),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (updateInfo.releaseNotes.isNotBlank()) {
                            Text(
                                updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onDownloadAndInstallUpdate) {
                            Text(if (updateManualOnly) s.settingsViewRelease else s.settingsDownloadAndInstall)
                        }
                    }
                    else -> {
                        if (updateError != null) {
                            Text(
                                s.settingsUpdateCheckFailed,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (!updateChecking) {
                            Text(
                                s.settingsUpToDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onCheckForUpdate, enabled = !updateChecking) {
                            Text(s.settingsCheckForUpdates)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

