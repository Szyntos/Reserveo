package org.julsz.smnt.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    fun init(config: ApplicationConfig) {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl         = config.property("database.url").getString()
            driverClassName = config.property("database.driver").getString()
            username        = config.property("database.user").getString()
            password        = config.property("database.password").getString()
            maximumPoolSize = config.property("database.maxPoolSize").getString().toInt()
            // Force UTF-8 on every connection so PostgreSQL sends/receives
            // multi-byte characters (Polish, etc.) correctly regardless of
            // the JVM's platform default charset.
            connectionInitSql = "SET client_encoding TO 'UTF8'"
            addDataSourceProperty("charSet", "UTF-8")
        })

        Flyway.configure()
            .dataSource(dataSource)
            // If the DB was created by the Docker init script (no flyway history table),
            // baseline at V4 so Flyway doesn't try to re-apply migrations 1-4.
            // Future migrations (V5+) will still run normally.
            .baselineOnMigrate(true)
            .baselineVersion("4")
            .load()
            .migrate()

        Database.connect(dataSource)
    }
}
