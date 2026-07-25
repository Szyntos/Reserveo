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
import org.julsz.smnt.CreateHolidayRequest
import org.julsz.smnt.HolidayDto
import org.julsz.smnt.ImportHolidaysRequest
import org.julsz.smnt.ImportHolidaysResponse
import org.julsz.smnt.TestDb
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class HolidayRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `non-admin cannot create a holiday`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local", appRole = "user")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val response = client.post("/api/holidays") {
            contentType(ContentType.Application.Json)
            setBody(CreateHolidayRequest(hotelId = hotelId, name = "New Year", fromDate = "2026-01-01", toDate = "2026-01-01"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin can create and delete a holiday`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val client = jsonClient("admin@test.local")

        val created = client.post("/api/holidays") {
            contentType(ContentType.Application.Json)
            setBody(CreateHolidayRequest(hotelId = hotelId, name = "New Year", fromDate = "2026-01-01", toDate = "2026-01-01"))
        }.body<HolidayDto>()

        val deleteResponse = client.delete("/api/holidays/${created.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        assertEquals(0, client.get("/api/holidays?hotelId=$hotelId").body<List<HolidayDto>>().size)
    }

    @Test
    fun `csv import skips header row and blank lines`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val client = jsonClient("admin@test.local")

        val csv = """
            holiday,date

            New Year,2026-01-01
            Constitution Day,2026-05-03
        """.trimIndent()

        val response = client.post("/api/holidays/import") {
            contentType(ContentType.Application.Json)
            setBody(ImportHolidaysRequest(hotelId = hotelId, csv = csv))
        }.body<ImportHolidaysResponse>()

        assertEquals(2, response.imported)
        assertEquals(setOf("New Year", "Constitution Day"), response.holidays.map { it.name }.toSet())
    }

    @Test
    fun `csv import with from and to date range`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val client = jsonClient("admin@test.local")

        val response = client.post("/api/holidays/import") {
            contentType(ContentType.Application.Json)
            setBody(ImportHolidaysRequest(hotelId = hotelId, csv = "Christmas Break,2026-12-24,2026-12-26"))
        }.body<ImportHolidaysResponse>()

        assertEquals(1, response.imported)
        val holiday = response.holidays.single()
        assertEquals("2026-12-24", holiday.fromDate)
        assertEquals("2026-12-26", holiday.toDate)
    }

    @Test
    fun `re-importing same date and name is a no-op, different name overwrites`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val client = jsonClient("admin@test.local")

        client.post("/api/holidays/import") {
            contentType(ContentType.Application.Json)
            setBody(ImportHolidaysRequest(hotelId = hotelId, csv = "New Year,2026-01-01"))
        }

        // Same date, same name -> skipped, no duplicate created.
        val sameAgain = client.post("/api/holidays/import") {
            contentType(ContentType.Application.Json)
            setBody(ImportHolidaysRequest(hotelId = hotelId, csv = "New Year,2026-01-01"))
        }.body<ImportHolidaysResponse>()
        assertEquals(0, sameAgain.imported)
        assertEquals(1, client.get("/api/holidays?hotelId=$hotelId").body<List<HolidayDto>>().size)

        // Same date, different name -> updates existing holiday's name in place.
        val renamed = client.post("/api/holidays/import") {
            contentType(ContentType.Application.Json)
            setBody(ImportHolidaysRequest(hotelId = hotelId, csv = "New Year's Day,2026-01-01"))
        }.body<ImportHolidaysResponse>()
        assertEquals(1, renamed.imported)
        val all = client.get("/api/holidays?hotelId=$hotelId").body<List<HolidayDto>>()
        assertEquals(1, all.size)
        assertEquals("New Year's Day", all.single().name)
    }
}
