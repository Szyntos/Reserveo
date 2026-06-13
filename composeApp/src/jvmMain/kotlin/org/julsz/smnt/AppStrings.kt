package org.julsz.smnt

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

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

    // Hotel picker
    fun welcomeBack(name: String): String
    val selectHotelToManage: String
    val noHotelsAssigned: String

    // Settings page
    val settingsTitle: String
    val settingsAppearance: String
    val settingsFontSize: String
    val settingsTimeline: String
    val settingsCenterViewRange: String
    val settingsNoShowAfterDays: String
    val settingsAutoCheckOutAfterDays: String
    val settingsLanguage: String

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

    // Dashboard — overdue panes
    val overdueCheckIns: String
    val noOverdueCheckIns: String
    val overdueCheckOuts: String
    val noOverdueCheckOuts: String
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
    override val loginSubtitle = "Select your account to continue"
    override val loginUserLabel = "User"
    override val loginNoUsers = "No users found"
    override val loginEnter = "Enter"
    override fun welcomeBack(name: String) = "Welcome back, $name"
    override val selectHotelToManage = "Select a hotel to manage"
    override val noHotelsAssigned = "No hotels assigned to your account."
    override val settingsTitle = "Settings"
    override val settingsAppearance = "Appearance"
    override val settingsFontSize = "Font size"
    override val settingsTimeline = "Timeline"
    override val settingsCenterViewRange = "Center view range"
    override val settingsNoShowAfterDays = "Auto no-show after"
    override val settingsAutoCheckOutAfterDays = "Auto check-out after"
    override val settingsLanguage = "Language"
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
    override val firstNameLabel = "First name *"
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
    override val overdueCheckIns   = "Overdue Check-ins"
    override val noOverdueCheckIns = "No overdue check-ins"
    override val overdueCheckOuts   = "Overdue Check-outs"
    override val noOverdueCheckOuts = "No overdue check-outs"
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
    override val loginSubtitle = "Wybierz konto, aby kontynuować"
    override val loginUserLabel = "Użytkownik"
    override val loginNoUsers = "Brak użytkowników"
    override val loginEnter = "Zaloguj"
    override fun welcomeBack(name: String) = "Witaj, $name"
    override val selectHotelToManage = "Wybierz hotel do zarządzania"
    override val noHotelsAssigned = "Brak hoteli przypisanych do Twojego konta."
    override val settingsTitle = "Ustawienia"
    override val settingsAppearance = "Wygląd"
    override val settingsFontSize = "Rozmiar czcionki"
    override val settingsTimeline = "Oś czasu"
    override val settingsCenterViewRange = "Zakres widoku"
    override val settingsNoShowAfterDays = "Automatyczna nieobecność po"
    override val settingsAutoCheckOutAfterDays = "Automatyczne wymeldowanie po"
    override val settingsLanguage = "Język"
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
    override val firstNameLabel = "Imię *"
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
    override val overdueCheckIns   = "Zaległe zameldowania"
    override val noOverdueCheckIns = "Brak zaległych zameldowań"
    override val overdueCheckOuts   = "Zaległe wymeldowania"
    override val noOverdueCheckOuts = "Brak zaległych wymeldowań"
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
