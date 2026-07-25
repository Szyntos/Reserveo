package org.julsz.smnt.routes

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.TagDto
import org.julsz.smnt.TestDb
import org.julsz.smnt.configureApp
import org.julsz.smnt.db.Tags
import org.julsz.smnt.insertHotel
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    private fun insertTag(hotelId: Int, name: String) = transaction {
        Tags.insert { it[Tags.hotelId] = hotelId; it[Tags.name] = name }
    }

    @Test
    fun `missing hotelId is a bad request`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val response = jsonClient("user@test.local").get("/api/tags")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `prefix search is case-insensitive and capped at 5`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelId = insertHotel()
        listOf("Beach", "beachfront", "Balcony", "beach view", "Beacher", "beachy", "Mountain")
            .forEach { insertTag(hotelId, it) }
        val client = jsonClient("user@test.local")

        val results = client.get("/api/tags?hotelId=$hotelId&prefix=BEA").body<List<TagDto>>()

        assertTrue(results.size <= 5)
        assertTrue(results.all { it.name.startsWith("bea", ignoreCase = true) || it.name.lowercase().startsWith("bea") })
    }

    @Test
    fun `tags are scoped to hotel`() = testApplication {
        application { configureApp() }
        insertUser("user@test.local")
        val hotelA = insertHotel("A")
        val hotelB = insertHotel("B")
        insertTag(hotelA, "sea view")
        insertTag(hotelB, "sea breeze")
        val client = jsonClient("user@test.local")

        val results = client.get("/api/tags?hotelId=$hotelA&prefix=sea").body<List<TagDto>>()
        assertEquals(listOf("sea view"), results.map { it.name })
    }
}
