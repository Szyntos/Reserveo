package org.julsz.smnt.routes

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.julsz.smnt.CreatePriceAdjustmentRequest
import org.julsz.smnt.CreatePriceSegmentRequest
import org.julsz.smnt.CreateReservationRequest
import org.julsz.smnt.CreateRoomBlockRequest
import org.julsz.smnt.ReservationDto
import org.julsz.smnt.ReservationPriceAdjustmentDto
import org.julsz.smnt.TestDb
import org.julsz.smnt.UpdateReservationRequest
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertGuest
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertRoom
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReservationRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `create reservation without segments uses provided totalAmount`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        val created = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(
                hotelId = hotelId, roomId = roomId, guestId = guestId,
                checkInDate = "2026-05-01", checkOutDate = "2026-05-04",
                totalAmount = 300.0
            ))
        }.body<ReservationDto>()

        assertEquals(300.0, created.totalAmount)
        assertEquals(0.0, created.paidAmount)
    }

    @Test
    fun `create reservation with segments computes total and ignores provided totalAmount`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        // 3 nights * 100/person/night * 2 adults = 600, overriding the bogus totalAmount below.
        val created = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(
                hotelId = hotelId, roomId = roomId, guestId = guestId,
                checkInDate = "2026-05-01", checkOutDate = "2026-05-04",
                totalAmount = 1.0,
                priceSegments = listOf(CreatePriceSegmentRequest(
                    fromDate = "2026-05-01", toDate = "2026-05-04", pricePerPersonPerNight = 100.0, adults = 2.0
                ))
            ))
        }.body<ReservationDto>()

        assertEquals(600.0, created.totalAmount)
        assertEquals(1, created.priceSegments.size)
    }

    @Test
    fun `overlapping reservation on same room is rejected`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-10"))
        }

        val overlapping = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-05", checkOutDate = "2026-05-15"))
        }
        assertEquals(HttpStatusCode.Conflict, overlapping.status)
    }

    @Test
    fun `cancelled reservations do not block new bookings`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-10", status = "cancelled"))
        }

        val response = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-05", checkOutDate = "2026-05-15"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `back-to-back checkin checkout on same day is not an overlap`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-10"))
        }

        val response = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-10", checkOutDate = "2026-05-15"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `reservation is rejected when room is blocked`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        client.post("/api/room-blocks") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomBlockRequest(roomId = roomId, fromDate = "2026-05-01", toDate = "2026-05-10"))
        }

        val response = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-05", checkOutDate = "2026-05-07"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `update can move reservation to overlap with itself but not with another`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        val res = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-10"))
        }.body<ReservationDto>()

        // Shifting its own dates slightly should succeed (self-exclusion in overlap check).
        val moved = client.put("/api/reservations/${res.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateReservationRequest(
                roomId = roomId, guestId = guestId,
                checkInDate = "2026-05-02", checkOutDate = "2026-05-11", status = "confirmed", adults = 1.0
            ))
        }
        assertEquals(HttpStatusCode.OK, moved.status)

        val otherRoom = insertRoom(hotelId, number = "999")
        client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = otherRoom, guestId = guestId, checkInDate = "2026-06-01", checkOutDate = "2026-06-05"))
        }
        val otherRes = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-07-01", checkOutDate = "2026-07-05"))
        }.body<ReservationDto>()

        // Now try to move the original reservation onto the new one's dates in the same room -> conflict.
        val conflicting = client.put("/api/reservations/${res.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateReservationRequest(
                roomId = roomId, guestId = guestId,
                checkInDate = "2026-07-02", checkOutDate = "2026-07-04", status = "confirmed", adults = 1.0
            ))
        }
        assertEquals(HttpStatusCode.Conflict, conflicting.status)
        assertTrue(otherRes.id > 0)
    }

    @Test
    fun `update with segments adds existing adjustments total`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        val created = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-04", totalAmount = 100.0))
        }.body<ReservationDto>()

        client.post("/api/reservations/${created.id}/price-adjustments") {
            contentType(ContentType.Application.Json)
            setBody(CreatePriceAdjustmentRequest(amount = 50.0, description = "extra bed"))
        }

        // 3 nights * 100/person/night * 1 adult = 300, plus the existing 50 adjustment = 350.
        val updated = client.put("/api/reservations/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateReservationRequest(
                roomId = roomId, guestId = guestId,
                checkInDate = "2026-05-01", checkOutDate = "2026-05-04", status = "confirmed", adults = 1.0,
                priceSegments = listOf(CreatePriceSegmentRequest(fromDate = "2026-05-01", toDate = "2026-05-04", pricePerPersonPerNight = 100.0, adults = 1.0))
            ))
        }.body<ReservationDto>()

        assertEquals(350.0, updated.totalAmount)
    }

    @Test
    fun `price adjustment updates reservation total, deleting it subtracts back`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        val created = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-04", totalAmount = 100.0))
        }.body<ReservationDto>()

        val adj = client.post("/api/reservations/${created.id}/price-adjustments") {
            contentType(ContentType.Application.Json)
            setBody(CreatePriceAdjustmentRequest(amount = 25.0))
        }.body<ReservationPriceAdjustmentDto>()

        val afterAdd = client.get("/api/reservations?hotelId=$hotelId").body<List<ReservationDto>>().first()
        assertEquals(125.0, afterAdd.totalAmount)

        client.delete("/api/reservations/${created.id}/price-adjustments/${adj.id}")

        val afterRemove = client.get("/api/reservations?hotelId=$hotelId").body<List<ReservationDto>>().first()
        assertEquals(100.0, afterRemove.totalAmount)
    }

    @Test
    fun `delete reservation cascades to payments and segments`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")

        val created = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(
                hotelId = hotelId, roomId = roomId, guestId = guestId,
                checkInDate = "2026-05-01", checkOutDate = "2026-05-04",
                priceSegments = listOf(CreatePriceSegmentRequest(fromDate = "2026-05-01", toDate = "2026-05-04", pricePerPersonPerNight = 50.0, adults = 1.0))
            ))
        }.body<ReservationDto>()

        val deleteResponse = client.delete("/api/reservations/${created.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val remaining = client.get("/api/reservations?hotelId=$hotelId").body<List<ReservationDto>>()
        assertEquals(0, remaining.size)
    }
}
