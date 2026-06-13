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

object Tags : Table("tags") {
    val id      = integer("id").autoIncrement()
    val hotelId = integer("hotel_id")
    val name    = varchar("name", 100)
    override val primaryKey = PrimaryKey(id)
}

object RoomTags : Table("room_tags") {
    val roomId = integer("room_id")
    val tagId  = integer("tag_id")
    override val primaryKey = PrimaryKey(roomId, tagId)
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
    val sourceType          = registerColumn<String>("source_type", object : VarCharColumnType(50) {
        override fun sqlType() = "reservation_source_type"
        override fun notNullValueToDB(value: String): Any =
            PGobject().apply { type = "reservation_source_type"; this.value = value }
    })
    val sourceName          = varchar("source_name", 255).nullable()
    val externalRef         = varchar("external_ref", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object ReservationPriceSegments : Table("reservation_price_segments") {
    val id                     = integer("id").autoIncrement()
    val reservationId          = integer("reservation_id")
    val ruleId                 = integer("rule_id").nullable()
    val fromDate               = date("from_date")
    val toDate                 = date("to_date")
    val pricePerPersonPerNight = decimal("price_per_person_per_night", 10, 2)
    val adults                 = decimal("adults", 4, 1)
    val currency               = varchar("currency", 10)
    override val primaryKey    = PrimaryKey(id)
}

object ReservationPriceAdjustments : Table("reservation_price_adjustments") {
    val id            = integer("id").autoIncrement()
    val reservationId = integer("reservation_id")
    val amount        = decimal("amount", 10, 2)
    val description   = varchar("description", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object Holidays : Table("holidays") {
    val id       = integer("id").autoIncrement()
    val hotelId  = integer("hotel_id")
    val name     = varchar("name", 255)
    val fromDate = date("from_date")
    val toDate   = date("to_date")
    override val primaryKey = PrimaryKey(id)
}

object Invoices : Table("invoices") {
    val id               = integer("id").autoIncrement()
    val hotelId          = integer("hotel_id")
    val reservationId    = integer("reservation_id").nullable()
    val invoiceNumber    = varchar("invoice_number", 50)
    val issueDate        = date("issue_date")
    val saleDate         = date("sale_date")
    val dueDate          = date("due_date")
    val paymentMethod    = varchar("payment_method", 50)
    val sellerName       = varchar("seller_name", 255)
    val sellerAddress    = text("seller_address").nullable()
    val sellerNip        = varchar("seller_nip", 20).nullable()
    val sellerRegon      = varchar("seller_regon", 20).nullable()
    val sellerBankAccount = varchar("seller_bank_account", 50).nullable()
    val sellerPhone      = varchar("seller_phone", 50).nullable()
    val sellerEmail      = varchar("seller_email", 255).nullable()
    val buyerName        = varchar("buyer_name", 255)
    val buyerAddress     = text("buyer_address").nullable()
    val buyerNip         = varchar("buyer_nip", 20).nullable()
    val buyerRegon       = varchar("buyer_regon", 20).nullable()
    val totalNet         = decimal("total_net", 10, 2)
    val totalVat         = decimal("total_vat", 10, 2)
    val totalGross       = decimal("total_gross", 10, 2)
    val notes            = text("notes").nullable()
    val createdAt        = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

object InvoiceSettings : Table("invoice_settings") {
    val hotelId              = integer("hotel_id")
    val sellerName           = varchar("seller_name", 255).nullable()
    val sellerAddress        = text("seller_address").nullable()
    val sellerNip            = varchar("seller_nip", 20).nullable()
    val sellerRegon          = varchar("seller_regon", 20).nullable()
    val sellerBankAccount    = varchar("seller_bank_account", 50).nullable()
    val sellerPhone          = varchar("seller_phone", 50).nullable()
    val sellerEmail          = varchar("seller_email", 255).nullable()
    val defaultPaymentMethod = varchar("default_payment_method", 50)
    val defaultDueDays       = integer("default_due_days")
    override val primaryKey  = PrimaryKey(hotelId)
}

object InvoiceItems : Table("invoice_items") {
    val id           = integer("id").autoIncrement()
    val invoiceId    = integer("invoice_id")
    val ordinal      = integer("ordinal")
    val name         = varchar("name", 500)
    val quantity     = decimal("quantity", 10, 3)
    val unit         = varchar("unit", 50)
    val unitNetPrice = decimal("unit_net_price", 10, 2)
    val vatRate      = varchar("vat_rate", 10)
    val netAmount    = decimal("net_amount", 10, 2)
    val vatAmount    = decimal("vat_amount", 10, 2)
    val grossAmount  = decimal("gross_amount", 10, 2)
    override val primaryKey = PrimaryKey(id)
}
