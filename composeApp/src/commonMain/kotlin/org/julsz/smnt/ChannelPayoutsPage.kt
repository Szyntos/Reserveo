package org.julsz.smnt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * Reconciles what Booking said guests would pay against what Booking actually transferred.
 *
 * The stored payments on a channel reservation are the guest-side amount, not income — the
 * real money arrives as a lump sum each month. This page records those sums, derives the
 * commission the channel kept, and lets the derived attribution be corrected by hand against
 * Booking's own report.
 */
@Composable
fun ChannelPayoutsPage(
    client: HttpClient,
    hotel: UserHotelRoleDto,
    canEdit: Boolean = true
) {
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    val scope    = rememberCoroutineScope()

    var reservations  by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var payouts       by remember { mutableStateOf<List<ChannelPayoutDto>>(emptyList()) }
    var overrideList  by remember { mutableStateOf<List<ChannelPayoutOverrideDto>>(emptyList()) }
    var loading       by remember { mutableStateOf(true) }
    var editing       by remember { mutableStateOf<PayoutMonthSummary?>(null) }
    var excluding     by remember { mutableStateOf<ReservationDto?>(null) }
    var expanded      by remember { mutableStateOf<YearMonth?>(null) }

    suspend fun reload() {
        loading = true
        try {
            reservations = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            payouts      = client.get("$BASE_URL/api/channel-payouts?hotelId=${hotel.hotelId}").body()
            overrideList = client.get("$BASE_URL/api/channel-payout-overrides?hotelId=${hotel.hotelId}").body()
        } catch (e: Exception) {
            snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
        }
        loading = false
    }

    LaunchedEffect(hotel.hotelId) { reload() }

    val overrides = remember(overrideList) { PayoutOverrides.from(overrideList) }
    val summaries = remember(reservations, payouts, overrides) {
        buildPayoutSummaries(reservations, payouts, overrides)
    }
    val integrity = remember(reservations, overrides) { computePayoutIntegrity(reservations, overrides) }
    val excluded  = remember(reservations, overrides) { excludedReservations(reservations, overrides) }

    fun moveTo(res: ReservationDto, target: YearMonth) {
        scope.launch {
            try {
                client.put("$BASE_URL/api/reservations/${res.id}/payout-override") {
                    contentType(ContentType.Application.Json)
                    setBody(SetChannelPayoutOverrideRequest(year = target.year, month = target.monthValue))
                }
                reload()
            } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
        }
    }

    fun resetToDerived(res: ReservationDto) {
        scope.launch {
            try {
                client.delete("$BASE_URL/api/reservations/${res.id}/payout-override")
                reload()
            } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
        }
    }

    // Overall rate across settled months only — unsettled months have no received figure to
    // compare against, and folding them in would understate the rate.
    val settled       = summaries.filter { it.isSettled }
    val totalBooked   = settled.sumOf { it.booked }
    val totalReceived = settled.sumOf { it.received ?: 0.0 }
    val overallRate   = if (totalBooked > 0.0) (totalBooked - totalReceived) / totalBooked else null

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(s.payoutsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            s.payoutsSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            s.payoutAttributionNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )

        if (overallRate != null) {
            OverallRateCard(rate = overallRate, booked = totalBooked, received = totalReceived)
        }

        IntegrityLine(integrity)

        if (summaries.isEmpty() && excluded.isEmpty()) {
            Text(s.payoutsEmpty, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val payoutsLazyState = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = payoutsLazyState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(summaries, key = { it.month.toString() }) { summary ->
                    PayoutMonthCard(
                        summary   = summary,
                        overrides = overrides,
                        expanded  = expanded == summary.month,
                        onToggle  = { expanded = if (expanded == summary.month) null else summary.month },
                        onRecord  = { editing = summary },
                        onDelete  = {
                            val id = summary.payoutId
                            if (id != null) scope.launch {
                                try {
                                    client.delete("$BASE_URL/api/channel-payouts/$id")
                                    reload()
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                                }
                            }
                        },
                        availableMonths = summaries.map { it.month },
                        onMoveTo        = ::moveTo,
                        onReset         = ::resetToDerived,
                        onExclude       = { excluding = it },
                        canEdit         = canEdit
                    )
                }

                if (excluded.isNotEmpty()) {
                    item {
                        ExcludedCard(
                            reservations = excluded,
                            overrides    = overrides,
                            onRestore    = ::resetToDerived,
                            canEdit      = canEdit
                        )
                    }
                }
            }
            AppVerticalScrollbar(
                state    = payoutsLazyState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
            }
        }
    }

    editing?.let { summary ->
        PayoutDialog(
            summary   = summary,
            hotelId   = hotel.hotelId,
            client    = client,
            onDismiss = { editing = null },
            onSaved   = { editing = null; scope.launch { reload() } }
        )
    }

    excluding?.let { res ->
        ExcludeDialog(
            reservation = res,
            client      = client,
            onDismiss   = { excluding = null },
            onSaved     = { excluding = null; scope.launch { reload() } }
        )
    }
}

