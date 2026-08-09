package org.julsz.smnt

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Attribution of channel (Booking.com) reservations to the monthly payout that settled them.
 *
 * Booking does not pay per reservation or per month — it transfers every Thursday, bundling
 * whatever became payable since the last run. A reservation becomes payable at check-out, so
 * it is settled by the first Thursday *strictly after* its check-out date (a check-out that
 * falls on a Thursday waits a week). The "August payout" the user enters is simply the sum of
 * the Thursday transfers that landed in August, as reported by Booking's own app.
 *
 * The practical consequence: a stay of 26–28.06 is paid on 02.07 and belongs to **July**.
 * Every month has 0–6 days of trailing check-outs that spill into the next month this way, so
 * attributing by check-out month instead would inflate one month's booked total while
 * deflating the next — the error does not cancel out within a month, only across a full year.
 *
 * Attribution is derived rather than stored so it stays correct if a reservation's dates are
 * edited after its payout was recorded.
 */
object PayoutAttribution {

    /** Statuses that never earn channel revenue. `no_show` is kept: the guest didn't come but the money did. */
    private val NON_EARNING_STATUSES = setOf("cancelled")

    /** The date Booking transfers the money for a stay ending on [checkOutDate]. */
    fun payoutDate(checkOutDate: LocalDate): LocalDate =
        checkOutDate.with(TemporalAdjusters.next(DayOfWeek.THURSDAY))

    /** The calendar month whose payout total includes a stay ending on [checkOutDate]. */
    fun payoutMonth(checkOutDate: LocalDate): YearMonth =
        YearMonth.from(payoutDate(checkOutDate))

    fun payoutMonth(reservation: ReservationDto): YearMonth =
        payoutMonth(LocalDate.parse(reservation.checkOutDate))

    /** True for reservations settled through a channel rather than paid directly by the guest. */
    fun isChannelReservation(reservation: ReservationDto): Boolean =
        reservation.source == "external"

    /** Channel reservations that count toward a month's booked total. */
    fun settledBy(
        reservations: List<ReservationDto>,
        month: YearMonth,
        overrides: PayoutOverrides = PayoutOverrides.EMPTY
    ): List<ReservationDto> =
        attributable(reservations).filter { overrides.effectiveMonth(it) == month }

    /** Groups every channel reservation by the month that pays for it. */
    fun groupByPayoutMonth(
        reservations: List<ReservationDto>,
        overrides: PayoutOverrides = PayoutOverrides.EMPTY
    ): Map<YearMonth, List<ReservationDto>> =
        attributable(reservations)
            .mapNotNull { res -> overrides.effectiveMonth(res)?.let { it to res } }
            .groupBy({ it.first }, { it.second })

    /** Channel reservations eligible for attribution at all, before any override is applied. */
    fun attributable(reservations: List<ReservationDto>): List<ReservationDto> =
        reservations.filter { isChannelReservation(it) && it.status !in NON_EARNING_STATUSES }
}

/**
 * Manual corrections layered over derived attribution.
 *
 * An override is a *reassignment*, never a removal — [effectiveMonth] returns null only for a
 * reservation deliberately marked excluded. There is no way to detach a reservation from every
 * month by accident, which is what keeps the month set a true partition and the annual totals
 * trustworthy no matter how much hand-editing happens.
 */
class PayoutOverrides(private val byReservationId: Map<Int, ChannelPayoutOverrideDto>) {

    companion object {
        val EMPTY = PayoutOverrides(emptyMap())

        fun from(overrides: List<ChannelPayoutOverrideDto>) =
            PayoutOverrides(overrides.associateBy { it.reservationId })
    }

    fun forReservation(reservation: ReservationDto): ChannelPayoutOverrideDto? =
        byReservationId[reservation.id]

    fun isOverridden(reservation: ReservationDto): Boolean =
        byReservationId.containsKey(reservation.id)

    fun isExcluded(reservation: ReservationDto): Boolean =
        byReservationId[reservation.id]?.excluded == true

