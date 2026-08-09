package org.julsz.smnt

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

val LocalFontScale = compositionLocalOf { 1.0f }

interface AppStrings {
    val locale: Locale

    // Common actions
    val ok: String
    val cancel: String
    val save: String
    val add: String
    val edit: String
    val delete: String
    val close: String
    val remove: String
    val retry: String
    val create: String
    val select: String
    val change: String
    fun errorMsg(msg: String): String

    // App / navigation
    val appName: String
    val navDashboard: String
    val navReservations: String
    val navConfig: String
    val navSettings: String

    // Theme toggle
    val themeDark: String
    val themeLight: String

    // Sidebar
    val switchHotel: String
    val logout: String

    // Login
    val loginSubtitle: String
    val loginUserLabel: String
    val loginNoUsers: String
    val loginEnter: String
    val loginPasswordLabel: String
    val loginInvalidCredentials: String

    // Hotel picker
    fun welcomeBack(name: String): String
    val selectHotelToManage: String
    val noHotelsAssigned: String

    // Settings page
    val settingsTitle: String
    val settingsAppearance: String
    val settingsFontSize: String
    val settingsTimeline: String
    val settingsTimelineDisplay: String
    val settingsCenterViewRange: String
    val settingsNoShowAfterDays: String
    val settingsAutoCheckOutAfterDays: String
    val settingsLanguage: String
    val settingsServer: String
    val settingsServerLocalhost: String
    val settingsServerDeployment: String
    val settingsServerCustom: String
    val settingsServerCustomUrl: String

    // Config hub
    val configTitle: String
    val configRoomsTitle: String
    val configRoomsDesc: String
    val configBasePriceTitle: String
    val configBasePriceDesc: String
    val configHolidaysTitle: String
    val configHolidaysDesc: String
    val configManage: String

    // Holidays
    val noHolidays: String
    val addHolidayBtn: String
    val addHolidayTitle: String
    val holidayNameLabel: String
    val holidayNamePlaceholder: String
    val importCsvBtn: String
    fun importCsvResult(count: Int): String

    // Rooms config
    val breadcrumbConfig: String
    val roomsTitle: String
    fun roomsStats(active: Int, archived: Int): String
    val showArchived: String
    val addRoomBtn: String
    val noRoomsYet: String
    val allRoomsArchived: String
    fun roomLabel(number: String): String
    val archive: String
    val unarchive: String
    val archivedChip: String
    val addRoomTitle: String
    fun editRoomTitle(number: String): String
    val roomTypeLabel: String
    val roomTypePlaceholder: String
    val numberLabel: String
    val floorLabel: String
    val maxGuestsLabel: String
    val statusLabel: String
    val descriptionLabel: String
    val tagsLabel: String
    val tagInputHint: String
    val tagInputSupport: String

    // Base price config
    val basePriceTitle: String
    val basePriceSubtitle: String
    val addRoomsFirst: String
    val noRules: String
    fun rulesCount(count: Int): String
    val addRule: String
    val breadcrumbBasePrice: String
    val roomFieldLabel: String
    val selectRoomHint: String
    val fromLabel: String
    val toLabel: String
    val minNightsLabel: String
    val maxNightsLabel: String
    val blankNoLimit: String
    val priceLabel: String
    val currencyLabel: String

    // Rule generator
    val generateRulesBtn: String
    val ruleGeneratorTitle: String
    val ruleGeneratorDateRange: String
    val ruleGeneratorFromYear: String
    val ruleGeneratorToYear: String
    val ruleGeneratorTiers: String
    val ruleGeneratorAddTierFrom: String
    val ruleGeneratorAddTierBtn: String
    val ruleGeneratorGenerate: String
    fun ruleGeneratorSuccess(count: Int): String
    fun lastRuleDate(date: String): String

    // Dashboard
    val arrivals: String
    val departures: String
    val notArrived: String
    val arrived: String
    val notDeparted: String
    val departed: String
    val noArrivalsToday: String
    val noDeparturesToday: String
    fun roomShort(number: String): String

    // Reservations page
    val reservationsTitle: String
    val viewCalendar: String
    val viewTimeline: String
    val blockRoomBtn: String
    val blockModeLabel: String
    val dragModeReservation: String
    val dragModeExternal: String
    val dragModeBlock: String
    val blockConflictError: String
    val newReservationBtn: String
    val newExternalBtn: String
    val newExternalReservationTitle: String
    val externalSourceLabel: String
    val bookingRefLabel: String
    val bookingTotalLabel: String
    val dowLabels: List<String>
    val conflictError: String
    val viewOnlyBadge: String
    val blockedActionTitle: String
    val blockedActionMsg: String
    fun serverError(status: Any): String

    // Reservation detail dialog
    fun reservationDetailTitle(id: Int): String
    val checkIn: String
    val checkOut: String
    val nightsLabel: String
    val roomDetailLabel: String
    val adultsLabel: String
    val downPmtLabel: String
    val plnRequired: String
    val totalLabel: String
    val paidLabel: String
    val remainingLabel: String
    val notesLabel: String
    val notesPlaceholder: String
    val saveNotesBtn: String
    val editNoteBtn: String
    val editReservationBtn: String
    val editGuestBtn: String
    val managePaymentsBtn: String
    val blacklistedLabel: String
    val blacklistedWarning: String
    val guestNotesLabel: String
    val guestNotesPlaceholder: String
    val saveGuestBtn: String

    // Payments dialog
    fun paymentsTitle(id: Int): String
    val noPayments: String
    val typeCol: String
    val amountCol: String
    val dateCol: String
    val docCol: String
    val downPaymentName: String
    val paymentName: String
    fun downPaymentNeeded(amount: String): String
    val addPaymentSection: String
    val amountFieldLabel: String
    val dateFieldLabel: String
    val pick: String
    val nothing: String
    val receiptLabel: String
    val invoiceLabel: String
    val receiptNumberLabel: String
    val invoiceNumberLabel: String
    fun docLabel(receiptType: String?, receiptNumber: String?): String
    val customInvoiceNumberLabel: String
    val noInvoiceForReservation: String
    val viewInvoiceBtn: String
    val addPaymentBtn: String
    val typeRow: String
    val amountRow: String
    val dateRow: String
    val docTypeRow: String
    val docNumberRow: String

    // New / Edit reservation dialogs
    val newReservationTitle: String
    fun editReservationTitle(id: Int): String
    val checkInLabel: String
    val checkOutLabel: String
    val roomAlreadyReserved: String
    val requiresDownPayment: String
    val downPaymentAmountLabel: String
    val plnPerPersonPerNight: String
    val noMatchingRule: String
    fun deleteReservationTitle(id: Int): String
    val cannotBeUndone: String

    // Guest input
    val firstNameLabel: String
    val lastNameLabel: String
    val codeLabel: String
    val phoneNumberLabel: String
    val nationalityLabel: String
    val didYouMean: String

    // Timeline controls
    val scaleCenter: String
    val scaleMonth: String
    val scaleYear: String
    val today: String
    val widthLabel: String
    val heightLabel: String
    val labelWidthLabel: String
    val showRoomTypeLabel: String
    val hideRoomTypeLabel: String
    val fullDay: String
    val halfShiftLabel: String
    val hideCancelled: String
    val showCancelled: String
    val blocked: String
    val roomAbbr: String
    val nightsAbbr: String
    fun adultsStr(count: Double): String

