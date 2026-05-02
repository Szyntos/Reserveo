package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.julsz.smnt.CreateGuestRequest
import org.julsz.smnt.GuestDto
import org.julsz.smnt.UpdateGuestRequest
import org.julsz.smnt.db.Guests

fun Route.guestRoutes() {
    get("/guests") { call.respond(queryGuests()) }

    post("/guests") {
        val req = call.receive<CreateGuestRequest>()
        call.respond(HttpStatusCode.Created, createGuest(req))
    }

    put("/guests/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val req = call.receive<UpdateGuestRequest>()
        call.respond(updateGuest(id, req))
    }
}

private fun queryGuests(): List<GuestDto> = transaction {
    Guests.selectAll().orderBy(Guests.lastName, SortOrder.ASC).map { it.toDto() }
}

private fun createGuest(req: CreateGuestRequest): GuestDto = transaction {
    val newId = Guests.insert {
        it[Guests.firstName]   = req.firstName
        it[Guests.lastName]    = req.lastName
        it[Guests.countryCode] = req.countryCode
        it[Guests.phoneNumber] = req.phoneNumber
        it[Guests.nationality] = req.nationality
        it[Guests.blacklisted] = false
        it[Guests.notes]       = req.notes
    } get Guests.id
    GuestDto(
        id          = newId,
        firstName   = req.firstName,
        lastName    = req.lastName,
        countryCode = req.countryCode,
        phoneNumber = req.phoneNumber,
        nationality = req.nationality,
        blacklisted = false,
        notes       = req.notes
    )
}

private fun updateGuest(id: Int, req: UpdateGuestRequest): GuestDto = transaction {
    Guests.update({ Guests.id eq id }) {
        it[Guests.firstName]   = req.firstName
        it[Guests.lastName]    = req.lastName
        it[Guests.countryCode] = req.countryCode
        it[Guests.phoneNumber] = req.phoneNumber
        it[Guests.nationality] = req.nationality
        it[Guests.blacklisted] = req.blacklisted
        it[Guests.notes]       = req.notes
    }
    Guests.selectAll().where { Guests.id eq id }.first().toDto()
}

private fun org.jetbrains.exposed.sql.ResultRow.toDto() = GuestDto(
    id          = this[Guests.id],
    firstName   = this[Guests.firstName],
    lastName    = this[Guests.lastName],
    countryCode = this[Guests.countryCode],
    phoneNumber = this[Guests.phoneNumber],
    nationality = this[Guests.nationality],
    blacklisted = this[Guests.blacklisted],
    notes       = this[Guests.notes]
)
