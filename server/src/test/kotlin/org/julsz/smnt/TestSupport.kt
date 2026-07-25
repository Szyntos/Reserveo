package org.julsz.smnt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.db.Guests
import org.julsz.smnt.db.Hotels
import org.julsz.smnt.db.RoomTypes
import org.julsz.smnt.db.Rooms
import org.julsz.smnt.db.UserHotelRoles
import org.julsz.smnt.db.Users
import java.time.LocalDateTime

/** An authenticated JSON HTTP client for the test server, using Basic auth (bootstraps the password on first use). */
fun ApplicationTestBuilder.jsonClient(email: String, password: String = "pw"): HttpClient =
    createClient {
        install(ContentNegotiation) { json() }
        install(Auth) {
            basic {
                credentials { BasicAuthCredentials(username = email, password = password) }
                sendWithoutRequest { true }
            }
        }
    }

// Defaults to global "admin" so existing route tests (written before hotel-scoped roles existed) keep
// full access without needing a per-hotel role grant. Tests that specifically exercise hotel-role
// restrictions should pass appRole = "user" and grant a role via insertUserHotelRole.
fun insertUser(email: String, appRole: String = "admin", name: String = "Test User"): Int = transaction {
    Users.insert {
        it[Users.name] = name
        it[Users.email] = email
        it[Users.appRole] = appRole
        it[Users.createdAt] = LocalDateTime.now()
    } get Users.id
}

fun insertUserHotelRole(userId: Int, hotelId: Int, role: String): Unit = transaction {
    UserHotelRoles.insert {
        it[UserHotelRoles.userId] = userId
        it[UserHotelRoles.hotelId] = hotelId
        it[UserHotelRoles.role] = role
    }
}

fun insertHotel(name: String = "Test Hotel"): Int = transaction {
    Hotels.insert {
        it[Hotels.name] = name
        it[Hotels.address] = null
        it[Hotels.phone] = null
        it[Hotels.email] = null
    } get Hotels.id
}

fun insertRoom(hotelId: Int, number: String = "101", maxGuests: Int = 2, typeName: String = "Standard"): Int = transaction {
    val typeId = RoomTypes.selectAll()
        .where { (RoomTypes.hotelId eq hotelId) and (RoomTypes.name eq typeName) }
        .firstOrNull()
        ?.get(RoomTypes.id)
        ?: (RoomTypes.insert {
            it[RoomTypes.hotelId] = hotelId
            it[RoomTypes.name] = typeName
        } get RoomTypes.id)
    Rooms.insert {
        it[Rooms.hotelId] = hotelId
        it[Rooms.roomTypeId] = typeId
        it[Rooms.number] = number
        it[Rooms.floor] = null
        it[Rooms.maxGuests] = maxGuests
        it[Rooms.description] = null
    } get Rooms.id
}

fun insertGuest(firstName: String = "John", lastName: String = "Doe"): Int = transaction {
    Guests.insert {
        it[Guests.firstName] = firstName
        it[Guests.lastName] = lastName
        it[Guests.countryCode] = null
        it[Guests.phoneNumber] = null
        it[Guests.nationality] = null
        it[Guests.blacklisted] = false
        it[Guests.notes] = null
    } get Guests.id
}
