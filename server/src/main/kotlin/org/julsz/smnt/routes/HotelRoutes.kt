package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.insert
import org.julsz.smnt.CreateHotelRequest
import org.julsz.smnt.HotelDto
import org.julsz.smnt.auth.requireAdmin
import org.julsz.smnt.db.Hotels

fun Route.hotelRoutes() {
    get("/hotels") { call.respond(queryHotels()) }

    post("/hotels") {
        if (!call.requireAdmin()) return@post
        val req = call.receive<CreateHotelRequest>()
        call.respond(HttpStatusCode.Created, createHotel(req))
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
