package org.julsz.smnt.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.julsz.smnt.TagDto
import org.julsz.smnt.db.Tags

fun Route.tagRoutes() {
    get("/tags") {
        val hotelId = call.request.queryParameters["hotelId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing hotelId")
        val prefix = call.request.queryParameters["prefix"]?.trim() ?: ""

        val results = transaction {
            Tags.selectAll()
                .where { Tags.hotelId eq hotelId }
                .apply { if (prefix.isNotEmpty()) andWhere { Tags.name.lowerCase() like "${prefix.lowercase()}%" } }
                .orderBy(Tags.name to SortOrder.ASC)
                .limit(5)
                .map { TagDto(it[Tags.id], it[Tags.name]) }
        }
        call.respond(results)
    }
}
