package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.julsz.smnt.CreateReservationRequest
import org.julsz.smnt.ReservationDto
import org.julsz.smnt.UpdateReservationRequest
import org.julsz.smnt.db.Guests
import org.julsz.smnt.db.Hotels
import org.julsz.smnt.db.Payments
import org.julsz.smnt.db.Reservations
import org.julsz.smnt.db.Rooms
import java.time.LocalDate

fun Route.reservationRoutes() {
    get("/reservations") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
        call.respond(queryReservations(hotelId))
    }

    post("/reservations") {
        val req = call.receive<CreateReservationRequest>()
        if (hasOverlap(req.roomId, req.checkInDate, req.checkOutDate)) {
            return@post call.respond(HttpStatusCode.Conflict, "Room already booked for the selected dates")
        }
        call.respond(HttpStatusCode.Created, createReservation(req))
    }

    put("/reservations/{id}") {
        val id  = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val req = call.receive<UpdateReservationRequest>()
        if (hasOverlap(req.roomId, req.checkInDate, req.checkOutDate, excludeId = id)) {
            return@put call.respond(HttpStatusCode.Conflict, "Room already booked for the selected dates")
        }
        call.respond(updateReservation(id, req))
    }

    delete("/reservations/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        transaction { Reservations.deleteWhere { Reservations.id eq id } }
        call.respond(HttpStatusCode.NoContent)
    }
}

// ─── Overlap guard ────────────────────────────────────────────────────────────

private fun hasOverlap(roomId: Int, checkIn: String, checkOut: String, excludeId: Int? = null): Boolean =
    transaction {
        val cin  = LocalDate.parse(checkIn)
        val cout = LocalDate.parse(checkOut)
        val nonBlocking = setOf("cancelled", "no_show")
        var query = Reservations.selectAll().where {
            (Reservations.roomId eq roomId) and
            (Reservations.checkInDate less cout) and
            (Reservations.checkOutDate greater cin)
        }
        if (excludeId != null) query = query.andWhere { Reservations.id neq excludeId }
        query.any { it[Reservations.status] !in nonBlocking }
    }

// ─── Queries ──────────────────────────────────────────────────────────────────

private fun queryReservations(hotelId: Int?): List<ReservationDto> = transaction {
    val hotelNames  = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val roomNumbers = Rooms.selectAll().associate { it[Rooms.id] to it[Rooms.number] }
    val guestNames  = Guests.selectAll().associate {
        it[Guests.id] to "${it[Guests.firstName]} ${it[Guests.lastName]}"
    }
    val paidAmounts = Payments
        .select(Payments.reservationId, Payments.amount.sum())
        .groupBy(Payments.reservationId)
        .associate { it[Payments.reservationId] to (it[Payments.amount.sum()]?.toDouble() ?: 0.0) }

    val query = if (hotelId != null)
        Reservations.selectAll().where { Reservations.hotelId eq hotelId }
    else
        Reservations.selectAll()

    query.orderBy(Reservations.checkInDate to SortOrder.DESC)
        .map { it.toDto(hotelNames, roomNumbers, guestNames, paidAmounts) }
}

// ─── Mutations ────────────────────────────────────────────────────────────────