    // Block room dialog
    val blockRoomTitle: String
    val blockAllRoomsLabel: String
    val allRoomsLabel: String
    fun blockReservationConflict(rooms: String): String
    val reasonLabel: String
    val reasonPlaceholder: String
    val blockBtn: String
    val removeBlockTitle: String
    fun removeBlockConfirm(roomNumber: String, fromDate: String, toDate: String, reason: String?): String

    // Reservation status labels
    fun statusName(status: String): String

    // Price breakdown
    fun nightsPersonsLine(nights: Int, guests: Double): String
    fun priceTotalLine(total: Double): String
    fun priceRuleLine(minNights: Int, maxNights: Int?): String

    // Price adjustments
    val adjustPriceBtn: String
    val priceAdjustmentsTitle: String
    val noAdjustments: String
    val adjustmentAmountLabel: String
    val adjustmentDescriptionLabel: String
    val addAdjustmentBtn: String
    val segmentsBaseTotal: String
    val adjustmentsTotal: String
    val effectiveTotalLabel: String

    // Dashboard statistics
    val statCheckedIn: String
    val statOccupancy: String
    val statMonthRevenue: String
    val statUpcoming7d: String
    val statPendingDp: String

    // Statistics section
    val statsTitle: String
    val statsNightsTable: String
    val statsHistogram: String
    val statsGroupAll: String
    val statsGroupByType: String
    val statsGroupOneRoom: String
    val statsTimeSpan: String
    val statsNoData: String
    val statsNightsAxisLabel: String
    fun statsMonthsLabel(n: Int): String
    val statsKpiOccupancy: String
    val statsKpiRevenue: String
    val statsKpiAdr: String
    val statsKpiRevpar: String
    val statsKpiAvgStay: String
    val statsKpiCancelRate: String
    val statsBySource: String

    // Dashboard — overdue panes
    val overdueCheckIns: String
    val noOverdueCheckIns: String
    val overdueCheckOuts: String
    val noOverdueCheckOuts: String

    // Invoice page
    // Invoice config (settings in Config page)
    val configInvoiceTitle: String
    val configInvoiceDesc: String
    val invoiceConfigTitle: String
    val invoiceConfigSubtitle: String
    val defaultDueDaysLabel: String
    val savedLabel: String

    // Channel payouts
    val navPayouts: String
    val payoutsTitle: String
    val payoutsSubtitle: String
    val payoutsEmpty: String
    val payoutBooked: String
    val payoutReceived: String
    val payoutCommission: String
    val payoutCommissionEst: String
    val payoutNotSettled: String
    val payoutSettled: String
    val payoutRecordBtn: String
    val payoutEditBtn: String
    val payoutAddTitle: String
    val payoutEditTitle: String
    val payoutAmountLabel: String
    val payoutNotesLabel: String
    val payoutMonthLabel: String
    val payoutYearLabel: String
    val payoutNoReservations: String
    val payoutEstimateDisclaimer: String
    val payoutAnomalyWarning: String
    val payoutAttributionNote: String
    val payoutSettledByMonth: String
    val payoutDeleteConfirm: String
    val payoutOverallRate: String
    fun payoutReservationCount(n: Int): String
    fun payoutPaidOn(date: String): String
    fun payoutSettledIn(month: String): String

    // Attribution overrides
    val payoutMoveTo: String
    val payoutOverridden: String
    val payoutDerived: String
    val payoutResetToDerived: String
    val payoutExcludeBtn: String
    val payoutExcludedSection: String
    val payoutExcludeTitle: String
    val payoutExcludeReasonLabel: String
    val payoutExcludeExplain: String
    val payoutRestoreBtn: String
    val payoutIntegrityLabel: String
    val payoutIntegrityBroken: String
    fun payoutIntegrityLine(total: Int, assigned: Int, excluded: Int, unaccounted: Int): String
    fun payoutMovedFrom(month: String): String

    val navInvoices: String
    val invoicesTitle: String
    val newInvoiceBtn: String
    val createInvoiceBtn: String
    val noInvoices: String
    val createInvoiceTitle: String
    val sellerSection: String
    val buyerSection: String
    val sellerNameLabel: String
    val sellerAddressLabel: String
    val nipLabel: String
    val regonLabel: String
    val bankAccountLabel: String
    val buyerNameLabel: String
    val buyerAddressLabel: String
    val invoiceIssueDateLabel: String
    val invoiceSaleDateLabel: String
    val invoiceDueDateLabel: String
    val paymentMethodLabel: String
    val paymentMethodTransfer: String
    val paymentMethodCash: String
    val paymentMethodCard: String
    val paymentMethodBlik: String
    val itemsSection: String
    val addItemBtn: String
    val totalAmountLabel: String
    val createAndDownloadBtn: String
    val linkedReservationLabel: String
    val selectReservationOptional: String
    val noLinkedReservation: String
    val addReservationHint: String
    val differentGuestTitle: String
    fun differentGuestWarning(incoming: String, existing: String): String
    val addAnywayBtn: String
    val deleteInvoiceTitle: String
    val downloadPdfBtn: String
    val pdfSavedSuccess: String
    val pdfSaveError: String
    val invoiceNumberPreviewLabel: String
    val editInvoiceTitle: String
    val openInvoiceBtn: String
    fun invoiceAlreadyExistsFor(number: String): String
}

// ─── English ──────────────────────────────────────────────────────────────────

