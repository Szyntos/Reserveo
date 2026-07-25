package org.julsz.smnt.routes

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.julsz.smnt.InvoiceSettingsDto
import org.julsz.smnt.SaveInvoiceSettingsRequest
import org.julsz.smnt.TestDb
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class InvoiceSettingsRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `get returns unsaved defaults without persisting them`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val settings = client.get("/api/hotels/$hotelId/invoice-settings").body<InvoiceSettingsDto>()
        assertEquals("transfer", settings.defaultPaymentMethod)
        assertEquals(14, settings.defaultDueDays)
    }

    @Test
    fun `non-admin cannot save invoice settings`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local", appRole = "user")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val response = client.put("/api/hotels/$hotelId/invoice-settings") {
            contentType(ContentType.Application.Json)
            setBody(SaveInvoiceSettingsRequest(
                sellerName = "Acme", sellerAddress = null, sellerNip = null, sellerRegon = null,
                sellerBankAccount = null, sellerPhone = null, sellerEmail = null,
                defaultPaymentMethod = "cash", defaultDueDays = 7
            ))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin save then re-save updates in place instead of duplicating`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val client = jsonClient("admin@test.local")

        client.put("/api/hotels/$hotelId/invoice-settings") {
            contentType(ContentType.Application.Json)
            setBody(SaveInvoiceSettingsRequest(
                sellerName = "Acme", sellerAddress = null, sellerNip = null, sellerRegon = null,
                sellerBankAccount = null, sellerPhone = null, sellerEmail = null,
                defaultPaymentMethod = "cash", defaultDueDays = 7
            ))
        }
        val updated = client.put("/api/hotels/$hotelId/invoice-settings") {
            contentType(ContentType.Application.Json)
            setBody(SaveInvoiceSettingsRequest(
                sellerName = "Acme Corp", sellerAddress = null, sellerNip = null, sellerRegon = null,
                sellerBankAccount = null, sellerPhone = null, sellerEmail = null,
                defaultPaymentMethod = "transfer", defaultDueDays = 30
            ))
        }.body<InvoiceSettingsDto>()

        assertEquals("Acme Corp", updated.sellerName)
        val fetched = client.get("/api/hotels/$hotelId/invoice-settings").body<InvoiceSettingsDto>()
        assertEquals("Acme Corp", fetched.sellerName)
        assertEquals(30, fetched.defaultDueDays)
    }
}
