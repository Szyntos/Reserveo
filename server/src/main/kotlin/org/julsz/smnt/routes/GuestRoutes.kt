package org.julsz.smnt.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.GuestDto
import org.julsz.smnt.db.Guests

fun Route.guestRoutes() {
    get("/guests") { call.respond(queryGuests()) }
}

private fun queryGuests(): List<GuestDto> = transaction {
    Guests.selectAll().orderBy(Guests.lastName, SortOrder.ASC).map {
        GuestDto(
            id          = it[Guests.id],
            firstName   = it[Guests.firstName],
            lastName    = it[Guests.lastName],
            email       = it[Guests.email],
            phone       = it[Guests.phone],
            nationality = it[Guests.nationality],
            blacklisted = it[Guests.blacklisted]
        )
    }
}
