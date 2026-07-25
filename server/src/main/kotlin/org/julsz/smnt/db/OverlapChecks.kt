package org.julsz.smnt.db

import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

private val NON_BLOCKING_STATUSES = setOf("cancelled", "no_show")

/** True if `roomId` has a non-blocking-excluded reservation overlapping [from, to). */
fun hasReservationOverlap(roomId: Int, from: String, to: String, excludeId: Int? = null): Boolean =
    transaction {
        val cin = LocalDate.parse(from)
        val cout = LocalDate.parse(to)
        var query = Reservations.selectAll().where {
            (Reservations.roomId eq roomId) and
            (Reservations.checkInDate less cout) and
            (Reservations.checkOutDate greater cin)
        }
        if (excludeId != null) query = query.andWhere { Reservations.id neq excludeId }
        query.any { it[Reservations.status] !in NON_BLOCKING_STATUSES }
    }

/** True if `roomId` has a room block overlapping [from, to). */
fun hasBlockOverlap(roomId: Int, from: String, to: String): Boolean =
    transaction {
        val fromDate = LocalDate.parse(from)
        val toDate = LocalDate.parse(to)
        RoomBlocks.selectAll().where {
            (RoomBlocks.roomId eq roomId) and
            (RoomBlocks.fromDate less toDate) and
            (RoomBlocks.toDate greater fromDate)
        }.count() > 0
    }
