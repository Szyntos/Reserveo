package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.julsz.smnt.CreatePriceAdjustmentRequest
import org.julsz.smnt.CreateReservationRequest
import org.julsz.smnt.ReservationDto
import org.julsz.smnt.ReservationPriceAdjustmentDto
import org.julsz.smnt.ReservationPriceSegmentDto
import org.julsz.smnt.UpdateReservationRequest
import org.julsz.smnt.auth.requireHotelManager
import org.julsz.smnt.db.Guests
import org.julsz.smnt.db.Hotels
import org.julsz.smnt.db.Payments
import org.julsz.smnt.db.ReservationPriceAdjustments
import org.julsz.smnt.db.ReservationPriceSegments
import org.julsz.smnt.db.Reservations
import org.julsz.smnt.db.Rooms
import org.julsz.smnt.db.hasBlockOverlap
import org.julsz.smnt.db.hasReservationOverlap
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun Route.reservationRoutes() {
    get("/reservations") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
        call.respond(queryReservations(hotelId))
    }

    post("/reservations") {
        val req = call.receive<CreateReservationRequest>()
        if (!call.requireHotelManager(req.hotelId)) return@post
        if (hasReservationOverlap(req.roomId, req.checkInDate, req.checkOutDate)) {
            return@post call.respond(HttpStatusCode.Conflict, "Room already booked for the selected dates")
        }
        if (hasBlockOverlap(req.roomId, req.checkInDate, req.checkOutDate)) {
            return@post call.respond(HttpStatusCode.Conflict, "Room is blocked for the selected dates")
        }
        call.respond(HttpStatusCode.Created, createReservation(req))
    }

    put("/reservations/{id}") {
        val id  = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = reservationHotelId(id)
            ?: return@put call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@put
        val req = call.receive<UpdateReservationRequest>()
        if (hasReservationOverlap(req.roomId, req.checkInDate, req.checkOutDate, excludeId = id)) {
            return@put call.respond(HttpStatusCode.Conflict, "Room already booked for the selected dates")
        }
        if (hasBlockOverlap(req.roomId, req.checkInDate, req.checkOutDate)) {
            return@put call.respond(HttpStatusCode.Conflict, "Room is blocked for the selected dates")
        }
        call.respond(updateReservation(id, req))
    }

    delete("/reservations/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = reservationHotelId(id)
            ?: return@delete call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@delete
        transaction {
            ReservationPriceAdjustments.deleteWhere { ReservationPriceAdjustments.reservationId eq id }
            ReservationPriceSegments.deleteWhere { ReservationPriceSegments.reservationId eq id }
            Payments.deleteWhere { Payments.reservationId eq id }
            Reservations.deleteWhere { Reservations.id eq id }
        }
        call.respond(HttpStatusCode.NoContent)
    }

    // ─── Price adjustments ────────────────────────────────────────────────────

    post("/reservations/{id}/price-adjustments") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = reservationHotelId(id)
            ?: return@post call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@post
        val req = call.receive<CreatePriceAdjustmentRequest>()
        call.respond(HttpStatusCode.Created, createAdjustment(id, req))
    }

    delete("/reservations/{id}/price-adjustments/{adjId}") {
        val id    = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val adjId = call.parameters["adjId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid adjId")
        val hotelId = reservationHotelId(id)
            ?: return@delete call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@delete
        deleteAdjustment(id, adjId)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun reservationHotelId(reservationId: Int): Int? = transaction {
    Reservations.selectAll().where { Reservations.id eq reservationId }.firstOrNull()?.get(Reservations.hotelId)
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

    val segmentsByRes = ReservationPriceSegments.selectAll()
        .groupBy({ it[ReservationPriceSegments.reservationId] }) { row ->
            ReservationPriceSegmentDto(
                id                     = row[ReservationPriceSegments.id],
                reservationId          = row[ReservationPriceSegments.reservationId],
                priceRuleId            = row[ReservationPriceSegments.ruleId],
                fromDate               = row[ReservationPriceSegments.fromDate].toString(),
                toDate                 = row[ReservationPriceSegments.toDate].toString(),
                pricePerPersonPerNight = row[ReservationPriceSegments.pricePerPersonPerNight].toDouble(),
                adults                 = row[ReservationPriceSegments.adults].toDouble(),
                currency               = row[ReservationPriceSegments.currency]
            )
        }

    val adjustmentsByRes = ReservationPriceAdjustments.selectAll()
        .groupBy({ it[ReservationPriceAdjustments.reservationId] }) { row ->
            ReservationPriceAdjustmentDto(
                id            = row[ReservationPriceAdjustments.id],
                reservationId = row[ReservationPriceAdjustments.reservationId],
                amount        = row[ReservationPriceAdjustments.amount].toDouble(),
                description   = row[ReservationPriceAdjustments.description]
            )
        }

    val query = if (hotelId != null)
        Reservations.selectAll().where { Reservations.hotelId eq hotelId }
    else
        Reservations.selectAll()

    query.orderBy(Reservations.checkInDate to SortOrder.DESC)
        .map { row ->
            val resId = row[Reservations.id]
            row.toDto(
                hotelNames, roomNumbers, guestNames, paidAmounts,
                segmentsByRes[resId] ?: emptyList(),
                adjustmentsByRes[resId] ?: emptyList()
            )
        }
}

// ─── Mutations ────────────────────────────────────────────────────────────────

private fun createReservation(req: CreateReservationRequest): ReservationDto = transaction {
    val hotelName  = Hotels.selectAll().where { Hotels.id eq req.hotelId }.first()[Hotels.name]
    val roomNumber = Rooms.selectAll().where { Rooms.id eq req.roomId }.first()[Rooms.number]
    val guest      = Guests.selectAll().where { Guests.id eq req.guestId }.first()
    val guestName  = "${guest[Guests.firstName]} ${guest[Guests.lastName]}"

    val segmentsTotal = req.priceSegments.takeIf { it.isNotEmpty() }
        ?.sumOf { seg ->
            val nights = ChronoUnit.DAYS.between(LocalDate.parse(seg.fromDate), LocalDate.parse(seg.toDate))
            seg.pricePerPersonPerNight * nights * seg.adults
        }
    val effectiveTotal = segmentsTotal ?: req.totalAmount

    val newId = Reservations.insert {
        it[Reservations.hotelId]             = req.hotelId
        it[Reservations.roomId]              = req.roomId
        it[Reservations.guestId]             = req.guestId
        it[Reservations.checkInDate]         = LocalDate.parse(req.checkInDate)
        it[Reservations.checkOutDate]        = LocalDate.parse(req.checkOutDate)
        it[Reservations.status]              = req.status
        it[Reservations.adults]              = BigDecimal.valueOf(req.adults)
        it[Reservations.totalAmount]         = effectiveTotal?.let { v -> BigDecimal.valueOf(v) }
        it[Reservations.requiresDownPayment] = req.requiresDownPayment
        it[Reservations.downPaymentAmount]   = req.downPaymentAmount?.let { v -> BigDecimal.valueOf(v) }
        it[Reservations.sourceType]          = req.source
        it[Reservations.sourceName]          = req.sourceName
        it[Reservations.externalRef]         = req.externalRef
    } get Reservations.id

    val savedSegments = req.priceSegments.map { seg ->
        val segId = ReservationPriceSegments.insert {
            it[ReservationPriceSegments.reservationId]          = newId
            it[ReservationPriceSegments.ruleId]                 = seg.priceRuleId
            it[ReservationPriceSegments.fromDate]               = LocalDate.parse(seg.fromDate)
            it[ReservationPriceSegments.toDate]                 = LocalDate.parse(seg.toDate)
            it[ReservationPriceSegments.pricePerPersonPerNight] = BigDecimal.valueOf(seg.pricePerPersonPerNight)
            it[ReservationPriceSegments.adults]                 = BigDecimal.valueOf(seg.adults)
            it[ReservationPriceSegments.currency]               = seg.currency
        } get ReservationPriceSegments.id
        ReservationPriceSegmentDto(
            id                     = segId,
            reservationId          = newId,
            priceRuleId            = seg.priceRuleId,
            fromDate               = seg.fromDate,
            toDate                 = seg.toDate,
            pricePerPersonPerNight = seg.pricePerPersonPerNight,
            adults                 = seg.adults,
            currency               = seg.currency
        )
    }

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
        totalAmount         = effectiveTotal,
        requiresDownPayment = req.requiresDownPayment,
        downPaymentAmount   = req.downPaymentAmount,
        priceSegments       = savedSegments,
        priceAdjustments    = emptyList(),
        source      = req.source,
        sourceName  = req.sourceName,
        externalRef = req.externalRef
    )
}

