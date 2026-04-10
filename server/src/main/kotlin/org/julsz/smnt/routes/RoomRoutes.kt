package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.CreateRoomRequest
import org.julsz.smnt.RoomDto
import org.julsz.smnt.UpdateRoomRequest
import org.julsz.smnt.db.Hotels
import org.julsz.smnt.db.RoomTypes
import org.julsz.smnt.db.Rooms
import java.time.LocalDateTime

fun Route.roomRoutes() {
    get("/rooms") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
        call.respond(queryRooms(hotelId))
    }

    post("/rooms") {
        val req = call.receive<CreateRoomRequest>()
        call.respond(HttpStatusCode.Created, createRoom(req))
    }

    put("/rooms/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid room id")
        val req = call.receive<UpdateRoomRequest>()
        call.respond(updateRoom(id, req))
    }

    patch("/rooms/{id}/archive") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid room id")
        call.respond(setArchived(id, archived = true))
    }

    patch("/rooms/{id}/unarchive") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid room id")
        call.respond(setArchived(id, archived = false))
    }
}

// ─── Queries ──────────────────────────────────────────────────────────────────

private fun queryRooms(hotelId: Int? = null): List<RoomDto> = transaction {
    val hotelNames = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val typeNames  = RoomTypes.selectAll().associate { it[RoomTypes.id] to it[RoomTypes.name] }

    val query = if (hotelId != null) {
        Rooms.selectAll().where { Rooms.hotelId eq hotelId }
    } else {
        Rooms.selectAll()
    }

    query.orderBy(Rooms.number to SortOrder.ASC).map { it.toDto(hotelNames, typeNames) }
}

// ─── Mutations ────────────────────────────────────────────────────────────────

private fun createRoom(req: CreateRoomRequest): RoomDto = transaction {
    val hotelName = Hotels.selectAll()
        .where { Hotels.id eq req.hotelId }
        .first()[Hotels.name]

    val roomTypeId = findOrCreateRoomType(req.hotelId, req.typeName)

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
        typeName    = req.typeName,
        number      = req.number,
        floor       = req.floor,
        maxGuests   = req.maxGuests,
        status      = "available",
        description = req.description,
        archivedAt  = null
    )
}

private fun updateRoom(id: Int, req: UpdateRoomRequest): RoomDto = transaction {
    val room = Rooms.selectAll().where { Rooms.id eq id }.first()
    val roomTypeId = findOrCreateRoomType(room[Rooms.hotelId], req.typeName)

    Rooms.update({ Rooms.id eq id }) {
        it[Rooms.roomTypeId]  = roomTypeId
        it[Rooms.number]      = req.number
        it[Rooms.floor]       = req.floor
        it[Rooms.maxGuests]   = req.maxGuests
        it[Rooms.status]      = req.status
        it[Rooms.description] = req.description
    }

    val hotelNames = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val typeNames  = RoomTypes.selectAll().associate { it[RoomTypes.id] to it[RoomTypes.name] }
    Rooms.selectAll().where { Rooms.id eq id }.first().toDto(hotelNames, typeNames)
}

private fun setArchived(id: Int, archived: Boolean): RoomDto = transaction {
    Rooms.update({ Rooms.id eq id }) {
        it[Rooms.archivedAt] = if (archived) LocalDateTime.now() else null
    }
    val hotelNames = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val typeNames  = RoomTypes.selectAll().associate { it[RoomTypes.id] to it[RoomTypes.name] }
    Rooms.selectAll().where { Rooms.id eq id }.first().toDto(hotelNames, typeNames)
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun findOrCreateRoomType(hotelId: Int, typeName: String): Int =
    RoomTypes.selectAll()
        .where { (RoomTypes.hotelId eq hotelId) and (RoomTypes.name eq typeName) }
        .firstOrNull()
        ?.get(RoomTypes.id)
        ?: (RoomTypes.insert {
            it[RoomTypes.hotelId] = hotelId
            it[RoomTypes.name]    = typeName
        } get RoomTypes.id)

private fun ResultRow.toDto(hotelNames: Map<Int, String>, typeNames: Map<Int, String>) = RoomDto(
    id          = this[Rooms.id],
    hotelId     = this[Rooms.hotelId],
    hotelName   = hotelNames[this[Rooms.hotelId]] ?: "—",
    typeName    = typeNames[this[Rooms.roomTypeId]] ?: "—",
    number      = this[Rooms.number],
    floor       = this[Rooms.floor],
    maxGuests   = this[Rooms.maxGuests],
    status      = this[Rooms.status],
    description = this[Rooms.description],
    archivedAt  = this[Rooms.archivedAt]?.toString()
)
