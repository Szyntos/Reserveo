package org.julsz.smnt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private enum class AppScreen { Dashboard, Reservations, Statistics, Config, Settings }

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
    onLanguageChange: (AppLanguage) -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(AppScreen.Dashboard) }
    val snackbarState = remember { SnackbarHostState() }

    CompositionLocalProvider(LocalSnackbar provides snackbarState) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                AppSidebar(
                    currentUser    = currentUser,
                    selectedHotel  = selectedHotel,
                    currentScreen  = currentScreen,
                    onScreenChange = { currentScreen = it },
                    onSwitchHotel  = onSwitchHotel,
                    onLogout       = onLogout,
                    isDark         = isDark,
                    onThemeToggle  = onThemeToggle
                )
                VerticalDivider()
                Box(Modifier.fillMaxSize().padding(28.dp)) {
                    when (currentScreen) {
                        AppScreen.Dashboard    -> DashboardPage(client = client, hotel = selectedHotel, noShowAfterDays = noShowAfterDays, autoCheckOutAfterDays = autoCheckOutAfterDays)
                        AppScreen.Reservations -> ReservationsCalendarPage(client, selectedHotel, centerDays, noShowAfterDays, autoCheckOutAfterDays)
                        AppScreen.Statistics   -> StatisticsPage(client = client, hotel = selectedHotel)
                        AppScreen.Config       -> ConfigPage(client, selectedHotel)
                        AppScreen.Settings     -> SettingsPage(fontScale = fontScale, onFontScaleChange = onFontScaleChange, centerDays = centerDays, onCenterDaysChange = onCenterDaysChange, noShowAfterDays = noShowAfterDays, onNoShowAfterDaysChange = onNoShowAfterDaysChange, autoCheckOutAfterDays = autoCheckOutAfterDays, onAutoCheckOutAfterDaysChange = onAutoCheckOutAfterDaysChange, language = language, onLanguageChange = onLanguageChange)
                    }
                }
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
    currentScreen: AppScreen,
    onScreenChange: (AppScreen) -> Unit,
    onSwitchHotel: () -> Unit,
    onLogout: () -> Unit,
    isDark: Boolean,
    onThemeToggle: () -> Unit
) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Hotel header
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 4.dp)
            ) {
                Text(
                    selectedHotel.hotelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(selectedHotel.role, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            AppScreen.entries.forEach { screen ->
                val label = when (screen) {
                    AppScreen.Dashboard    -> s.navDashboard
                    AppScreen.Reservations -> s.navReservations
                    AppScreen.Statistics   -> s.statsTitle
                    AppScreen.Config       -> s.navConfig
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
            HorizontalDivider()
            // User info
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    currentUser.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    currentUser.email,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            HorizontalDivider()
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
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    listOf(true to s.themeDark, false to s.themeLight).forEach { (dark, label) ->
                        val selected = isDark == dark
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable(enabled = !selected, onClick = onThemeToggle)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
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
    val bg        = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}

// ─── Pages ────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardPage(client: HttpClient, hotel: UserHotelRoleDto, noShowAfterDays: Int = 14, autoCheckOutAfterDays: Int = 3) {
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

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(hotel.hotelName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (loading) {
            CircularProgressIndicator()
        } else {
            // ── Stat cards ────────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title    = s.statCheckedIn,
                    value    = if (totalRooms > 0) "$checkedInCount / $totalRooms" else "$checkedInCount",
                    subtitle = if (totalRooms > 0) "$occupancyPct% ${s.statOccupancy}" else "",
                    valueColor = if (checkedInCount > 0) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
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
                    title    = s.statPendingDp,
                    value    = "$pendingDpCount",
                    subtitle = s.reservationsTitle,
                    valueColor = if (pendingDpCount > 0) MaterialTheme.colorScheme.error
                                 else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Arrivals / Departures / Overdue ───────────────────────────────
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardTile(
                    title        = s.arrivals,
                    date         = todayStr,
                    pending      = arrivals.filter { it.status in listOf("confirmed", "pending") },
                    done         = arrivals.filter { it.status == "checked_in" },
                    pendingLabel = s.notArrived,
                    doneLabel    = s.arrived,
                    emptyLabel   = s.noArrivalsToday,
                    onAction     = { updateStatus(it, "checked_in") },
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
                    modifier     = Modifier.weight(1f)
                )
                OverdueTile(
                    reservations = overdue,
                    onSetStatus  = { res, newStatus -> updateStatus(res, newStatus) },
                    modifier     = Modifier.weight(1f)
                )
                OverdueCheckOutsTile(
                    reservations = overdueCheckOuts,
                    onSetStatus  = { res, newStatus -> updateStatus(res, newStatus) },
                    modifier     = Modifier.weight(1f)
                )
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
    Card(
        modifier  = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(date, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()

        if (pending.isEmpty() && done.isEmpty()) {
            Text(emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (pending.isNotEmpty()) {
                Text("$pendingLabel (${pending.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                pending.forEach { res ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(res.guestName, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium)
                            Text(s.roomShort(res.roomNumber), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onAction(res) }, modifier = Modifier.size(36.dp)) {
                            Text("✓", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (done.isNotEmpty()) {
                if (pending.isNotEmpty()) HorizontalDivider()
                Text("$doneLabel (${done.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                done.forEach { res ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(res.guestName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.roomShort(res.roomNumber), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Text("✓", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 8.dp))
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
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val errorBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    val normalBg = MaterialTheme.colorScheme.surfaceVariant
    val bg = if (reservations.isNotEmpty()) errorBg else normalBg

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(16.dp),
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
            if (reservations.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        "${reservations.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        HorizontalDivider()

        if (reservations.isEmpty()) {
            Text(
                s.noOverdueCheckIns,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reservations) { res ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            res.guestName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${s.roomShort(res.roomNumber)} · ${res.checkInDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("checked_in", "no_show", "cancelled").forEach { code ->
                                OutlinedButton(
                                    onClick = { onSetStatus(res, code) },
                                    modifier = Modifier.weight(1f).height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        s.statusName(code),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
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
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val warnBg  = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    val normalBg = MaterialTheme.colorScheme.surfaceVariant
    val bg = if (reservations.isNotEmpty()) warnBg else normalBg

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(16.dp),
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
            if (reservations.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        "${reservations.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        HorizontalDivider()

        if (reservations.isEmpty()) {
            Text(
                s.noOverdueCheckOuts,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reservations) { res ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            res.guestName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${s.roomShort(res.roomNumber)} · ${res.checkOutDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("checked_out", "cancelled").forEach { code ->
                                OutlinedButton(
                                    onClick = { onSetStatus(res, code) },
                                    modifier = Modifier.weight(1f).height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        s.statusName(code),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
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

        Text(s.statsNightsTable, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NightsTable(rooms = sortedRooms, months = months, reservations = active)

        Spacer(Modifier.height(4.dp))

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
    onLanguageChange: (AppLanguage) -> Unit = {}
) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(s.settingsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Column(
            modifier = Modifier
                .fillMaxWidth(0.55f)
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
                valueRange = 0.75f..1.5f,
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
        }
    }
}

