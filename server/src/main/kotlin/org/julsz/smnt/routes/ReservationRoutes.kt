package org.julsz.smnt.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.ReservationDto
import org.julsz.smnt.db.Guests
import org.julsz.smnt.db.Hotels
import org.julsz.smnt.db.Reservations
import org.julsz.smnt.db.Rooms

fun Route.reservationRoutes() {
    get("/reservations") { call.respond(queryReservations()) }
}

private fun queryReservations(): List<ReservationDto> = transaction {
    val hotelNames  = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val roomNumbers = Rooms.selectAll().associate { it[Rooms.id] to it[Rooms.number] }
    val guestNames  = Guests.selectAll().associate {
        it[Guests.id] to "${it[Guests.firstName]} ${it[Guests.lastName]}"
    }
    Reservations.selectAll()
        .orderBy(Reservations.checkInDate, SortOrder.DESC)
        .map {
            ReservationDto(
                id           = it[Reservations.id],
                hotelName    = hotelNames[it[Reservations.hotelId]] ?: "—",
                roomNumber   = roomNumbers[it[Reservations.roomId]] ?: "—",
                guestName    = guestNames[it[Reservations.guestId]] ?: "—",
                checkInDate  = it[Reservations.checkInDate].toString(),
                checkOutDate = it[Reservations.checkOutDate].toString(),
                status       = it[Reservations.status],
                adults       = it[Reservations.adults],
                children     = it[Reservations.children],
                totalAmount  = it[Reservations.totalAmount]?.toDouble()
            )
        }
}
