package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.julsz.smnt.CreateRoomBlockRequest
import org.julsz.smnt.RoomBlockDto
import org.julsz.smnt.auth.requireHotelManager
import org.julsz.smnt.db.RoomBlocks
import org.julsz.smnt.db.Rooms
import org.julsz.smnt.db.hasBlockOverlap
import org.julsz.smnt.db.hasReservationOverlap
import java.time.LocalDate

fun Route.roomBlockRoutes() {
    get("/room-blocks") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
        call.respond(queryRoomBlocks(hotelId))
    }

    post("/room-blocks") {
        val req = call.receive<CreateRoomBlockRequest>()
        val hotelId = roomHotelId(req.roomId)
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid room id")
        if (!call.requireHotelManager(hotelId)) return@post
        if (hasReservationOverlap(req.roomId, req.fromDate, req.toDate)) {
            return@post call.respond(HttpStatusCode.Conflict, "Room has active reservations in the selected dates")
        }
        if (hasBlockOverlap(req.roomId, req.fromDate, req.toDate)) {
            return@post call.respond(HttpStatusCode.Conflict, "Room is already blocked for the selected dates")
        }
        call.respond(HttpStatusCode.Created, createRoomBlock(req))
    }

    delete("/room-blocks/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val hotelId = roomBlockHotelId(id)
            ?: return@delete call.respond(HttpStatusCode.NotFound)
        if (!call.requireHotelManager(hotelId)) return@delete
        transaction { RoomBlocks.deleteWhere { RoomBlocks.id eq id } }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun roomHotelId(roomId: Int): Int? = transaction {
    Rooms.selectAll().where { Rooms.id eq roomId }.firstOrNull()?.get(Rooms.hotelId)
}

private fun roomBlockHotelId(roomBlockId: Int): Int? = transaction {
    val roomId = RoomBlocks.selectAll().where { RoomBlocks.id eq roomBlockId }.firstOrNull()?.get(RoomBlocks.roomId)
        ?: return@transaction null
    Rooms.selectAll().where { Rooms.id eq roomId }.firstOrNull()?.get(Rooms.hotelId)
}

// ─── Queries ──────────────────────────────────────────────────────────────────

private fun queryRoomBlocks(hotelId: Int?): List<RoomBlockDto> = transaction {
    val roomNumbers = Rooms.selectAll().associate { it[Rooms.id] to it[Rooms.number] }

    val hotelRoomIds = if (hotelId != null)
        Rooms.selectAll().where { Rooms.hotelId eq hotelId }.map { it[Rooms.id] }.toSet()
    else null

    RoomBlocks.selectAll()
        .orderBy(RoomBlocks.fromDate to SortOrder.ASC)
        .filter { row -> hotelRoomIds == null || row[RoomBlocks.roomId] in hotelRoomIds }
        .map { row ->
            RoomBlockDto(
                id         = row[RoomBlocks.id],
                roomId     = row[RoomBlocks.roomId],
                roomNumber = roomNumbers[row[RoomBlocks.roomId]] ?: "—",
                fromDate   = row[RoomBlocks.fromDate].toString(),
                toDate     = row[RoomBlocks.toDate].toString(),
                reason     = row[RoomBlocks.reason]
            )
        }
}

// ─── Mutations ────────────────────────────────────────────────────────────────

private fun createRoomBlock(req: CreateRoomBlockRequest): RoomBlockDto = transaction {
    val roomNumber = Rooms.selectAll().where { Rooms.id eq req.roomId }.first()[Rooms.number]

    val newId = RoomBlocks.insert {
        it[RoomBlocks.roomId]   = req.roomId
        it[RoomBlocks.fromDate] = LocalDate.parse(req.fromDate)
        it[RoomBlocks.toDate]   = LocalDate.parse(req.toDate)
        it[RoomBlocks.reason]   = req.reason
    } get RoomBlocks.id

    RoomBlockDto(
        id         = newId,
        roomId     = req.roomId,
        roomNumber = roomNumber,
        fromDate   = req.fromDate,
        toDate     = req.toDate,
        reason     = req.reason
    )
}
