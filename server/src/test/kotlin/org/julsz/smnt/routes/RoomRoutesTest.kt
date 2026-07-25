package org.julsz.smnt.routes

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.julsz.smnt.CreateRoomRequest
import org.julsz.smnt.RoomDto
import org.julsz.smnt.TestDb
import org.julsz.smnt.UpdateRoomRequest
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `non-admin cannot create a room`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local", appRole = "user")
        val hotelId = insertHotel()
        val client = jsonClient("user@test.local")

        val response = client.post("/api/rooms") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(hotelId = hotelId, typeName = "Standard", number = "101", maxGuests = 2))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin can create room with tags and new room is free`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val client = jsonClient("admin@test.local")

        val created = client.post("/api/rooms") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(
                hotelId = hotelId, typeName = "Deluxe", number = "201",
                maxGuests = 3, tags = listOf("Sea View", " balcony ")
            ))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val dto = created.body<RoomDto>()
        assertEquals("free", dto.status)
        assertEquals("Deluxe", dto.typeName)
        assertNull(dto.archivedAt)

        val list = client.get("/api/rooms?hotelId=$hotelId").body<List<RoomDto>>()
        assertEquals(1, list.size)
        // Tags are normalized to trimmed lowercase on the server.
        assertEquals(setOf("sea view", "balcony"), list[0].tags.toSet())
    }

    @Test
    fun `update room replaces tags entirely`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val client = jsonClient("admin@test.local")

        val created = client.post("/api/rooms") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(hotelId = hotelId, typeName = "Standard", number = "101", maxGuests = 2, tags = listOf("wifi")))
        }.body<RoomDto>()

        val updated = client.put("/api/rooms/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateRoomRequest(typeName = "Standard", number = "101", maxGuests = 2, tags = listOf("quiet")))
        }.body<RoomDto>()

        assertEquals(listOf("quiet"), updated.tags)
    }

    @Test
    fun `archive then unarchive round trips archivedAt`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val client = jsonClient("admin@test.local")

        val created = client.post("/api/rooms") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(hotelId = hotelId, typeName = "Standard", number = "101", maxGuests = 2))
        }.body<RoomDto>()

        val archived = client.patch("/api/rooms/${created.id}/archive").body<RoomDto>()
        assertNotNull(archived.archivedAt)

        val unarchived = client.patch("/api/rooms/${created.id}/unarchive").body<RoomDto>()
        assertNull(unarchived.archivedAt)
    }

    @Test
    fun `rooms are sorted by number as a string`() = testApplication {
        application { configureApp() }
        insertUser("admin@test.local", appRole = "admin")
        val hotelId = insertHotel()
        val client = jsonClient("admin@test.local")

        listOf("10", "2", "1").forEach { number ->
            client.post("/api/rooms") {
                contentType(ContentType.Application.Json)
                setBody(CreateRoomRequest(hotelId = hotelId, typeName = "Standard", number = number, maxGuests = 2))
            }
        }

        val numbers = client.get("/api/rooms?hotelId=$hotelId").body<List<RoomDto>>().map { it.number }
        // String sort: "1" < "10" < "2"
        assertEquals(listOf("1", "10", "2"), numbers)
    }
}
