package org.julsz.smnt

import io.ktor.server.config.MapApplicationConfig
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.db.*
import org.testcontainers.containers.PostgreSQLContainer

/**
 * One Postgres container + one DB connection pool shared across every test class in this module.
 * Reused (not restarted) per test, since Testcontainers startup is slow; instead [reset] truncates
 * data between tests. Flyway migrations run once, the first time [init] is called.
 */
object TestDb {
    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }

    private var initialized = false

    @Synchronized
    fun init() {
        if (initialized) return
        val config = MapApplicationConfig().apply {
            put("database.url", container.jdbcUrl)
            put("database.driver", container.driverClassName)
            put("database.user", container.username)
            put("database.password", container.password)
            put("database.maxPoolSize", "5")
        }
        DatabaseFactory.init(config)
        initialized = true
    }

    /** Deletes all rows from every app table (children before parents) so each test starts clean. */
    fun reset() = transaction {
        truncateAll(
            InvoiceItems, Invoices, InvoiceSettings,
            Holidays,
            ReservationPriceAdjustments, ReservationPriceSegments, Payments,
            ChannelPayoutOverrides, Reservations,
            ChannelPayouts,
            RoomBlocks, PriceRules,
            RoomTags, Tags, Rooms, RoomTypes,
            UserHotelRoles, Users,
            Guests, Hotels
        )
    }

    private fun truncateAll(vararg tables: org.jetbrains.exposed.sql.Table) {
        tables.forEach { it.deleteAll() }
    }
}
