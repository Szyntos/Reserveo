package org.julsz.smnt

import com.typesafe.config.ConfigFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.julsz.smnt.auth.authenticateUser
import org.julsz.smnt.db.DatabaseFactory
import org.julsz.smnt.routes.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

private val log = LoggerFactory.getLogger("org.julsz.smnt.Application")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: SERVER_PORT
    log.info("Starting Reserveo on port {}", port)
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(dbConfig: ApplicationConfig? = null) {
    DatabaseFactory.init(dbConfig ?: HoconApplicationConfig(ConfigFactory.load()))
    configureApp()
}

/** Route/plugin wiring, separate from DB initialization so tests can reuse a single DB connection pool. */
fun Application.configureApp() {
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

    install(Authentication) {
        basic("auth-basic") {
            realm = "Reserveo"
            validate { credentials -> authenticateUser(credentials.name, credentials.password) }
        }
    }

    routing {
        get("/") { call.respondText("Ktor: ${Greeting().greet()}") }
        route("/api") {
            publicUserRoutes()
        }
        authenticate("auth-basic") {
            route("/api") {
                hotelRoutes()
                roomRoutes()
                guestRoutes()
                reservationRoutes()
                userRoutes()
                priceRoutes()
                roomBlockRoutes()
                paymentRoutes()
                channelPayoutRoutes()
                holidayRoutes()
                tagRoutes()
                invoiceRoutes()
                invoiceSettingsRoutes()
            }
        }
    }
}
