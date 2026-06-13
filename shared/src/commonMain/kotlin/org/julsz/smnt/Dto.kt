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
data class TagDto(val id: Int, val name: String)

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
    val tags: List<String> = emptyList(),
    val archivedAt: String?
)

@Serializable
data class GuestDto(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val countryCode: String?,
    val phoneNumber: String?,
    val nationality: String?,
    val blacklisted: Boolean,
    val notes: String? = null
)

@Serializable
data class ReservationPriceSegmentDto(
    val id: Int,
    val reservationId: Int,
    val priceRuleId: Int?,
    val fromDate: String,
    val toDate: String,
    val pricePerPersonPerNight: Double,
    val adults: Double,
    val currency: String
)

@Serializable
data class ReservationPriceAdjustmentDto(
    val id: Int,
    val reservationId: Int,
    val amount: Double,
    val description: String?
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
    val adults: Double,
    val totalAmount: Double?,
    val description: String? = null,
    val paidAmount: Double = 0.0,
    val requiresDownPayment: Boolean = false,
    val downPaymentAmount: Double? = null,
    val priceSegments: List<ReservationPriceSegmentDto> = emptyList(),
    val priceAdjustments: List<ReservationPriceAdjustmentDto> = emptyList(),
    val source: String = "private",
    val sourceName: String? = null,
    val externalRef: String? = null
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
    val description: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class UpdateRoomRequest(
    val typeName: String,
    val number: String,
    val floor: Int? = null,
    val maxGuests: Int,
    val description: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class CreatePriceSegmentRequest(
    val fromDate: String,
    val toDate: String,
    val pricePerPersonPerNight: Double,
    val adults: Double,
    val currency: String = "PLN",
    val priceRuleId: Int? = null
)

@Serializable
data class CreatePriceAdjustmentRequest(
    val amount: Double,
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
    val adults: Double = 1.0,
    val totalAmount: Double? = null,
    val requiresDownPayment: Boolean = false,
    val downPaymentAmount: Double? = null,
    val priceSegments: List<CreatePriceSegmentRequest> = emptyList(),
    val source: String = "private",
    val sourceName: String? = null,
    val externalRef: String? = null
)

@Serializable
data class UpdateReservationRequest(
    val roomId: Int,
    val guestId: Int,
    val checkInDate: String,
    val checkOutDate: String,
    val status: String,
    val adults: Double,
    val totalAmount: Double? = null,
    val description: String? = null,
    val requiresDownPayment: Boolean = false,
    val downPaymentAmount: Double? = null,
    val priceSegments: List<CreatePriceSegmentRequest> = emptyList(),
    val externalRef: String? = null
)

@Serializable
data class CreateGuestRequest(
    val firstName: String,
    val lastName: String,
    val countryCode: String? = null,
    val phoneNumber: String? = null,
    val nationality: String? = null,
    val notes: String? = null
)

@Serializable
data class UpdateGuestRequest(
    val firstName: String,
    val lastName: String,
    val countryCode: String? = null,
    val phoneNumber: String? = null,
    val nationality: String? = null,
    val blacklisted: Boolean = false,
    val notes: String? = null
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
data class RoomBlockDto(
    val id: Int,
    val roomId: Int,
    val roomNumber: String,
    val fromDate: String,
    val toDate: String,
    val reason: String?
)

@Serializable
data class CreateRoomBlockRequest(
    val roomId: Int,
    val fromDate: String,
    val toDate: String,
    val reason: String? = null
)

@Serializable
data class PaymentDto(
    val id: Int,
    val reservationId: Int,
    val isDeposit: Boolean,
    val amount: Double,
    val currency: String?,
    val paidAt: String?,
    val notes: String?,
    val receiptType: String? = null,
    val receiptNumber: String? = null
)

@Serializable
data class CreatePaymentRequest(
    val isDeposit: Boolean = false,
    val amount: Double,
    val currency: String? = "PLN",
    val paidAt: String? = null,
    val notes: String? = null,
    val receiptType: String? = null,
    val receiptNumber: String? = null
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

@Serializable
data class HolidayDto(
    val id: Int,
    val hotelId: Int,
    val name: String,
    val fromDate: String,
    val toDate: String
)

@Serializable
data class CreateHolidayRequest(
    val hotelId: Int,
    val name: String,
    val fromDate: String,
    val toDate: String
)

@Serializable
data class ImportHolidaysRequest(
    val hotelId: Int,
    val csv: String
)

@Serializable
data class ImportHolidaysResponse(
    val imported: Int,
    val holidays: List<HolidayDto>
)

// ─── Invoice DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class InvoiceItemDto(
    val id: Int,
    val invoiceId: Int,
    val ordinal: Int,
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitNetPrice: Double,
    val vatRate: String,
    val netAmount: Double,
    val vatAmount: Double,
    val grossAmount: Double
)

@Serializable
data class InvoiceDto(
    val id: Int,
    val hotelId: Int,
    val reservationId: Int?,
    val invoiceNumber: String,
    val issueDate: String,
    val saleDate: String,
    val dueDate: String,
    val paymentMethod: String,
    val sellerName: String,
    val sellerAddress: String?,
    val sellerNip: String?,
    val sellerRegon: String?,
    val sellerBankAccount: String?,
    val sellerPhone: String?,
    val sellerEmail: String?,
    val buyerName: String,
    val buyerAddress: String?,
    val buyerNip: String?,
    val buyerRegon: String?,
    val totalNet: Double,
    val totalVat: Double,
    val totalGross: Double,
    val notes: String?,
    val createdAt: String,
    val items: List<InvoiceItemDto> = emptyList()
)

@Serializable
data class CreateInvoiceItemRequest(
    val ordinal: Int,
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitNetPrice: Double,
    val vatRate: String
)

@Serializable
data class CreateInvoiceRequest(
    val hotelId: Int,
    val reservationId: Int?,
    val invoiceNumber: String? = null,
    val issueDate: String,
    val saleDate: String,
    val dueDate: String,
    val paymentMethod: String,
    val sellerName: String,
    val sellerAddress: String?,
    val sellerNip: String?,
    val sellerRegon: String?,
    val sellerBankAccount: String?,
    val sellerPhone: String?,
    val sellerEmail: String?,
    val buyerName: String,
    val buyerAddress: String?,
    val buyerNip: String?,
    val buyerRegon: String?,
    val notes: String?,
    val items: List<CreateInvoiceItemRequest>
)

@Serializable
data class NextInvoiceNumberResponse(val number: String)

@Serializable
data class InvoiceSettingsDto(
    val hotelId: Int,
    val sellerName: String?,
    val sellerAddress: String?,
    val sellerNip: String?,
    val sellerRegon: String?,
    val sellerBankAccount: String?,
    val sellerPhone: String?,
    val sellerEmail: String?,
    val defaultPaymentMethod: String,
    val defaultDueDays: Int
)

@Serializable
data class UpdateInvoiceRequest(
    val invoiceNumber: String,
    val issueDate: String,
    val saleDate: String,
    val dueDate: String,
    val paymentMethod: String,
    val sellerName: String,
    val sellerAddress: String?,
    val sellerNip: String?,
    val sellerRegon: String?,
    val sellerBankAccount: String?,
    val sellerPhone: String?,
    val sellerEmail: String?,
    val buyerName: String,
    val buyerAddress: String?,
    val buyerNip: String?,
    val buyerRegon: String?,
    val notes: String?,
    val items: List<CreateInvoiceItemRequest>
)

@Serializable
data class SaveInvoiceSettingsRequest(
    val sellerName: String?,
    val sellerAddress: String?,
    val sellerNip: String?,
    val sellerRegon: String?,
    val sellerBankAccount: String?,
    val sellerPhone: String?,
    val sellerEmail: String?,
    val defaultPaymentMethod: String,
    val defaultDueDays: Int
)
