package org.julsz.smnt

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.julsz.smnt.invoice.*
import java.awt.Desktop
import java.io.File
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

    var invoices         by remember { mutableStateOf<List<InvoiceDto>>(emptyList()) }
    var reservations     by remember { mutableStateOf<List<ReservationDto>>(emptyList()) }
    var invoiceSettings  by remember { mutableStateOf<InvoiceSettingsDto?>(null) }
    var loading          by remember { mutableStateOf(true) }
    var showCreate       by remember { mutableStateOf(false) }
    var deleteTarget     by remember { mutableStateOf<InvoiceDto?>(null) }
    var editTarget       by remember { mutableStateOf<InvoiceDto?>(null) }
    // Captured separately so that calling onInitialConsumed() doesn't clear it before the dialog opens
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

    // Auto-open dialog when navigated from a reservation.
    // Wait for reload() to finish so invoiceSettings is populated before the dialog opens,
    // then capture the reservation locally before clearing the parent reference.
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
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(32.dp),
                contentAlignment = Alignment.Center) {
                Text(s.noInvoices, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            InvoiceListTable(
                invoices = invoices,
                onDownloadPdf = { inv ->
                    scope.launch { downloadPdf(inv, s, snackbar) }
                },
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
            fontScale          = fontScale,
            onDismiss          = { showCreate = false; dialogInitialRes = null },
            onCreate           = { created ->
                showCreate = false
                dialogInitialRes = null
                scope.launch {
                    reload()
                    downloadPdf(created, s, snackbar)
                }
            }
        )
    }

    editTarget?.let { inv ->
        EditInvoiceDialog(
            client    = client,
            invoice   = inv,
            fontScale = fontScale,
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

// ─── Invoice list table ───────────────────────────────────────────────────────

@Composable
private fun InvoiceListTable(
    invoices: List<InvoiceDto>,
    onDownloadPdf: (InvoiceDto) -> Unit,
    onEdit: (InvoiceDto) -> Unit,
    onDelete: (InvoiceDto) -> Unit
) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TableHeaderCell(s.invoiceNumberPreviewLabel, Modifier.width(130.dp))
            TableHeaderCell(s.invoiceIssueDateLabel.trimEnd('*', ' '), Modifier.width(100.dp))
            TableHeaderCell(s.buyerSection,               Modifier.weight(1f))
            TableHeaderCell(s.totalAmountLabel,           Modifier.width(100.dp))
            Spacer(Modifier.width(170.dp))
        }
        HorizontalDivider()

        LazyColumn {
            items(invoices) { inv ->
                InvoiceRow(inv, onDownloadPdf, onEdit, onDelete)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InvoiceRow(
    inv: InvoiceDto,
    onDownloadPdf: (InvoiceDto) -> Unit,
    onEdit: (InvoiceDto) -> Unit,
    onDelete: (InvoiceDto) -> Unit
) {
    val s = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(inv.invoiceNumber, Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(inv.issueDate,     Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
        Text(inv.buyerName,     Modifier.weight(1f),    style = MaterialTheme.typography.bodySmall)
        Text("%.2f PLN".format(inv.totalAmount), Modifier.width(100.dp),
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { onEdit(inv) },
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(6.dp)
                ) { Text(s.edit, style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(
                    onClick = { onDownloadPdf(inv) },
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(6.dp)
                ) { Text(s.openInvoiceBtn, style = MaterialTheme.typography.labelSmall) }
            }
            OutlinedButton(
                onClick = { onDelete(inv) },
                modifier = Modifier.fillMaxWidth().height(28.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(s.delete, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

// ─── Create Invoice Dialog (resizable DialogWindow) ───────────────────────────

@Composable
private fun CreateInvoiceDialog(
    client: HttpClient,
    hotel: UserHotelRoleDto,
    reservations: List<ReservationDto>,
    existingInvoices: List<InvoiceDto>,
    initialReservation: ReservationDto?,
    invoiceSettings: InvoiceSettingsDto?,
    fontScale: Float,
    onDismiss: () -> Unit,
    onCreate: (InvoiceDto) -> Unit
) {
    val s             = LocalStrings.current
    val snackbar      = LocalSnackbar.current
    val scope         = rememberCoroutineScope()
    val today         = remember { LocalDate.now() }
    val tempIdCounter = remember { AtomicInteger(1) }
    val dueDays       = invoiceSettings?.defaultDueDays?.toLong() ?: 14L

    // ── Form state — prefilled from invoice settings ────────────────────────
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
    var pendingAddRes     by remember { mutableStateOf<ReservationDto?>(null) }
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
    val resWithExistingInvoice = remember(selectedResList, existingInvoices) {
        selectedResList.firstOrNull { res -> existingInvoices.any { it.reservationId == res.id } }
    }
    val isValid = invoiceSeq.isNotBlank() && sellerName.isNotBlank() && buyerName.isNotBlank() &&
        issueDate.isNotBlank() && saleDate.isNotBlank() && dueDate.isNotBlank() &&
        items.isNotEmpty() && resWithExistingInvoice == null

    val scrollState = rememberScrollState()

    val baseDensity = LocalDensity.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state    = rememberDialogState(size = DpSize(1020.dp, 780.dp)),
        resizable = true,
        title    = s.createInvoiceTitle
    ) {
        CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {

                // ── Scrollable body ────────────────────────────────────────
                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(s.createInvoiceTitle,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        // ── Invoice number + linked reservations ───────────
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value         = invoiceSeq,
                                    onValueChange = { invoiceSeq = it },
                                    label         = { Text("FA  ___  / ${today.year}") },
                                    placeholder   = { Text("np. 1") },
                                    modifier      = Modifier.fillMaxWidth(),
                                    singleLine    = true,
                                    textStyle     = MaterialTheme.typography.bodySmall,
                                    isError       = submitted && invoiceSeq.isBlank()
                                )
                                if (invoiceSeq.isNotBlank()) {
                                    Text(
                                        fullInvoiceNumber,
                                        style     = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color     = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.linkedReservationLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MultiReservationPicker(
                                    allReservations  = reservations,
                                    existingInvoices = existingInvoices,
                                    selectedResList  = selectedResList,
                                    onAdd = { res ->
                                        if (selectedResList.isNotEmpty() && res.guestId != selectedResList.first().guestId) {
                                            pendingAddRes = res
                                        } else {
                                            val newList = selectedResList + res
                                            selectedResList = newList
                                            populateFromReservations(newList)
                                        }
                                    },
                                    onRemove = { res ->
                                        val newList = selectedResList.filter { it.id != res.id }
                                        selectedResList = newList
                                        populateFromReservations(newList)
                                    },
                                    s = s
                                )
                                resWithExistingInvoice?.let { res ->
                                    val existingInv = existingInvoices.first { it.reservationId == res.id }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            s.invoiceAlreadyExistsFor(existingInv.invoiceNumber),
                                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // ── Seller + Buyer ─────────────────────────────────
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionTitle(s.sellerSection)
                                InvoiceTextField(sellerName, { sellerName = it }, s.sellerNameLabel, isError = submitted && sellerName.isBlank())
                                InvoiceTextField(sellerAddress, { sellerAddress = it }, s.sellerAddressLabel)
                                InvoiceTextField(sellerNip, { sellerNip = it }, s.nipLabel)
                                InvoiceTextField(sellerRegon, { sellerRegon = it }, s.regonLabel)
                                InvoiceTextField(sellerBankAccount, { sellerBankAccount = it }, s.bankAccountLabel)
                                InvoiceTextField(sellerPhone, { sellerPhone = it }, "Tel.")
                                InvoiceTextField(sellerEmail, { sellerEmail = it }, "E-mail")
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionTitle(s.buyerSection)
                                InvoiceTextField(buyerName, { buyerName = it }, s.buyerNameLabel, isError = submitted && buyerName.isBlank())
                                InvoiceTextField(buyerAddress, { buyerAddress = it }, s.buyerAddressLabel)
                                InvoiceTextField(buyerNip, { buyerNip = it }, s.nipLabel)
                                InvoiceTextField(buyerRegon, { buyerRegon = it }, s.regonLabel)
                            }
                        }

                        HorizontalDivider()

                        // ── Dates + Payment ────────────────────────────────
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                InvoiceTextField(issueDate, { issueDate = it }, s.invoiceIssueDateLabel, placeholder = "YYYY-MM-DD", isError = submitted && issueDate.isBlank())
                                InvoiceTextField(saleDate,  { saleDate  = it }, s.invoiceSaleDateLabel,  placeholder = "YYYY-MM-DD", isError = submitted && saleDate.isBlank())
                                InvoiceTextField(dueDate,   { dueDate   = it }, s.invoiceDueDateLabel,   placeholder = "YYYY-MM-DD", isError = submitted && dueDate.isBlank())
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(s.paymentMethodLabel, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PaymentMethodSelector(paymentMethod, { paymentMethod = it }, s)
                                InvoiceTextField(invoiceNotes, { invoiceNotes = it }, s.notesLabel)
                            }
                        }

                        HorizontalDivider()

                        // ── Line items ─────────────────────────────────────
                        SectionTitle(s.itemsSection)
                        ItemsEditor(
                            items    = items,
                            onChange = { items = it },
                            nextId   = { tempIdCounter.getAndIncrement() }
                        )

                        HorizontalDivider()
                        TotalAmountRow(totals)
                    }

                    VerticalScrollbar(
                        adapter  = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }

                // ── Bottom action bar ──────────────────────────────────────
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically
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
                        else Text(s.createAndDownloadBtn)
                    }
                }
            }

            // ── Guest-mismatch warning ─────────────────────────────────────
            pendingAddRes?.let { pending ->
                AppAlertDialog(
                    onDismissRequest = { pendingAddRes = null },
                    title = { Text(s.differentGuestTitle) },
                    text  = {
                        Text(s.differentGuestWarning(
                            pending.guestName,
                            selectedResList.firstOrNull()?.guestName ?: ""
                        ))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val newList = selectedResList + pending
                            selectedResList = newList
                            populateFromReservations(newList)
                            pendingAddRes = null
                        }) { Text(s.addAnywayBtn) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingAddRes = null }) { Text(s.cancel) }
                    }
                )
            }
        }
        } // CompositionLocalProvider
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun InvoiceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isError: Boolean = false
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = true,
        textStyle     = MaterialTheme.typography.bodySmall,
        isError       = isError
    )
}

@Composable
private fun PaymentMethodSelector(
    selected: String,
    onSelect: (String) -> Unit,
    s: AppStrings
) {
    val methods = listOf(
        "transfer" to s.paymentMethodTransfer,
        "cash"     to s.paymentMethodCash,
        "card"     to s.paymentMethodCard,
        "blik"     to s.paymentMethodBlik
    )
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        methods.forEach { (code, label) ->
            val sel = selected == code
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(enabled = !sel) { onSelect(code) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MultiReservationPicker(
    allReservations: List<ReservationDto>,
    existingInvoices: List<InvoiceDto>,
    selectedResList: List<ReservationDto>,
    onAdd: (ReservationDto) -> Unit,
    onRemove: (ReservationDto) -> Unit,
    s: AppStrings
) {
    val available = allReservations.filter { r ->
        r.status !in setOf("cancelled", "no_show") &&
        selectedResList.none { it.id == r.id } &&
        existingInvoices.none { it.reservationId == r.id }
    }
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (selectedResList.isEmpty()) {
            Text(s.noLinkedReservation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            selectedResList.forEach { res ->
                Surface(
                    shape    = RoundedCornerShape(6.dp),
                    color    = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("#${res.id} · ${res.guestName}",
                                style      = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
                            Text("${res.checkInDate} – ${res.checkOutDate}  ·  ${res.roomNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemove(res) }, modifier = Modifier.size(28.dp)) {
                            Text("✕", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (available.isNotEmpty()) {
            Box {
                OutlinedButton(
                    onClick        = { expanded = true },
                    shape          = RoundedCornerShape(6.dp),
                    modifier       = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(s.addReservationHint, style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    available.forEach { res ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("#${res.id} ${res.guestName}",
                                        style      = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium)
                                    Text("${res.checkInDate} – ${res.checkOutDate}  ·  ${res.roomNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { onAdd(res); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemsEditor(
    items: List<InvoiceLineItem>,
    onChange: (List<InvoiceLineItem>) -> Unit,
    nextId: () -> Int
) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { idx, item ->
            ItemRow(
                item     = item,
                ordinal  = idx + 1,
                onChange = { updated -> onChange(items.toMutableList().also { it[idx] = updated }) },
                onDelete = { onChange(items.toMutableList().also { it.removeAt(idx) }) }
            )
            if (idx < items.lastIndex)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }

        OutlinedButton(
            onClick = {
                onChange(items + InvoiceLineItem(
                    tempId    = nextId(),
                    ordinal   = items.size + 1,
                    name      = "",
                    quantity  = 1.0,
                    unit      = "szt.",
                    unitPrice = 0.0
                ))
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) { Text(s.addItemBtn, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun ItemRow(
    item: InvoiceLineItem,
    ordinal: Int,
    onChange: (InvoiceLineItem) -> Unit,
    onDelete: () -> Unit
) {
    var nameText  by remember(item.tempId) { mutableStateOf(item.name) }
    var unitText  by remember(item.tempId) { mutableStateOf(item.unit) }
    var qtyText   by remember(item.tempId) { mutableStateOf(item.quantity.let { if (it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.ROOT, "%.6f", it).trimEnd('0').trimEnd('.') }) }
    var priceText by remember(item.tempId) { mutableStateOf("%.2f".format(item.unitPrice)) }

    val qty    = qtyText.replace(',', '.').toDoubleOrNull() ?: item.quantity
    val price  = priceText.replace(',', '.').toDoubleOrNull() ?: item.unitPrice
    val amount = qty * price

    fun commit() = onChange(item.copy(name = nameText, unit = unitText, quantity = qty, unitPrice = price))

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$ordinal.",
                Modifier.width(24.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value         = nameText,
                onValueChange = { nameText = it; onChange(item.copy(name = it)) },
                modifier      = Modifier.weight(1f),
                label         = { Text("Nazwa") },
                textStyle     = MaterialTheme.typography.bodySmall,
                singleLine    = true
            )
            IconButton(onClick = { commit(); onDelete() }, modifier = Modifier.size(40.dp)) {
                Text("✕", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 30.dp, end = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = unitText,
                onValueChange = { unitText = it; onChange(item.copy(unit = it)) },
                modifier      = Modifier.width(70.dp),
                label         = { Text("J.m.") },
                textStyle     = MaterialTheme.typography.bodySmall,
                singleLine    = true
            )
            OutlinedTextField(
                value         = qtyText,
                onValueChange = { v ->
                    qtyText = v
                    val newQty = v.replace(',', '.').toDoubleOrNull() ?: qty
                    onChange(item.copy(name = nameText, unit = unitText, quantity = newQty, unitPrice = price))
                },
                modifier      = Modifier.width(76.dp),
                label         = { Text("Ilość") },
                textStyle     = MaterialTheme.typography.bodySmall,
                singleLine    = true
            )
            OutlinedTextField(
                value         = priceText,
                onValueChange = { v ->
                    priceText = v
                    val newPrice = v.replace(',', '.').toDoubleOrNull() ?: price
                    onChange(item.copy(name = nameText, unit = unitText, quantity = qty, unitPrice = newPrice))
                },
                modifier      = Modifier.width(100.dp),
                label         = { Text("Cena") },
                textStyle     = MaterialTheme.typography.bodySmall,
                singleLine    = true
            )
            Column(Modifier.weight(1f)) {
                Text("wartość", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.2f".format(amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TotalAmountRow(totals: InvoiceTotals) {
    val s = LocalStrings.current
    Row(
        Modifier.fillMaxWidth(0.5f).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(s.totalAmountLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text("%.2f PLN".format(totals.totalAmount), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

// ─── Edit Invoice Dialog ──────────────────────────────────────────────────────

@Composable
private fun EditInvoiceDialog(
    client: HttpClient,
    invoice: InvoiceDto,
    fontScale: Float,
    onDismiss: () -> Unit,
    onSaved: (InvoiceDto) -> Unit
) {
    val s             = LocalStrings.current
    val snackbar      = LocalSnackbar.current
    val scope         = rememberCoroutineScope()
    val tempIdCounter = remember { AtomicInteger(invoice.items.size + 1) }

    val faRegex = remember { Regex("^FA (.+)/(\\d{4})$") }
    val faMatch = remember { faRegex.find(invoice.invoiceNumber) }
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
            InvoiceLineItem(
                tempId    = idx + 1,
                ordinal   = item.ordinal,
                name      = item.name,
                quantity  = item.quantity,
                unit      = item.unit,
                unitPrice = item.unitPrice
            )
        })
    }
    var submitting by remember { mutableStateOf(false) }
    var submitted  by remember { mutableStateOf(false) }

    val totals  = remember(items) { InvoiceCalculator.calculateTotals(items) }
    val isValid = invoiceSeq.isNotBlank() && sellerName.isNotBlank() && buyerName.isNotBlank() &&
        issueDate.isNotBlank() && saleDate.isNotBlank() && dueDate.isNotBlank() && items.isNotEmpty()

    val scrollState = rememberScrollState()
    val baseDensity = LocalDensity.current

    DialogWindow(
        onCloseRequest = onDismiss,
        state     = rememberDialogState(size = DpSize(1020.dp, 780.dp)),
        resizable = true,
        title     = s.editInvoiceTitle
    ) {
        CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(s.editInvoiceTitle,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value         = invoiceSeq,
                                    onValueChange = { invoiceSeq = it },
                                    label         = { Text("FA  ___  / $parsedYear") },
                                    modifier      = Modifier.fillMaxWidth(),
                                    singleLine    = true,
                                    textStyle     = MaterialTheme.typography.bodySmall,
                                    isError       = submitted && invoiceSeq.isBlank()
                                )
                                if (invoiceSeq.isNotBlank()) {
                                    Text(
                                        fullInvoiceNumber,
                                        style      = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionTitle(s.sellerSection)
                                InvoiceTextField(sellerName,        { sellerName = it },        s.sellerNameLabel,    isError = submitted && sellerName.isBlank())
                                InvoiceTextField(sellerAddress,     { sellerAddress = it },     s.sellerAddressLabel)
                                InvoiceTextField(sellerNip,         { sellerNip = it },         s.nipLabel)
                                InvoiceTextField(sellerRegon,       { sellerRegon = it },       s.regonLabel)
                                InvoiceTextField(sellerBankAccount, { sellerBankAccount = it }, s.bankAccountLabel)
                                InvoiceTextField(sellerPhone,       { sellerPhone = it },       "Tel.")
                                InvoiceTextField(sellerEmail,       { sellerEmail = it },       "E-mail")
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionTitle(s.buyerSection)
                                InvoiceTextField(buyerName,    { buyerName = it },    s.buyerNameLabel,    isError = submitted && buyerName.isBlank())
                                InvoiceTextField(buyerAddress, { buyerAddress = it }, s.buyerAddressLabel)
                                InvoiceTextField(buyerNip,     { buyerNip = it },     s.nipLabel)
                                InvoiceTextField(buyerRegon,   { buyerRegon = it },   s.regonLabel)
                            }
                        }

                        HorizontalDivider()

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                InvoiceTextField(issueDate, { issueDate = it }, s.invoiceIssueDateLabel, placeholder = "YYYY-MM-DD", isError = submitted && issueDate.isBlank())
                                InvoiceTextField(saleDate,  { saleDate  = it }, s.invoiceSaleDateLabel,  placeholder = "YYYY-MM-DD", isError = submitted && saleDate.isBlank())
                                InvoiceTextField(dueDate,   { dueDate   = it }, s.invoiceDueDateLabel,   placeholder = "YYYY-MM-DD", isError = submitted && dueDate.isBlank())
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(s.paymentMethodLabel, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PaymentMethodSelector(paymentMethod, { paymentMethod = it }, s)
                                InvoiceTextField(invoiceNotes, { invoiceNotes = it }, s.notesLabel)
                            }
                        }

                        HorizontalDivider()

                        SectionTitle(s.itemsSection)
                        ItemsEditor(
                            items    = items,
                            onChange = { items = it },
                            nextId   = { tempIdCounter.getAndIncrement() }
                        )

                        HorizontalDivider()
                        TotalAmountRow(totals)
                    }

                    VerticalScrollbar(
                        adapter  = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically
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
        } // CompositionLocalProvider
    }
}

// ─── PDF save helper ──────────────────────────────────────────────────────────

private suspend fun downloadPdf(inv: InvoiceDto, s: AppStrings, snackbar: SnackbarHostState) {
    try {
        val pdfBytes    = InvoicePdfGenerator.generate(inv)
        val defaultName = "${inv.invoiceNumber.replace('/', '_').replace(' ', '_')}.pdf"
        val target = saveFilePicker(
            title       = s.downloadPdfBtn,
            defaultName = defaultName,
            filter      = "PDF (*.pdf)|*.pdf|All files (*.*)|*.*"
        ) ?: return
        target.writeBytes(pdfBytes)
        snackbar.showSnackbar(s.pdfSavedSuccess)
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(target)
    } catch (e: Exception) {
        snackbar.showSnackbar(s.pdfSaveError + ": " + (e.message ?: "?"))
    }
}
