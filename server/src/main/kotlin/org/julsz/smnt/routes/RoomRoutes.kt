package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.CreateRoomRequest
import org.julsz.smnt.RoomDto
import org.julsz.smnt.db.Hotels
import org.julsz.smnt.db.RoomTypes
import org.julsz.smnt.db.Rooms

fun Route.roomRoutes() {
    get("/rooms") { call.respond(queryRooms()) }

    post("/rooms") {
        val req = call.receive<CreateRoomRequest>()
        call.respond(HttpStatusCode.Created, createRoom(req))
    }
}

private fun queryRooms(): List<RoomDto> = transaction {
    val hotelNames = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    Rooms.selectAll()
        .orderBy(Rooms.hotelId to SortOrder.ASC, Rooms.number to SortOrder.ASC)
        .map {
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
