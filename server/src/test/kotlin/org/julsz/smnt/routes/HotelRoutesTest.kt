package org.julsz.smnt.routes

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.julsz.smnt.CreateHotelRequest
import org.julsz.smnt.HotelDto
import org.julsz.smnt.TestDb
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotelRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `create then list hotels`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val client = jsonClient("user@test.local")

        val created = client.post("/api/hotels") {
            contentType(ContentType.Application.Json)
            setBody(CreateHotelRequest(name = "Grand Hotel", address = "Main St 1", phone = "123", email = "h@x.com"))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val dto = created.body<HotelDto>()
        assertEquals("Grand Hotel", dto.name)
        assertEquals("123", dto.phone)

        val list = client.get("/api/hotels").body<List<HotelDto>>()
        assertEquals(1, list.size)
        assertEquals(dto.id, list[0].id)
    }

    @Test
    fun `optional fields default to null`() = testApplication {
        application { configureApp() }
        insertUser("user2@test.local")
        val client = jsonClient("user2@test.local")

        val created = client.post("/api/hotels") {
            contentType(ContentType.Application.Json)
            setBody(CreateHotelRequest(name = "Minimal Hotel"))
        }.body<HotelDto>()
        assertNull(created.address)
        assertNull(created.phone)
        assertNull(created.email)
    }

    @Test
    fun `unauthenticated request is rejected`() = testApplication {
        application { configureApp() }
        val response = client.get("/api/hotels")
        assertTrue(response.status == HttpStatusCode.Unauthorized)
    }
}
