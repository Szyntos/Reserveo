package org.julsz.smnt

import kotlinx.serialization.Serializable

@Serializable
data class HotelDto(
    val id: Int,
    val name: String,
    val address: String?,
    val phone: String?,
    val email: String?
)

@Serializable
data class RoomDto(
    val id: Int,
    val hotelId: Int,
    val hotelName: String,
    val typeName: String,
    val number: String,
    val floor: Int?,
    val maxGuests: Int,
    val status: String,
    val description: String?,
    val archivedAt: String?
)

@Serializable
data class GuestDto(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val nationality: String?,
    val blacklisted: Boolean
)

@Serializable
data class ReservationDto(
    val id: Int,
    val hotelId: Int,
    val roomId: Int,
    val guestId: Int,
    val hotelName: String,
    val roomNumber: String,
    val guestName: String,
    val checkInDate: String,
    val checkOutDate: String,
    val status: String,
    val adults: Int,
    val children: Int,
    val totalAmount: Double?
)

@Serializable
data class UserHotelRoleDto(
    val hotelId: Int,
    val hotelName: String,
    val role: String
)

@Serializable
data class UserDto(
    val id: Int,
    val name: String,
    val email: String,
    val appRole: String,
    val createdAt: String
)

// ─── Request bodies ───────────────────────────────────────────────────────────

@Serializable
data class CreateHotelRequest(
    val name: String,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null
)

@Serializable
data class CreateRoomRequest(
    val hotelId: Int,
    val typeName: String,
    val number: String,
    val floor: Int? = null,
    val maxGuests: Int,
    val description: String? = null
)

@Serializable
data class UpdateRoomRequest(
    val typeName: String,
    val number: String,
    val floor: Int? = null,
    val maxGuests: Int,
    val status: String,
    val description: String? = null
)

@Serializable
data class CreateReservationRequest(
    val hotelId: Int,
    val roomId: Int,
    val guestId: Int,
    val checkInDate: String,
    val checkOutDate: String,
    val status: String = "confirmed",
    val adults: Int = 1,
    val children: Int = 0,
    val totalAmount: Double? = null
)

@Serializable
data class UpdateReservationRequest(
    val roomId: Int,
    val guestId: Int,
    val checkInDate: String,
    val checkOutDate: String,
    val status: String,
    val adults: Int,
    val children: Int,
    val totalAmount: Double? = null
)

@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String,
    val appRole: String = "user"
)

@Serializable
data class PriceRuleDto(
    val id: Int,
    val roomId: Int,
    val roomNumber: String,
    val fromDate: String,
    val toDate: String,
    val minNights: Int,
    val maxNights: Int?,
    val pricePerPersonPerNight: Double,
    val currency: String
)

@Serializable
data class CreatePriceRuleRequest(
    val roomId: Int,
    val fromDate: String,
    val toDate: String,
    val minNights: Int = 1,
    val maxNights: Int? = null,
    val pricePerPersonPerNight: Double,
    val currency: String = "PLN"
)

@Serializable
data class UpdatePriceRuleRequest(
    val fromDate: String,
    val toDate: String,
    val minNights: Int = 1,
    val maxNights: Int? = null,
    val pricePerPersonPerNight: Double,
    val currency: String = "PLN"
)