private fun createReservation(req: CreateReservationRequest): ReservationDto = transaction {
    val hotelName  = Hotels.selectAll().where { Hotels.id eq req.hotelId }.first()[Hotels.name]
    val roomNumber = Rooms.selectAll().where { Rooms.id eq req.roomId }.first()[Rooms.number]
    val guest      = Guests.selectAll().where { Guests.id eq req.guestId }.first()
    val guestName  = "${guest[Guests.firstName]} ${guest[Guests.lastName]}"

    val newId = Reservations.insert {
        it[Reservations.hotelId]             = req.hotelId
        it[Reservations.roomId]              = req.roomId
        it[Reservations.guestId]             = req.guestId
        it[Reservations.checkInDate]         = LocalDate.parse(req.checkInDate)
        it[Reservations.checkOutDate]        = LocalDate.parse(req.checkOutDate)
        it[Reservations.status]              = req.status
        it[Reservations.adults]              = java.math.BigDecimal.valueOf(req.adults)
        it[Reservations.totalAmount]         = req.totalAmount?.let { v -> java.math.BigDecimal.valueOf(v) }
        it[Reservations.requiresDownPayment] = req.requiresDownPayment
        it[Reservations.downPaymentAmount]   = req.downPaymentAmount?.let { v -> java.math.BigDecimal.valueOf(v) }
    } get Reservations.id

    ReservationDto(
        id                  = newId,
        hotelId             = req.hotelId,
        roomId              = req.roomId,
        guestId             = req.guestId,
        hotelName           = hotelName,
        roomNumber          = roomNumber,
        guestName           = guestName,
        checkInDate         = req.checkInDate,
        checkOutDate        = req.checkOutDate,
        status              = req.status,
        adults              = req.adults,
        totalAmount         = req.totalAmount,
        requiresDownPayment = req.requiresDownPayment,
        downPaymentAmount   = req.downPaymentAmount
    )
}

private fun updateReservation(id: Int, req: UpdateReservationRequest): ReservationDto = transaction {
    Reservations.update({ Reservations.id eq id }) {
        it[Reservations.roomId]              = req.roomId
        it[Reservations.guestId]             = req.guestId
        it[Reservations.checkInDate]         = LocalDate.parse(req.checkInDate)
        it[Reservations.checkOutDate]        = LocalDate.parse(req.checkOutDate)
        it[Reservations.status]              = req.status
        it[Reservations.adults]              = java.math.BigDecimal.valueOf(req.adults)
        it[Reservations.totalAmount]         = req.totalAmount?.let { v -> java.math.BigDecimal.valueOf(v) }
        it[Reservations.description]         = req.description
        it[Reservations.requiresDownPayment] = req.requiresDownPayment
        it[Reservations.downPaymentAmount]   = req.downPaymentAmount?.let { v -> java.math.BigDecimal.valueOf(v) }
    }
    val hotelNames  = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val roomNumbers = Rooms.selectAll().associate { it[Rooms.id] to it[Rooms.number] }
    val guestNames  = Guests.selectAll().associate {
        it[Guests.id] to "${it[Guests.firstName]} ${it[Guests.lastName]}"
    }
    Reservations.selectAll().where { Reservations.id eq id }.first()
        .toDto(hotelNames, roomNumbers, guestNames)
}

// ─── Helper ───────────────────────────────────────────────────────────────────

private fun ResultRow.toDto(
    hotelNames: Map<Int, String>,
    roomNumbers: Map<Int, String>,
    guestNames: Map<Int, String>,
    paidAmounts: Map<Int, Double> = emptyMap()
) = ReservationDto(
    id           = this[Reservations.id],
    hotelId      = this[Reservations.hotelId],
    roomId       = this[Reservations.roomId],
    guestId      = this[Reservations.guestId],
    hotelName    = hotelNames[this[Reservations.hotelId]] ?: "—",
    roomNumber   = roomNumbers[this[Reservations.roomId]] ?: "—",
    guestName    = guestNames[this[Reservations.guestId]] ?: "—",
    checkInDate  = this[Reservations.checkInDate].toString(),
    checkOutDate = this[Reservations.checkOutDate].toString(),
    status       = this[Reservations.status],
    adults       = this[Reservations.adults].toDouble(),
    totalAmount         = this[Reservations.totalAmount]?.toDouble(),
    description         = this[Reservations.description],
    paidAmount          = paidAmounts[this[Reservations.id]] ?: 0.0,
    requiresDownPayment = this[Reservations.requiresDownPayment],
    downPaymentAmount   = this[Reservations.downPaymentAmount]?.toDouble()
)