object EnglishStrings : AppStrings {
    override val locale = Locale.ENGLISH
    override val ok = "OK"
    override val cancel = "Cancel"
    override val save = "Save"
    override val add = "Add"
    override val edit = "Edit"
    override val delete = "Delete"
    override val close = "Close"
    override val remove = "Remove"
    override val retry = "Retry"
    override val create = "Create"
    override val select = "Select"
    override val change = "Change"
    override fun errorMsg(msg: String) = "Error: $msg"
    override val appName = "Reserveo"
    override val navDashboard = "Dashboard"
    override val navReservations = "Reservations"
    override val navConfig = "Config"
    override val navSettings = "Settings"
    override val themeDark = "Dark"
    override val themeLight = "Light"
    override val switchHotel = "Switch Hotel"
    override val logout = "Logout"
    override val loginSubtitle = "Sign in to continue"
    override val loginUserLabel = "Email"
    override val loginNoUsers = "No users found"
    override val loginEnter = "Enter"
    override val loginPasswordLabel = "Password"
    override val loginInvalidCredentials = "Invalid email or password"
    override fun welcomeBack(name: String) = "Welcome back, $name"
    override val selectHotelToManage = "Select a hotel to manage"
    override val noHotelsAssigned = "No hotels assigned to your account."
    override val settingsTitle = "Settings"
    override val settingsAppearance = "Appearance"
    override val settingsFontSize = "Font size"
    override val settingsTimeline = "Timeline"
    override val settingsTimelineDisplay = "Timeline display"
    override val settingsCenterViewRange = "Center view range"
    override val settingsNoShowAfterDays = "Auto no-show after"
    override val settingsAutoCheckOutAfterDays = "Auto check-out after"
    override val settingsLanguage = "Language"
    override val settingsServer = "Server"
    override val settingsServerLocalhost = "Localhost"
    override val settingsServerDeployment = "Deployment"
    override val settingsServerCustom = "Custom"
    override val settingsServerCustomUrl = "Server URL"
    override val configTitle = "Config"
    override val configRoomsTitle = "Rooms"
    override val configRoomsDesc = "Manage rooms, statuses and availability"
    override val configBasePriceTitle = "Base Price"
    override val configBasePriceDesc = "Set pricing rules by room, period and stay length"
    override val configHolidaysTitle = "Holidays"
    override val configHolidaysDesc = "Mark public holidays and school breaks for calendar highlighting"
    override val configManage = "Manage →"
    override val noHolidays           = "No holidays defined"
    override val addHolidayBtn        = "Add Holiday"
    override val addHolidayTitle      = "Add Holiday"
    override val holidayNameLabel     = "Name *"
    override val holidayNamePlaceholder = "Christmas, Spring Break…"
    override val importCsvBtn         = "Import from CSV"
    override fun importCsvResult(count: Int) = "Imported $count holiday${if (count != 1) "s" else ""}"
    override val breadcrumbConfig = "← Config"
    override val roomsTitle = "Rooms"
    override fun roomsStats(active: Int, archived: Int) = "$active active · $archived archived"
    override val showArchived = "Show archived"
    override val addRoomBtn = "Add Room"
    override val noRoomsYet = "No rooms yet. Add the first room."
    override val allRoomsArchived = "All rooms are archived."
    override fun roomLabel(number: String) = "Room $number"
    override val archive = "Archive"
    override val unarchive = "Unarchive"
    override val archivedChip = "archived"
    override val addRoomTitle = "Add Room"
    override fun editRoomTitle(number: String) = "Edit Room $number"
    override val roomTypeLabel = "Room Type *"
    override val roomTypePlaceholder = "Single / Double / Suite …"
    override val numberLabel = "Number *"
    override val floorLabel = "Floor"
    override val maxGuestsLabel = "Max Guests *"
    override val statusLabel = "Status"
    override val descriptionLabel = "Description"
    override val tagsLabel = "Tags"
    override val tagInputHint = "e.g. balcony, sea view, wifi…"
    override val tagInputSupport = "Press Enter or + to add a tag"
    override val basePriceTitle = "Base Price"
    override val basePriceSubtitle = "Select a room to manage its price rules"
    override val addRoomsFirst = "Add rooms first before setting price rules."
    override val noRules = "No rules"
    override fun rulesCount(count: Int) = "$count rule${if (count != 1) "s" else ""}"
    override val addRule = "Add Rule"
    override val breadcrumbBasePrice = "← Base Price"
    override val roomFieldLabel = "Room *"
    override val selectRoomHint = "Select room"
    override val fromLabel = "From *"
    override val toLabel = "To *"
    override val minNightsLabel = "Min nights *"
    override val maxNightsLabel = "Max nights"
    override val blankNoLimit = "blank = no limit"
    override val priceLabel = "Price / person / night *"
    override val currencyLabel = "Currency"
    override val generateRulesBtn        = "Rule Generator"
    override val ruleGeneratorTitle      = "Rule Generator"
    override val ruleGeneratorDateRange  = "Date Range (year ignored)"
    override val ruleGeneratorFromYear   = "From year"
    override val ruleGeneratorToYear     = "To year"
    override val ruleGeneratorTiers      = "Night tiers & prices"
    override val ruleGeneratorAddTierFrom = "Add tier from night"
    override val ruleGeneratorAddTierBtn  = "Add tier"
    override val ruleGeneratorGenerate   = "Generate Rules"
    override fun ruleGeneratorSuccess(count: Int) = "Generated $count rule${if (count != 1) "s" else ""}"
    override fun lastRuleDate(date: String) = "Last existing rule ends: $date"
    override val arrivals = "Arrivals"
    override val departures = "Departures"
    override val notArrived = "Not arrived"
    override val arrived = "Arrived"
    override val notDeparted = "Not departed"
    override val departed = "Departed"
    override val noArrivalsToday = "No arrivals today"
    override val noDeparturesToday = "No departures today"
    override fun roomShort(number: String) = "Room $number"
    override val reservationsTitle = "Reservations"
    override val viewCalendar = "Calendar"
    override val viewTimeline = "Timeline"
    override val blockRoomBtn = "Block Room"
    override val blockModeLabel = "Block Mode"
    override val dragModeReservation = "Reservation"
    override val dragModeExternal    = "Booking"
    override val dragModeBlock       = "Block"
    override val blockConflictError = "Blocked: room is blocked for these dates"
    override val newReservationBtn = "New Reservation"
    override val newExternalBtn = "New Booking"
    override val newExternalReservationTitle = "New Booking.com Reservation"
    override val externalSourceLabel = "External"
    override val bookingRefLabel = "Booking reference"
    override val bookingTotalLabel = "Total (Booking)"
    override val dowLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    override val conflictError = "Double booking: room already reserved for these dates"
    override val viewOnlyBadge = "View only"
    override val blockedActionTitle = "View-only access"
    override val blockedActionMsg = "Your role for this hotel only allows viewing. You can't make changes here."
    override fun serverError(status: Any) = "Server error: $status"
    override fun reservationDetailTitle(id: Int) = "Reservation #$id"
    override val checkIn = "Check-in"
    override val checkOut = "Check-out"
    override val nightsLabel = "Nights"
    override val roomDetailLabel = "Room"
    override val adultsLabel = "Adults"
    override val downPmtLabel = "Down Pmt"
    override val plnRequired = "PLN required"
    override val totalLabel = "Total"
    override val paidLabel = "Paid"
    override val remainingLabel = "Remaining"
    override val notesLabel = "Notes"
    override val notesPlaceholder = "Add notes about this reservation…"
    override val saveNotesBtn = "Save Notes"
    override val editNoteBtn = "Edit Note"
    override val editReservationBtn = "Edit Reservation"
    override val editGuestBtn = "Edit Guest"
    override val managePaymentsBtn = "Manage Payments"
    override val blacklistedLabel = "Blacklisted"
    override val blacklistedWarning = "⛔ This guest is on the blacklist"
    override val guestNotesLabel = "Guest note / blacklist reason"
    override val guestNotesPlaceholder = "Reason for blacklist or general note…"
    override val saveGuestBtn = "Save Guest"
    override fun paymentsTitle(id: Int) = "Payments · Reservation #$id"
    override val noPayments = "No payments recorded yet."
    override val typeCol = "Type"
    override val amountCol = "Amount"
    override val dateCol = "Date"
    override val docCol = "Doc"
    override val downPaymentName = "Down Payment"
    override val paymentName = "Payment"
    override fun downPaymentNeeded(amount: String) = "Down payment needed: $amount PLN"
    override val addPaymentSection = "Add Payment"
    override val amountFieldLabel = "Amount (PLN) *"
    override val dateFieldLabel = "Date"
    override val pick = "Pick"
    override val nothing = "Nothing"
    override val receiptLabel = "Receipt"
    override val invoiceLabel = "Invoice"
    override val receiptNumberLabel = "Receipt number"
    override val invoiceNumberLabel = "Invoice number"
    override fun docLabel(receiptType: String?, receiptNumber: String?) = when (receiptType) {
        "receipt" -> "R: ${receiptNumber ?: "—"}"
        "invoice" -> "I: ${receiptNumber ?: "—"}"
        else      -> "—"
    }
    override val customInvoiceNumberLabel = "Use custom invoice number"
    override val noInvoiceForReservation  = "No invoice created for this reservation yet"
    override val viewInvoiceBtn           = "View invoice"
    override val addPaymentBtn = "Add Payment"
    override val typeRow = "Type"
    override val amountRow = "Amount"
    override val dateRow = "Date"
    override val docTypeRow = "Doc type"
    override val docNumberRow = "Doc #"
    override val newReservationTitle = "New Reservation"
    override fun editReservationTitle(id: Int) = "Edit Reservation #$id"
    override val checkInLabel = "Check-in *"
    override val checkOutLabel = "Check-out *"
    override val roomAlreadyReserved = "Room already reserved for these dates"
    override val requiresDownPayment = "Requires down payment"
    override val downPaymentAmountLabel = "Down payment amount (PLN)"
    override val plnPerPersonPerNight = "PLN / person / night"
    override val noMatchingRule = "No matching rule"
    override fun deleteReservationTitle(id: Int) = "Delete reservation #$id?"
    override val cannotBeUndone = "This cannot be undone."
    override val firstNameLabel = "First name"
    override val lastNameLabel = "Last name *"
    override val codeLabel = "Code"
    override val phoneNumberLabel = "Phone number"
    override val nationalityLabel = "Nationality"
    override val didYouMean = "Did you mean?"
    override val scaleCenter = "Center"
    override val scaleMonth = "Month"
    override val scaleYear = "Year"
    override val today = "Today"
    override val widthLabel = "Width:"
    override val heightLabel = "Height:"
    override val labelWidthLabel = "Label:"
    override val showRoomTypeLabel = "Type"
    override val hideRoomTypeLabel = "No type"
    override val fullDay = "Full day"
    override val halfShiftLabel = "Half shift"
    override val hideCancelled = "Hide cancelled"
    override val showCancelled = "Show cancelled"
    override val blocked = "Blocked"
    override val roomAbbr = "Rm"
    override val nightsAbbr = "n."
    override fun adultsStr(count: Double): String {
        val n = if (count % 1.0 == 0.0) count.toLong().toString() else "%.1f".format(count)
        return "$n adult${if (count != 1.0) "s" else ""}"
    }
    override val blockRoomTitle = "Block Room"
    override val blockAllRoomsLabel = "Block all rooms"
    override val allRoomsLabel = "All rooms"
    override fun blockReservationConflict(rooms: String) = "Cannot block — active reservations in: $rooms"
    override val reasonLabel = "Reason"
    override val reasonPlaceholder = "Maintenance, cleaning…"
    override val blockBtn = "Block"
    override val removeBlockTitle = "Remove Block"
    override fun removeBlockConfirm(roomNumber: String, fromDate: String, toDate: String, reason: String?) =
        "Remove block for Room $roomNumber from $fromDate to $toDate${reason?.let { " ($it)" } ?: ""}?"
    override fun statusName(status: String) =
        status.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
    override val adjustPriceBtn              = "Adjust Price"
    override val priceAdjustmentsTitle       = "Price Adjustments"
    override val noAdjustments               = "No adjustments."
    override val adjustmentAmountLabel       = "Amount (PLN, negative = discount)"
    override val adjustmentDescriptionLabel  = "Description"
    override val addAdjustmentBtn            = "Add Adjustment"
    override val segmentsBaseTotal           = "Base (from segments)"
    override val adjustmentsTotal            = "Adjustments"
    override val effectiveTotalLabel         = "Effective Total"
    override val statCheckedIn    = "Checked In"
    override val statOccupancy    = "occupancy"
    override val statMonthRevenue = "Month Collected"
    override val statUpcoming7d   = "Arrivals · Next 7d"
    override val statPendingDp    = "Pending Down Pmts"
    override val statsTitle             = "Statistics"
    override val statsNightsTable       = "Nights · Room · Month"
    override val statsHistogram         = "Stay length distribution"
    override val statsGroupAll          = "All rooms"
    override val statsGroupByType       = "By type"
    override val statsGroupOneRoom      = "One room"
    override val statsTimeSpan          = "Period:"
    override val statsNoData            = "No data"
    override val statsNightsAxisLabel   = "nights"
    override fun statsMonthsLabel(n: Int) = "${n}M"
    override val statsKpiOccupancy      = "Occupancy"
    override val statsKpiRevenue        = "Revenue"
    override val statsKpiAdr            = "ADR"
    override val statsKpiRevpar         = "RevPAR"
    override val statsKpiAvgStay        = "Avg. stay"
    override val statsKpiCancelRate     = "Cancel/no-show rate"
    override val statsBySource          = "Bookings by source"
    override val overdueCheckIns   = "Overdue Check-ins"
    override val noOverdueCheckIns = "No overdue check-ins"
    override val overdueCheckOuts   = "Overdue Check-outs"
    override val noOverdueCheckOuts = "No overdue check-outs"
    override val configInvoiceTitle        = "Invoice Settings"
    override val configInvoiceDesc        = "Configure default seller info for VAT invoices"
    override val invoiceConfigTitle       = "Invoice Settings"
    override val invoiceConfigSubtitle    = "Default seller data used when creating invoices"
    override val defaultDueDaysLabel      = "Default due days"
    override val savedLabel               = "Saved"
    override val navPayouts               = "Payouts"
    override val payoutsTitle             = "Channel payouts"
    override val payoutsSubtitle          = "Money actually received from Booking.com, month by month"
    override val payoutsEmpty             = "No channel reservations yet."
    override val payoutBooked             = "Booked"
    override val payoutReceived           = "Received"
    override val payoutCommission         = "Commission"
    override val payoutCommissionEst      = "Est. commission"
    override val payoutNotSettled         = "Awaiting payout"
    override val payoutSettled            = "Settled"
    override val payoutRecordBtn          = "Record payout"
    override val payoutEditBtn            = "Edit payout"
    override val payoutAddTitle           = "Record monthly payout"
    override val payoutEditTitle          = "Edit monthly payout"
    override val payoutAmountLabel        = "Amount received"
    override val payoutNotesLabel         = "Notes"
    override val payoutMonthLabel         = "Month"
    override val payoutYearLabel          = "Year"
    override val payoutNoReservations     = "No reservations settled in this month."
    override val payoutEstimateDisclaimer =
        "Booking reports only a monthly total, so per-reservation commission is the month's blended rate applied pro-rata — an estimate, not a per-booking figure."
    override val payoutAnomalyWarning     =
        "This rate is outside the usual range — check the amount, or whether a reservation total is missing."
    override val payoutAttributionNote    =
        "Booking pays every Thursday, so a stay is settled by the first Thursday after check-out. A stay ending 28.06 is paid on 02.07 and counts toward July."
    override val payoutSettledByMonth     = "Settled by"
    override val payoutDeleteConfirm      = "Delete this payout?"
    override val payoutOverallRate        = "Overall commission"
    override fun payoutReservationCount(n: Int) = if (n == 1) "1 reservation" else "$n reservations"
    override fun payoutPaidOn(date: String) = "Paid out $date"
    override fun payoutSettledIn(month: String) = "Settled in $month"