/**
 * Makes the partition invariant visible: every channel reservation is either assigned to a
 * month or deliberately excluded. A non-zero "unaccounted" means the override layer is broken.
 */
@Composable
private fun IntegrityLine(integrity: PayoutIntegrity) {
    val s  = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${s.payoutIntegrityLabel}:",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
        Text(
            s.payoutIntegrityLine(
                integrity.total, integrity.assigned, integrity.excluded, integrity.unaccounted
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (integrity.isSound) cs.onSurfaceVariant else cs.error,
            fontWeight = if (integrity.isSound) FontWeight.Normal else FontWeight.Bold
        )
    }
    if (!integrity.isSound) {
        Text(s.payoutIntegrityBroken, style = MaterialTheme.typography.labelSmall, color = cs.error)
    }
}

@Composable
private fun OverallRateCard(rate: Double, booked: Double, received: Double) {
    val s  = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = BorderStroke(1.dp, cs.outlineVariant),
        colors    = CardDefaults.cardColors(containerColor = cs.surface),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(s.payoutOverallRate, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text(
                    "${"%.1f".format(rate * 100)}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )
            }
            Column {
                Text(s.payoutBooked, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text("${"%.2f".format(booked)} PLN", style = MaterialTheme.typography.bodyMedium)
            }
            Column {
                Text(s.payoutReceived, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text("${"%.2f".format(received)} PLN", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun monthLabel(month: YearMonth): String {
    val s = LocalStrings.current
    return remember(month, s.locale) {
        Month.of(month.monthValue).getDisplayName(TextStyle.FULL, s.locale)
            .replaceFirstChar { it.uppercase(s.locale) } + " ${month.year}"
    }
}

@Composable
private fun PayoutMonthCard(
    summary: PayoutMonthSummary,
    overrides: PayoutOverrides,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRecord: () -> Unit,
    onDelete: () -> Unit,
    availableMonths: List<YearMonth>,
    onMoveTo: (ReservationDto, YearMonth) -> Unit,
    onReset: (ReservationDto) -> Unit,
    onExclude: (ReservationDto) -> Unit,
    canEdit: Boolean
) {
    val s     = LocalStrings.current
    val cs    = MaterialTheme.colorScheme
    val label = monthLabel(summary.month)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = BorderStroke(
            1.dp,
            when {
                summary.looksAnomalous -> cs.error.copy(alpha = 0.5f)
                summary.isSettled      -> cs.primary.copy(alpha = 0.35f)
                else                   -> cs.outlineVariant
            }
        ),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        shape  = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        s.payoutReservationCount(summary.reservations.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
                StatusPill(
                    text  = if (summary.isSettled) s.payoutSettled else s.payoutNotSettled,
                    color = if (summary.isSettled) cs.primary else cs.onSurfaceVariant
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Figure(s.payoutBooked, "${"%.2f".format(summary.booked)} PLN")
                Figure(
                    s.payoutReceived,
                    summary.received?.let { "${"%.2f".format(it)} ${summary.currency}" } ?: "—"
                )
                Figure(
                    s.payoutCommission,
                    summary.commission?.let { c ->
                        val pct = summary.commissionRate?.let { " (${"%.1f".format(it * 100)}%)" } ?: ""
                        "${"%.2f".format(c)} PLN$pct"
                    } ?: "—",
                    color = if (summary.looksAnomalous) cs.error else Color.Unspecified
                )
            }

            if (summary.looksAnomalous) {
                Text(s.payoutAnomalyWarning, style = MaterialTheme.typography.labelSmall, color = cs.error)
            }
            summary.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }

            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRecord, modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            if (summary.isSettled) s.payoutEditBtn else s.payoutRecordBtn,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (summary.isSettled) {
                        OutlinedButton(onClick = onDelete, modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(6.dp)) {
                            Text(s.delete, style = MaterialTheme.typography.labelSmall, color = cs.error)
                        }
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(color = cs.outlineVariant)
                if (summary.reservations.isEmpty()) {
                    Text(s.payoutNoReservations, style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant)
                } else {
                    if (summary.isSettled) {
                        Text(s.payoutEstimateDisclaimer, style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant.copy(alpha = 0.75f))
                    }
                    summary.reservations.forEach { res ->
                        ReservationLine(
                            res             = res,
                            summary         = summary,
                            overrides       = overrides,
                            availableMonths = availableMonths,
                            onMoveTo        = onMoveTo,
                            onReset         = onReset,
                            onExclude       = onExclude,
                            canEdit         = canEdit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationLine(
    res: ReservationDto,
    summary: PayoutMonthSummary,
    overrides: PayoutOverrides,
    availableMonths: List<YearMonth>,
    onMoveTo: (ReservationDto, YearMonth) -> Unit,
    onReset: (ReservationDto) -> Unit,
    onExclude: (ReservationDto) -> Unit,
    canEdit: Boolean
) {
    val s          = LocalStrings.current
    val cs         = MaterialTheme.colorScheme
    val est        = summary.estimatedCommissionFor(res)
    val overridden = overrides.isOverridden(res)
    val derived    = remember(res.checkOutDate) {
        runCatching { PayoutAttribution.payoutMonth(res) }.getOrNull()
    }
    var menuOpen by remember(res.id) { mutableStateOf(false) }

    // Months to offer: every month already on the page plus the neighbours of the derived one,
    // so a reservation can be moved into a month that has no reservations yet.
    val choices = remember(availableMonths, derived) {
        val neighbours = derived?.let { listOf(it.minusMonths(1), it, it.plusMonths(1)) }.orEmpty()
        (availableMonths + neighbours).distinct().sortedDescending()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(res.guestName, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (overridden) {
                        StatusPill(text = s.payoutOverridden, color = cs.tertiary)
                    }
                }
                Text(
                    "${s.roomShort(res.roomNumber)} · ${res.checkInDate} → ${res.checkOutDate} · " +
                        s.payoutPaidOn(
                            PayoutAttribution.payoutDate(LocalDate.parse(res.checkOutDate)).toString()
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                if (overridden && derived != null && derived != summary.month) {
                    Text(
                        s.payoutMovedFrom(
                            Month.of(derived.monthValue).getDisplayName(TextStyle.FULL, s.locale) +
                                " ${derived.year}"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.tertiary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${"%.2f".format(res.totalAmount ?: 0.0)} PLN", style = MaterialTheme.typography.bodySmall)
                if (est != null) {
                    Text(
                        "−${"%.2f".format(est)} ${s.payoutCommissionEst.lowercase(s.locale)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            if (canEdit) {
                Box {
                    TextButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("⋯", style = MaterialTheme.typography.labelMedium)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Text(
                            s.payoutMoveTo,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        choices.filter { it != summary.month }.forEach { target ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        Month.of(target.monthValue)
                                            .getDisplayName(TextStyle.FULL, s.locale) + " ${target.year}"
                                    )
                                },
                                onClick = { menuOpen = false; onMoveTo(res, target) }
                            )
                        }
                        HorizontalDivider()
                        if (overridden) {
                            DropdownMenuItem(
                                text    = { Text(s.payoutResetToDerived) },
                                onClick = { menuOpen = false; onReset(res) }
                            )
                        }
                        DropdownMenuItem(
                            text    = { Text(s.payoutExcludeBtn, color = cs.error) },
                            onClick = { menuOpen = false; onExclude(res) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcludedCard(
    reservations: List<ReservationDto>,
    overrides: PayoutOverrides,
    onRestore: (ReservationDto) -> Unit,
    canEdit: Boolean
) {
    val s  = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = BorderStroke(1.dp, cs.outlineVariant),
        colors    = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.4f)),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${s.payoutExcludedSection} (${reservations.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = cs.outlineVariant)
            reservations.forEach { res ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(res.guestName, style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${s.roomShort(res.roomNumber)} · ${res.checkInDate} → ${res.checkOutDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                        overrides.forReservation(res)?.reason?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant.copy(alpha = 0.75f))
                        }
                    }
                    Text(
                        "${"%.2f".format(res.totalAmount ?: 0.0)} PLN",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                    if (canEdit) {
                        TextButton(
                            onClick = { onRestore(res) },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(s.payoutRestoreBtn, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, color: Color = Color.Unspecified) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ExcludeDialog(
    reservation: ReservationDto,
    client: HttpClient,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    val scope    = rememberCoroutineScope()

    var reason by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.payoutExcludeTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(reservation.guestName, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text(
                    "${s.roomShort(reservation.roomNumber)} · ${reservation.checkInDate} → ${reservation.checkOutDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(s.payoutExcludeExplain, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(s.payoutExcludeReasonLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            client.put("$BASE_URL/api/reservations/${reservation.id}/payout-override") {
                                contentType(ContentType.Application.Json)
                                setBody(SetChannelPayoutOverrideRequest(
                                    excluded = true,
                                    reason   = reason.takeIf { it.isNotBlank() }
                                ))
                            }
                            onSaved()
                        } catch (e: Exception) {
                            snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                        }
                        saving = false
                    }
                }
            ) { Text(s.payoutExcludeBtn) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
private fun PayoutDialog(
    summary: PayoutMonthSummary,
    hotelId: Int,
    client: HttpClient,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    val scope    = rememberCoroutineScope()

    var amountText by remember { mutableStateOf(summary.received?.let { "%.2f".format(it) } ?: "") }
    var notes      by remember { mutableStateOf(summary.notes.orEmpty()) }
    var saving     by remember { mutableStateOf(false) }

    val amount    = amountText.replace(',', '.').toDoubleOrNull()
    val canSubmit = amount != null && amount >= 0.0 && !saving
    val label     = monthLabel(summary.month)

    // Live preview of the rate this amount implies, so a typo is obvious before saving.
    val previewRate = amount?.takeIf { summary.booked > 0.0 }?.let { (summary.booked - it) / summary.booked }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (summary.isSettled) s.payoutEditTitle else s.payoutAddTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${s.payoutBooked}: ${"%.2f".format(summary.booked)} PLN · " +
                        s.payoutReservationCount(summary.reservations.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(s.payoutAmountLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (previewRate != null) {
                    val bad = previewRate < 0.0 || previewRate > 0.5
                    Text(
                        "${s.payoutCommission}: ${"%.1f".format(previewRate * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bad) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(s.payoutNotesLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    val value = amount ?: return@TextButton
                    saving = true
                    scope.launch {
                        try {
                            val existingId = summary.payoutId
                            if (existingId != null) {
                                client.put("$BASE_URL/api/channel-payouts/$existingId") {
                                    contentType(ContentType.Application.Json)
                                    setBody(UpdateChannelPayoutRequest(
                                        amount   = value,
                                        currency = summary.currency,
                                        notes    = notes.takeIf { it.isNotBlank() }
                                    ))
                                }
                            } else {
                                client.post("$BASE_URL/api/channel-payouts") {
                                    contentType(ContentType.Application.Json)
                                    setBody(CreateChannelPayoutRequest(
                                        hotelId = hotelId,
                                        year    = summary.month.year,
                                        month   = summary.month.monthValue,
                                        amount  = value,
                                        notes   = notes.takeIf { it.isNotBlank() }
                                    ))
                                }
                            }
                            onSaved()
                        } catch (e: Exception) {
                            snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                        }
                        saving = false
                    }
                }
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}
