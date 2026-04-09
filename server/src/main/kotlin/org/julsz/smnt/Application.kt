package org.julsz.smnt

import com.typesafe.config.ConfigFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init(HoconApplicationConfig(ConfigFactory.load()))
    install(ContentNegotiation) { json() }

    routing {
        get("/") { call.respondText("Ktor: ${Greeting().greet()}") }
        apiRoutes()
    }
}