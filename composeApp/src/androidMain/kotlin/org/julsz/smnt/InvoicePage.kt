package org.julsz.smnt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.julsz.smnt.invoice.*
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

@Composable
actual fun InvoicePage(
    client: HttpClient,
    hotel: UserHotelRoleDto,
    initialReservation: ReservationDto?,
    onInitialConsumed: () -> Unit,
    fontScale: Float
) {
    val s        = LocalStrings.current
    val snackbar = LocalSnackbar.current
    val scope    = rememberCoroutineScope()

    var invoices        by remember { mutableStateOf<List<InvoiceDto>>(emptyList()) }
    var reservations    by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var invoiceSettings by remember { mutableStateOf<InvoiceSettingsDto?>(null) }
    var loading         by remember { mutableStateOf(true) }
    var showCreate      by remember { mutableStateOf(false) }
    var deleteTarget    by remember { mutableStateOf<InvoiceDto?>(null) }
    var editTarget      by remember { mutableStateOf<InvoiceDto?>(null) }
    var dialogInitialRes by remember { mutableStateOf(initialReservation) }

    suspend fun reload() {
        loading = true
        try {
            invoices        = client.get("$BASE_URL/api/invoices?hotelId=${hotel.hotelId}").body()
            reservations    = client.get("$BASE_URL/api/reservations?hotelId=${hotel.hotelId}").body()
            invoiceSettings = client.get("$BASE_URL/api/hotels/${hotel.hotelId}/invoice-settings").body()
        } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
        loading = false
    }

    LaunchedEffect(hotel.hotelId) { reload() }

    LaunchedEffect(initialReservation) {
        if (initialReservation != null) {
            snapshotFlow { loading }.first { !it }
            dialogInitialRes = initialReservation
            showCreate = true
            onInitialConsumed()
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.invoicesTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showCreate = true }) { Text(s.newInvoiceBtn) }
        }

        if (loading) {
            CircularProgressIndicator()
        } else if (invoices.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(s.noInvoices, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            InvoiceListTable(
                invoices = invoices,
                onEdit   = { editTarget = it },
                onDelete = { deleteTarget = it }
            )
        }
    }

    if (showCreate) {
        CreateInvoiceDialog(
            client             = client,
            hotel              = hotel,
            reservations       = reservations,
            existingInvoices   = invoices,
            initialReservation = dialogInitialRes,
            invoiceSettings    = invoiceSettings,
            onDismiss          = { showCreate = false; dialogInitialRes = null },
            onCreate           = {
                showCreate = false
                dialogInitialRes = null
                scope.launch { reload() }
            }
        )
    }

    editTarget?.let { inv ->
        EditInvoiceDialog(
            client    = client,
            invoice   = inv,
            onDismiss = { editTarget = null },
            onSaved   = { editTarget = null; scope.launch { reload() } }
        )
    }

    deleteTarget?.let { inv ->
        AppAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title   = { Text(s.deleteInvoiceTitle) },
            text    = { Text("${inv.invoiceNumber} · ${inv.buyerName}") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            client.delete("$BASE_URL/api/invoices/${inv.id}")
                            deleteTarget = null
                            reload()
                        } catch (e: Exception) { snackbar.showSnackbar(s.errorMsg(e.message ?: "?")) }
                    }
                }) { Text(s.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(s.cancel) } }
        )
    }
}

// ─── Invoice list ─────────────────────────────────────────────────────────────

