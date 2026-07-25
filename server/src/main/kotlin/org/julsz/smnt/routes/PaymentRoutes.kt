package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.CreatePaymentRequest
import org.julsz.smnt.PaymentDto
import org.julsz.smnt.auth.requireHotelManager
import org.julsz.smnt.db.Payments
import org.julsz.smnt.db.Reservations
import java.math.BigDecimal
import java.time.LocalDate

fun Route.paymentRoutes() {
    get("/reservations/{id}/payments") {
        val resId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
        call.respond(queryPayments(resId))
    }

    post("/reservations/{id}/payments") {
        val resId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = reservationHotelId(resId)
            ?: return@post call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@post
        val req = call.receive<CreatePaymentRequest>()
        call.respond(HttpStatusCode.Created, createPayment(resId, req))
    }

    delete("/payments/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = paymentHotelId(id)
            ?: return@delete call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@delete
        transaction { Payments.deleteWhere { Payments.id eq id } }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun reservationHotelId(reservationId: Int): Int? = transaction {
    Reservations.selectAll().where { Reservations.id eq reservationId }.firstOrNull()?.get(Reservations.hotelId)
}

private fun paymentHotelId(paymentId: Int): Int? = transaction {
    val resId = Payments.selectAll().where { Payments.id eq paymentId }.firstOrNull()?.get(Payments.reservationId)
        ?: return@transaction null
    Reservations.selectAll().where { Reservations.id eq resId }.firstOrNull()?.get(Reservations.hotelId)
}

private fun queryPayments(reservationId: Int): List<PaymentDto> = transaction {
    Payments.selectAll()
        .where { Payments.reservationId eq reservationId }
        .orderBy(Payments.id to SortOrder.ASC)
        .map { it.toDto() }
}

private fun createPayment(reservationId: Int, req: CreatePaymentRequest): PaymentDto = transaction {
    val newId = Payments.insert {
        it[Payments.reservationId] = reservationId
        it[Payments.isDeposit]     = req.isDeposit
        it[Payments.amount]        = BigDecimal.valueOf(req.amount)
        it[Payments.currency]      = req.currency
        it[Payments.paidAt]        = req.paidAt?.let { d -> LocalDate.parse(d).atStartOfDay() }
        it[Payments.notes]         = req.notes
        it[Payments.receiptType]   = req.receiptType
        it[Payments.receiptNumber] = req.receiptNumber
    } get Payments.id

    PaymentDto(
        id            = newId,
        reservationId = reservationId,
        isDeposit     = req.isDeposit,
        amount        = req.amount,
        currency      = req.currency,
        paidAt        = req.paidAt,
        notes         = req.notes,
        receiptType   = req.receiptType,
        receiptNumber = req.receiptNumber
    )
}

private fun ResultRow.toDto() = PaymentDto(
    id            = this[Payments.id],
    reservationId = this[Payments.reservationId],
    isDeposit     = this[Payments.isDeposit],
    amount        = this[Payments.amount].toDouble(),
    currency      = this[Payments.currency],
    paidAt        = this[Payments.paidAt]?.toLocalDate()?.toString(),
    notes         = this[Payments.notes],
    receiptType   = this[Payments.receiptType],
    receiptNumber = this[Payments.receiptNumber]
)
