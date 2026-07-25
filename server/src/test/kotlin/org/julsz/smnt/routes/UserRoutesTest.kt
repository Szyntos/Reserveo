package org.julsz.smnt.routes

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.julsz.smnt.CreateUserRequest
import org.julsz.smnt.TestDb
import org.julsz.smnt.UserDto
import org.julsz.smnt.configureApp
import org.julsz.smnt.insertUser
import org.julsz.smnt.jsonClient
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class UserRoutesTest {

    @Before
    fun setup() {
        TestDb.init()
        TestDb.reset()
    }

    @Test
    fun `create user defaults app role to user`() = testApplication {
        application { configureApp() }
        insertUser("caller@test.local")
        val client = jsonClient("caller@test.local")

        val created = client.post("/api/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(name = "New Person", email = "new@test.local"))
        }.body<UserDto>()

        assertEquals("user", created.appRole)
        assertEquals("new@test.local", created.email)
    }

    @Test
    fun `users me returns the authenticated principal`() = testApplication {
        application { configureApp() }
        insertUser("me@test.local", appRole = "admin", name = "Me")
        val client = jsonClient("me@test.local")

        val me = client.get("/api/users/me").body<UserDto>()
        assertEquals("me@test.local", me.email)
        assertEquals("admin", me.appRole)
    }

    @Test
    fun `first login bootstraps password and second login must match it`() = testApplication {
        application { configureApp() }
        insertUser("bootstrap@test.local")

        val first = jsonClient("bootstrap@test.local", password = "first-pass")
            .get("/api/users/me")
        assertEquals(HttpStatusCode.OK, first.status)

        // Same password again succeeds.
        val second = jsonClient("bootstrap@test.local", password = "first-pass")
            .get("/api/users/me")
        assertEquals(HttpStatusCode.OK, second.status)

        // A different password now fails since it was already bootstrapped.
        val wrongPassword = jsonClient("bootstrap@test.local", password = "wrong")
            .get("/api/users/me")
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
    }
}
