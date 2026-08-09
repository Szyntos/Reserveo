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
import org.julsz.smnt.ChannelPayoutDto
import org.julsz.smnt.ChannelPayoutOverrideDto
import org.julsz.smnt.CreateChannelPayoutRequest
import org.julsz.smnt.CreateReservationRequest
import org.julsz.smnt.ReservationDto
import org.julsz.smnt.SetChannelPayoutOverrideRequest
import org.julsz.smnt.TestDb
import org.julsz.smnt.UpdateChannelPayoutRequest
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertGuest
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertRoom
import org.julsz.smnt.insertUser
import org.julsz.smnt.insertUserHotelRole
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ChannelPayoutRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `create, list and delete a payout`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val created = client.post("/api/channel-payouts") {
            contentType(ContentType.Application.Json)
            setBody(CreateChannelPayoutRequest(hotelId = hotelId, year = 2026, month = 7, amount = 8450.25))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val dto = created.body<ChannelPayoutDto>()
        assertEquals(8450.25, dto.amount)
        assertEquals(7, dto.month)
        assertEquals("PLN", dto.currency)

        assertEquals(1, client.get("/api/channel-payouts?hotelId=$hotelId").body<List<ChannelPayoutDto>>().size)

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/channel-payouts/${dto.id}").status)
        assertEquals(0, client.get("/api/channel-payouts?hotelId=$hotelId").body<List<ChannelPayoutDto>>().size)
    }

    @Test
    fun `a second payout for the same month is rejected`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        client.post("/api/channel-payouts") {
            contentType(ContentType.Application.Json)
            setBody(CreateChannelPayoutRequest(hotelId = hotelId, year = 2026, month = 7, amount = 1000.0))
        }
        val duplicate = client.post("/api/channel-payouts") {
            contentType(ContentType.Application.Json)
            setBody(CreateChannelPayoutRequest(hotelId = hotelId, year = 2026, month = 7, amount = 2000.0))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
    }

    @Test
    fun `the same month in different hotels is allowed`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelA = insertHotel("A")
        val hotelB = insertHotel("B")
        val client = jsonClient("user@test.local")

        listOf(hotelA, hotelB).forEach { hotel ->
            val response = client.post("/api/channel-payouts") {
                contentType(ContentType.Application.Json)
                setBody(CreateChannelPayoutRequest(hotelId = hotel, year = 2026, month = 7, amount = 500.0))
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }
        assertEquals(1, client.get("/api/channel-payouts?hotelId=$hotelA").body<List<ChannelPayoutDto>>().size)
    }

    @Test
    fun `updating a payout replaces the amount`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val dto = client.post("/api/channel-payouts") {
            contentType(ContentType.Application.Json)
            setBody(CreateChannelPayoutRequest(hotelId = hotelId, year = 2026, month = 7, amount = 1000.0))
        }.body<ChannelPayoutDto>()

        val updated = client.put("/api/channel-payouts/${dto.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateChannelPayoutRequest(amount = 1234.56, notes = "corrected"))
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        val body = updated.body<ChannelPayoutDto>()
        assertEquals(1234.56, body.amount)
        assertEquals("corrected", body.notes)
    }

    @Test
    fun `invalid month is rejected`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val response = client.post("/api/channel-payouts") {
            contentType(ContentType.Application.Json)
            setBody(CreateChannelPayoutRequest(hotelId = hotelId, year = 2026, month = 13, amount = 100.0))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `negative amount is rejected`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val response = client.post("/api/channel-payouts") {
            contentType(ContentType.Application.Json)
            setBody(CreateChannelPayoutRequest(hotelId = hotelId, year = 2026, month = 7, amount = -5.0))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a viewer cannot record a payout`() = testApplication {
        application { configureApp() }
        val userId = insertUser("viewer@test.local", appRole = "user")
        val hotelId = insertHotel()
        insertUserHotelRole(userId, hotelId, "viewer")
        val client = jsonClient("viewer@test.local")

        val response = client.post("/api/channel-payouts") {
            contentType(ContentType.Application.Json)
            setBody(CreateChannelPayoutRequest(hotelId = hotelId, year = 2026, month = 7, amount = 100.0))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `deleting a missing payout returns 404`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        insertHotel()
        val client = jsonClient("user@test.local")

        assertEquals(HttpStatusCode.NotFound, client.delete("/api/channel-payouts/9999").status)
    }

    // ─── Attribution overrides ────────────────────────────────────────────────

    private suspend fun createReservation(client: io.ktor.client.HttpClient, hotelId: Int, roomId: Int, guestId: Int): Int =
        client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(CreateReservationRequest(
                hotelId = hotelId, roomId = roomId, guestId = guestId,
                checkInDate = "2026-06-26", checkOutDate = "2026-06-28",
                totalAmount = 1000.0, source = "external"
            ))
        }.body<ReservationDto>().id

    @Test
    fun `set, list and clear an override`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")
        val resId = createReservation(client, hotelId, roomId, guestId)

        val set = client.put("/api/reservations/$resId/payout-override") {
            contentType(ContentType.Application.Json)
            setBody(SetChannelPayoutOverrideRequest(year = 2026, month = 6))
        }
        assertEquals(HttpStatusCode.OK, set.status)
        val dto = set.body<ChannelPayoutOverrideDto>()
        assertEquals(2026, dto.year)
        assertEquals(6, dto.month)
        assertEquals(false, dto.excluded)

        assertEquals(1, client.get("/api/channel-payout-overrides?hotelId=$hotelId")
            .body<List<ChannelPayoutOverrideDto>>().size)

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/reservations/$resId/payout-override").status)
        assertEquals(0, client.get("/api/channel-payout-overrides?hotelId=$hotelId")
            .body<List<ChannelPayoutOverrideDto>>().size)
    }

    @Test
    fun `setting an override twice replaces rather than duplicates`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")
        val resId = createReservation(client, hotelId, roomId, guestId)

        client.put("/api/reservations/$resId/payout-override") {
            contentType(ContentType.Application.Json)
            setBody(SetChannelPayoutOverrideRequest(year = 2026, month = 6))
        }
        client.put("/api/reservations/$resId/payout-override") {
            contentType(ContentType.Application.Json)
            setBody(SetChannelPayoutOverrideRequest(year = 2026, month = 8))
        }

        val all = client.get("/api/channel-payout-overrides?hotelId=$hotelId")
            .body<List<ChannelPayoutOverrideDto>>()
        assertEquals(1, all.size)
        assertEquals(8, all.single().month)
    }

    @Test
    fun `an override that neither excludes nor names a month is rejected`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")
        val resId = createReservation(client, hotelId, roomId, guestId)

        // This is the shape that would orphan a reservation — it must never be storable.
        val response = client.put("/api/reservations/$resId/payout-override") {
            contentType(ContentType.Application.Json)
            setBody(SetChannelPayoutOverrideRequest(year = null, month = null, excluded = false))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `excluding needs no target month`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")
        val resId = createReservation(client, hotelId, roomId, guestId)

        val response = client.put("/api/reservations/$resId/payout-override") {
            contentType(ContentType.Application.Json)
            setBody(SetChannelPayoutOverrideRequest(excluded = true, reason = "never paid"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val dto = response.body<ChannelPayoutOverrideDto>()
        assertEquals(true, dto.excluded)
        assertEquals("never paid", dto.reason)
        assertEquals(null, dto.month)
    }

    @Test
    fun `deleting a reservation removes its override`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val client = jsonClient("user@test.local")
        val resId = createReservation(client, hotelId, roomId, guestId)

        client.put("/api/reservations/$resId/payout-override") {
            contentType(ContentType.Application.Json)
            setBody(SetChannelPayoutOverrideRequest(year = 2026, month = 6))
        }
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/reservations/$resId").status)
        assertEquals(0, client.get("/api/channel-payout-overrides?hotelId=$hotelId")
            .body<List<ChannelPayoutOverrideDto>>().size)
    }

    @Test
    fun `a viewer cannot override attribution`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val guestId = insertGuest()
        val adminClient = jsonClient("admin@test.local")
        val resId = createReservation(adminClient, hotelId, roomId, guestId)

        val viewerId = insertUser("viewer@test.local", appRole = "user")
        insertUserHotelRole(viewerId, hotelId, "viewer")
        val viewerClient = jsonClient("viewer@test.local")

        val response = viewerClient.put("/api/reservations/$resId/payout-override") {
            contentType(ContentType.Application.Json)
            setBody(SetChannelPayoutOverrideRequest(year = 2026, month = 6))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `overrides are scoped to the requested hotel`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelA = insertHotel("A")
        val hotelB = insertHotel("B")
        val client = jsonClient("user@test.local")
        val guestId = insertGuest()
        val resA = createReservation(client, hotelA, insertRoom(hotelA), guestId)
        val resB = createReservation(client, hotelB, insertRoom(hotelB), guestId)

        listOf(resA, resB).forEach { id ->
            client.put("/api/reservations/$id/payout-override") {
                contentType(ContentType.Application.Json)
                setBody(SetChannelPayoutOverrideRequest(year = 2026, month = 6))
            }
        }

        val forA = client.get("/api/channel-payout-overrides?hotelId=$hotelA")
            .body<List<ChannelPayoutOverrideDto>>()
        assertEquals(1, forA.size)
        assertEquals(resA, forA.single().reservationId)
    }
}