    override val payoutMoveTo              = "Move to"
    override val payoutOverridden          = "Manually assigned"
    override val payoutDerived             = "Auto"
    override val payoutResetToDerived      = "Reset to automatic"
    override val payoutExcludeBtn          = "Exclude"
    override val payoutExcludedSection     = "Excluded from all payouts"
    override val payoutExcludeTitle        = "Exclude from payouts"
    override val payoutExcludeReasonLabel  = "Reason"
    override val payoutExcludeExplain      =
        "This reservation will count toward no month at all. Use it only when the channel genuinely never paid for it."
    override val payoutRestoreBtn          = "Restore"
    override val payoutIntegrityLabel      = "Accounted for"
    override val payoutIntegrityBroken     =
        "Some channel reservations belong to no month — this is a bug, please report it."
    override fun payoutIntegrityLine(total: Int, assigned: Int, excluded: Int, unaccounted: Int) =
        "$total channel reservations · $assigned assigned · $excluded excluded · $unaccounted unaccounted"
    override fun payoutMovedFrom(month: String) = "moved from $month"

    override val navInvoices              = "Invoices"
    override val invoicesTitle            = "Invoices"
    override val newInvoiceBtn            = "New Invoice"
    override val createInvoiceBtn         = "Create Invoice"
    override val noInvoices               = "No invoices yet."
    override val createInvoiceTitle       = "New Invoice"
    override val sellerSection            = "Seller"
    override val buyerSection             = "Buyer"
    override val sellerNameLabel          = "Seller name *"
    override val sellerAddressLabel       = "Address"
    override val nipLabel                 = "NIP"
    override val regonLabel               = "REGON"
    override val bankAccountLabel         = "Bank account number"
    override val buyerNameLabel           = "Buyer name *"
    override val buyerAddressLabel        = "Buyer address"
    override val invoiceIssueDateLabel    = "Issue date *"
    override val invoiceSaleDateLabel     = "Sale date *"
    override val invoiceDueDateLabel      = "Due date *"
    override val paymentMethodLabel       = "Payment method"
    override val paymentMethodTransfer    = "Bank transfer"
    override val paymentMethodCash        = "Cash"
    override val paymentMethodCard        = "Card"
    override val paymentMethodBlik        = "BLIK"
    override val itemsSection             = "Line items"
    override val addItemBtn               = "Add item"
    override val totalAmountLabel         = "Total"
    override val createAndDownloadBtn     = "Create & Download PDF"
    override val linkedReservationLabel    = "Link to reservation(s)"
    override val selectReservationOptional = "Select reservation (optional)"
    override val noLinkedReservation       = "No linked reservations"
    override val addReservationHint        = "+ Add reservation"
    override val differentGuestTitle       = "Different guest"
    override fun differentGuestWarning(incoming: String, existing: String) =
        "Reservation for \"$incoming\" differs from the current buyer \"$existing\". Add anyway?"
    override val addAnywayBtn              = "Add anyway"
    override val deleteInvoiceTitle       = "Delete invoice?"
    override val downloadPdfBtn           = "Download PDF"
    override val pdfSavedSuccess          = "PDF saved successfully"
    override val pdfSaveError             = "Failed to save PDF"
    override val invoiceNumberPreviewLabel = "Invoice number"
    override val editInvoiceTitle = "Edit Invoice"
    override val openInvoiceBtn = "Open"
    override fun invoiceAlreadyExistsFor(number: String) = "Reservation already has invoice $number"
    override fun nightsPersonsLine(nights: Int, guests: Double): String {
        val g = if (guests % 1.0 == 0.0) guests.toLong().toString() else "%.1f".format(guests)
        return "$nights night${if (nights != 1) "s" else ""} × $g person${if (guests != 1.0) "s" else ""}"
    }
    override fun priceTotalLine(total: Double) = "= ${"%.2f".format(total)} PLN total"
    override fun priceRuleLine(minNights: Int, maxNights: Int?) =
        "Rule: $minNights${maxNights?.let { "–$it" } ?: "+"} nights"
}