private fun updateReservation(id: Int, req: UpdateReservationRequest): ReservationDto = transaction {
    val existingAdjustments = ReservationPriceAdjustments.selectAll()
        .where { ReservationPriceAdjustments.reservationId eq id }
        .map { it[ReservationPriceAdjustments.amount].toDouble() }
    val adjustmentsTotal = existingAdjustments.sum()

    val segmentsTotal = req.priceSegments.takeIf { it.isNotEmpty() }
        ?.sumOf { seg ->
            val nights = ChronoUnit.DAYS.between(LocalDate.parse(seg.fromDate), LocalDate.parse(seg.toDate))
            seg.pricePerPersonPerNight * nights * seg.adults
        }
    val effectiveTotal = if (segmentsTotal != null) segmentsTotal + adjustmentsTotal else req.totalAmount

    Reservations.update({ Reservations.id eq id }) {
        it[Reservations.roomId]              = req.roomId
        it[Reservations.guestId]             = req.guestId
        it[Reservations.checkInDate]         = LocalDate.parse(req.checkInDate)
        it[Reservations.checkOutDate]        = LocalDate.parse(req.checkOutDate)
        it[Reservations.status]              = req.status
        it[Reservations.adults]              = BigDecimal.valueOf(req.adults)
        it[Reservations.totalAmount]         = effectiveTotal?.let { v -> BigDecimal.valueOf(v) }
        it[Reservations.description]         = req.description
        it[Reservations.requiresDownPayment] = req.requiresDownPayment
        it[Reservations.downPaymentAmount]   = req.downPaymentAmount?.let { v -> BigDecimal.valueOf(v) }
        it[Reservations.externalRef]         = req.externalRef
    }

    if (req.priceSegments.isNotEmpty()) {
        ReservationPriceSegments.deleteWhere { ReservationPriceSegments.reservationId eq id }
        req.priceSegments.forEach { seg ->
            ReservationPriceSegments.insert {
                it[ReservationPriceSegments.reservationId]          = id
                it[ReservationPriceSegments.ruleId]                 = seg.priceRuleId
                it[ReservationPriceSegments.fromDate]               = LocalDate.parse(seg.fromDate)
                it[ReservationPriceSegments.toDate]                 = LocalDate.parse(seg.toDate)
                it[ReservationPriceSegments.pricePerPersonPerNight] = BigDecimal.valueOf(seg.pricePerPersonPerNight)
                it[ReservationPriceSegments.adults]                 = BigDecimal.valueOf(seg.adults)
                it[ReservationPriceSegments.currency]               = seg.currency
            }
        }
    }

    val hotelNames  = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val roomNumbers = Rooms.selectAll().associate { it[Rooms.id] to it[Rooms.number] }
    val guestNames  = Guests.selectAll().associate {
        it[Guests.id] to "${it[Guests.firstName]} ${it[Guests.lastName]}"
    }
    val segments = ReservationPriceSegments.selectAll()
        .where { ReservationPriceSegments.reservationId eq id }
        .map { row ->
            ReservationPriceSegmentDto(
                id                     = row[ReservationPriceSegments.id],
                reservationId          = id,
                priceRuleId            = row[ReservationPriceSegments.ruleId],
                fromDate               = row[ReservationPriceSegments.fromDate].toString(),
                toDate                 = row[ReservationPriceSegments.toDate].toString(),
                pricePerPersonPerNight = row[ReservationPriceSegments.pricePerPersonPerNight].toDouble(),
                adults                 = row[ReservationPriceSegments.adults].toDouble(),
                currency               = row[ReservationPriceSegments.currency]
            )
        }
    val adjustments = ReservationPriceAdjustments.selectAll()
        .where { ReservationPriceAdjustments.reservationId eq id }
        .map { row ->
            ReservationPriceAdjustmentDto(
                id            = row[ReservationPriceAdjustments.id],
                reservationId = id,
                amount        = row[ReservationPriceAdjustments.amount].toDouble(),
                description   = row[ReservationPriceAdjustments.description]
            )
        }
    Reservations.selectAll().where { Reservations.id eq id }.first()
        .toDto(hotelNames, roomNumbers, guestNames, segments = segments, adjustments = adjustments)
}

