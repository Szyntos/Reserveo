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
import org.julsz.smnt.CreatePriceRuleRequest
import org.julsz.smnt.PriceRuleDto
import org.julsz.smnt.TestDb
import org.julsz.smnt.UpdatePriceRuleRequest
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertRoom
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class PriceRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `non-admin cannot create a price rule`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local", appRole = "user")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val client = jsonClient("user@test.local")

        val response = client.post("/api/price-rules") {
            contentType(ContentType.Application.Json)
            setBody(CreatePriceRuleRequest(roomId = roomId, fromDate = "2026-01-01", toDate = "2026-01-31", pricePerPersonPerNight = 100.0))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin can create update and delete a price rule, and rules do not check overlap`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val roomId = insertRoom(hotelId)
        val client = jsonClient("admin@test.local")

        val created = client.post("/api/price-rules") {
            contentType(ContentType.Application.Json)
            setBody(CreatePriceRuleRequest(roomId = roomId, fromDate = "2026-06-01", toDate = "2026-06-30", pricePerPersonPerNight = 150.0))
        }.body<PriceRuleDto>()
        assertEquals(150.0, created.pricePerPersonPerNight)

        // Overlapping rule for the same room/dates is allowed (no overlap guard on price rules).
        val overlapping = client.post("/api/price-rules") {
            contentType(ContentType.Application.Json)
            setBody(CreatePriceRuleRequest(roomId = roomId, fromDate = "2026-06-15", toDate = "2026-07-15", pricePerPersonPerNight = 200.0))
        }
        assertEquals(HttpStatusCode.Created, overlapping.status)

        val updated = client.put("/api/price-rules/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdatePriceRuleRequest(fromDate = "2026-06-01", toDate = "2026-06-30", pricePerPersonPerNight = 175.0))
        }.body<PriceRuleDto>()
        assertEquals(175.0, updated.pricePerPersonPerNight)

        val deleteResponse = client.delete("/api/price-rules/${created.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val remaining = client.get("/api/hotels/$hotelId/price-rules").body<List<PriceRuleDto>>()
        assertEquals(1, remaining.size)
    }

    @Test
    fun `price rules are scoped to hotel via its rooms`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelA = insertHotel("A")
        val hotelB = insertHotel("B")
        val roomA = insertRoom(hotelA, number = "1")
        val roomB = insertRoom(hotelB, number = "1")
        val client = jsonClient("admin@test.local")

        client.post("/api/price-rules") {
            contentType(ContentType.Application.Json)
            setBody(CreatePriceRuleRequest(roomId = roomA, fromDate = "2026-01-01", toDate = "2026-01-31", pricePerPersonPerNight = 100.0))
        }
        client.post("/api/price-rules") {
            contentType(ContentType.Application.Json)
            setBody(CreatePriceRuleRequest(roomId = roomB, fromDate = "2026-01-01", toDate = "2026-01-31", pricePerPersonPerNight = 999.0))
        }

        val rulesForA = client.get("/api/hotels/$hotelA/price-rules").body<List<PriceRuleDto>>()
        assertEquals(1, rulesForA.size)
        assertEquals(roomA, rulesForA[0].roomId)
    }
}
