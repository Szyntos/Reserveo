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
import org.julsz.smnt.CreateInvoiceItemRequest
import org.julsz.smnt.CreateInvoiceRequest
import org.julsz.smnt.InvoiceDto
import org.julsz.smnt.TestDb
import org.julsz.smnt.UpdateInvoiceRequest
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class InvoiceRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    private fun baseRequest(hotelId: Int, invoiceNumber: String, quantity: Double = 2.0, unitPrice: Double = 100.0) =
        CreateInvoiceRequest(
            hotelId = hotelId, reservationId = null, invoiceNumber = invoiceNumber,
            issueDate = "2026-01-10", saleDate = "2026-01-10", dueDate = "2026-01-24",
            paymentMethod = "transfer",
            sellerName = "Reserveo Sp. z o.o.", sellerAddress = null, sellerNip = null,
            sellerRegon = null, sellerBankAccount = null, sellerPhone = null, sellerEmail = null,
            buyerName = "Jan Kowalski", buyerAddress = null, buyerNip = null, buyerRegon = null,
            notes = null,
            items = listOf(CreateInvoiceItemRequest(ordinal = 1, name = "Accommodation", quantity = quantity, unit = "night", unitPrice = unitPrice))
        )

    @Test
    fun `create invoice computes total from items and is VAT exempt`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val created = client.post("/api/invoices") {
            contentType(ContentType.Application.Json)
            setBody(baseRequest(hotelId, "FV/1/2026", quantity = 3.0, unitPrice = 100.0))
        }.body<InvoiceDto>()

        assertEquals(300.0, created.totalAmount)
        assertEquals(1, created.items.size)
        assertEquals(300.0, created.items[0].amount)
    }

    @Test
    fun `duplicate invoice number for same hotel is rejected`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        client.post("/api/invoices") {
            contentType(ContentType.Application.Json)
            setBody(baseRequest(hotelId, "FV/1/2026"))
        }
        val duplicate = client.post("/api/invoices") {
            contentType(ContentType.Application.Json)
            setBody(baseRequest(hotelId, "FV/1/2026"))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
    }

    @Test
    fun `same invoice number is allowed across different hotels`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelA = insertHotel("A")
        val hotelB = insertHotel("B")
        val client = jsonClient("user@test.local")

        client.post("/api/invoices") {
            contentType(ContentType.Application.Json)
            setBody(baseRequest(hotelA, "FV/1/2026"))
        }
        val second = client.post("/api/invoices") {
            contentType(ContentType.Application.Json)
            setBody(baseRequest(hotelB, "FV/1/2026"))
        }
        assertEquals(HttpStatusCode.Created, second.status)
    }

    @Test
    fun `update replaces items and recomputes total, rejects duplicate number`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val created = client.post("/api/invoices") {
            contentType(ContentType.Application.Json)
            setBody(baseRequest(hotelId, "FV/1/2026", quantity = 1.0, unitPrice = 100.0))
        }.body<InvoiceDto>()
        client.post("/api/invoices") {
            contentType(ContentType.Application.Json)
            setBody(baseRequest(hotelId, "FV/2/2026", quantity = 1.0, unitPrice = 50.0))
        }

        val updated = client.put("/api/invoices/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateInvoiceRequest(
                invoiceNumber = "FV/1/2026", issueDate = "2026-01-10", saleDate = "2026-01-10", dueDate = "2026-01-24",
                paymentMethod = "transfer", sellerName = "Reserveo Sp. z o.o.", sellerAddress = null, sellerNip = null,
                sellerRegon = null, sellerBankAccount = null, sellerPhone = null, sellerEmail = null,
                buyerName = "Jan Kowalski", buyerAddress = null, buyerNip = null, buyerRegon = null, notes = "updated",
                items = listOf(CreateInvoiceItemRequest(ordinal = 1, name = "Accommodation", quantity = 5.0, unit = "night", unitPrice = 20.0))
            ))
        }.body<InvoiceDto>()
        assertEquals(100.0, updated.totalAmount)
        assertEquals(1, updated.items.size)
        assertEquals(5.0, updated.items[0].quantity)

        val takenNumber = client.put("/api/invoices/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateInvoiceRequest(
                invoiceNumber = "FV/2/2026", issueDate = "2026-01-10", saleDate = "2026-01-10", dueDate = "2026-01-24",
                paymentMethod = "transfer", sellerName = "Reserveo Sp. z o.o.", sellerAddress = null, sellerNip = null,
                sellerRegon = null, sellerBankAccount = null, sellerPhone = null, sellerEmail = null,
                buyerName = "Jan Kowalski", buyerAddress = null, buyerNip = null, buyerRegon = null, notes = null,
                items = listOf(CreateInvoiceItemRequest(ordinal = 1, name = "Accommodation", quantity = 1.0, unit = "night", unitPrice = 1.0))
            ))
        }
        assertEquals(HttpStatusCode.Conflict, takenNumber.status)
    }

    @Test
    fun `delete invoice cascades to items`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val created = client.post("/api/invoices") {
            contentType(ContentType.Application.Json)
            setBody(baseRequest(hotelId, "FV/1/2026"))
        }.body<InvoiceDto>()

        val deleteResponse = client.delete("/api/invoices/${created.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val notFound = client.get("/api/invoices/${created.id}")
        assertEquals(HttpStatusCode.NotFound, notFound.status)
    }
}
