package org.julsz.smnt.auth

import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.julsz.smnt.CreateReservationRequest
import org.julsz.smnt.CreateRoomRequest
import org.julsz.smnt.TestDb
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertGuest
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertRoom
import org.julsz.smnt.insertUser
import org.julsz.smnt.insertUserHotelRole
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Per-hotel roles: admin (full access), manager (reservation endpoints, no config),
 * viewer (read-only, no mutations at all).
 */
class HotelRoleAuthorizationTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `hotel viewer cannot create reservations`() = testApplication {
        application { configureApp() }
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val userId = insertUser("viewer@test.local", appRole = "user")
        insertUserHotelRole(userId, hotelId, "viewer")
        val client = jsonClient("viewer@test.local")

        val response = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-04"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `hotel manager can create reservations but not rooms`() = testApplication {
        application { configureApp() }
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val userId = insertUser("manager@test.local", appRole = "user")
        insertUserHotelRole(userId, hotelId, "manager")
        val client = jsonClient("manager@test.local")

        val reservationResponse = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-04"))
        }
        assertEquals(HttpStatusCode.Created, reservationResponse.status)

        val roomResponse = client.post("/api/rooms") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(hotelId = hotelId, typeName = "Standard", number = "202", maxGuests = 2))
        }
        assertEquals(HttpStatusCode.Forbidden, roomResponse.status)
    }

    @Test
    fun `hotel admin role can create rooms without global admin app role`() = testApplication {
        application { configureApp() }
        val hotelId = insertHotel()
        val userId = insertUser("hoteladmin@test.local", appRole = "user")
        insertUserHotelRole(userId, hotelId, "admin")
        val client = jsonClient("hoteladmin@test.local")

        val response = client.post("/api/rooms") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(hotelId = hotelId, typeName = "Standard", number = "101", maxGuests = 2))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `role in one hotel does not grant access to another hotel`() = testApplication {
        application { configureApp() }
        val hotelA = insertHotel("Hotel A")
        val hotelB = insertHotel("Hotel B")
        val roomB = insertRoom(hotelB)
        val guestId = insertGuest()
        val userId = insertUser("manager@test.local", appRole = "user")
        insertUserHotelRole(userId, hotelA, "manager")
        val client = jsonClient("manager@test.local")

        val response = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelB, roomId = roomB, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-04"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `user with no hotel role at all is forbidden from mutations`() = testApplication {
        application { configureApp() }
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        insertUser("nobody@test.local", appRole = "user")
        val client = jsonClient("nobody@test.local")

        val response = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-04"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
