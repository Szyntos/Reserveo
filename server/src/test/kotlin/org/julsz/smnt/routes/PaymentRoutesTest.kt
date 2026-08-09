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
import org.julsz.smnt.CreatePaymentRequest
import org.julsz.smnt.CreateReservationRequest
import org.julsz.smnt.PaymentDto
import org.julsz.smnt.ReservationDto
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

class PaymentRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    private suspend fun createReservation(client: io.ktor.client.HttpClient, hotelId: Int, roomId: Int, guestId: Int): ReservationDto =
        client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(hotelId = hotelId, roomId = roomId, guestId = guestId, checkInDate = "2026-05-01", checkOutDate = "2026-05-04", totalAmount = 300.0))
        }.body()

    @Test
    fun `payments sum into reservation paidAmount`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")
        val reservation = createReservation(client, hotelId, roomId, guestId)

        client.post("/api/reservations/${reservation.id}/payments") {
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentRequest(amount = 100.0, isDeposit = true))
        }
        client.post("/api/reservations/${reservation.id}/payments") {
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentRequest(amount = 50.0))
        }

        val updated = client.get("/api/reservations?hotelId=$hotelId").body<List<ReservationDto>>().first()
        assertEquals(150.0, updated.paidAmount)

        val payments = client.get("/api/reservations/${reservation.id}/payments").body<List<PaymentDto>>()
        assertEquals(2, payments.size)
        assertEquals(true, payments.first { it.amount == 100.0 }.isDeposit)
    }

    @Test
    fun `deleting a payment removes it from the total`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")
        val reservation = createReservation(client, hotelId, roomId, guestId)

        val payment = client.post("/api/reservations/${reservation.id}/payments") {
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentRequest(amount = 80.0))
        }.body<PaymentDto>()

        val deleteResponse = client.delete("/api/payments/${payment.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val payments = client.get("/api/reservations/${reservation.id}/payments").body<List<PaymentDto>>()
        assertEquals(0, payments.size)
    }

    @Test
    fun `reservation auto-confirms once paid covers the down payment`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")
        val reservation: ReservationDto = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(
                hotelId = hotelId, roomId = roomId, guestId = guestId,
                checkInDate = "2026-05-01", checkOutDate = "2026-05-04",
                status = "pending", totalAmount = 300.0,
                requiresDownPayment = true, downPaymentAmount = 100.0
            ))
        }.body()

        client.post("/api/reservations/${reservation.id}/payments") {
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentRequest(amount = 60.0, isDeposit = true))
        }
        val stillPending = client.get("/api/reservations?hotelId=$hotelId").body<List<ReservationDto>>().first()
        assertEquals("pending", stillPending.status)

        client.post("/api/reservations/${reservation.id}/payments") {
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentRequest(amount = 40.0, isDeposit = true))
        }
        val confirmed = client.get("/api/reservations?hotelId=$hotelId").body<List<ReservationDto>>().first()
        assertEquals("confirmed", confirmed.status)
    }

    @Test
    fun `reservation reverts to pending when a payment is deleted below the down payment`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")
        val reservation: ReservationDto = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(
                hotelId = hotelId, roomId = roomId, guestId = guestId,
                checkInDate = "2026-05-01", checkOutDate = "2026-05-04",
                status = "pending", totalAmount = 300.0,
                requiresDownPayment = true, downPaymentAmount = 100.0
            ))
        }.body()

        val payment = client.post("/api/reservations/${reservation.id}/payments") {
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentRequest(amount = 100.0, isDeposit = true))
        }.body<PaymentDto>()
        val confirmed = client.get("/api/reservations?hotelId=$hotelId").body<List<ReservationDto>>().first()
        assertEquals("confirmed", confirmed.status)

        client.delete("/api/payments/${payment.id}")
        val revertedToPending = client.get("/api/reservations?hotelId=$hotelId").body<List<ReservationDto>>().first()
        assertEquals("pending", revertedToPending.status)
    }
}