// ─── Polish ───────────────────────────────────────────────────────────────────

object PolishStrings : AppStrings {
    override val locale = Locale.forLanguageTag("pl")
    override val ok = "OK"
    override val cancel = "Anuluj"
    override val save = "Zapisz"
    override val add = "Dodaj"
    override val edit = "Edytuj"
    override val delete = "Usuń"
    override val close = "Zamknij"
    override val remove = "Usuń"
    override val retry = "Ponów"
    override val create = "Utwórz"
    override val select = "Wybierz"
    override val change = "Zmień"
    override fun errorMsg(msg: String) = "Błąd: $msg"
    override val appName = "Reserveo"
    override val navDashboard = "Panel"
    override val navReservations = "Rezerwacje"
    override val navConfig = "Konfiguracja"
    override val navSettings = "Ustawienia"
    override val themeDark = "Ciemny"
    override val themeLight = "Jasny"
    override val switchHotel = "Zmień hotel"
    override val logout = "Wyloguj"
    override val loginSubtitle = "Zaloguj się, aby kontynuować"
    override val loginUserLabel = "Email"
    override val loginNoUsers = "Brak użytkowników"
    override val loginEnter = "Zaloguj"
    override val loginPasswordLabel = "Hasło"
    override val loginInvalidCredentials = "Nieprawidłowy email lub hasło"
    override fun welcomeBack(name: String) = "Witaj, $name"
    override val selectHotelToManage = "Wybierz hotel do zarządzania"
    override val noHotelsAssigned = "Brak hoteli przypisanych do Twojego konta."
    override val settingsTitle = "Ustawienia"
    override val settingsAppearance = "Wygląd"
    override val settingsFontSize = "Rozmiar czcionki"
    override val settingsTimeline = "Oś czasu"
    override val settingsTimelineDisplay = "Wygląd osi czasu"
    override val settingsCenterViewRange = "Zakres widoku"
    override val settingsNoShowAfterDays = "Automatyczna nieobecność po"
    override val settingsAutoCheckOutAfterDays = "Automatyczne wymeldowanie po"
    override val settingsLanguage = "Język"
    override val settingsServer = "Serwer"
    override val settingsServerLocalhost = "Lokalny"
    override val settingsServerDeployment = "Wdrożenie"
    override val settingsServerCustom = "Własny"
    override val settingsServerCustomUrl = "URL serwera"
    override val configTitle = "Konfiguracja"
    override val configRoomsTitle = "Pokoje"
    override val configRoomsDesc = "Zarządzaj pokojami, statusami i dostępnością"
    override val configBasePriceTitle = "Ceny bazowe"
    override val configBasePriceDesc = "Ustaw reguły cenowe według pokoju, okresu i długości pobytu"
    override val configHolidaysTitle = "Święta"
    override val configHolidaysDesc = "Oznacz święta i przerwy szkolne na kalendarzu"
    override val configManage = "Zarządzaj →"
    override val noHolidays           = "Brak zdefiniowanych świąt"
    override val addHolidayBtn        = "Dodaj święto"
    override val addHolidayTitle      = "Dodaj święto"
    override val holidayNameLabel     = "Nazwa *"
    override val holidayNamePlaceholder = "Boże Narodzenie, Ferie zimowe…"
    override val importCsvBtn         = "Importuj z CSV"
    override fun importCsvResult(count: Int) = when {
        count == 1             -> "Zaimportowano 1 święto"
        count in 2..4          -> "Zaimportowano $count święta"
        else                   -> "Zaimportowano $count świąt"
    }
    override val breadcrumbConfig = "← Konfiguracja"
    override val roomsTitle = "Pokoje"
    override fun roomsStats(active: Int, archived: Int) = "$active aktywne · $archived zarchiwizowane"
    override val showArchived = "Pokaż zarchiwizowane"
    override val addRoomBtn = "Dodaj pokój"
    override val noRoomsYet = "Brak pokoi. Dodaj pierwszy pokój."
    override val allRoomsArchived = "Wszystkie pokoje są zarchiwizowane."
    override fun roomLabel(number: String) = "Pokój $number"
    override val archive = "Archiwizuj"
    override val unarchive = "Przywróć"
    override val archivedChip = "zarchiwizowany"
    override val addRoomTitle = "Dodaj pokój"
    override fun editRoomTitle(number: String) = "Edytuj pokój $number"
    override val roomTypeLabel = "Typ pokoju *"
    override val roomTypePlaceholder = "Jednoosobowy / Dwuosobowy / Suite …"
    override val numberLabel = "Numer *"
    override val floorLabel = "Piętro"
    override val maxGuestsLabel = "Maks. gości *"
    override val statusLabel = "Status"
    override val descriptionLabel = "Opis"
    override val tagsLabel = "Etykiety"
    override val tagInputHint = "np. balkon, widok na morze, wifi…"
    override val tagInputSupport = "Naciśnij Enter lub + aby dodać etykietę"
    override val basePriceTitle = "Ceny bazowe"
    override val basePriceSubtitle = "Wybierz pokój, aby zarządzać regułami cenowymi"
    override val addRoomsFirst = "Najpierw dodaj pokoje, aby ustawić reguły cenowe."
    override val noRules = "Brak reguł"
    override fun rulesCount(count: Int) = when {
        count == 1    -> "1 reguła"
        count in 2..4 -> "$count reguły"
        else          -> "$count reguł"
    }
    override val addRule = "Dodaj regułę"
    override val breadcrumbBasePrice = "← Ceny bazowe"
    override val roomFieldLabel = "Pokój *"
    override val selectRoomHint = "Wybierz pokój"
    override val fromLabel = "Od *"
    override val toLabel = "Do *"
    override val minNightsLabel = "Min. nocy *"
    override val maxNightsLabel = "Maks. nocy"
    override val blankNoLimit = "puste = bez limitu"
    override val priceLabel = "Cena / osoba / noc *"
    override val currencyLabel = "Waluta"
    override val generateRulesBtn        = "Generator reguł"
    override val ruleGeneratorTitle      = "Generator reguł"
    override val ruleGeneratorDateRange  = "Zakres dat (rok ignorowany)"
    override val ruleGeneratorFromYear   = "Od roku"
    override val ruleGeneratorToYear     = "Do roku"
    override val ruleGeneratorTiers      = "Progi noclegowe i ceny"
    override val ruleGeneratorAddTierFrom = "Dodaj próg od nocy"
    override val ruleGeneratorAddTierBtn  = "Dodaj próg"
    override val ruleGeneratorGenerate   = "Generuj reguły"
    override fun ruleGeneratorSuccess(count: Int) = when {
        count == 1             -> "Wygenerowano 1 regułę"
        count in 2..4          -> "Wygenerowano $count reguły"
        else                   -> "Wygenerowano $count reguł"
    }
    override fun lastRuleDate(date: String) = "Ostatnia istniejąca reguła kończy się: $date"
    override val arrivals = "Przyjazdy"
    override val departures = "Wyjazdy"
    override val notArrived = "Nie przyjechał"
    override val arrived = "Przyjechał"
    override val notDeparted = "Nie wyjechał"
    override val departed = "Wyjechał"
    override val noArrivalsToday = "Brak przyjazdów"
    override val noDeparturesToday = "Brak wyjazdów"
    override fun roomShort(number: String) = "Pokój $number"
    override val reservationsTitle = "Rezerwacje"
    override val viewCalendar = "Kalendarz"
    override val viewTimeline = "Oś czasu"
    override val blockRoomBtn = "Zablokuj pokój"
    override val blockModeLabel = "Tryb blokady"
    override val dragModeReservation = "Rezerwacja"
    override val dragModeExternal    = "Booking"
    override val dragModeBlock       = "Blokada"
    override val blockConflictError = "Zablokowane: pokój jest zablokowany w tych datach"
    override val newReservationBtn = "Nowa rezerwacja"
    override val newExternalBtn = "Nowa rezerwacja zewnętrzna"
    override val newExternalReservationTitle = "Nowa rezerwacja Booking.com"
    override val externalSourceLabel = "Zewnętrzna"
    override val bookingRefLabel = "Nr rezerwacji Booking"
    override val bookingTotalLabel = "Kwota (Booking)"
    override val dowLabels = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")
    override val conflictError = "Podwójna rezerwacja: pokój jest już zajęty w tych datach"
    override val viewOnlyBadge = "Tylko podgląd"
    override val blockedActionTitle = "Dostęp tylko do podglądu"
    override val blockedActionMsg = "Twoja rola w tym hotelu pozwala jedynie na podgląd. Nie możesz tu wprowadzać zmian."
    override fun serverError(status: Any) = "Błąd serwera: $status"
    override fun reservationDetailTitle(id: Int) = "Rezerwacja #$id"
    override val checkIn = "Zameldowanie"
    override val checkOut = "Wymeldowanie"
    override val nightsLabel = "Noce"
    override val roomDetailLabel = "Pokój"
    override val adultsLabel = "Dorośli"
    override val downPmtLabel = "Zadatek"
    override val plnRequired = "PLN wymagane"
    override val totalLabel = "Suma"
    override val paidLabel = "Zapłacono"
    override val remainingLabel = "Pozostało"
    override val notesLabel = "Notatki"
    override val notesPlaceholder = "Dodaj notatki do tej rezerwacji…"
    override val saveNotesBtn = "Zapisz notatki"
    override val editNoteBtn = "Edytuj notatkę"
    override val editReservationBtn = "Edytuj rezerwację"
    override val editGuestBtn = "Edytuj gościa"
    override val managePaymentsBtn = "Zarządzaj płatnościami"
    override val blacklistedLabel = "Na czarnej liście"
    override val blacklistedWarning = "⛔ Ten gość jest na czarnej liście"
    override val guestNotesLabel = "Notatka / powód czarnej listy"
    override val guestNotesPlaceholder = "Powód lub ogólna notatka o gościu…"
    override val saveGuestBtn = "Zapisz gościa"
    override fun paymentsTitle(id: Int) = "Płatności · Rezerwacja #$id"
    override val noPayments = "Brak zarejestrowanych płatności."
    override val typeCol = "Typ"
    override val amountCol = "Kwota"
    override val dateCol = "Data"
    override val docCol = "Dok."
    override val downPaymentName = "Zadatek"
    override val paymentName = "Płatność"
    override fun downPaymentNeeded(amount: String) = "Wymagany zadatek: $amount PLN"
    override val addPaymentSection = "Dodaj płatność"
    override val amountFieldLabel = "Kwota (PLN) *"
    override val dateFieldLabel = "Data"
    override val pick = "Wybierz"
    override val nothing = "Brak"
    override val receiptLabel = "Paragon"
    override val invoiceLabel = "Faktura"
    override val receiptNumberLabel = "Numer paragonu"
    override val invoiceNumberLabel = "Numer faktury"
    override fun docLabel(receiptType: String?, receiptNumber: String?) = when (receiptType) {
        "receipt" -> "P: ${receiptNumber ?: "—"}"
        "invoice" -> "F: ${receiptNumber ?: "—"}"
        else      -> "—"
    }
    override val customInvoiceNumberLabel = "Użyj własnego numeru faktury"
    override val noInvoiceForReservation  = "Nie utworzono jeszcze faktury dla tej rezerwacji"
    override val viewInvoiceBtn           = "Otwórz fakturę"
    override val addPaymentBtn = "Dodaj płatność"
    override val typeRow = "Typ"
    override val amountRow = "Kwota"
    override val dateRow = "Data"
    override val docTypeRow = "Typ dok."
    override val docNumberRow = "Nr dok."
    override val newReservationTitle = "Nowa rezerwacja"
    override fun editReservationTitle(id: Int) = "Edytuj rezerwację #$id"
    override val checkInLabel = "Zameldowanie *"
    override val checkOutLabel = "Wymeldowanie *"
    override val roomAlreadyReserved = "Pokój jest już zajęty w tych datach"
    override val requiresDownPayment = "Wymagany zadatek"
    override val downPaymentAmountLabel = "Kwota zadatku (PLN)"
    override val plnPerPersonPerNight = "PLN / osoba / noc"
    override val noMatchingRule = "Brak pasującej reguły"
    override fun deleteReservationTitle(id: Int) = "Usunąć rezerwację #$id?"
    override val cannotBeUndone = "Tej operacji nie można cofnąć."
    override val firstNameLabel = "Imię"
    override val lastNameLabel = "Nazwisko *"
    override val codeLabel = "Kod"
    override val phoneNumberLabel = "Numer telefonu"
    override val nationalityLabel = "Narodowość"
    override val didYouMean = "Czy chodziło o?"
    override val scaleCenter = "Środek"
    override val scaleMonth = "Miesiąc"
    override val scaleYear = "Rok"
    override val today = "Dziś"
    override val widthLabel = "Szer.:"
    override val heightLabel = "Wys.:"
    override val labelWidthLabel = "Etyk.:"
    override val showRoomTypeLabel = "Typ"
    override val hideRoomTypeLabel = "Bez typu"
    override val fullDay = "Pełny dzień"
    override val halfShiftLabel = "Pół dnia"
    override val hideCancelled = "Ukryj anulowane"
    override val showCancelled = "Pokaż anulowane"
    override val blocked = "Zablokowany"
    override val roomAbbr = "Pk."
    override val nightsAbbr = "n."
    override fun adultsStr(count: Double): String {
        val n = if (count % 1.0 == 0.0) count.toLong().toString() else "%.1f".format(count)
        return "$n dorosłych"
    }
    override val blockRoomTitle = "Zablokuj pokój"
    override val blockAllRoomsLabel = "Zablokuj wszystkie pokoje"
    override val allRoomsLabel = "Wszystkie pokoje"
    override fun blockReservationConflict(rooms: String) = "Nie można zablokować — aktywne rezerwacje w: $rooms"
    override val reasonLabel = "Powód"
    override val reasonPlaceholder = "Konserwacja, sprzątanie…"
    override val blockBtn = "Zablokuj"
    override val removeBlockTitle = "Usuń blokadę"
    override fun removeBlockConfirm(roomNumber: String, fromDate: String, toDate: String, reason: String?) =
        "Usunąć blokadę pokoju $roomNumber od $fromDate do $toDate${reason?.let { " ($it)" } ?: ""}?"
    override fun statusName(status: String) = when (status) {
        "pending"     -> "Oczekujące"
        "confirmed"   -> "Potwierdzone"
        "checked_in"  -> "Zameldowany"
        "checked_out" -> "Wymeldowany"
        "cancelled"   -> "Anulowane"
        "no_show"     -> "Nieobecność"
        else          -> status.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
    }
    override val adjustPriceBtn              = "Koryguj cenę"
    override val priceAdjustmentsTitle       = "Korekty ceny"
    override val noAdjustments               = "Brak korekt."
    override val adjustmentAmountLabel       = "Kwota (PLN, ujemna = rabat)"
    override val adjustmentDescriptionLabel  = "Opis"
    override val addAdjustmentBtn            = "Dodaj korektę"
    override val segmentsBaseTotal           = "Podstawa (segmenty)"
    override val adjustmentsTotal            = "Korekty"
    override val effectiveTotalLabel         = "Łączna kwota"
    override val statCheckedIn    = "Zameldowani"
    override val statOccupancy    = "obłożenie"
    override val statMonthRevenue = "Zebrany przychód"
    override val statUpcoming7d   = "Przyjazdy · Następne 7 dni"
    override val statPendingDp    = "Zaległe zadatki"
    override val statsTitle             = "Statystyki"
    override val statsNightsTable       = "Noce · Pokój · Miesiąc"
    override val statsHistogram         = "Rozkład długości pobytów"
    override val statsGroupAll          = "Wszystkie"
    override val statsGroupByType       = "Wg. typu"
    override val statsGroupOneRoom      = "Jeden pokój"
    override val statsTimeSpan          = "Okres:"
    override val statsNoData            = "Brak danych"
    override val statsNightsAxisLabel   = "nocy"
    override fun statsMonthsLabel(n: Int) = "${n}M"
    override val statsKpiOccupancy      = "Obłożenie"
    override val statsKpiRevenue        = "Przychód"
    override val statsKpiAdr            = "ADR"
    override val statsKpiRevpar         = "RevPAR"
    override val statsKpiAvgStay        = "Śr. długość pobytu"
    override val statsKpiCancelRate     = "Odwołania/no-show"
    override val statsBySource          = "Rezerwacje wg źródła"
    override val overdueCheckIns   = "Zaległe zameldowania"
    override val noOverdueCheckIns = "Brak zaległych zameldowań"
    override val overdueCheckOuts   = "Zaległe wymeldowania"
    override val noOverdueCheckOuts = "Brak zaległych wymeldowań"
    override val configInvoiceTitle        = "Ustawienia faktur"
    override val configInvoiceDesc        = "Skonfiguruj domyślne dane sprzedawcy do faktur"
    override val invoiceConfigTitle       = "Ustawienia faktur"
    override val invoiceConfigSubtitle    = "Domyślne dane sprzedawcy używane przy wystawianiu faktur"
    override val defaultDueDaysLabel      = "Domyślny termin płatności (dni)"
    override val savedLabel               = "Zapisano"
    override val navPayouts               = "Wypłaty"
    override val payoutsTitle             = "Wypłaty z kanałów"
    override val payoutsSubtitle          = "Pieniądze faktycznie otrzymane z Booking.com, miesiąc po miesiącu"
    override val payoutsEmpty             = "Brak rezerwacji z kanałów."
    override val payoutBooked             = "Zarezerwowano"
    override val payoutReceived           = "Otrzymano"
    override val payoutCommission         = "Prowizja"
    override val payoutCommissionEst      = "Szac. prowizja"
    override val payoutNotSettled         = "Oczekuje na wypłatę"
    override val payoutSettled            = "Rozliczone"
    override val payoutRecordBtn          = "Zapisz wypłatę"
    override val payoutEditBtn            = "Edytuj wypłatę"
    override val payoutAddTitle           = "Zapisz miesięczną wypłatę"
    override val payoutEditTitle          = "Edytuj miesięczną wypłatę"
    override val payoutAmountLabel        = "Otrzymana kwota"
    override val payoutNotesLabel         = "Notatki"
    override val payoutMonthLabel         = "Miesiąc"
    override val payoutYearLabel          = "Rok"
    override val payoutNoReservations     = "Brak rezerwacji rozliczanych w tym miesiącu."
    override val payoutEstimateDisclaimer =
        "Booking podaje tylko sumę miesięczną, więc prowizja na rezerwację to uśredniona stawka miesiąca rozłożona proporcjonalnie — szacunek, a nie kwota z pojedynczej rezerwacji."
    override val payoutAnomalyWarning     =
        "Ta stawka odbiega od normy — sprawdź kwotę albo czy w którejś rezerwacji nie brakuje ceny."
    override val payoutAttributionNote    =
        "Booking wypłaca w każdy czwartek, więc pobyt rozlicza pierwszy czwartek po wymeldowaniu. Pobyt kończący się 28.06 jest wypłacany 02.07 i liczy się do lipca."
    override val payoutSettledByMonth     = "Rozliczane przez"
    override val payoutDeleteConfirm      = "Usunąć tę wypłatę?"
    override val payoutOverallRate        = "Prowizja łącznie"
    override fun payoutReservationCount(n: Int) = when {
        n == 1 -> "1 rezerwacja"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n rezerwacje"
        else -> "$n rezerwacji"
    }
    override fun payoutPaidOn(date: String) = "Wypłacono $date"
    override fun payoutSettledIn(month: String) = "Rozliczane w: $month"