    /** The month that settles this reservation, or null if it is explicitly excluded. */
    fun effectiveMonth(reservation: ReservationDto): YearMonth? {
        val override = byReservationId[reservation.id]
            ?: return PayoutAttribution.payoutMonth(reservation)
        if (override.excluded) return null
        val year = override.year
        val month = override.month
        // A non-excluding override without a target is rejected by both the API and the DB
        // constraint; if one ever appears, fall back to derivation rather than losing the row.
        return if (year != null && month != null) YearMonth.of(year, month)
               else PayoutAttribution.payoutMonth(reservation)
    }
}

/**
 * Proof that every channel reservation is accounted for. [unaccounted] must always be zero —
 * it exists so that a bug in the override layer is visible in the UI instead of quietly
 * shrinking a month's booked total.
 */
data class PayoutIntegrity(
    val total: Int,
    val assigned: Int,
    val excluded: Int
) {
    val unaccounted: Int = total - assigned - excluded
    val isSound: Boolean get() = unaccounted == 0
}

fun computePayoutIntegrity(
    reservations: List<ReservationDto>,
    overrides: PayoutOverrides = PayoutOverrides.EMPTY
): PayoutIntegrity {
    val attributable = PayoutAttribution.attributable(reservations)
    val excluded = attributable.count { overrides.effectiveMonth(it) == null }
    return PayoutIntegrity(
        total    = attributable.size,
        assigned = attributable.size - excluded,
        excluded = excluded
    )
}

/** Channel reservations deliberately excluded from every payout month. */
fun excludedReservations(
    reservations: List<ReservationDto>,
    overrides: PayoutOverrides
): List<ReservationDto> =
    PayoutAttribution.attributable(reservations).filter { overrides.effectiveMonth(it) == null }

/**
 * A month's reconciliation between what guests were charged and what the channel actually paid.
 *
 * [received] is null until the user records that month's payout — the reservations are still
 * attributed, they're simply not settled yet.
 */
data class PayoutMonthSummary(
    val month: YearMonth,
    val reservations: List<ReservationDto>,
    val received: Double?,
    val currency: String = "PLN",
    val payoutId: Int? = null,
    val notes: String? = null
) {
    /** What the channel told us the guests would pay, for the reservations this month settles. */
    val booked: Double = reservations.sumOf { it.totalAmount ?: 0.0 }

    val isSettled: Boolean get() = received != null

    /** Money the channel kept. Null until the payout is recorded. */
    val commission: Double? = received?.let { booked - it }

    /**
     * Blended commission rate for the month, 0.0–1.0. Null when unsettled or when nothing was
     * booked (a payout against a zero booked total says nothing about a rate).
     */
    val commissionRate: Double? =
        if (received == null || booked <= 0.0) null else (booked - received) / booked

    /**
     * Per-reservation commission is **not observable** — Booking reports only the monthly sum,
     * so the best available figure is this month's blended rate applied pro-rata. Useful for
     * spotting outlier months, not for auditing an individual booking.
     */
    fun estimatedCommissionFor(reservation: ReservationDto): Double? {
        val rate = commissionRate ?: return null
        val total = reservation.totalAmount ?: return null
        return total * rate
    }

    /** True when the payout doesn't plausibly reflect a commission — worth flagging in the UI. */
    val looksAnomalous: Boolean
        get() = commissionRate?.let { it < 0.0 || it > 0.5 } ?: false
}

/**
 * Builds one summary per month that has either channel reservations or a recorded payout,
 * newest first. Months with reservations but no payout appear as unsettled.
 */
fun buildPayoutSummaries(
    reservations: List<ReservationDto>,
    payouts: List<ChannelPayoutDto>,
    overrides: PayoutOverrides = PayoutOverrides.EMPTY
): List<PayoutMonthSummary> {
    val byMonth = PayoutAttribution.groupByPayoutMonth(reservations, overrides)
    val payoutByMonth = payouts.associateBy { YearMonth.of(it.year, it.month) }

    return (byMonth.keys + payoutByMonth.keys)
        .distinct()
        .sortedDescending()
        .map { month ->
            val payout = payoutByMonth[month]
            PayoutMonthSummary(
                month        = month,
                reservations = byMonth[month].orEmpty().sortedBy { it.checkOutDate },
                received     = payout?.amount,
                currency     = payout?.currency ?: "PLN",
                payoutId     = payout?.id,
                notes        = payout?.notes
            )
        }
}
