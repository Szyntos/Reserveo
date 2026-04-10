package org.julsz.smnt

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.format.DateTimeFormatter

private val dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun Route.apiRoutes() {
    route("/api") {
        get("/hotels")       { call.respond(queryHotels()) }
        get("/rooms")        { call.respond(queryRooms()) }
        get("/guests")       { call.respond(queryGuests()) }
        get("/reservations") { call.respond(queryReservations()) }
        get("/users")        { call.respond(queryUsers()) }

        post("/hotels") {
            val req = call.receive<CreateHotelRequest>()
            call.respond(HttpStatusCode.Created, createHotel(req))
        }
        post("/rooms") {
            val req = call.receive<CreateRoomRequest>()
            call.respond(HttpStatusCode.Created, createRoom(req))
        }
        post("/users") {
            val req = call.receive<CreateUserRequest>()
            call.respond(HttpStatusCode.Created, createUser(req))
        }
    }
}

// ─── Queries ──────────────────────────────────────────────────────────────────

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
    Rooms.selectAll().orderBy(Rooms.hotelId to SortOrder.ASC, Rooms.number to SortOrder.ASC).map {
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
    val hotelNames  = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val roomNumbers = Rooms.selectAll().associate { it[Rooms.id] to it[Rooms.number] }
    val guestNames  = Guests.selectAll().associate {
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

private fun queryUsers(): List<UserDto> = transaction {
    Users.selectAll().orderBy(Users.id, SortOrder.ASC).map {
        UserDto(
            id        = it[Users.id],
            name      = it[Users.name],
            email     = it[Users.email],
            createdAt = it[Users.createdAt].format(dtFormatter)
        )
    }
}

// ─── Mutations ────────────────────────────────────────────────────────────────

private fun createHotel(req: CreateHotelRequest): HotelDto = transaction {
    val newId = Hotels.insert {
        it[Hotels.name]    = req.name
        it[Hotels.address] = req.address
        it[Hotels.phone]   = req.phone
        it[Hotels.email]   = req.email
    } get Hotels.id
    HotelDto(id = newId, name = req.name, address = req.address,
             phone = req.phone, email = req.email)
}

private fun createRoom(req: CreateRoomRequest): RoomDto = transaction {
    val hotelName = Hotels.selectAll()
        .where { Hotels.id eq req.hotelId }
        .first()[Hotels.name]

    val roomTypeId = RoomTypes.selectAll()
        .where { (RoomTypes.hotelId eq req.hotelId) and (RoomTypes.name eq req.typeName) }
        .firstOrNull()
        ?.get(RoomTypes.id)
        ?: (RoomTypes.insert {
            it[RoomTypes.hotelId] = req.hotelId
            it[RoomTypes.name]    = req.typeName
        } get RoomTypes.id)

    val newId = Rooms.insert {
        it[Rooms.hotelId]     = req.hotelId
        it[Rooms.roomTypeId]  = roomTypeId
        it[Rooms.number]      = req.number
        it[Rooms.floor]       = req.floor
        it[Rooms.maxGuests]   = req.maxGuests
        it[Rooms.status]      = "available"
        it[Rooms.description] = req.description
    } get Rooms.id

    RoomDto(
        id          = newId,
        hotelId     = req.hotelId,
        hotelName   = hotelName,
        number      = req.number,
        floor       = req.floor,
        maxGuests   = req.maxGuests,
        status      = "available",
        description = req.description
    )
}

private fun createUser(req: CreateUserRequest): UserDto = transaction {
    val newId = Users.insert {
        it[Users.name]  = req.name
        it[Users.email] = req.email
    } get Users.id

    val createdAt = Users.selectAll()
        .where { Users.id eq newId }
        .first()[Users.createdAt]

    UserDto(id = newId, name = req.name, email = req.email,
            createdAt = createdAt.format(dtFormatter))
}
