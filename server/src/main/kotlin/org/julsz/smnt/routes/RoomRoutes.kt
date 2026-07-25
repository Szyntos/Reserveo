package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.CreateRoomRequest
import org.julsz.smnt.RoomDto
import org.julsz.smnt.UpdateRoomRequest
import org.julsz.smnt.auth.requireHotelAdmin
import org.julsz.smnt.db.Hotels
import org.julsz.smnt.db.Reservations
import org.julsz.smnt.db.RoomBlocks
import org.julsz.smnt.db.RoomTags
import org.julsz.smnt.db.RoomTypes
import org.julsz.smnt.db.Rooms
import org.julsz.smnt.db.Tags
import java.time.LocalDate
import java.time.LocalDateTime

fun Route.roomRoutes() {
    get("/rooms") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
        call.respond(queryRooms(hotelId))
    }

    post("/rooms") {
        val req = call.receive<CreateRoomRequest>()
        if (!call.requireHotelAdmin(req.hotelId)) return@post
        call.respond(HttpStatusCode.Created, createRoom(req))
    }

    put("/rooms/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid room id")
        val hotelId = roomHotelId(id)
            ?: return@put call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelAdmin(hotelId)) return@put
        val req = call.receive<UpdateRoomRequest>()
        call.respond(updateRoom(id, req))
    }

    patch("/rooms/{id}/archive") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid room id")
        val hotelId = roomHotelId(id)
            ?: return@patch call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelAdmin(hotelId)) return@patch
        call.respond(setArchived(id, archived = true))
    }

    patch("/rooms/{id}/unarchive") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid room id")
        val hotelId = roomHotelId(id)
            ?: return@patch call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelAdmin(hotelId)) return@patch
        call.respond(setArchived(id, archived = false))
    }
}

private fun roomHotelId(roomId: Int): Int? = transaction {
    Rooms.selectAll().where { Rooms.id eq roomId }.firstOrNull()?.get(Rooms.hotelId)
}

// ─── Queries ──────────────────────────────────────────────────────────────────

private fun loadStatusSets(): Pair<Set<Int>, Set<Int>> {
    val today = LocalDate.now()
    val occupiedRoomIds = Reservations.selectAll().where {
        (Reservations.checkInDate lessEq today) and
        (Reservations.checkOutDate greater today) and
        (Reservations.status neq "cancelled") and
        (Reservations.status neq "no_show") and
        (Reservations.status neq "checked_out")
    }.map { it[Reservations.roomId] }.toSet()
    val blockedRoomIds = RoomBlocks.selectAll().where {
        (RoomBlocks.fromDate lessEq today) and
        (RoomBlocks.toDate greaterEq today)
    }.map { it[RoomBlocks.roomId] }.toSet()
    return occupiedRoomIds to blockedRoomIds
}

private fun loadRoomTagMap(hotelId: Int? = null): Map<Int, List<String>> {
    val join = RoomTags.innerJoin(Tags, { RoomTags.tagId }, { Tags.id })
    val query = if (hotelId != null)
        join.innerJoin(Rooms, { RoomTags.roomId }, { Rooms.id }).selectAll().where { Rooms.hotelId eq hotelId }
    else
        join.selectAll()
    return query.groupBy({ it[RoomTags.roomId] }, { it[Tags.name] })
}

private fun loadTagsForRoom(roomId: Int): List<String> =
    RoomTags.innerJoin(Tags, { RoomTags.tagId }, { Tags.id }).selectAll()
        .where { RoomTags.roomId eq roomId }
        .map { it[Tags.name] }

private fun queryRooms(hotelId: Int? = null): List<RoomDto> = transaction {
    val hotelNames = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val typeNames  = RoomTypes.selectAll().associate { it[RoomTypes.id] to it[RoomTypes.name] }
    val (occupiedRoomIds, blockedRoomIds) = loadStatusSets()
    val roomTagMap = loadRoomTagMap(hotelId)

    val query = if (hotelId != null) {
        Rooms.selectAll().where { Rooms.hotelId eq hotelId }
    } else {
        Rooms.selectAll()
    }

    query.orderBy(Rooms.number to SortOrder.ASC)
        .map { it.toDto(hotelNames, typeNames, occupiedRoomIds, blockedRoomIds, roomTagMap) }
}

