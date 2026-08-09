package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.ChannelPayoutDto
import org.julsz.smnt.ChannelPayoutOverrideDto
import org.julsz.smnt.CreateChannelPayoutRequest
import org.julsz.smnt.SetChannelPayoutOverrideRequest
import org.julsz.smnt.UpdateChannelPayoutRequest
import org.julsz.smnt.auth.requireHotelManager
import org.julsz.smnt.db.ChannelPayouts
import org.julsz.smnt.db.ChannelPayoutOverrides
import org.julsz.smnt.db.Reservations
import java.math.BigDecimal
import java.time.LocalDateTime

fun Route.channelPayoutRoutes() {
    get("/channel-payouts") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
        call.respond(queryChannelPayouts(hotelId))
    }

    post("/channel-payouts") {
        val req = call.receive<CreateChannelPayoutRequest>()
        if (!call.requireHotelManager(req.hotelId)) return@post
        if (req.month !in 1..12) {
            return@post call.respond(HttpStatusCode.BadRequest, "Month must be between 1 and 12")
        }
        if (req.amount < 0) {
            return@post call.respond(HttpStatusCode.BadRequest, "Amount cannot be negative")
        }
        if (payoutExists(req.hotelId, req.year, req.month)) {
            return@post call.respond(
                HttpStatusCode.Conflict,
                "A payout for ${req.year}-${req.month} already exists — edit it instead"
            )
        }
        call.respond(HttpStatusCode.Created, createChannelPayout(req))
    }

    put("/channel-payouts/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = channelPayoutHotelId(id)
            ?: return@put call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@put
        val req = call.receive<UpdateChannelPayoutRequest>()
        if (req.amount < 0) {
            return@put call.respond(HttpStatusCode.BadRequest, "Amount cannot be negative")
        }
        call.respond(updateChannelPayout(id, req))
    }

    delete("/channel-payouts/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = channelPayoutHotelId(id)
            ?: return@delete call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@delete
        transaction { ChannelPayouts.deleteWhere { ChannelPayouts.id eq id } }
        call.respond(HttpStatusCode.NoContent)
    }

    // ─── Attribution overrides ────────────────────────────────────────────────

    get("/channel-payout-overrides") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
        call.respond(queryOverrides(hotelId))
    }

    /** Upsert — a reservation has at most one override, so re-setting replaces it. */
    put("/reservations/{id}/payout-override") {
        val resId = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = reservationHotelIdForPayout(resId)
            ?: return@put call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@put
        val req = call.receive<SetChannelPayoutOverrideRequest>()

        // Mirrors the DB constraint: an override either excludes, or names a real month.
        // Rejecting "neither" here is what keeps a reservation from silently belonging nowhere.
        if (!req.excluded) {
            val year  = req.year
            val month = req.month
            if (year == null || month == null) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    "An override must either exclude the reservation or name a target month"
                )
            }
            if (month !in 1..12) {
                return@put call.respond(HttpStatusCode.BadRequest, "Month must be between 1 and 12")
            }
        }
        call.respond(setOverride(resId, req))
    }

    /** Removing an override returns the reservation to derived attribution. */
    delete("/reservations/{id}/payout-override") {
        val resId = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = reservationHotelIdForPayout(resId)
            ?: return@delete call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@delete
        transaction {
            ChannelPayoutOverrides.deleteWhere { ChannelPayoutOverrides.reservationId eq resId }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun reservationHotelIdForPayout(reservationId: Int): Int? = transaction {
    Reservations.selectAll().where { Reservations.id eq reservationId }
        .firstOrNull()?.get(Reservations.hotelId)
}

private fun queryOverrides(hotelId: Int?): List<ChannelPayoutOverrideDto> = transaction {
    val rows = ChannelPayoutOverrides.selectAll().map { it.toOverrideDto() }
    if (hotelId == null) return@transaction rows

    val hotelReservationIds = Reservations.selectAll()
        .where { Reservations.hotelId eq hotelId }
        .map { it[Reservations.id] }
        .toSet()
    rows.filter { it.reservationId in hotelReservationIds }
}

private fun setOverride(
    reservationId: Int,
    req: SetChannelPayoutOverrideRequest
): ChannelPayoutOverrideDto = transaction {
    val now = LocalDateTime.now()
    ChannelPayoutOverrides.deleteWhere { ChannelPayoutOverrides.reservationId eq reservationId }
    ChannelPayoutOverrides.insert {
        it[ChannelPayoutOverrides.reservationId] = reservationId
        it[ChannelPayoutOverrides.year]          = if (req.excluded) null else req.year
        it[ChannelPayoutOverrides.month]         = if (req.excluded) null else req.month
        it[ChannelPayoutOverrides.excluded]      = req.excluded
        it[ChannelPayoutOverrides.reason]        = req.reason
        it[ChannelPayoutOverrides.createdAt]     = now
    }
    ChannelPayoutOverrides.selectAll()
        .where { ChannelPayoutOverrides.reservationId eq reservationId }
        .first().toOverrideDto()
}

private fun ResultRow.toOverrideDto() = ChannelPayoutOverrideDto(
    reservationId = this[ChannelPayoutOverrides.reservationId],
    year          = this[ChannelPayoutOverrides.year],
    month         = this[ChannelPayoutOverrides.month],
    excluded      = this[ChannelPayoutOverrides.excluded],
    reason        = this[ChannelPayoutOverrides.reason],
    createdAt     = this[ChannelPayoutOverrides.createdAt].toString()
)

private fun channelPayoutHotelId(id: Int): Int? = transaction {
    ChannelPayouts.selectAll().where { ChannelPayouts.id eq id }.firstOrNull()?.get(ChannelPayouts.hotelId)
}

private fun payoutExists(hotelId: Int, year: Int, month: Int): Boolean = transaction {
    ChannelPayouts.selectAll().where {
        (ChannelPayouts.hotelId eq hotelId) and
        (ChannelPayouts.year eq year) and
        (ChannelPayouts.month eq month)
    }.any()
}

// ─── Queries ──────────────────────────────────────────────────────────────────

private fun queryChannelPayouts(hotelId: Int?): List<ChannelPayoutDto> = transaction {
    val query = if (hotelId != null)
        ChannelPayouts.selectAll().where { ChannelPayouts.hotelId eq hotelId }
    else
        ChannelPayouts.selectAll()

    query
        .orderBy(ChannelPayouts.year to SortOrder.DESC, ChannelPayouts.month to SortOrder.DESC)
        .map { it.toDto() }
}

// ─── Mutations ────────────────────────────────────────────────────────────────

private fun createChannelPayout(req: CreateChannelPayoutRequest): ChannelPayoutDto = transaction {
    val now = LocalDateTime.now()
    val newId = ChannelPayouts.insert {
        it[ChannelPayouts.hotelId]   = req.hotelId
        it[ChannelPayouts.year]      = req.year
        it[ChannelPayouts.month]     = req.month
        it[ChannelPayouts.amount]    = BigDecimal.valueOf(req.amount)
        it[ChannelPayouts.currency]  = req.currency
        it[ChannelPayouts.notes]     = req.notes
        it[ChannelPayouts.createdAt] = now
    } get ChannelPayouts.id

    ChannelPayoutDto(
        id        = newId,
        hotelId   = req.hotelId,
        year      = req.year,
        month     = req.month,
        amount    = req.amount,
        currency  = req.currency,
        notes     = req.notes,
        createdAt = now.toString()
    )
}

private fun updateChannelPayout(id: Int, req: UpdateChannelPayoutRequest): ChannelPayoutDto = transaction {
    ChannelPayouts.update({ ChannelPayouts.id eq id }) {
        it[ChannelPayouts.amount]   = BigDecimal.valueOf(req.amount)
        it[ChannelPayouts.currency] = req.currency
        it[ChannelPayouts.notes]    = req.notes
    }
    ChannelPayouts.selectAll().where { ChannelPayouts.id eq id }.first().toDto()
}

private fun ResultRow.toDto() = ChannelPayoutDto(
    id        = this[ChannelPayouts.id],
    hotelId   = this[ChannelPayouts.hotelId],
    year      = this[ChannelPayouts.year],
    month     = this[ChannelPayouts.month],
    amount    = this[ChannelPayouts.amount].toDouble(),
    currency  = this[ChannelPayouts.currency],
    notes     = this[ChannelPayouts.notes],
    createdAt = this[ChannelPayouts.createdAt].toString()
)
