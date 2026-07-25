package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.*
import org.julsz.smnt.auth.requireHotelManager
import org.julsz.smnt.db.InvoiceItems
import org.julsz.smnt.db.Invoices
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

fun Route.invoiceRoutes() {
    get("/invoices") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing hotelId")
        call.respond(queryInvoices(hotelId))
    }

    get("/invoices/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val invoice = queryInvoice(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(invoice)
    }

    post("/invoices") {
        val req = call.receive<CreateInvoiceRequest>()
        if (!call.requireHotelManager(req.hotelId)) return@post
        val duplicate = transaction {
            Invoices.selectAll()
                .where { (Invoices.hotelId eq req.hotelId) and (Invoices.invoiceNumber eq req.invoiceNumber) }
                .count() > 0
        }
        if (duplicate) return@post call.respond(HttpStatusCode.Conflict, "Invoice number already exists")
        call.respond(HttpStatusCode.Created, createInvoice(req))
    }

    put("/invoices/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = invoiceHotelId(id)
            ?: return@put call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@put
        val req = call.receive<UpdateInvoiceRequest>()
        val duplicate = transaction {
            Invoices.selectAll()
                .where { (Invoices.invoiceNumber eq req.invoiceNumber) and (Invoices.id neq id) }
                .count() > 0
        }
        if (duplicate) return@put call.respond(HttpStatusCode.Conflict, "Invoice number already exists")
        val updated = updateInvoice(id, req) ?: return@put call.respond(HttpStatusCode.NotFound)
        call.respond(updated)
    }

    delete("/invoices/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = invoiceHotelId(id)
            ?: return@delete call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@delete
        transaction {
            InvoiceItems.deleteWhere { InvoiceItems.invoiceId eq id }
            Invoices.deleteWhere { Invoices.id eq id }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun invoiceHotelId(invoiceId: Int): Int? = transaction {
    Invoices.selectAll().where { Invoices.id eq invoiceId }.firstOrNull()?.get(Invoices.hotelId)
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
    val totalAmount = req.items.sumOf { it.quantity * it.unitPrice }
    val bd = { v: Double -> BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP) }

    val newId = Invoices.insert {
        it[hotelId]           = req.hotelId
        it[reservationId]     = req.reservationId
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
        it[Invoices.totalNet]   = bd(totalAmount)
        it[Invoices.totalVat]   = bd(0.0)
        it[Invoices.totalGross] = bd(totalAmount)
        it[notes]             = req.notes
        it[createdAt]         = LocalDateTime.now()
    } get Invoices.id

    val createdItems = req.items.map { itemReq ->
        val amount = itemReq.quantity * itemReq.unitPrice
        val itemId = InvoiceItems.insert {
            it[invoiceId]    = newId
            it[ordinal]      = itemReq.ordinal
            it[name]         = itemReq.name
            it[quantity]     = BigDecimal.valueOf(itemReq.quantity)
            it[unit]         = itemReq.unit
            it[unitNetPrice] = bd(itemReq.unitPrice)
            it[vatRate]      = "zw"
            it[netAmount]    = bd(amount)
            it[vatAmount]    = bd(0.0)
            it[grossAmount]  = bd(amount)
        } get InvoiceItems.id
        InvoiceItemDto(
            id = itemId, invoiceId = newId, ordinal = itemReq.ordinal,
            name = itemReq.name, quantity = itemReq.quantity, unit = itemReq.unit,
            unitPrice = itemReq.unitPrice, amount = amount
        )
    }

    InvoiceDto(
        id = newId, hotelId = req.hotelId, reservationId = req.reservationId,
        invoiceNumber = req.invoiceNumber,
        issueDate = req.issueDate, saleDate = req.saleDate, dueDate = req.dueDate,
        paymentMethod = req.paymentMethod,
        sellerName = req.sellerName, sellerAddress = req.sellerAddress,
        sellerNip = req.sellerNip, sellerRegon = req.sellerRegon,
        sellerBankAccount = req.sellerBankAccount,
        sellerPhone = req.sellerPhone, sellerEmail = req.sellerEmail,
        buyerName = req.buyerName, buyerAddress = req.buyerAddress,
        buyerNip = req.buyerNip, buyerRegon = req.buyerRegon,
        totalAmount = totalAmount,
        notes = req.notes, createdAt = LocalDateTime.now().toString(),
        items = createdItems
    )
}

private fun updateInvoice(id: Int, req: UpdateInvoiceRequest): InvoiceDto? = transaction {
    Invoices.selectAll().where { Invoices.id eq id }.singleOrNull() ?: return@transaction null
    val totalAmount = req.items.sumOf { it.quantity * it.unitPrice }
    val bd = { v: Double -> BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP) }

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
        it[Invoices.totalNet]   = bd(totalAmount)
        it[Invoices.totalVat]   = bd(0.0)
        it[Invoices.totalGross] = bd(totalAmount)
        it[notes]             = req.notes
    }

    InvoiceItems.deleteWhere { InvoiceItems.invoiceId eq id }
    val updatedItems = req.items.map { itemReq ->
        val amount = itemReq.quantity * itemReq.unitPrice
        val itemId = InvoiceItems.insert {
            it[invoiceId]    = id
            it[ordinal]      = itemReq.ordinal
            it[name]         = itemReq.name
            it[quantity]     = BigDecimal.valueOf(itemReq.quantity)
            it[unit]         = itemReq.unit
            it[unitNetPrice] = bd(itemReq.unitPrice)
            it[vatRate]      = "zw"
            it[netAmount]    = bd(amount)
            it[vatAmount]    = bd(0.0)
            it[grossAmount]  = bd(amount)
        } get InvoiceItems.id
        InvoiceItemDto(
            id = itemId, invoiceId = id, ordinal = itemReq.ordinal,
            name = itemReq.name, quantity = itemReq.quantity, unit = itemReq.unit,
            unitPrice = itemReq.unitPrice, amount = amount
        )
    }

    val row = Invoices.selectAll().where { Invoices.id eq id }.single()
    row.toDto(updatedItems)
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
    totalAmount       = this[Invoices.totalGross].toDouble(),
    notes             = this[Invoices.notes],
    createdAt         = this[Invoices.createdAt].toString(),
    items             = items
)

private fun ResultRow.toItemDto() = InvoiceItemDto(
    id        = this[InvoiceItems.id],
    invoiceId = this[InvoiceItems.invoiceId],
    ordinal   = this[InvoiceItems.ordinal],
    name      = this[InvoiceItems.name],
    quantity  = this[InvoiceItems.quantity].toDouble(),
    unit      = this[InvoiceItems.unit],
    unitPrice = this[InvoiceItems.unitNetPrice].toDouble(),
    amount    = this[InvoiceItems.grossAmount].toDouble()
)
