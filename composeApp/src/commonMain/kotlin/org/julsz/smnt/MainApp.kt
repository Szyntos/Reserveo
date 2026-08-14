package org.julsz.smnt

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import kotlin.math.roundToInt

private enum class AppScreen { Dashboard, Reservations, Statistics, Payouts, Config, Invoices, Settings }

private data class StatYM(val year: Int, val month: Int) : Comparable<StatYM> {
    override fun compareTo(other: StatYM) = compareValuesBy(this, other, StatYM::year, StatYM::month)
    fun prev() = java.time.YearMonth.of(year, month).minusMonths(1).let { StatYM(it.year, it.monthValue) }
    fun next() = java.time.YearMonth.of(year, month).plusMonths(1).let { StatYM(it.year, it.monthValue) }
    fun label(locale: java.util.Locale) = Month.of(month).getDisplayName(TextStyle.SHORT, locale) + " $year"
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

@Composable
private fun StatisticsPage(client: HttpClient, hotel: UserHotelRoleDto) {
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    var reservations by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var rooms        by remember { mutableStateOf<List<RoomDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }

    LaunchedEffect(hotel.hotelId) {
        loading = true
        try {
            reservations = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            rooms = client.get("$BASE_URL/api/rooms?hotelId=${hotel.hotelId}").body<List<RoomDto>>()
                .filter { it.archivedAt == null }
        } catch (e: Exception) {
            snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
        }
        loading = false
    }

    if (loading) {
        CircularProgressIndicator()
    } else {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            StatisticsSection(reservations = reservations, rooms = rooms)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatisticsSection(reservations: List<ReservationDto>, rooms: List<RoomDto>) {
    val s      = LocalStrings.current
    val today  = remember { LocalDate.now() }
    val currYM = remember { StatYM(today.year, today.monthValue) }

    var monthsBack   by remember { mutableStateOf(6) }
    var endYM        by remember { mutableStateOf(currYM) }
    var histGroup    by remember { mutableStateOf(HistGroup.All) }
    var selectedRoom by remember { mutableStateOf<RoomDto?>(null) }

    val sortedRooms = remember(rooms) {
        rooms.sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number }))
    }

    val months = remember(endYM, monthsBack) {
        buildList {
            var ym = endYM
            repeat(monthsBack) { add(0, ym); ym = ym.prev() }
        }
    }

    val active = remember(reservations) {
        reservations.filter { it.status !in setOf("cancelled", "no_show") }
    }

    val kpis = remember(reservations, active, rooms, months) {
        computeStatsKpis(reservations, active, rooms, months)
    }

