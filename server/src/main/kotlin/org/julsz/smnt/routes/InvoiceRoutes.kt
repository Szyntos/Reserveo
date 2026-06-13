package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.*
import org.julsz.smnt.db.InvoiceItems
import org.julsz.smnt.db.Invoices
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

fun Route.invoiceRoutes() {
    get("/invoices/next-number") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing hotelId")
        val number = nextInvoiceNumber(hotelId)
        call.respond(NextInvoiceNumberResponse(number))
    }

    get("/invoices") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing hotelId")
        call.respond(queryInvoices(hotelId))
    }

    get("/invoices/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val invoice = queryInvoice(id)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(invoice)
    }

    post("/invoices") {
        val req = call.receive<CreateInvoiceRequest>()
        val created = createInvoice(req)
        call.respond(HttpStatusCode.Created, created)
    }

    put("/invoices/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val req = call.receive<UpdateInvoiceRequest>()
        val updated = updateInvoice(id, req) ?: return@put call.respond(HttpStatusCode.NotFound)
        call.respond(updated)
    }

    delete("/invoices/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        transaction {
            InvoiceItems.deleteWhere { InvoiceItems.invoiceId eq id }
            Invoices.deleteWhere { Invoices.id eq id }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun nextInvoiceNumber(hotelId: Int): String {
    val year = LocalDate.now().year
    val count = transaction {
        Invoices.selectAll()
            .where { (Invoices.hotelId eq hotelId) and (Invoices.issueDate greaterEq LocalDate.of(year, 1, 1)) }
            .count()
    }
    return "FA ${count + 1}/$year"
}

private fun queryInvoices(hotelId: Int): List<InvoiceDto> = transaction {
    Invoices.selectAll()
        .where { Invoices.hotelId eq hotelId }
        .orderBy(Invoices.id to SortOrder.DESC)
        .map { row ->
            val invId = row[Invoices.id]
            val items = InvoiceItems.selectAll()
                .where { InvoiceItems.invoiceId eq invId }
                .orderBy(InvoiceItems.ordinal to SortOrder.ASC)
                .map { it.toItemDto() }
            row.toDto(items)
        }
}

private fun queryInvoice(id: Int): InvoiceDto? = transaction {
    val row = Invoices.selectAll().where { Invoices.id eq id }.singleOrNull() ?: return@transaction null
    val items = InvoiceItems.selectAll()
        .where { InvoiceItems.invoiceId eq id }
        .orderBy(InvoiceItems.ordinal to SortOrder.ASC)
        .map { it.toItemDto() }
    row.toDto(items)
}

private fun createInvoice(req: CreateInvoiceRequest): InvoiceDto = transaction {
    val totalNet      = req.items.sumOf { it.quantity * it.unitNetPrice }
    val totalVat      = req.items.sumOf { vatAmount(it.quantity * it.unitNetPrice, it.vatRate) }
    val totalGross    = totalNet + totalVat
    val resolvedNumber = req.invoiceNumber ?: nextInvoiceNumber(req.hotelId)

    val newId = Invoices.insert {
        it[hotelId]          = req.hotelId
        it[reservationId]    = req.reservationId
        it[invoiceNumber]    = resolvedNumber
        it[issueDate]        = LocalDate.parse(req.issueDate)
        it[saleDate]         = LocalDate.parse(req.saleDate)
        it[dueDate]          = LocalDate.parse(req.dueDate)
        it[paymentMethod]    = req.paymentMethod
        it[sellerName]       = req.sellerName
        it[sellerAddress]    = req.sellerAddress
        it[sellerNip]        = req.sellerNip
        it[sellerRegon]      = req.sellerRegon
        it[sellerBankAccount] = req.sellerBankAccount
        it[sellerPhone]      = req.sellerPhone
        it[sellerEmail]      = req.sellerEmail
        it[buyerName]        = req.buyerName
        it[buyerAddress]     = req.buyerAddress
        it[buyerNip]         = req.buyerNip
        it[buyerRegon]       = req.buyerRegon
        it[Invoices.totalNet]   = BigDecimal.valueOf(totalNet).setScale(2, java.math.RoundingMode.HALF_UP)
        it[Invoices.totalVat]   = BigDecimal.valueOf(totalVat).setScale(2, java.math.RoundingMode.HALF_UP)
        it[Invoices.totalGross] = BigDecimal.valueOf(totalGross).setScale(2, java.math.RoundingMode.HALF_UP)
        it[notes]            = req.notes
        it[createdAt]        = LocalDateTime.now()
    } get Invoices.id

    val createdItems = req.items.map { itemReq ->
        val net   = itemReq.quantity * itemReq.unitNetPrice
        val vat   = vatAmount(net, itemReq.vatRate)
        val gross = net + vat
        val itemId = InvoiceItems.insert {
            it[invoiceId]    = newId
            it[ordinal]      = itemReq.ordinal
            it[name]         = itemReq.name
            it[quantity]     = BigDecimal.valueOf(itemReq.quantity)
            it[unit]         = itemReq.unit
            it[unitNetPrice] = BigDecimal.valueOf(itemReq.unitNetPrice).setScale(2, java.math.RoundingMode.HALF_UP)
            it[vatRate]      = itemReq.vatRate
            it[netAmount]    = BigDecimal.valueOf(net).setScale(2, java.math.RoundingMode.HALF_UP)
            it[vatAmount]    = BigDecimal.valueOf(vat).setScale(2, java.math.RoundingMode.HALF_UP)
            it[grossAmount]  = BigDecimal.valueOf(gross).setScale(2, java.math.RoundingMode.HALF_UP)
        } get InvoiceItems.id
        InvoiceItemDto(
            id = itemId, invoiceId = newId, ordinal = itemReq.ordinal,
            name = itemReq.name, quantity = itemReq.quantity, unit = itemReq.unit,
            unitNetPrice = itemReq.unitNetPrice, vatRate = itemReq.vatRate,
            netAmount = net, vatAmount = vat, grossAmount = gross
        )
    }

    InvoiceDto(
        id = newId, hotelId = req.hotelId, reservationId = req.reservationId,
        invoiceNumber = resolvedNumber,
        issueDate = req.issueDate, saleDate = req.saleDate, dueDate = req.dueDate,
        paymentMethod = req.paymentMethod,
        sellerName = req.sellerName, sellerAddress = req.sellerAddress,
        sellerNip = req.sellerNip, sellerRegon = req.sellerRegon,
        sellerBankAccount = req.sellerBankAccount,
        sellerPhone = req.sellerPhone, sellerEmail = req.sellerEmail,
        buyerName = req.buyerName, buyerAddress = req.buyerAddress, buyerNip = req.buyerNip, buyerRegon = req.buyerRegon,
        totalNet = totalNet, totalVat = totalVat, totalGross = totalGross,
        notes = req.notes, createdAt = LocalDateTime.now().toString(),
        items = createdItems
    )
}

private fun updateInvoice(id: Int, req: UpdateInvoiceRequest): InvoiceDto? = transaction {
    Invoices.selectAll().where { Invoices.id eq id }.singleOrNull() ?: return@transaction null
    val totalNet   = req.items.sumOf { it.quantity * it.unitNetPrice }
    val totalVat   = req.items.sumOf { vatAmount(it.quantity * it.unitNetPrice, it.vatRate) }
    val totalGross = totalNet + totalVat

    Invoices.update({ Invoices.id eq id }) {
        it[invoiceNumber]     = req.invoiceNumber
        it[issueDate]         = LocalDate.parse(req.issueDate)
        it[saleDate]          = LocalDate.parse(req.saleDate)
        it[dueDate]           = LocalDate.parse(req.dueDate)
        it[paymentMethod]     = req.paymentMethod
        it[sellerName]        = req.sellerName
        it[sellerAddress]     = req.sellerAddress
        it[sellerNip]         = req.sellerNip
        it[sellerRegon]       = req.sellerRegon
        it[sellerBankAccount] = req.sellerBankAccount
        it[sellerPhone]       = req.sellerPhone
        it[sellerEmail]       = req.sellerEmail
        it[buyerName]         = req.buyerName
        it[buyerAddress]      = req.buyerAddress
        it[buyerNip]          = req.buyerNip
        it[buyerRegon]        = req.buyerRegon
        it[Invoices.totalNet]   = BigDecimal.valueOf(totalNet).setScale(2, java.math.RoundingMode.HALF_UP)
        it[Invoices.totalVat]   = BigDecimal.valueOf(totalVat).setScale(2, java.math.RoundingMode.HALF_UP)
        it[Invoices.totalGross] = BigDecimal.valueOf(totalGross).setScale(2, java.math.RoundingMode.HALF_UP)
        it[notes]             = req.notes
    }

    InvoiceItems.deleteWhere { InvoiceItems.invoiceId eq id }
    val updatedItems = req.items.map { itemReq ->
        val net   = itemReq.quantity * itemReq.unitNetPrice
        val vat   = vatAmount(net, itemReq.vatRate)
        val gross = net + vat
        val itemId = InvoiceItems.insert {
            it[invoiceId]    = id
            it[ordinal]      = itemReq.ordinal
            it[name]         = itemReq.name
            it[quantity]     = BigDecimal.valueOf(itemReq.quantity)
            it[unit]         = itemReq.unit
            it[unitNetPrice] = BigDecimal.valueOf(itemReq.unitNetPrice).setScale(2, java.math.RoundingMode.HALF_UP)
            it[vatRate]      = itemReq.vatRate
            it[netAmount]    = BigDecimal.valueOf(net).setScale(2, java.math.RoundingMode.HALF_UP)
            it[vatAmount]    = BigDecimal.valueOf(vat).setScale(2, java.math.RoundingMode.HALF_UP)
            it[grossAmount]  = BigDecimal.valueOf(gross).setScale(2, java.math.RoundingMode.HALF_UP)
        } get InvoiceItems.id
        InvoiceItemDto(
            id = itemId, invoiceId = id, ordinal = itemReq.ordinal,
            name = itemReq.name, quantity = itemReq.quantity, unit = itemReq.unit,
            unitNetPrice = itemReq.unitNetPrice, vatRate = itemReq.vatRate,
            netAmount = net, vatAmount = vat, grossAmount = gross
        )
    }

    val row = Invoices.selectAll().where { Invoices.id eq id }.single()
    row.toDto(updatedItems)
}

private fun vatAmount(net: Double, rateCode: String): Double = when (rateCode) {
    "23" -> net * 0.23
    "8"  -> net * 0.08
    "5"  -> net * 0.05
    else -> 0.0
}

private fun ResultRow.toDto(items: List<InvoiceItemDto>) = InvoiceDto(
    id                = this[Invoices.id],
    hotelId           = this[Invoices.hotelId],
    reservationId     = this[Invoices.reservationId],
    invoiceNumber     = this[Invoices.invoiceNumber],
    issueDate         = this[Invoices.issueDate].toString(),
    saleDate          = this[Invoices.saleDate].toString(),
    dueDate           = this[Invoices.dueDate].toString(),
    paymentMethod     = this[Invoices.paymentMethod],
    sellerName        = this[Invoices.sellerName],
    sellerAddress     = this[Invoices.sellerAddress],
    sellerNip         = this[Invoices.sellerNip],
    sellerRegon       = this[Invoices.sellerRegon],
    sellerBankAccount = this[Invoices.sellerBankAccount],
    sellerPhone       = this[Invoices.sellerPhone],
    sellerEmail       = this[Invoices.sellerEmail],
    buyerName         = this[Invoices.buyerName],
    buyerAddress      = this[Invoices.buyerAddress],
    buyerNip          = this[Invoices.buyerNip],
    buyerRegon        = this[Invoices.buyerRegon],
    totalNet          = this[Invoices.totalNet].toDouble(),
    totalVat          = this[Invoices.totalVat].toDouble(),
    totalGross        = this[Invoices.totalGross].toDouble(),
    notes             = this[Invoices.notes],
    createdAt         = this[Invoices.createdAt].toString(),
    items             = items
)

private fun ResultRow.toItemDto() = InvoiceItemDto(
    id           = this[InvoiceItems.id],
    invoiceId    = this[InvoiceItems.invoiceId],
    ordinal      = this[InvoiceItems.ordinal],
    name         = this[InvoiceItems.name],
    quantity     = this[InvoiceItems.quantity].toDouble(),
    unit         = this[InvoiceItems.unit],
    unitNetPrice = this[InvoiceItems.unitNetPrice].toDouble(),
    vatRate      = this[InvoiceItems.vatRate],
    netAmount    = this[InvoiceItems.netAmount].toDouble(),
    vatAmount    = this[InvoiceItems.vatAmount].toDouble(),
    grossAmount  = this[InvoiceItems.grossAmount].toDouble()
)