    override val payoutMoveTo              = "Przenieś do"
    override val payoutOverridden          = "Przypisane ręcznie"
    override val payoutDerived             = "Auto"
    override val payoutResetToDerived      = "Przywróć automatyczne"
    override val payoutExcludeBtn          = "Wyklucz"
    override val payoutExcludedSection     = "Wykluczone ze wszystkich wypłat"
    override val payoutExcludeTitle        = "Wyklucz z wypłat"
    override val payoutExcludeReasonLabel  = "Powód"
    override val payoutExcludeExplain      =
        "Ta rezerwacja nie będzie liczona do żadnego miesiąca. Używaj tylko wtedy, gdy kanał faktycznie nigdy za nią nie zapłacił."
    override val payoutRestoreBtn          = "Przywróć"
    override val payoutIntegrityLabel      = "Rozliczenie kompletne"
    override val payoutIntegrityBroken     =
        "Część rezerwacji z kanałów nie należy do żadnego miesiąca — to błąd, zgłoś go."
    override fun payoutIntegrityLine(total: Int, assigned: Int, excluded: Int, unaccounted: Int) =
        "$total z kanałów · $assigned przypisane · $excluded wykluczone · $unaccounted nierozliczone"
    override fun payoutMovedFrom(month: String) = "przeniesione z: $month"

