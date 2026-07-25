package org.julsz.smnt.routes

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.julsz.smnt.CreateReservationRequest
import org.julsz.smnt.CreateRoomBlockRequest
import org.julsz.smnt.RoomBlockDto
import org.julsz.smnt.TestDb
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertGuest
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertRoom
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class RoomBlockRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `create and delete a room block`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val client = jsonClient("user@test.local")

        val created = client.post("/api/room-blocks") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomBlockRequest(roomId = roomId, fromDate = "2026-03-01", toDate = "2026-03-05", reason = "Maintenance"))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val dto = created.body<RoomBlockDto>()
        assertEquals("Maintenance", dto.reason)

        val deleteResponse = client.delete("/api/room-blocks/${dto.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        assertEquals(0, client.get("/api/room-blocks?hotelId=$hotelId").body<List<RoomBlockDto>>().size)
    }

    @Test
    fun `overlapping block on the same room is rejected`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val client = jsonClient("user@test.local")

        client.post("/api/room-blocks") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomBlockRequest(roomId = roomId, fromDate = "2026-03-01", toDate = "2026-03-10"))
        }

        val overlapping = client.post("/api/room-blocks") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomBlockRequest(roomId = roomId, fromDate = "2026-03-05", toDate = "2026-03-15"))
        }
        assertEquals(HttpStatusCode.Conflict, overlapping.status)
    }

    @Test
    fun `back-to-back blocks on checkout day are not an overlap`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val client = jsonClient("user@test.local")

        client.post("/api/room-blocks") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomBlockRequest(roomId = roomId, fromDate = "2026-03-01", toDate = "2026-03-10"))
        }
        val backToBack = client.post("/api/room-blocks") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomBlockRequest(roomId = roomId, fromDate = "2026-03-10", toDate = "2026-03-15"))
        }
        assertEquals(HttpStatusCode.Created, backToBack.status)
    }

    @Test
    fun `block is rejected when room has an active reservation in range`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(
                hotelId = hotelId, roomId = roomId, guestId = guestId,
                checkInDate = "2026-04-01", checkOutDate = "2026-04-05", status = "confirmed"
            ))
        }

        val blockResponse = client.post("/api/room-blocks") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomBlockRequest(roomId = roomId, fromDate = "2026-04-03", toDate = "2026-04-07"))
        }
        assertEquals(HttpStatusCode.Conflict, blockResponse.status)
    }

    @Test
    fun `block is allowed when overlapping reservation is cancelled`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(
                hotelId = hotelId, roomId = roomId, guestId = guestId,
                checkInDate = "2026-04-01", checkOutDate = "2026-04-05", status = "cancelled"
            ))
        }

        val blockResponse = client.post("/api/room-blocks") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomBlockRequest(roomId = roomId, fromDate = "2026-04-03", toDate = "2026-04-07"))
        }
        assertEquals(HttpStatusCode.Created, blockResponse.status)
    }
}
