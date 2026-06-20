package org.julsz.smnt

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.launch

private data class NightTier(val minNights: Int, val maxNights: Int?, val label: String)

private fun computeTiers(breakpoints: List<Int>): List<NightTier> {
    if (breakpoints.isEmpty()) return listOf(NightTier(1, null, "1+"))
    val sorted = breakpoints.sorted()
    return buildList {
        val firstMax = sorted[0] - 1
        add(NightTier(1, firstMax, if (firstMax == 1) "1" else "1–$firstMax"))
        sorted.forEachIndexed { i, bp ->
            val maxN = sorted.getOrNull(i + 1)?.let { it - 1 }
            val label = when {
                maxN == null -> "$bp+"
                maxN == bp   -> "$bp"
                else         -> "$bp–$maxN"
            }
            add(NightTier(bp, maxN, label))
        }
    }
}

@Composable
private fun GeneratorDateRangePicker(
    fromDate: String,
    toDate: String,
    fromLabel: String,
    toLabel: String,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = fromDate, onValueChange = {}, readOnly = true,
            label = { Text(fromLabel) },
            trailingIcon = {
                TextButton(
                    onClick = { showPicker = true },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("📅") }
            },
            modifier = Modifier.weight(1f), singleLine = true
        )
        OutlinedTextField(
            value = toDate, onValueChange = {}, readOnly = true,
            label = { Text(toLabel) },
            trailingIcon = {
                TextButton(
                    onClick = { showPicker = true },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("📅") }
            },
            modifier = Modifier.weight(1f), singleLine = true
        )
    }

    if (showPicker) {
        AppRangePickerDialog(
            startDate = fromDate,
            endDate   = toDate,
            onDismiss = { showPicker = false },
            onConfirm = { start, end -> onFromChange(start); onToChange(end); showPicker = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleGeneratorPage(
    client: HttpClient,
    rooms: List<RoomDto>,
    existingRules: List<PriceRuleDto>,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    val scope    = rememberCoroutineScope()

    var fromDate by remember { mutableStateOf("") }
    var toDate   by remember { mutableStateOf("") }
    var fromYear by remember { mutableStateOf(java.time.LocalDate.now().year.toString()) }
    var toYear   by remember { mutableStateOf(java.time.LocalDate.now().year.toString()) }

    var breakpoints   by remember { mutableStateOf<List<Int>>(emptyList()) }
    var newBreakpoint by remember { mutableStateOf("") }

    // prices key = (roomId, tierIndex)
    var prices by remember { mutableStateOf<Map<Pair<Int, Int>, String>>(emptyMap()) }

    var generating by remember { mutableStateOf(false) }

    val tiers = remember(breakpoints) { computeTiers(breakpoints) }

    val lastRuleDate = remember(existingRules) {
        existingRules.maxByOrNull { it.toDate }?.toDate
    }

    val vScroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(vScroll).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) { Text(s.breadcrumbBasePrice, style = MaterialTheme.typography.labelMedium) }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.ruleGeneratorTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (lastRuleDate != null) {
                    Text(
                        s.lastRuleDate(lastRuleDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Date range card ──────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.ruleGeneratorDateRange, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    GeneratorDateRangePicker(
                        fromDate     = fromDate,
                        toDate       = toDate,
                        fromLabel    = s.fromLabel,
                        toLabel      = s.toLabel,
                        onFromChange = { fromDate = it },
                        onToChange   = { toDate = it }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromYear,
                            onValueChange = { fromYear = it.filter { c -> c.isDigit() } },
                            label = { Text(s.ruleGeneratorFromYear) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toYear,
                            onValueChange = { toYear = it.filter { c -> c.isDigit() } },
                            label = { Text(s.ruleGeneratorToYear) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Night tiers & price matrix card ──────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.ruleGeneratorTiers, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                    // Add-tier input
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newBreakpoint,
                            onValueChange = { newBreakpoint = it.filter { c -> c.isDigit() } },
                            label = { Text(s.ruleGeneratorAddTierFrom) },
                            placeholder = { Text("2") },
                            singleLine = true,
                            modifier = Modifier.width(200.dp)
                        )
                        Button(
                            onClick = {
                                val bp = newBreakpoint.toIntOrNull()
                                if (bp != null && bp > 1 && bp !in breakpoints) {
                                    breakpoints = (breakpoints + bp).sorted()
                                    prices = emptyMap()
                                    newBreakpoint = ""
                                }
                            },
                            enabled = newBreakpoint.toIntOrNull()?.let { it > 1 && it !in breakpoints } == true
                        ) { Text(s.ruleGeneratorAddTierBtn) }
                    }

                    if (rooms.isEmpty()) {
                        Text(
                            s.addRoomsFirst,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val hScroll = rememberScrollState()
                        Box(Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                                // Header row
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Spacer(Modifier.width(120.dp))
                                    tiers.forEachIndexed { tierIdx, tier ->
                                        Column(
                                            Modifier.width(140.dp).padding(horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "${tier.label} ${s.nightsAbbr}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (tierIdx > 0) {
                                                TextButton(
                                                    onClick = {
                                                        breakpoints = breakpoints.filter { it != breakpoints[tierIdx - 1] }
                                                        prices = emptyMap()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                                ) {
                                                    Text(
                                                        s.delete,
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            } else {
                                                Spacer(Modifier.height(28.dp))
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider()

                                // Room rows
                                rooms.forEach { room ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            s.roomLabel(room.number),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.width(120.dp)
                                        )
                                        tiers.forEachIndexed { tierIdx, _ ->
                                            val key = room.id to tierIdx
                                            OutlinedTextField(
                                                value = prices[key] ?: "",
                                                onValueChange = { v -> prices = prices + (key to v) },
                                                placeholder = { Text("PLN") },
                                                singleLine = true,
                                                modifier = Modifier.width(140.dp).padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Actions ──────────────────────────────────────────────────────────
            val fromYearInt = fromYear.toIntOrNull()
            val toYearInt   = toYear.toIntOrNull()
            val canGenerate = fromDate.isNotBlank() &&
                toDate.isNotBlank() &&
                fromYearInt != null && toYearInt != null &&
                fromYearInt <= toYearInt &&
                rooms.isNotEmpty() &&
                !generating

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            generating = true
                            try {
                                val yFrom = fromYear.toIntOrNull() ?: return@launch
                                val yTo   = toYear.toIntOrNull()   ?: return@launch
                                val baseFromYear = fromDate.substring(0, 4).toIntOrNull() ?: return@launch
                                val baseToYear   = toDate.substring(0, 4).toIntOrNull()   ?: return@launch
                                val fromMD = fromDate.substring(4) // "-MM-DD"
                                val toMD   = toDate.substring(4)
                                var count = 0
                                for (year in yFrom..yTo) {
                                    val offset = year - baseFromYear
                                    val genFrom = "${baseFromYear + offset}$fromMD"
                                    val genTo   = "${baseToYear + offset}$toMD"
                                    for (room in rooms) {
                                        tiers.forEachIndexed { tierIdx, tier ->
                                            val price = prices[room.id to tierIdx]
                                                ?.toDoubleOrNull() ?: return@forEachIndexed
                                            client.post("$BASE_URL/api/price-rules") {
                                                contentType(ContentType.Application.Json)
                                                setBody(CreatePriceRuleRequest(
                                                    roomId                 = room.id,
                                                    fromDate               = genFrom,
                                                    toDate                 = genTo,
                                                    minNights              = tier.minNights,
                                                    maxNights              = tier.maxNights,
                                                    pricePerPersonPerNight = price,
                                                    currency               = "PLN"
                                                ))
                                            }
                                            count++
                                        }
                                    }
                                }
                                snackbar.showSnackbar(s.ruleGeneratorSuccess(count))
                                onDone()
                            } catch (e: Exception) {
                                snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                            }
                            generating = false
                        }
                    },
                    enabled = canGenerate
                ) {
                    if (generating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(s.ruleGeneratorGenerate)
                }
                TextButton(onClick = onBack) { Text(s.cancel) }
            }

            Spacer(Modifier.height(16.dp))
        }

        AppVerticalScrollbar(
            state    = vScroll,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}
