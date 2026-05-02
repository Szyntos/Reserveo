package org.julsz.smnt.db

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
    val description = text("description").nullable()
    val archivedAt  = datetime("archived_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Guests : Table("guests") {
    val id          = integer("id").autoIncrement()
    val firstName   = varchar("first_name", 100)
    val lastName    = varchar("last_name", 100)
    val countryCode = varchar("country_code", 10).nullable()
    val phoneNumber = varchar("phone_number", 50).nullable()
    val nationality = varchar("nationality", 10).nullable()
    val blacklisted = bool("blacklisted")
    val notes       = text("notes").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id        = integer("id").autoIncrement()
    val name      = varchar("name", 255)
    val email     = varchar("email", 255)
    val appRole   = registerColumn<String>("app_role", object : VarCharColumnType(50) {
        override fun sqlType() = "app_role"
        override fun notNullValueToDB(value: String): Any =
            PGobject().apply { type = "app_role"; this.value = value }
    })
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

object UserHotelRoles : Table("user_hotel_roles") {
    val userId  = integer("user_id")
    val hotelId = integer("hotel_id")
    val role    = registerColumn<String>("role", object : VarCharColumnType(50) {
        override fun sqlType() = "hotel_user_role"
        override fun notNullValueToDB(value: String): Any =
            PGobject().apply { type = "hotel_user_role"; this.value = value }
    })
    override val primaryKey = PrimaryKey(userId, hotelId)
}

object PriceRules : Table("price_rules") {
    val id                     = integer("id").autoIncrement()
    val roomId                 = integer("room_id")
    val fromDate               = date("from_date")
    val toDate                 = date("to_date")
    val minNights              = integer("min_nights")
    val maxNights              = integer("max_nights").nullable()
    val pricePerPersonPerNight = decimal("price_per_person_per_night", 10, 2)
    val currency               = varchar("currency", 10)
    override val primaryKey    = PrimaryKey(id)
}

object RoomBlocks : Table("room_blocks") {
    val id       = integer("id").autoIncrement()
    val roomId   = integer("room_id")
    val fromDate = date("from_date")
    val toDate   = date("to_date")
    val reason   = varchar("reason", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object Payments : Table("payments") {
    val id            = integer("id").autoIncrement()
    val reservationId = integer("reservation_id")
    val isDeposit     = bool("is_deposit")
    val amount        = decimal("amount", 10, 2)
    val currency      = varchar("currency", 100).nullable()
    val paidAt        = datetime("paid_at").nullable()
    val notes         = text("notes").nullable()
    val receiptType   = varchar("receipt_type", 10).nullable()
    val receiptNumber = varchar("receipt_number", 100).nullable()
    override val primaryKey = PrimaryKey(id)
}

object Reservations : Table("reservations") {
    val id                  = integer("id").autoIncrement()
    val hotelId             = integer("hotel_id")
    val roomId              = integer("room_id")
    val guestId             = integer("guest_id")
    val checkInDate         = date("check_in_date")
    val checkOutDate        = date("check_out_date")
    val status              = registerColumn<String>("status", object : VarCharColumnType(50) {
        override fun sqlType() = "reservation_status"
        override fun notNullValueToDB(value: String): Any =
            PGobject().apply { type = "reservation_status"; this.value = value }
    })
    val adults              = decimal("adults", 4, 1)
    val totalAmount         = decimal("total_amount", 10, 2).nullable()
    val description         = text("description").nullable()
    val requiresDownPayment = bool("requires_down_payment")
    val downPaymentAmount   = decimal("down_payment_amount", 10, 2).nullable()
    override val primaryKey = PrimaryKey(id)
}
