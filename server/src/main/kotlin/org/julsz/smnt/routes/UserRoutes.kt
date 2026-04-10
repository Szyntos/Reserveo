package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.JoinType
import org.julsz.smnt.CreateUserRequest
import org.julsz.smnt.UserDto
import org.julsz.smnt.UserHotelRoleDto
import org.julsz.smnt.db.Hotels
import org.julsz.smnt.db.UserHotelRoles
import org.julsz.smnt.db.Users
import java.time.format.DateTimeFormatter

private val dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun Route.userRoutes() {
    get("/users") { call.respond(queryUsers()) }

    get("/users/{id}/hotels") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user id")
        call.respond(queryUserHotels(userId))
    }

    post("/users") {
        val req = call.receive<CreateUserRequest>()
        call.respond(HttpStatusCode.Created, createUser(req))
    }
}

private fun queryUsers(): List<UserDto> = transaction {
    Users.selectAll().orderBy(Users.id, SortOrder.ASC).map {
        UserDto(
            id        = it[Users.id],
            name      = it[Users.name],
            email     = it[Users.email],
            appRole   = it[Users.appRole],
            createdAt = it[Users.createdAt].format(dtFormatter)
        )
    }
}

private fun queryUserHotels(userId: Int): List<UserHotelRoleDto> = transaction {
    UserHotelRoles
        .join(Hotels, JoinType.INNER, onColumn = UserHotelRoles.hotelId, otherColumn = Hotels.id)
        .selectAll()
        .where { UserHotelRoles.userId eq userId }
        .map {
            UserHotelRoleDto(
                hotelId   = it[Hotels.id],
                hotelName = it[Hotels.name],
                role      = it[UserHotelRoles.role]
            )
        }
}

private fun createUser(req: CreateUserRequest): UserDto = transaction {
    val newId = Users.insert {
        it[Users.name]    = req.name
        it[Users.email]   = req.email
        it[Users.appRole] = req.appRole
    } get Users.id

    val createdAt = Users.selectAll()
        .where { Users.id eq newId }
        .first()[Users.createdAt]

    UserDto(id = newId, name = req.name, email = req.email,
            appRole = req.appRole, createdAt = createdAt.format(dtFormatter))
}
