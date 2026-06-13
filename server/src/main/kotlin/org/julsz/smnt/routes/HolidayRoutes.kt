package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.CreateHolidayRequest
import org.julsz.smnt.HolidayDto
import org.julsz.smnt.ImportHolidaysRequest
import org.julsz.smnt.ImportHolidaysResponse
import org.julsz.smnt.db.Holidays
import java.time.LocalDate

fun Route.holidayRoutes() {
    get("/holidays") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
        call.respond(queryHolidays(hotelId))
    }

    post("/holidays") {
        val req = call.receive<CreateHolidayRequest>()
        call.respond(HttpStatusCode.Created, createHoliday(req))
    }

    post("/holidays/import") {
        val req = call.receive<ImportHolidaysRequest>()
        val created = importHolidaysFromCsv(req)
        call.respond(HttpStatusCode.Created, ImportHolidaysResponse(imported = created.size, holidays = created))
    }

    delete("/holidays/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
        transaction { Holidays.deleteWhere { Holidays.id eq id } }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun queryHolidays(hotelId: Int?): List<HolidayDto> = transaction {
    val query = if (hotelId != null)
        Holidays.selectAll().where { Holidays.hotelId eq hotelId }
    else
        Holidays.selectAll()

    query.orderBy(Holidays.fromDate to SortOrder.ASC).map { row ->
        HolidayDto(
            id       = row[Holidays.id],
            hotelId  = row[Holidays.hotelId],
            name     = row[Holidays.name],
            fromDate = row[Holidays.fromDate].toString(),
            toDate   = row[Holidays.toDate].toString()
        )
    }
}

private fun importHolidaysFromCsv(req: ImportHolidaysRequest): List<HolidayDto> {
    val lines = req.csv.lines()
    val results = mutableListOf<HolidayDto>()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) continue
        val parts = trimmed.split(",")
        if (parts.size < 2) continue

        val lastPart = parts.last().trim()
        val secondLastPart = if (parts.size >= 3) parts[parts.size - 2].trim() else null

        val toDate   = runCatching { LocalDate.parse(lastPart) }.getOrNull() ?: continue
        val fromDate = if (secondLastPart != null) runCatching { LocalDate.parse(secondLastPart) }.getOrNull() else null

        val (nameEndIdx, from, to) = if (fromDate != null) {
            Triple(parts.size - 2, fromDate, toDate)
        } else {
            Triple(parts.size - 1, toDate, toDate)
        }

        val name = parts.subList(0, nameEndIdx).joinToString(",").trim()
        if (name.isBlank()) continue
        // skip header row
        if (name.equals("holiday", ignoreCase = true) || name.equals("święto", ignoreCase = true)) continue

        results += createHoliday(CreateHolidayRequest(
            hotelId  = req.hotelId,
            name     = name,
            fromDate = from.toString(),
            toDate   = to.toString()
        ))
    }
    return results
}

private fun createHoliday(req: CreateHolidayRequest): HolidayDto = transaction {
    val newId = Holidays.insert {
        it[Holidays.hotelId]  = req.hotelId
        it[Holidays.name]     = req.name
        it[Holidays.fromDate] = LocalDate.parse(req.fromDate)
        it[Holidays.toDate]   = LocalDate.parse(req.toDate)
    } get Holidays.id

    HolidayDto(
        id       = newId,
        hotelId  = req.hotelId,
        name     = req.name,
        fromDate = req.fromDate,
        toDate   = req.toDate
    )
}