    override val navInvoices              = "Faktury"
    override val invoicesTitle            = "Faktury"
    override val newInvoiceBtn            = "Nowa faktura"
    override val createInvoiceBtn         = "Wystaw fakturę"
    override val noInvoices               = "Brak faktur."
    override val createInvoiceTitle       = "Nowa faktura"
    override val sellerSection            = "Sprzedawca"
    override val buyerSection             = "Nabywca"
    override val sellerNameLabel          = "Nazwa sprzedawcy *"
    override val sellerAddressLabel       = "Adres"
    override val nipLabel                 = "NIP"
    override val regonLabel               = "REGON"
    override val bankAccountLabel         = "Nr rachunku bankowego"
    override val buyerNameLabel           = "Nazwa nabywcy *"
    override val buyerAddressLabel        = "Adres nabywcy"
    override val invoiceIssueDateLabel    = "Data wystawienia *"
    override val invoiceSaleDateLabel     = "Data sprzedaży *"
    override val invoiceDueDateLabel      = "Termin płatności *"
    override val paymentMethodLabel       = "Sposób płatności"
    override val paymentMethodTransfer    = "Przelew"
    override val paymentMethodCash        = "Gotówka"
    override val paymentMethodCard        = "Karta"
    override val paymentMethodBlik        = "BLIK"
    override val itemsSection             = "Pozycje faktury"
    override val addItemBtn               = "Dodaj pozycję"
    override val totalAmountLabel         = "Razem"
    override val createAndDownloadBtn     = "Utwórz i pobierz PDF"
    override val linkedReservationLabel    = "Powiąż z rezerwacjami"
    override val selectReservationOptional = "Wybierz rezerwację (opcjonalnie)"
    override val noLinkedReservation       = "Brak powiązanych rezerwacji"
    override val addReservationHint        = "+ Dodaj rezerwację"
    override val differentGuestTitle       = "Inny gość"
    override fun differentGuestWarning(incoming: String, existing: String) =
        "Rezerwacja dla \"$incoming\" dotyczy innego gościa niż obecny nabywca \"$existing\". Dodać mimo to?"
    override val addAnywayBtn              = "Dodaj mimo to"
    override val deleteInvoiceTitle       = "Usunąć fakturę?"
    override val downloadPdfBtn           = "Pobierz PDF"
    override val pdfSavedSuccess          = "PDF zapisany pomyślnie"
    override val pdfSaveError             = "Błąd zapisu PDF"
    override val invoiceNumberPreviewLabel = "Numer faktury"
    override val editInvoiceTitle = "Edytuj fakturę"
    override val openInvoiceBtn = "Otwórz"
    override fun invoiceAlreadyExistsFor(number: String) = "Rezerwacja ma już fakturę $number"
    override fun nightsPersonsLine(nights: Int, guests: Double): String {
        val g = if (guests % 1.0 == 0.0) guests.toLong().toString() else "%.1f".format(guests)
        val nightsStr = when {
            nights == 1                                          -> "1 noc"
            nights % 10 in 2..4 && nights % 100 !in 12..14     -> "$nights noce"
            else                                                 -> "$nights nocy"
        }
        val guestsStr = when {
            guests == 1.0                                                        -> "1 osoba"
            guests % 1.0 != 0.0 || guests.toLong() % 10 in 2L..4L && guests.toLong() % 100 !in 12L..14L -> "$g osoby"
            else                                                                 -> "$g osób"
        }
        return "$nightsStr × $guestsStr"
    }
    override fun priceTotalLine(total: Double) = "= ${"%.2f".format(total)} PLN łącznie"
    override fun priceRuleLine(minNights: Int, maxNights: Int?) =
        "Reguła: $minNights${maxNights?.let { "–$it" } ?: "+"} nocy"
}

// ─── Language enum + CompositionLocal ─────────────────────────────────────────

enum class AppLanguage(val strings: AppStrings, val displayName: String) {
    English(EnglishStrings, "English"),
    Polish(PolishStrings, "Polski")
}

val LocalStrings = compositionLocalOf<AppStrings> { EnglishStrings }