// ─── Mutations ────────────────────────────────────────────────────────────────

private fun createRoom(req: CreateRoomRequest): RoomDto = transaction {
    val hotelName  = Hotels.selectAll().where { Hotels.id eq req.hotelId }.first()[Hotels.name]
    val roomTypeId = findOrCreateRoomType(req.hotelId, req.typeName)

    val newId = Rooms.insert {
        it[Rooms.hotelId]     = req.hotelId
        it[Rooms.roomTypeId]  = roomTypeId
        it[Rooms.number]      = req.number
        it[Rooms.floor]       = req.floor
        it[Rooms.maxGuests]   = req.maxGuests
        it[Rooms.description] = req.description
    } get Rooms.id

    req.tags.forEach { tagName ->
        val tagId = findOrCreateTag(req.hotelId, tagName)
        RoomTags.insert { it[RoomTags.roomId] = newId; it[RoomTags.tagId] = tagId }
    }

    RoomDto(
        id          = newId,
        hotelId     = req.hotelId,
        hotelName   = hotelName,
        typeName    = req.typeName,
        number      = req.number,
        floor       = req.floor,
        maxGuests   = req.maxGuests,
        status      = "free",
        description = req.description,
        tags        = req.tags,
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
        it[Rooms.description] = req.description
    }

    RoomTags.deleteWhere { RoomTags.roomId eq id }
    req.tags.forEach { tagName ->
        val tagId = findOrCreateTag(room[Rooms.hotelId], tagName)
        RoomTags.insert { it[RoomTags.roomId] = id; it[RoomTags.tagId] = tagId }
    }

    val hotelNames = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val typeNames  = RoomTypes.selectAll().associate { it[RoomTypes.id] to it[RoomTypes.name] }
    val (occupiedRoomIds, blockedRoomIds) = loadStatusSets()
    Rooms.selectAll().where { Rooms.id eq id }.first()
        .toDto(hotelNames, typeNames, occupiedRoomIds, blockedRoomIds, mapOf(id to loadTagsForRoom(id)))
}

private fun setArchived(id: Int, archived: Boolean): RoomDto = transaction {
    Rooms.update({ Rooms.id eq id }) {
        it[Rooms.archivedAt] = if (archived) LocalDateTime.now() else null
    }
    val hotelNames = Hotels.selectAll().associate { it[Hotels.id] to it[Hotels.name] }
    val typeNames  = RoomTypes.selectAll().associate { it[RoomTypes.id] to it[RoomTypes.name] }
    val (occupiedRoomIds, blockedRoomIds) = loadStatusSets()
    Rooms.selectAll().where { Rooms.id eq id }.first()
        .toDto(hotelNames, typeNames, occupiedRoomIds, blockedRoomIds, mapOf(id to loadTagsForRoom(id)))
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

private fun findOrCreateTag(hotelId: Int, name: String): Int {
    val normalized = name.trim().lowercase()
    return Tags.selectAll()
        .where { (Tags.hotelId eq hotelId) and (Tags.name eq normalized) }
        .firstOrNull()
        ?.get(Tags.id)
        ?: (Tags.insert {
            it[Tags.hotelId] = hotelId
            it[Tags.name]    = normalized
        } get Tags.id)
}

private fun ResultRow.toDto(
    hotelNames: Map<Int, String>,
    typeNames: Map<Int, String>,
    occupiedRoomIds: Set<Int>,
    blockedRoomIds: Set<Int>,
    roomTagMap: Map<Int, List<String>>
) = RoomDto(
    id          = this[Rooms.id],
    hotelId     = this[Rooms.hotelId],
    hotelName   = hotelNames[this[Rooms.hotelId]] ?: "—",
    typeName    = typeNames[this[Rooms.roomTypeId]] ?: "—",
    number      = this[Rooms.number],
    floor       = this[Rooms.floor],
    maxGuests   = this[Rooms.maxGuests],
    status      = when {
        this[Rooms.id] in blockedRoomIds  -> "out_of_order"
        this[Rooms.id] in occupiedRoomIds -> "occupied"
        else                              -> "free"
    },
    description = this[Rooms.description],
    tags        = roomTagMap[this[Rooms.id]] ?: emptyList(),
    archivedAt  = this[Rooms.archivedAt]?.toString()
)
