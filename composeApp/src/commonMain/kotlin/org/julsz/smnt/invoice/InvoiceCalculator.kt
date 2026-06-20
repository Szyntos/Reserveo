package org.julsz.smnt.invoice

import org.julsz.smnt.ReservationDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object InvoiceCalculator {

    fun calculateTotals(items: List<InvoiceLineItem>): InvoiceTotals =
        InvoiceTotals(items.sumOf { it.amount })

    fun fromReservation(res: ReservationDto, nextTempId: () -> Int): List<InvoiceLineItem> {
        val items = mutableListOf<InvoiceLineItem>()
        var ordinal = 1

        val nights = runCatching {
            ChronoUnit.DAYS.between(
                LocalDate.parse(res.checkInDate),
                LocalDate.parse(res.checkOutDate)
            ).toInt()
        }.getOrDefault(0)

        if (res.priceSegments.isNotEmpty()) {
            res.priceSegments.forEach { seg ->
                val segNights = runCatching {
                    ChronoUnit.DAYS.between(
                        LocalDate.parse(seg.fromDate),
                        LocalDate.parse(seg.toDate)
                    ).toInt()
                }.getOrDefault(0)
                if (segNights > 0) {
                    items += InvoiceLineItem(
                        tempId    = nextTempId(),
                        ordinal   = ordinal++,
                        name      = "Pokój nr ${res.roomNumber} | ${seg.fromDate} – ${seg.toDate}",
                        quantity  = segNights.toDouble(),
                        unit      = "doba",
                        unitPrice = seg.pricePerPersonPerNight * seg.adults
                    )
                }
            }
        } else if (nights > 0) {
            val unitPrice = if (nights > 0) (res.totalAmount ?: 0.0) / nights else 0.0
            items += InvoiceLineItem(
                tempId    = nextTempId(),
                ordinal   = ordinal++,
                name      = "Pokój nr ${res.roomNumber} | ${res.checkInDate} – ${res.checkOutDate}",
                quantity  = nights.toDouble(),
                unit      = "doba",
                unitPrice = unitPrice
            )
        }

        res.priceAdjustments.forEach { adj ->
            if (adj.amount != 0.0) {
                items += InvoiceLineItem(
                    tempId    = nextTempId(),
                    ordinal   = ordinal++,
                    name      = adj.description ?: "Korekta ceny",
                    quantity  = 1.0,
                    unit      = "szt.",
                    unitPrice = adj.amount
                )
            }
        }

        return items
    }
}
