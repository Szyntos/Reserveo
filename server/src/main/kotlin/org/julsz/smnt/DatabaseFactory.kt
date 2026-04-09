package org.julsz.smnt

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    fun init(config: ApplicationConfig) {
        println("Initializing database with URL: ${config.property("database.url").getString()}")
        println("Initializing database with driver: ${config.property("database.driver").getString()}")
        println("Initializing database with user: ${config.property("database.user").getString()}")
        println("Initializing database with password: ${config.property("database.password").getString()}")
        println("Initializing database with max pool size: ${config.property("database.maxPoolSize").getString()}")
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl         = config.property("database.url").getString()
            driverClassName = config.property("database.driver").getString()
            username        = config.property("database.user").getString()
            password        = config.property("database.password").getString()
            maximumPoolSize = config.property("database.maxPoolSize").getString().toInt()
        })

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()

        Database.connect(dataSource)
    }
}
