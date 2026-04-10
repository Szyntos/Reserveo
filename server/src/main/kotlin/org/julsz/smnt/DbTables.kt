package org.julsz.smnt

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.postgresql.util.PGobject

object Hotels : Table("hotels") {
    val id      = integer("id").autoIncrement()
    val name    = varchar("name", 255)
    val address = varchar("address", 255).nullable()
    val phone   = varchar("phone", 50).nullable()
    val email   = varchar("email", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object RoomTypes : Table("room_types") {
    val id      = integer("id").autoIncrement()
    val hotelId = integer("hotel_id")
    val name    = varchar("name", 100)
    override val primaryKey = PrimaryKey(id)
}

object Rooms : Table("rooms") {
    val id          = integer("id").autoIncrement()
    val hotelId     = integer("hotel_id")
    val roomTypeId  = integer("room_type_id")
    val number      = varchar("number", 50)
    val floor       = integer("floor").nullable()
    val maxGuests   = integer("max_guests")
    val status      = registerColumn<String>("status", object : VarCharColumnType(50) {
        override fun sqlType() = "room_status"
        override fun notNullValueToDB(value: String): Any =
            PGobject().apply { type = "room_status"; this.value = value }
    })
    val description = text("description").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Guests : Table("guests") {
    val id          = integer("id").autoIncrement()
    val firstName   = varchar("first_name", 100)
    val lastName    = varchar("last_name", 100)
    val email       = varchar("email", 255).nullable()
    val phone       = varchar("phone", 50).nullable()
    val nationality = varchar("nationality", 10).nullable()
    val blacklisted = bool("blacklisted")
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id        = integer("id").autoIncrement()
    val name      = varchar("name", 255)
    val email     = varchar("email", 255)
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Reservations : Table("reservations") {
    val id            = integer("id").autoIncrement()
    val hotelId       = integer("hotel_id")
    val roomId        = integer("room_id")
    val guestId       = integer("guest_id")
    val checkInDate   = date("check_in_date")
    val checkOutDate  = date("check_out_date")
    val status        = varchar("status", 50)
    val adults        = integer("adults")
    val children      = integer("children")
    val totalAmount   = decimal("total_amount", 10, 2).nullable()
    override val primaryKey = PrimaryKey(id)
}
