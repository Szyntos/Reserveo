package org.julsz.smnt

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayoutAttributionTest {

    private fun reservation(
        id: Int = 1,
        checkIn: String,
        checkOut: String,
        total: Double? = 1000.0,
        source: String = "external",
        status: String = "checked_out"
    ) = ReservationDto(
        id = id, hotelId = 1, roomId = 1, guestId = 1,
        hotelName = "H", roomNumber = "101", guestName = "G",
        checkInDate = checkIn, checkOutDate = checkOut,
        status = status, adults = 2.0, totalAmount = total,
        source = source
    )

    // ─── The Thursday rule ────────────────────────────────────────────────────

    @Test
    fun `stay ending Sunday is paid the following Thursday`() {
        // 28.06.2026 is a Sunday; the next Thursday is 02.07.2026.
        assertEquals(LocalDate.of(2026, 7, 2), PayoutAttribution.payoutDate(LocalDate.of(2026, 6, 28)))
    }

    @Test
    fun `late June checkout spills into July's payout`() {
        assertEquals(YearMonth.of(2026, 7), PayoutAttribution.payoutMonth(LocalDate.of(2026, 6, 28)))
    }

    @Test
    fun `checkout on a Thursday waits a full week`() {
        // 02.07.2026 is itself a Thursday — payment is strictly after, so 09.07.
        assertEquals(LocalDate.of(2026, 7, 9), PayoutAttribution.payoutDate(LocalDate.of(2026, 7, 2)))
    }

    @Test
    fun `checkout on Wednesday is paid the very next day`() {
        // 01.07.2026 is a Wednesday.
        assertEquals(LocalDate.of(2026, 7, 2), PayoutAttribution.payoutDate(LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun `year boundary rolls into January`() {
        // 30.12.2026 is a Wednesday; next Thursday is 31.12.2026 — still December.
        assertEquals(YearMonth.of(2026, 12), PayoutAttribution.payoutMonth(LocalDate.of(2026, 12, 30)))
        // 31.12.2026 is a Thursday, so it waits until 07.01.2027 — January.
        assertEquals(YearMonth.of(2027, 1), PayoutAttribution.payoutMonth(LocalDate.of(2026, 12, 31)))
    }

    // ─── Eligibility ──────────────────────────────────────────────────────────

    @Test
    fun `direct reservations are not channel reservations`() {
        assertTrue(PayoutAttribution.isChannelReservation(reservation(checkIn = "2026-06-26", checkOut = "2026-06-28")))
        assertTrue(
            !PayoutAttribution.isChannelReservation(
                reservation(checkIn = "2026-06-26", checkOut = "2026-06-28", source = "private")
            )
        )
    }

    @Test
    fun `cancelled reservations are excluded but no_show is kept`() {
        val month = YearMonth.of(2026, 7)
        val list = listOf(
            reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28", status = "cancelled"),
            reservation(id = 2, checkIn = "2026-06-26", checkOut = "2026-06-28", status = "no_show"),
            reservation(id = 3, checkIn = "2026-06-26", checkOut = "2026-06-28", status = "checked_out")
        )
        assertEquals(listOf(2, 3), PayoutAttribution.settledBy(list, month).map { it.id })
    }

    @Test
    fun `direct reservations never enter a payout month`() {
        val list = listOf(reservation(checkIn = "2026-06-26", checkOut = "2026-06-28", source = "private"))
        assertTrue(PayoutAttribution.settledBy(list, YearMonth.of(2026, 7)).isEmpty())
    }

    // ─── Commission arithmetic ────────────────────────────────────────────────

    @Test
    fun `commission is booked minus received`() {
        val summary = PayoutMonthSummary(
            month = YearMonth.of(2026, 7),
            reservations = listOf(
                reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28", total = 600.0),
                reservation(id = 2, checkIn = "2026-06-29", checkOut = "2026-07-01", total = 400.0)
            ),
            received = 850.0
        )
        assertEquals(1000.0, summary.booked)
        assertEquals(150.0, summary.commission)
        assertEquals(0.15, summary.commissionRate!!, 1e-9)
    }

    @Test
    fun `per-reservation estimate is pro-rata on the blended rate`() {
        val big = reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28", total = 600.0)
        val small = reservation(id = 2, checkIn = "2026-06-29", checkOut = "2026-07-01", total = 400.0)
        val summary = PayoutMonthSummary(YearMonth.of(2026, 7), listOf(big, small), received = 850.0)

        assertEquals(90.0, summary.estimatedCommissionFor(big)!!, 1e-9)
        assertEquals(60.0, summary.estimatedCommissionFor(small)!!, 1e-9)
    }

    @Test
    fun `unsettled month exposes no commission`() {
        val summary = PayoutMonthSummary(
            month = YearMonth.of(2026, 7),
            reservations = listOf(reservation(checkIn = "2026-06-26", checkOut = "2026-06-28")),
            received = null
        )
        assertNull(summary.commission)
        assertNull(summary.commissionRate)
        assertTrue(!summary.isSettled)
    }

    @Test
    fun `payout against zero booked total yields no rate`() {
        val summary = PayoutMonthSummary(YearMonth.of(2026, 7), emptyList(), received = 500.0)
        assertNull(summary.commissionRate)
    }

    @Test
    fun `received above booked flags an anomaly`() {
        val summary = PayoutMonthSummary(
            month = YearMonth.of(2026, 7),
            reservations = listOf(reservation(checkIn = "2026-06-26", checkOut = "2026-06-28", total = 500.0)),
            received = 700.0
        )
        assertTrue(summary.looksAnomalous)
    }

    // ─── Summary building ─────────────────────────────────────────────────────

    @Test
    fun `months with reservations but no payout appear unsettled`() {
        val summaries = buildPayoutSummaries(
            reservations = listOf(reservation(checkIn = "2026-06-26", checkOut = "2026-06-28")),
            payouts = emptyList()
        )
        assertEquals(1, summaries.size)
        assertEquals(YearMonth.of(2026, 7), summaries.first().month)
        assertTrue(!summaries.first().isSettled)
    }

    @Test
    fun `a payout with no reservations still shows up`() {
        val summaries = buildPayoutSummaries(
            reservations = emptyList(),
            payouts = listOf(
                ChannelPayoutDto(1, hotelId = 1, year = 2026, month = 7, amount = 100.0,
                    currency = "PLN", createdAt = "2026-08-01T00:00")
            )
        )
        assertEquals(1, summaries.size)
        assertTrue(summaries.first().isSettled)
        assertEquals(0.0, summaries.first().booked)
    }

    // ─── Overrides ────────────────────────────────────────────────────────────

    private fun override(
        reservationId: Int,
        year: Int? = null,
        month: Int? = null,
        excluded: Boolean = false,
        reason: String? = null
    ) = ChannelPayoutOverrideDto(
        reservationId = reservationId, year = year, month = month,
        excluded = excluded, reason = reason, createdAt = "2026-08-01T00:00"
    )

    @Test
    fun `an override moves a reservation to another month`() {
        val res = reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28")
        val overrides = PayoutOverrides.from(listOf(override(1, year = 2026, month = 6)))

        // Derived would be July; the override says June.
        assertEquals(YearMonth.of(2026, 7), PayoutAttribution.payoutMonth(res))
        assertEquals(YearMonth.of(2026, 6), overrides.effectiveMonth(res))
    }

    @Test
    fun `an overridden reservation leaves its derived month`() {
        val res = reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28")
        val overrides = PayoutOverrides.from(listOf(override(1, year = 2026, month = 6)))

        assertTrue(PayoutAttribution.settledBy(listOf(res), YearMonth.of(2026, 7), overrides).isEmpty())
        assertEquals(1, PayoutAttribution.settledBy(listOf(res), YearMonth.of(2026, 6), overrides).size)
    }

    @Test
    fun `reservations without an override stay derived`() {
        val moved = reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28")
        val untouched = reservation(id = 2, checkIn = "2026-06-26", checkOut = "2026-06-28")
        val overrides = PayoutOverrides.from(listOf(override(1, year = 2026, month = 6)))

        assertEquals(YearMonth.of(2026, 7), overrides.effectiveMonth(untouched))
        assertTrue(!overrides.isOverridden(untouched))
        assertTrue(overrides.isOverridden(moved))
    }

    @Test
    fun `excluded reservations belong to no month`() {
        val res = reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28")
        val overrides = PayoutOverrides.from(listOf(override(1, excluded = true, reason = "never paid")))

        assertNull(overrides.effectiveMonth(res))
        assertTrue(overrides.isExcluded(res))
        assertTrue(PayoutAttribution.groupByPayoutMonth(listOf(res), overrides).isEmpty())
        assertEquals(listOf(1), excludedReservations(listOf(res), overrides).map { it.id })
    }

    @Test
    fun `moving a reservation shifts booked totals between months`() {
        val stays = listOf(
            reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28", total = 600.0),
            reservation(id = 2, checkIn = "2026-06-29", checkOut = "2026-07-01", total = 400.0)
        )
        val derived = buildPayoutSummaries(stays, emptyList())
        assertEquals(1000.0, derived.single { it.month == YearMonth.of(2026, 7) }.booked)

        val overrides = PayoutOverrides.from(listOf(override(1, year = 2026, month = 6)))
        val corrected = buildPayoutSummaries(stays, emptyList(), overrides)
        assertEquals(600.0, corrected.single { it.month == YearMonth.of(2026, 6) }.booked)
        assertEquals(400.0, corrected.single { it.month == YearMonth.of(2026, 7) }.booked)
    }

    // ─── The partition invariant ──────────────────────────────────────────────

    @Test
    fun `every channel reservation is accounted for when untouched`() {
        val stays = (1..5).map {
            reservation(id = it, checkIn = "2026-06-26", checkOut = "2026-06-28")
        }
        val integrity = computePayoutIntegrity(stays)
        assertEquals(5, integrity.total)
        assertEquals(5, integrity.assigned)
        assertEquals(0, integrity.excluded)
        assertEquals(0, integrity.unaccounted)
        assertTrue(integrity.isSound)
    }

    @Test
    fun `moving reservations never creates an orphan`() {
        val stays = (1..5).map {
            reservation(id = it, checkIn = "2026-06-26", checkOut = "2026-06-28")
        }
        // Scatter them across three different months.
        val overrides = PayoutOverrides.from(listOf(
            override(1, year = 2026, month = 5),
            override(2, year = 2026, month = 6),
            override(3, year = 2026, month = 8)
        ))
        val integrity = computePayoutIntegrity(stays, overrides)
        assertEquals(5, integrity.assigned)
        assertEquals(0, integrity.unaccounted)
        assertTrue(integrity.isSound)

        // And the same five are still reachable through the month grouping.
        val grouped = PayoutAttribution.groupByPayoutMonth(stays, overrides).values.flatten()
        assertEquals(5, grouped.size)
    }

    @Test
    fun `exclusions are counted, not lost`() {
        val stays = (1..4).map {
            reservation(id = it, checkIn = "2026-06-26", checkOut = "2026-06-28")
        }
        val overrides = PayoutOverrides.from(listOf(
            override(1, excluded = true),
            override(2, excluded = true)
        ))
        val integrity = computePayoutIntegrity(stays, overrides)
        assertEquals(4, integrity.total)
        assertEquals(2, integrity.assigned)
        assertEquals(2, integrity.excluded)
        assertEquals(0, integrity.unaccounted)
        assertTrue(integrity.isSound)
    }

    @Test
    fun `a malformed override falls back to derivation instead of vanishing`() {
        // Both the API and a DB constraint reject this shape; if one ever slips through,
        // the reservation must still land somewhere rather than silently disappearing.
        val res = reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28")
        val overrides = PayoutOverrides.from(listOf(override(1, year = null, month = null, excluded = false)))

        assertEquals(YearMonth.of(2026, 7), overrides.effectiveMonth(res))
        assertTrue(computePayoutIntegrity(listOf(res), overrides).isSound)
    }

    @Test
    fun `overrides on direct reservations are ignored`() {
        val res = reservation(id = 1, checkIn = "2026-06-26", checkOut = "2026-06-28", source = "private")
        val overrides = PayoutOverrides.from(listOf(override(1, year = 2026, month = 6)))

        assertEquals(0, computePayoutIntegrity(listOf(res), overrides).total)
        assertTrue(buildPayoutSummaries(listOf(res), emptyList(), overrides).isEmpty())
    }

    @Test
    fun `summaries are newest first`() {
        val summaries = buildPayoutSummaries(
            reservations = listOf(
                reservation(id = 1, checkIn = "2026-05-01", checkOut = "2026-05-04"),
                reservation(id = 2, checkIn = "2026-06-26", checkOut = "2026-06-28")
            ),
            payouts = emptyList()
        )
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 5)), summaries.map { it.month })
    }
}
