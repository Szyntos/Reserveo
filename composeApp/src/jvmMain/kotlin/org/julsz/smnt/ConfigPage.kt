package org.julsz.smnt

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


private enum class ConfigSection { Rooms, BasePrice, Holidays, InvoiceConfig }

@Composable
fun ConfigPage(client: HttpClient, hotel: UserHotelRoleDto) {
    var section by remember { mutableStateOf<ConfigSection?>(null) }

    when (section) {
        null ->
            ConfigHub(hotel, onNavigate = { section = it })
        ConfigSection.Rooms ->
            RoomsConfigPage(client, hotel, onBack = { section = null })
        ConfigSection.BasePrice ->
            BasePriceConfigPage(client, hotel, onBack = { section = null })
        ConfigSection.Holidays ->
            HolidaysConfigPage(client, hotel, onBack = { section = null })
        ConfigSection.InvoiceConfig ->
            InvoiceConfigPage(client, hotel, onBack = { section = null })
    }
}

// ─── Hub ──────────────────────────────────────────────────────────────────────

@Composable
private fun ConfigHub(hotel: UserHotelRoleDto, onNavigate: (ConfigSection) -> Unit) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                s.configTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                hotel.hotelName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ConfigSection.entries.forEach { entity ->
                val title = when (entity) {
                    ConfigSection.Rooms         -> s.configRoomsTitle
                    ConfigSection.BasePrice     -> s.configBasePriceTitle
                    ConfigSection.Holidays      -> s.configHolidaysTitle
                    ConfigSection.InvoiceConfig -> s.configInvoiceTitle
                }
                val description = when (entity) {
                    ConfigSection.Rooms         -> s.configRoomsDesc
                    ConfigSection.BasePrice     -> s.configBasePriceDesc
                    ConfigSection.Holidays      -> s.configHolidaysDesc
                    ConfigSection.InvoiceConfig -> s.configInvoiceDesc
                }
                ConfigCard(
                    title       = title,
                    description = description,
                    onClick     = { onNavigate(entity) },
                    modifier    = Modifier.width(220.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfigCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    LocalStrings.current.configManage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─── Invoice config page ──────────────────────────────────────────────────────

@Composable
private fun InvoiceConfigPage(client: HttpClient, hotel: UserHotelRoleDto, onBack: () -> Unit) {
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    val scope    = rememberCoroutineScope()

    var sellerName    by remember { mutableStateOf("") }
    var sellerAddress by remember { mutableStateOf("") }
    var sellerNip     by remember { mutableStateOf("") }
    var sellerRegon   by remember { mutableStateOf("") }
    var sellerBank    by remember { mutableStateOf("") }
    var sellerPhone   by remember { mutableStateOf("") }
    var sellerEmail   by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("transfer") }
    var dueDays       by remember { mutableStateOf("14") }
    var loading       by remember { mutableStateOf(true) }
    var saving        by remember { mutableStateOf(false) }

    LaunchedEffect(hotel.hotelId) {
        loading = true
        try {
            val dto: InvoiceSettingsDto = client.get("$BASE_URL/api/hotels/${hotel.hotelId}/invoice-settings").body()
            sellerName    = dto.sellerName    ?: ""
            sellerAddress = dto.sellerAddress ?: ""
            sellerNip     = dto.sellerNip     ?: ""
            sellerRegon   = dto.sellerRegon   ?: ""
            sellerBank    = dto.sellerBankAccount ?: ""
            sellerPhone   = dto.sellerPhone   ?: ""
            sellerEmail   = dto.sellerEmail   ?: ""
            paymentMethod = dto.defaultPaymentMethod
            dueDays       = dto.defaultDueDays.toString()
        } catch (e: Exception) {
            snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
        }
        loading = false
    }

    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) { Text(s.breadcrumbConfig) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.invoiceConfigTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(s.invoiceConfigSubtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (loading) {
                CircularProgressIndicator()
            } else {
                Card(modifier = Modifier.fillMaxWidth(0.65f)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(s.sellerSection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                        OutlinedTextField(
                            value = sellerName, onValueChange = { sellerName = it },
                            label = { Text(s.sellerNameLabel) }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = sellerAddress, onValueChange = { sellerAddress = it },
                            label = { Text(s.sellerAddressLabel) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sellerNip, onValueChange = { sellerNip = it },
                                label = { Text(s.nipLabel) }, modifier = Modifier.weight(1f), singleLine = true
                            )
                            OutlinedTextField(
                                value = sellerRegon, onValueChange = { sellerRegon = it },
                                label = { Text(s.regonLabel) }, modifier = Modifier.weight(1f), singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = sellerBank, onValueChange = { sellerBank = it },
                            label = { Text(s.bankAccountLabel) }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sellerPhone, onValueChange = { sellerPhone = it },
                                label = { Text("Tel.") }, modifier = Modifier.weight(1f), singleLine = true
                            )
                            OutlinedTextField(
                                value = sellerEmail, onValueChange = { sellerEmail = it },
                                label = { Text("E-mail") }, modifier = Modifier.weight(1f), singleLine = true
                            )
                        }

                        HorizontalDivider()

                        Text(s.paymentMethodLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "transfer" to s.paymentMethodTransfer,
                                "cash"     to s.paymentMethodCash,
                                "card"     to s.paymentMethodCard
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = paymentMethod == code,
                                    onClick  = { paymentMethod = code },
                                    label    = { Text(label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = dueDays, onValueChange = { dueDays = it.filter { c -> c.isDigit() } },
                            label = { Text(s.defaultDueDaysLabel) }, singleLine = true,
                            modifier = Modifier.width(160.dp)
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    try {
                                        client.put("$BASE_URL/api/hotels/${hotel.hotelId}/invoice-settings") {
                                            contentType(ContentType.Application.Json)
                                            setBody(SaveInvoiceSettingsRequest(
                                                sellerName           = sellerName.ifBlank { null },
                                                sellerAddress        = sellerAddress.ifBlank { null },
                                                sellerNip            = sellerNip.ifBlank { null },
                                                sellerRegon          = sellerRegon.ifBlank { null },
                                                sellerBankAccount    = sellerBank.ifBlank { null },
                                                sellerPhone          = sellerPhone.ifBlank { null },
                                                sellerEmail          = sellerEmail.ifBlank { null },
                                                defaultPaymentMethod = paymentMethod,
                                                defaultDueDays       = dueDays.toIntOrNull() ?: 14
                                            ))
                                        }
                                        snackbar.showSnackbar(s.savedLabel)
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                                    }
                                    saving = false
                                }
                            },
                            enabled = !saving
                        ) {
                            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(s.save)
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter  = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

// ─── Holidays config page ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HolidaysConfigPage(client: HttpClient, hotel: UserHotelRoleDto, onBack: () -> Unit) {
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    val scope    = rememberCoroutineScope()

    var holidays by remember { mutableStateOf<List<HolidayDto>>(emptyList()) }
    var loading  by remember { mutableStateOf(true) }

    var newName     by remember { mutableStateOf("") }
    var newFromDate by remember { mutableStateOf("") }
    var newToDate   by remember { mutableStateOf("") }

    LaunchedEffect(hotel.hotelId) {
        loading = true
        try {
            holidays = client.get("$BASE_URL/api/holidays?hotelId=${hotel.hotelId}").body()
        } catch (e: Exception) {
            snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
        }
        loading = false
    }

    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) { Text(s.breadcrumbConfig) }

            Text(s.configHolidaysTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            if (loading) {
                CircularProgressIndicator()
            } else {
                // ── Add form ──
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.addHolidayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value          = newName,
                            onValueChange  = { newName = it },
                            label          = { Text(s.holidayNameLabel) },
                            placeholder    = { Text(s.holidayNamePlaceholder) },
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value         = newFromDate,
                                onValueChange = { newFromDate = it },
                                label         = { Text(s.fromLabel) },
                                placeholder   = { Text("YYYY-MM-DD") },
                                modifier      = Modifier.weight(1f),
                                singleLine    = true
                            )
                            OutlinedTextField(
                                value         = newToDate,
                                onValueChange = { newToDate = it },
                                label         = { Text(s.toLabel) },
                                placeholder   = { Text("YYYY-MM-DD") },
                                modifier      = Modifier.weight(1f),
                                singleLine    = true
                            )
                        }
                        val canAdd = newName.isNotBlank() &&
                            runCatching { java.time.LocalDate.parse(newFromDate.trim()) }.isSuccess &&
                            runCatching { java.time.LocalDate.parse(newToDate.trim()) }.isSuccess
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val created: HolidayDto = client.post("$BASE_URL/api/holidays") {
                                                contentType(ContentType.Application.Json)
                                                setBody(CreateHolidayRequest(
                                                    hotelId  = hotel.hotelId,
                                                    name     = newName.trim(),
                                                    fromDate = newFromDate.trim(),
                                                    toDate   = newToDate.trim()
                                                ))
                                            }.body()
                                            holidays = (holidays + created).sortedBy { it.fromDate }
                                            newName = ""; newFromDate = ""; newToDate = ""
                                        } catch (e: Exception) {
                                            snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                                        }
                                    }
                                },
                                enabled = canAdd
                            ) { Text(s.addHolidayBtn) }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        val file = openFilePicker(
                                            title  = s.importCsvBtn,
                                            filter = "CSV files (*.csv)|*.csv|All files (*.*)|*.*"
                                        )
                                        if (file == null) return@launch
                                        val csv = withContext(Dispatchers.IO) { file.readText() }
                                        val response: ImportHolidaysResponse = client.post("$BASE_URL/api/holidays/import") {
                                            contentType(ContentType.Application.Json)
                                            setBody(ImportHolidaysRequest(hotelId = hotel.hotelId, csv = csv))
                                        }.body()
                                        holidays = (holidays + response.holidays).sortedBy { it.fromDate }
                                        snackbar.showSnackbar(s.importCsvResult(response.imported))
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                                    }
                                }
                            }) { Text(s.importCsvBtn) }
                        }
                    }
                }

                // ── List ──
                if (holidays.isEmpty()) {
                    Text(
                        s.noHolidays,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val byYear = remember(holidays) {
                        holidays.groupBy { it.fromDate.take(4) }.entries.sortedBy { it.key }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        byYear.forEach { (year, yearHolidays) ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    year,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                                ) {
                                    yearHolidays.forEach { h ->
                                        Card(modifier = Modifier.width(260.dp)) {
                                            Row(
                                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(h.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                                    val dateStr = if (h.fromDate == h.toDate) h.fromDate
                                                                  else "${h.fromDate} – ${h.toDate}"
                                                    Text(dateStr, style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                client.delete("$BASE_URL/api/holidays/${h.id}")
                                                                holidays = holidays.filter { it.id != h.id }
                                                            } catch (e: Exception) {
                                                                snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text("×", style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.error)
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

        VerticalScrollbar(
            adapter  = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}