    val sourceBreakdown = remember(active, months) {
        if (months.isEmpty()) emptyList()
        else {
            val spanStart = LocalDate.of(months.first().year, months.first().month, 1)
            val spanEnd   = LocalDate.of(months.last().year, months.last().month, 1).plusMonths(1)
            active.filter {
                val ci = LocalDate.parse(it.checkInDate)
                !ci.isBefore(spanStart) && ci.isBefore(spanEnd)
            }.groupingBy { it.sourceName?.takeIf { n -> n.isNotBlank() } ?: it.source }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(s.statsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // time-span controls
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.statsTimeSpan, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf(3, 6, 9, 12).forEach { n ->
                    val sel = monthsBack == n
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { monthsBack = n }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(s.statsMonthsLabel(n), style = MaterialTheme.typography.labelSmall,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { endYM = endYM.prev() }, modifier = Modifier.size(32.dp)) {
                Text("‹", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Box(Modifier.widthIn(min = 85.dp), contentAlignment = Alignment.Center) {
                Text(endYM.label(s.locale), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(
                onClick  = { endYM = endYM.next() },
                enabled  = endYM < currYM,
                modifier = Modifier.size(32.dp)
            ) {
                Text("›", style = MaterialTheme.typography.titleMedium,
                    color = if (endYM < currYM) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }

        KpiTilesRow(kpis)

        Spacer(Modifier.height(4.dp))

        Text(s.statsNightsTable, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NightsTable(rooms = sortedRooms, months = months, reservations = active)

        Spacer(Modifier.height(4.dp))

        if (sourceBreakdown.isNotEmpty()) {
            Text(s.statsBySource, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SourceBreakdownBars(sourceBreakdown)
            Spacer(Modifier.height(4.dp))
        }

        // histogram controls
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.statsHistogram, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
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
                        Text(label, style = MaterialTheme.typography.labelSmall,
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
                            style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sortedRooms.forEach { room ->
                            DropdownMenuItem(
                                text    = { Text("${s.roomShort(room.number)} (${room.typeName})") },
                                onClick = { selectedRoom = room; expanded = false }
                            )
                        }
                    }
                }
            }
        }

        NightsHistogram(
            reservations = active,
            rooms        = sortedRooms,
            months       = months,
            group        = histGroup,
            selectedRoom = if (histGroup == HistGroup.OneRoom) selectedRoom else null
        )
        Spacer(Modifier.height(8.dp))
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

private fun computeStatsKpis(
    all: List<ReservationDto>,
    active: List<ReservationDto>,
    rooms: List<RoomDto>,
    months: List<StatYM>
): StatsKpis {
    if (months.isEmpty() || rooms.isEmpty()) {
        return StatsKpis(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
    val spanStart = LocalDate.of(months.first().year, months.first().month, 1)
    val spanEnd   = LocalDate.of(months.last().year, months.last().month, 1).plusMonths(1)
    val spanDays  = (spanEnd.toEpochDay() - spanStart.toEpochDay()).coerceAtLeast(1)

    var nightsInSpan = 0L
    var revenue = 0.0
    var checkInsInSpan = 0
    var nightsSumForCheckIns = 0L

    active.forEach { res ->
        val ci = LocalDate.parse(res.checkInDate)
        val co = LocalDate.parse(res.checkOutDate)
        val totalNights = (co.toEpochDay() - ci.toEpochDay()).coerceAtLeast(1)
        val overlapNights = (minOf(co, spanEnd).toEpochDay() - maxOf(ci, spanStart).toEpochDay()).coerceAtLeast(0)
        if (overlapNights > 0) {
            nightsInSpan += overlapNights
            val total = res.totalAmount ?: 0.0
            revenue += total * overlapNights.toDouble() / totalNights.toDouble()
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

@Composable
private fun KpiTilesRow(kpis: StatsKpis) {
    val s = LocalStrings.current
    val tiles = listOf(
        s.statsKpiOccupancy   to "${(kpis.occupancyRate * 100).roundToInt()}%",
        s.statsKpiRevenue     to "${"%.0f".format(kpis.revenue)} PLN",
        s.statsKpiAdr         to "${"%.0f".format(kpis.adr)} PLN",
        s.statsKpiRevpar      to "${"%.0f".format(kpis.revPar)} PLN",
        s.statsKpiAvgStay     to "${"%.1f".format(kpis.avgStayNights)}",
        s.statsKpiCancelRate  to "${(kpis.cancelRate * 100).roundToInt()}%"
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tiles.forEach { (label, value) ->
            Column(
                Modifier.widthIn(min = 120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SourceBreakdownBars(breakdown: List<Pair<String, Int>>) {
    val primary = MaterialTheme.colorScheme.primary
    val total = breakdown.sumOf { it.second }
    if (total == 0) return
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        breakdown.forEach { (name, count) ->
            val frac = count.toFloat() / total
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
                Box(Modifier.width(30.dp)) {
                    Text("$count", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NightsTable(
    rooms: List<RoomDto>,
    months: List<StatYM>,
    reservations: List<ReservationDto>
) {
    if (rooms.isEmpty() || months.isEmpty()) return
    val s         = LocalStrings.current
    val primary   = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfVar   = MaterialTheme.colorScheme.surfaceVariant

    val nightsMap = remember(rooms, months, reservations) {
        val map = rooms.associate { it.id to MutableList(months.size) { 0L } }
        reservations.forEach { res ->
            val perMonth = map[res.roomId] ?: return@forEach
            val ci = LocalDate.parse(res.checkInDate)
            val co = LocalDate.parse(res.checkOutDate)
            months.forEachIndexed { idx, ym ->
                val start = LocalDate.of(ym.year, ym.month, 1)
                val end   = start.plusMonths(1)
                val n     = (minOf(co, end).toEpochDay() - maxOf(ci, start).toEpochDay()).coerceAtLeast(0)
                perMonth[idx] += n
            }
        }
        map
    }

    val labelW  = 72.dp
    val colW    = 60.dp
    val headerH = 28.dp
    val cellH   = 32.dp

    val sortedRooms = remember(rooms) {
        rooms.sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number }))
    }

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
            sortedRooms.forEachIndexed { i, room ->
                Box(Modifier.height(cellH).fillMaxWidth().padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart) {
                    Text(room.number, style = MaterialTheme.typography.labelSmall)
                }
                if (i < sortedRooms.lastIndex) HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
        VerticalDivider()
        // horizontally scrollable months content
        Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Row {
                months.forEach { ym ->
                    Box(Modifier.width(colW).height(headerH), contentAlignment = Alignment.Center) {
                        Text(ym.label(s.locale), style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                }
            }
            HorizontalDivider()
            sortedRooms.forEachIndexed { i, room ->
                Row {
                    months.forEachIndexed { idx, ym ->
                        val nights    = nightsMap[room.id]?.getOrNull(idx) ?: 0L
                        val daysInM   = java.time.YearMonth.of(ym.year, ym.month).lengthOfMonth().toLong()
                        val intensity = (nights.toFloat() / daysInM).coerceIn(0f, 1f)
                        Box(
                            Modifier.width(colW).height(cellH)
                                .background(
                                    if (nights > 0) primary.copy(alpha = 0.15f + intensity * 0.70f)
                                    else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (nights > 0) {
                                Text("$nights", style = MaterialTheme.typography.labelSmall,
                                    color = if (intensity > 0.55f) MaterialTheme.colorScheme.onPrimary
                                            else onSurface)
                            }
                        }
                    }
                }
                if (i < sortedRooms.lastIndex) HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun NightsHistogram(
    reservations: List<ReservationDto>,
    rooms: List<RoomDto>,
    months: List<StatYM>,
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

    val barMap = remember(reservations, rooms, months, group, selectedRoom) {
        if (months.isEmpty()) return@remember emptyMap()
        val spanStart = LocalDate.of(months.first().year, months.first().month, 1)
        val spanEnd   = LocalDate.of(months.last().year, months.last().month, 1).plusMonths(1)
        val roomType  = rooms.associate { it.id to it.typeName }

        val counts = mutableMapOf<Int, MutableMap<String, Int>>()
        reservations.forEach { res ->
            if (selectedRoom != null && res.roomId != selectedRoom.id) return@forEach
            val ci = LocalDate.parse(res.checkInDate)
            if (ci.isBefore(spanStart) || !ci.isBefore(spanEnd)) return@forEach
            val co     = LocalDate.parse(res.checkOutDate)
            val nights = (co.toEpochDay() - ci.toEpochDay()).toInt().coerceAtLeast(1)
            val key    = if (group == HistGroup.ByType) roomType[res.roomId] ?: "?" else ""
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
                        barMap.values.flatMap { it.segments.map { seg -> seg.first } }.distinct().sorted()
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

