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
        })

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()

        Database.connect(dataSource)
    }
}
