package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.CreatePriceRuleRequest
import org.julsz.smnt.PriceRuleDto
import org.julsz.smnt.UpdatePriceRuleRequest
import org.julsz.smnt.db.PriceRules
import org.julsz.smnt.db.Rooms
import java.math.BigDecimal
import java.time.LocalDate

fun Route.priceRoutes() {
    get("/hotels/{hotelId}/price-rules") {
        val hotelId = call.parameters["hotelId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid hotel id")
        call.respond(queryPriceRules(hotelId))
    }

    post("/price-rules") {
        val req = call.receive<CreatePriceRuleRequest>()
        call.respond(HttpStatusCode.Created, createPriceRule(req))
    }

    put("/price-rules/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val req = call.receive<UpdatePriceRuleRequest>()
        call.respond(updatePriceRule(id, req))
    }

    delete("/price-rules/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        deletePriceRule(id)
        call.respond(HttpStatusCode.NoContent)
    }
}

// ─── Queries ──────────────────────────────────────────────────────────────────

private fun queryPriceRules(hotelId: Int): List<PriceRuleDto> = transaction {
    val roomNumbers = Rooms.selectAll()
        .where { Rooms.hotelId eq hotelId }
        .associate { it[Rooms.id] to it[Rooms.number] }

    PriceRules.selectAll()
        .where { PriceRules.roomId inList roomNumbers.keys }
        .orderBy(PriceRules.roomId to SortOrder.ASC, PriceRules.fromDate to SortOrder.ASC)
        .map { it.toDto(roomNumbers) }
}

// ─── Mutations ────────────────────────────────────────────────────────────────

private fun createPriceRule(req: CreatePriceRuleRequest): PriceRuleDto = transaction {
    val roomNumber = Rooms.selectAll()
        .where { Rooms.id eq req.roomId }
        .first()[Rooms.number]

    val newId = PriceRules.insert {
        it[PriceRules.roomId]                 = req.roomId
        it[PriceRules.fromDate]               = LocalDate.parse(req.fromDate)
        it[PriceRules.toDate]                 = LocalDate.parse(req.toDate)
        it[PriceRules.minNights]              = req.minNights
        it[PriceRules.maxNights]              = req.maxNights
        it[PriceRules.pricePerPersonPerNight] = BigDecimal.valueOf(req.pricePerPersonPerNight)
        it[PriceRules.currency]               = req.currency
    } get PriceRules.id

    PriceRuleDto(
        id                    = newId,
        roomId                = req.roomId,
        roomNumber            = roomNumber,
        fromDate              = req.fromDate,
        toDate                = req.toDate,
        minNights             = req.minNights,
        maxNights             = req.maxNights,
        pricePerPersonPerNight = req.pricePerPersonPerNight,
        currency              = req.currency
    )
}

private fun updatePriceRule(id: Int, req: UpdatePriceRuleRequest): PriceRuleDto = transaction {
    PriceRules.update({ PriceRules.id eq id }) {
        it[PriceRules.fromDate]               = LocalDate.parse(req.fromDate)
        it[PriceRules.toDate]                 = LocalDate.parse(req.toDate)
        it[PriceRules.minNights]              = req.minNights
        it[PriceRules.maxNights]              = req.maxNights
        it[PriceRules.pricePerPersonPerNight] = BigDecimal.valueOf(req.pricePerPersonPerNight)
        it[PriceRules.currency]               = req.currency
    }

    val row = PriceRules.selectAll().where { PriceRules.id eq id }.first()
    val roomNumbers = Rooms.selectAll().associate { it[Rooms.id] to it[Rooms.number] }
    row.toDto(roomNumbers)
}

private fun deletePriceRule(id: Int) = transaction {
    PriceRules.deleteWhere { PriceRules.id eq id }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

private fun ResultRow.toDto(roomNumbers: Map<Int, String>) = PriceRuleDto(
    id                    = this[PriceRules.id],
    roomId                = this[PriceRules.roomId],
    roomNumber            = roomNumbers[this[PriceRules.roomId]] ?: "—",
    fromDate              = this[PriceRules.fromDate].toString(),
    toDate                = this[PriceRules.toDate].toString(),
    minNights             = this[PriceRules.minNights],
    maxNights             = this[PriceRules.maxNights],
    pricePerPersonPerNight = this[PriceRules.pricePerPersonPerNight].toDouble(),
    currency              = this[PriceRules.currency]
)
