package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.InvoiceSettingsDto
import org.julsz.smnt.SaveInvoiceSettingsRequest
import org.julsz.smnt.db.InvoiceSettings

fun Route.invoiceSettingsRoutes() {
    get("/hotels/{id}/invoice-settings") {
        val hotelId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
        call.respond(getOrDefault(hotelId))
    }

    put("/hotels/{id}/invoice-settings") {
        val hotelId = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val req = call.receive<SaveInvoiceSettingsRequest>()
        call.respond(upsert(hotelId, req))
    }
}

private fun getOrDefault(hotelId: Int): InvoiceSettingsDto = transaction {
    InvoiceSettings.selectAll()
        .where { InvoiceSettings.hotelId eq hotelId }
        .singleOrNull()
        ?.toDto()
        ?: InvoiceSettingsDto(
            hotelId              = hotelId,
            sellerName           = null,
            sellerAddress        = null,
            sellerNip            = null,
            sellerRegon          = null,
            sellerBankAccount    = null,
            sellerPhone          = null,
            sellerEmail          = null,
            defaultPaymentMethod = "transfer",
            defaultDueDays       = 14
        )
}

private fun upsert(hotelId: Int, req: SaveInvoiceSettingsRequest): InvoiceSettingsDto = transaction {
    val exists = InvoiceSettings.selectAll()
        .where { InvoiceSettings.hotelId eq hotelId }
        .count() > 0

    if (exists) {
        InvoiceSettings.update({ InvoiceSettings.hotelId eq hotelId }) {
            it[sellerName]           = req.sellerName
            it[sellerAddress]        = req.sellerAddress
            it[sellerNip]            = req.sellerNip
            it[sellerRegon]          = req.sellerRegon
            it[sellerBankAccount]    = req.sellerBankAccount
            it[sellerPhone]          = req.sellerPhone
            it[sellerEmail]          = req.sellerEmail
            it[defaultPaymentMethod] = req.defaultPaymentMethod
            it[defaultDueDays]       = req.defaultDueDays
        }
    } else {
        InvoiceSettings.insert {
            it[InvoiceSettings.hotelId] = hotelId
            it[sellerName]           = req.sellerName
            it[sellerAddress]        = req.sellerAddress
            it[sellerNip]            = req.sellerNip
            it[sellerRegon]          = req.sellerRegon
            it[sellerBankAccount]    = req.sellerBankAccount
            it[sellerPhone]          = req.sellerPhone
            it[sellerEmail]          = req.sellerEmail
            it[defaultPaymentMethod] = req.defaultPaymentMethod
            it[defaultDueDays]       = req.defaultDueDays
        }
    }

    InvoiceSettingsDto(
        hotelId              = hotelId,
        sellerName           = req.sellerName,
        sellerAddress        = req.sellerAddress,
        sellerNip            = req.sellerNip,
        sellerRegon          = req.sellerRegon,
        sellerBankAccount    = req.sellerBankAccount,
        sellerPhone          = req.sellerPhone,
        sellerEmail          = req.sellerEmail,
        defaultPaymentMethod = req.defaultPaymentMethod,
        defaultDueDays       = req.defaultDueDays
    )
}

private fun ResultRow.toDto() = InvoiceSettingsDto(
    hotelId              = this[InvoiceSettings.hotelId],
    sellerName           = this[InvoiceSettings.sellerName],
    sellerAddress        = this[InvoiceSettings.sellerAddress],
    sellerNip            = this[InvoiceSettings.sellerNip],
    sellerRegon          = this[InvoiceSettings.sellerRegon],
    sellerBankAccount    = this[InvoiceSettings.sellerBankAccount],
    sellerPhone          = this[InvoiceSettings.sellerPhone],
    sellerEmail          = this[InvoiceSettings.sellerEmail],
    defaultPaymentMethod = this[InvoiceSettings.defaultPaymentMethod],
    defaultDueDays       = this[InvoiceSettings.defaultDueDays]
)