@Composable
private fun InvoiceListTable(
    invoices: List<InvoiceDto>,
    onEdit: (InvoiceDto) -> Unit,
    onDelete: (InvoiceDto) -> Unit
) {
    val s = LocalStrings.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(invoices) { inv ->
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border    = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(inv.invoiceNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("${inv.issueDate}  ·  ${inv.buyerName}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.2f PLN".format(inv.totalAmount), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEdit(inv) }, modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp)) {
                            Text(s.edit, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { onDelete(inv) }, modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(s.delete, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ─── Create / Edit dialogs ────────────────────────────────────────────────────

@Composable
private fun CreateInvoiceDialog(
    client: HttpClient,
    hotel: UserHotelRoleDto,
    reservations: List<ReservationDto>,
    existingInvoices: List<InvoiceDto>,
    initialReservation: ReservationDto?,
    invoiceSettings: InvoiceSettingsDto?,
    onDismiss: () -> Unit,
    onCreate: (InvoiceDto) -> Unit
) {
    val s             = LocalStrings.current
    val snackbar      = LocalSnackbar.current
    val scope         = rememberCoroutineScope()
    val today         = remember { LocalDate.now() }
    val tempIdCounter = remember { AtomicInteger(1) }
    val dueDays       = invoiceSettings?.defaultDueDays?.toLong() ?: 14L

    var invoiceSeq        by remember { mutableStateOf("") }
    val fullInvoiceNumber = "FA $invoiceSeq/${today.year}"
    var sellerName        by remember { mutableStateOf(invoiceSettings?.sellerName ?: hotel.hotelName) }
    var sellerAddress     by remember { mutableStateOf(invoiceSettings?.sellerAddress ?: "") }
    var sellerNip         by remember { mutableStateOf(invoiceSettings?.sellerNip ?: "") }
    var sellerRegon       by remember { mutableStateOf(invoiceSettings?.sellerRegon ?: "") }
    var sellerBankAccount by remember { mutableStateOf(invoiceSettings?.sellerBankAccount ?: "") }
    var sellerPhone       by remember { mutableStateOf(invoiceSettings?.sellerPhone ?: "") }
    var sellerEmail       by remember { mutableStateOf(invoiceSettings?.sellerEmail ?: "") }
    var buyerName         by remember { mutableStateOf("") }
    var buyerAddress      by remember { mutableStateOf("") }
    var buyerNip          by remember { mutableStateOf("") }
    var buyerRegon        by remember { mutableStateOf("") }
    var issueDate         by remember { mutableStateOf(today.toString()) }
    var saleDate          by remember { mutableStateOf(today.toString()) }
    var dueDate           by remember { mutableStateOf(today.plusDays(dueDays).toString()) }
    var paymentMethod     by remember { mutableStateOf(invoiceSettings?.defaultPaymentMethod ?: "transfer") }
    var invoiceNotes      by remember { mutableStateOf("") }
    var selectedResList   by remember { mutableStateOf(if (initialReservation != null) listOf(initialReservation) else emptyList()) }
    var items             by remember { mutableStateOf<List<InvoiceLineItem>>(emptyList()) }
    var submitting        by remember { mutableStateOf(false) }
    var submitted         by remember { mutableStateOf(false) }

    fun populateFromReservations(resList: List<ReservationDto>) {
        if (resList.isEmpty()) { items = emptyList(); buyerName = ""; return }
        buyerName = resList.first().guestName
        val combined = resList.flatMap { res ->
            InvoiceCalculator.fromReservation(res) { tempIdCounter.getAndIncrement() }
        }
        items = combined.mapIndexed { idx, item -> item.copy(ordinal = idx + 1) }
    }

    LaunchedEffect(Unit) { populateFromReservations(selectedResList) }

    val totals  = remember(items) { InvoiceCalculator.calculateTotals(items) }
    val isValid = invoiceSeq.isNotBlank() && sellerName.isNotBlank() && buyerName.isNotBlank() &&
        issueDate.isNotBlank() && saleDate.isNotBlank() && dueDate.isNotBlank() && items.isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(s.createInvoiceTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = invoiceSeq, onValueChange = { invoiceSeq = it },
                            label = { Text("FA ___ / ${today.year}") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            isError = submitted && invoiceSeq.isBlank()
                        )

                        HorizontalDivider()
                        Text(s.sellerSection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        InvField(sellerName, { sellerName = it }, s.sellerNameLabel, isError = submitted && sellerName.isBlank())
                        InvField(sellerAddress, { sellerAddress = it }, s.sellerAddressLabel)
                        InvField(sellerNip, { sellerNip = it }, s.nipLabel)
                        InvField(sellerRegon, { sellerRegon = it }, s.regonLabel)
                        InvField(sellerBankAccount, { sellerBankAccount = it }, s.bankAccountLabel)
                        InvField(sellerPhone, { sellerPhone = it }, "Tel.")
                        InvField(sellerEmail, { sellerEmail = it }, "E-mail")

                        HorizontalDivider()
                        Text(s.buyerSection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        InvField(buyerName, { buyerName = it }, s.buyerNameLabel, isError = submitted && buyerName.isBlank())
                        InvField(buyerAddress, { buyerAddress = it }, s.buyerAddressLabel)
                        InvField(buyerNip, { buyerNip = it }, s.nipLabel)
                        InvField(buyerRegon, { buyerRegon = it }, s.regonLabel)

                        HorizontalDivider()
                        InvField(issueDate, { issueDate = it }, s.invoiceIssueDateLabel, placeholder = "YYYY-MM-DD", isError = submitted && issueDate.isBlank())
                        InvField(saleDate,  { saleDate  = it }, s.invoiceSaleDateLabel,  placeholder = "YYYY-MM-DD")
                        InvField(dueDate,   { dueDate   = it }, s.invoiceDueDateLabel,   placeholder = "YYYY-MM-DD")
                        InvField(invoiceNotes, { invoiceNotes = it }, s.notesLabel)

                        HorizontalDivider()
                        Text(s.itemsSection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        SimpleItemsEditor(items = items, onChange = { items = it }, nextId = { tempIdCounter.getAndIncrement() })

                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.totalAmountLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("%.2f PLN".format(totals.totalAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(s.cancel) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            submitted = true
                            if (!isValid) return@Button
                            scope.launch {
                                submitting = true
                                try {
                                    val req = CreateInvoiceRequest(
                                        hotelId           = hotel.hotelId,
                                        reservationId     = selectedResList.firstOrNull()?.id,
                                        invoiceNumber     = fullInvoiceNumber,
                                        issueDate         = issueDate,
                                        saleDate          = saleDate,
                                        dueDate           = dueDate,
                                        paymentMethod     = paymentMethod,
                                        sellerName        = sellerName,
                                        sellerAddress     = sellerAddress.ifBlank { null },
                                        sellerNip         = sellerNip.ifBlank { null },
                                        sellerRegon       = sellerRegon.ifBlank { null },
                                        sellerBankAccount = sellerBankAccount.ifBlank { null },
                                        sellerPhone       = sellerPhone.ifBlank { null },
                                        sellerEmail       = sellerEmail.ifBlank { null },
                                        buyerName         = buyerName,
                                        buyerAddress      = buyerAddress.ifBlank { null },
                                        buyerNip          = buyerNip.ifBlank { null },
                                        buyerRegon        = buyerRegon.ifBlank { null },
                                        notes             = invoiceNotes.ifBlank { null },
                                        items             = items.mapIndexed { idx, it ->
                                            CreateInvoiceItemRequest(
                                                ordinal   = idx + 1,
                                                name      = it.name,
                                                quantity  = it.quantity,
                                                unit      = it.unit,
                                                unitPrice = it.unitPrice
                                            )
                                        }
                                    )
                                    val created: InvoiceDto = client.post("$BASE_URL/api/invoices") {
                                        contentType(ContentType.Application.Json)
                                        setBody(req)
                                    }.body()
                                    onCreate(created)
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                                }
                                submitting = false
                            }
                        },
                        enabled = !submitting
                    ) {
                        if (submitting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(s.save)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditInvoiceDialog(
    client: HttpClient,
    invoice: InvoiceDto,
    onDismiss: () -> Unit,
    onSaved: (InvoiceDto) -> Unit
) {
    val s             = LocalStrings.current
    val snackbar      = LocalSnackbar.current
    val scope         = rememberCoroutineScope()
    val tempIdCounter = remember { AtomicInteger(invoice.items.size + 1) }

    val faRegex    = remember { Regex("^FA (.+)/(\\d{4})$") }
    val faMatch    = remember { faRegex.find(invoice.invoiceNumber) }
    val parsedSeq  = remember { faMatch?.groupValues?.get(1) ?: invoice.invoiceNumber }
    val parsedYear = remember { faMatch?.groupValues?.get(2)?.toIntOrNull() ?: LocalDate.now().year }

    var invoiceSeq      by remember { mutableStateOf(parsedSeq) }
    val fullInvoiceNumber = "FA $invoiceSeq/$parsedYear"
    var sellerName        by remember { mutableStateOf(invoice.sellerName) }
    var sellerAddress     by remember { mutableStateOf(invoice.sellerAddress ?: "") }
    var sellerNip         by remember { mutableStateOf(invoice.sellerNip ?: "") }
    var sellerRegon       by remember { mutableStateOf(invoice.sellerRegon ?: "") }
    var sellerBankAccount by remember { mutableStateOf(invoice.sellerBankAccount ?: "") }
    var sellerPhone       by remember { mutableStateOf(invoice.sellerPhone ?: "") }
    var sellerEmail       by remember { mutableStateOf(invoice.sellerEmail ?: "") }
    var buyerName         by remember { mutableStateOf(invoice.buyerName) }
    var buyerAddress      by remember { mutableStateOf(invoice.buyerAddress ?: "") }
    var buyerNip          by remember { mutableStateOf(invoice.buyerNip ?: "") }
    var buyerRegon        by remember { mutableStateOf(invoice.buyerRegon ?: "") }
    var issueDate         by remember { mutableStateOf(invoice.issueDate) }
    var saleDate          by remember { mutableStateOf(invoice.saleDate) }
    var dueDate           by remember { mutableStateOf(invoice.dueDate) }
    var paymentMethod     by remember { mutableStateOf(invoice.paymentMethod) }
    var invoiceNotes      by remember { mutableStateOf(invoice.notes ?: "") }
    var items             by remember {
        mutableStateOf(invoice.items.mapIndexed { idx, item ->
            InvoiceLineItem(tempId = idx + 1, ordinal = item.ordinal, name = item.name,
                quantity = item.quantity, unit = item.unit, unitPrice = item.unitPrice)
        })
    }
    var submitting by remember { mutableStateOf(false) }

    val totals  = remember(items) { InvoiceCalculator.calculateTotals(items) }
    val isValid = invoiceSeq.isNotBlank() && sellerName.isNotBlank() && buyerName.isNotBlank() &&
        issueDate.isNotBlank() && items.isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(s.editInvoiceTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = invoiceSeq, onValueChange = { invoiceSeq = it },
                            label = { Text("FA ___ / $parsedYear") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        HorizontalDivider()
                        Text(s.sellerSection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        InvField(sellerName, { sellerName = it }, s.sellerNameLabel)
                        InvField(sellerAddress, { sellerAddress = it }, s.sellerAddressLabel)
                        InvField(sellerNip, { sellerNip = it }, s.nipLabel)
                        InvField(sellerRegon, { sellerRegon = it }, s.regonLabel)
                        InvField(sellerBankAccount, { sellerBankAccount = it }, s.bankAccountLabel)
                        InvField(sellerPhone, { sellerPhone = it }, "Tel.")
                        InvField(sellerEmail, { sellerEmail = it }, "E-mail")
                        HorizontalDivider()
                        Text(s.buyerSection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        InvField(buyerName, { buyerName = it }, s.buyerNameLabel)
                        InvField(buyerAddress, { buyerAddress = it }, s.buyerAddressLabel)
                        InvField(buyerNip, { buyerNip = it }, s.nipLabel)
                        InvField(buyerRegon, { buyerRegon = it }, s.regonLabel)
                        HorizontalDivider()
                        InvField(issueDate, { issueDate = it }, s.invoiceIssueDateLabel, placeholder = "YYYY-MM-DD")
                        InvField(saleDate,  { saleDate  = it }, s.invoiceSaleDateLabel,  placeholder = "YYYY-MM-DD")
                        InvField(dueDate,   { dueDate   = it }, s.invoiceDueDateLabel,   placeholder = "YYYY-MM-DD")
                        InvField(invoiceNotes, { invoiceNotes = it }, s.notesLabel)
                        HorizontalDivider()
                        Text(s.itemsSection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        SimpleItemsEditor(items = items, onChange = { items = it }, nextId = { tempIdCounter.getAndIncrement() })
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.totalAmountLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("%.2f PLN".format(totals.totalAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(s.cancel) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (!isValid) return@Button
                            scope.launch {
                                submitting = true
                                try {
                                    val req = UpdateInvoiceRequest(
                                        invoiceNumber     = fullInvoiceNumber,
                                        issueDate         = issueDate,
                                        saleDate          = saleDate,
                                        dueDate           = dueDate,
                                        paymentMethod     = paymentMethod,
                                        sellerName        = sellerName,
                                        sellerAddress     = sellerAddress.ifBlank { null },
                                        sellerNip         = sellerNip.ifBlank { null },
                                        sellerRegon       = sellerRegon.ifBlank { null },
                                        sellerBankAccount = sellerBankAccount.ifBlank { null },
                                        sellerPhone       = sellerPhone.ifBlank { null },
                                        sellerEmail       = sellerEmail.ifBlank { null },
                                        buyerName         = buyerName,
                                        buyerAddress      = buyerAddress.ifBlank { null },
                                        buyerNip          = buyerNip.ifBlank { null },
                                        buyerRegon        = buyerRegon.ifBlank { null },
                                        notes             = invoiceNotes.ifBlank { null },
                                        items             = items.mapIndexed { idx, it ->
                                            CreateInvoiceItemRequest(
                                                ordinal   = idx + 1,
                                                name      = it.name,
                                                quantity  = it.quantity,
                                                unit      = it.unit,
                                                unitPrice = it.unitPrice
                                            )
                                        }
                                    )
                                    val updated: InvoiceDto = client.put("$BASE_URL/api/invoices/${invoice.id}") {
                                        contentType(ContentType.Application.Json)
                                        setBody(req)
                                    }.body()
                                    onSaved(updated)
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(s.errorMsg(e.message ?: "?"))
                                }
                                submitting = false
                            }
                        },
                        enabled = !submitting
                    ) {
                        if (submitting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(s.save)
                    }
                }
            }
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun InvField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        isError = isError
    )
}

@Composable
private fun SimpleItemsEditor(
    items: List<InvoiceLineItem>,
    onChange: (List<InvoiceLineItem>) -> Unit,
    nextId: () -> Int
) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { idx, item ->
            var name  by remember(item.tempId) { mutableStateOf(item.name) }
            var qty   by remember(item.tempId) { mutableStateOf(item.quantity.let { if (it % 1.0 == 0.0) it.toLong().toString() else "%.3f".format(it).trimEnd('0').trimEnd('.') }) }
            var price by remember(item.tempId) { mutableStateOf("%.2f".format(item.unitPrice)) }
            val qtyD  = qty.replace(',', '.').toDoubleOrNull() ?: item.quantity
            val priceD = price.replace(',', '.').toDoubleOrNull() ?: item.unitPrice

            fun commit() = onChange(items.toMutableList().also {
                it[idx] = item.copy(name = name, quantity = qtyD, unitPrice = priceD)
            })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = name, onValueChange = { name = it; commit() },
                    modifier = Modifier.weight(1f), label = { Text("${idx + 1}. ${s.itemsSection}") },
                    textStyle = MaterialTheme.typography.bodySmall, singleLine = true)
                IconButton(onClick = { commit(); onChange(items.toMutableList().also { it.removeAt(idx) }) },
                    modifier = Modifier.size(40.dp)) {
                    Text("✕", color = MaterialTheme.colorScheme.error)
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = qty, onValueChange = { qty = it; commit() },
                    modifier = Modifier.width(80.dp), label = { Text("Ilość") },
                    textStyle = MaterialTheme.typography.bodySmall, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it; commit() },
                    modifier = Modifier.width(100.dp), label = { Text("Cena") },
                    textStyle = MaterialTheme.typography.bodySmall, singleLine = true)
                Text("= %.2f".format(qtyD * priceD),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedButton(
            onClick = {
                onChange(items + InvoiceLineItem(tempId = nextId(), ordinal = items.size + 1,
                    name = "", quantity = 1.0, unit = "szt.", unitPrice = 0.0))
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) { Text(s.addItemBtn, style = MaterialTheme.typography.labelSmall) }
    }
}
