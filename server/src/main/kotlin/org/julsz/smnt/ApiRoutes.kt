package org.julsz.smnt

import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.apiRoutes() {
    route("/api") {
        get("/hotels") { call.respond(queryHotels()) }
        get("/rooms") { call.respond(queryRooms()) }
        get("/guests") { call.respond(queryGuests()) }
        get("/reservations") { call.respond(queryReservations()) }
    }
}

private fun queryHotels(): List<HotelDto> = transaction {
    Hotels.selectAll().map {
        HotelDto(
            id      = it[Hotels.id],
            name    = it[Hotels.name],
            address = it[Hotels.address],
            phone   = it[Hotels.phone],
            email   = it[Hotels.email]
        )
    }
}

private fun queryRooms(): List<RoomDto> = transaction {
    val hotelNames = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    Rooms.selectAll().orderBy(Rooms.hotelId, SortOrder.ASC).map {
        RoomDto(
            id          = it[Rooms.id],
            hotelId     = it[Rooms.hotelId],
            hotelName   = hotelNames[it[Rooms.hotelId]] ?: "—",
            number      = it[Rooms.number],
            floor       = it[Rooms.floor],
            maxGuests   = it[Rooms.maxGuests],
            status      = it[Rooms.status],
            description = it[Rooms.description]
        )
    }
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

private fun queryReservations(): List<ReservationDto> = transaction {
    val hotelNames = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val roomNumbers = Rooms.selectAll().associate { it[Rooms.id] to it[Rooms.number] }
    val guestNames = Guests.selectAll().associate {
        it[Guests.id] to "${it[Guests.firstName]} ${it[Guests.lastName]}"
    }
    Reservations.selectAll().orderBy(Reservations.checkInDate, SortOrder.DESC).map {
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
