package org.julsz.smnt.routes

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.julsz.smnt.CreateGuestRequest
import org.julsz.smnt.GuestDto
import org.julsz.smnt.TestDb
import org.julsz.smnt.UpdateGuestRequest
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuestRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `create guest defaults blacklisted to false regardless of caller`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val client = jsonClient("user@test.local")

        val created = client.post("/api/guests") {
            contentType(ContentType.Application.Json)
            setBody(CreateGuestRequest(firstName = "Anna", lastName = "Kowalska"))
        }.body<GuestDto>()

        assertFalse(created.blacklisted)
        assertEquals("Anna", created.firstName)
    }

    @Test
    fun `create guest allows a null first name`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val client = jsonClient("user@test.local")

        val created = client.post("/api/guests") {
            contentType(ContentType.Application.Json)
            setBody(CreateGuestRequest(firstName = null, lastName = "Kowalska"))
        }.body<GuestDto>()

        assertEquals(null, created.firstName)
        assertEquals("Kowalska", created.lastName)
    }

    @Test
    fun `list guests is ordered by last name`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val client = jsonClient("user@test.local")

        client.post("/api/guests") {
            contentType(ContentType.Application.Json)
            setBody(CreateGuestRequest(firstName = "Zed", lastName = "Zzz"))
        }
        client.post("/api/guests") {
            contentType(ContentType.Application.Json)
            setBody(CreateGuestRequest(firstName = "Amy", lastName = "Aaa"))
        }

        val list = client.get("/api/guests").body<List<GuestDto>>()
        assertEquals(listOf("Aaa", "Zzz"), list.map { it.lastName })
    }

    @Test
    fun `update guest can set blacklisted`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val client = jsonClient("user@test.local")

        val created = client.post("/api/guests") {
            contentType(ContentType.Application.Json)
            setBody(CreateGuestRequest(firstName = "John", lastName = "Doe"))
        }.body<GuestDto>()

        val updated = client.put("/api/guests/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateGuestRequest(firstName = "John", lastName = "Doe", blacklisted = true, notes = "trouble"))
        }.body<GuestDto>()

        assertTrue(updated.blacklisted)
        assertEquals("trouble", updated.notes)
    }
}