private fun createAdjustment(reservationId: Int, req: CreatePriceAdjustmentRequest): ReservationPriceAdjustmentDto = transaction {
    val adjId = ReservationPriceAdjustments.insert {
        it[ReservationPriceAdjustments.reservationId] = reservationId
        it[ReservationPriceAdjustments.amount]        = BigDecimal.valueOf(req.amount)
        it[ReservationPriceAdjustments.description]   = req.description
    } get ReservationPriceAdjustments.id

    // Update totalAmount on the reservation to include this adjustment
    val currentTotal = Reservations.selectAll()
        .where { Reservations.id eq reservationId }
        .first()[Reservations.totalAmount]?.toDouble()
    val newTotal = (currentTotal ?: 0.0) + req.amount
    Reservations.update({ Reservations.id eq reservationId }) {
        it[Reservations.totalAmount] = BigDecimal.valueOf(newTotal)
    }

    ReservationPriceAdjustmentDto(
        id            = adjId,
        reservationId = reservationId,
        amount        = req.amount,
        description   = req.description
    )
}

private fun deleteAdjustment(reservationId: Int, adjId: Int): Unit = transaction {
    val amount = ReservationPriceAdjustments.selectAll()
        .where { (ReservationPriceAdjustments.id eq adjId) and (ReservationPriceAdjustments.reservationId eq reservationId) }
        .firstOrNull()
        ?.get(ReservationPriceAdjustments.amount)?.toDouble() ?: return@transaction

    ReservationPriceAdjustments.deleteWhere {
        (ReservationPriceAdjustments.id eq adjId) and (ReservationPriceAdjustments.reservationId eq reservationId)
    }

    val currentTotal = Reservations.selectAll()
        .where { Reservations.id eq reservationId }
        .first()[Reservations.totalAmount]?.toDouble()
    if (currentTotal != null) {
        Reservations.update({ Reservations.id eq reservationId }) {
            it[Reservations.totalAmount] = BigDecimal.valueOf(currentTotal - amount)
        }
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

private fun ResultRow.toDto(
    hotelNames: Map<Int, String>,
    roomNumbers: Map<Int, String>,
    guestNames: Map<Int, String>,
    paidAmounts: Map<Int, Double> = emptyMap(),
    segments: List<ReservationPriceSegmentDto> = emptyList(),
    adjustments: List<ReservationPriceAdjustmentDto> = emptyList()
) = ReservationDto(
    id                  = this[Reservations.id],
    hotelId             = this[Reservations.hotelId],
    roomId              = this[Reservations.roomId],
    guestId             = this[Reservations.guestId],
    hotelName           = hotelNames[this[Reservations.hotelId]] ?: "—",
    roomNumber          = roomNumbers[this[Reservations.roomId]] ?: "—",
    guestName           = guestNames[this[Reservations.guestId]] ?: "—",
    checkInDate         = this[Reservations.checkInDate].toString(),
    checkOutDate        = this[Reservations.checkOutDate].toString(),
    status              = this[Reservations.status],
    adults              = this[Reservations.adults].toDouble(),
    totalAmount         = this[Reservations.totalAmount]?.toDouble(),
    description         = this[Reservations.description],
    paidAmount          = paidAmounts[this[Reservations.id]] ?: 0.0,
    requiresDownPayment = this[Reservations.requiresDownPayment],
    downPaymentAmount   = this[Reservations.downPaymentAmount]?.toDouble(),
    priceSegments       = segments,
    priceAdjustments    = adjustments,
    source      = this[Reservations.sourceType],
    sourceName  = this[Reservations.sourceName],
    externalRef = this[Reservations.externalRef]
)
