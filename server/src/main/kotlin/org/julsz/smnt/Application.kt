package org.julsz.smnt

import com.typesafe.config.ConfigFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.julsz.smnt.db.DatabaseFactory
import org.julsz.smnt.routes.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

private val log = LoggerFactory.getLogger("org.julsz.smnt.Application")

fun main() {
    log.info("Starting Reserveo on port {}", SERVER_PORT)
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init(HoconApplicationConfig(ConfigFactory.load()))

    install(ContentNegotiation) { json() }

    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path   = call.request.path()
            "$method $path -> $status"
        }
    }

    routing {
        get("/") { call.respondText("Ktor: ${Greeting().greet()}") }
        route("/api") {
            hotelRoutes()
            roomRoutes()
            guestRoutes()
            reservationRoutes()
            userRoutes()
            priceRoutes()
            roomBlockRoutes()
            paymentRoutes()
            holidayRoutes()
            tagRoutes()
            invoiceRoutes()
            invoiceSettingsRoutes()
        }
    }
}
