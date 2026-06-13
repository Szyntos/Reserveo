package org.julsz.smnt.invoice

import org.julsz.smnt.ReservationDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object InvoiceCalculator {

    fun calculateTotals(items: List<InvoiceLineItem>): InvoiceTotals {
        val totalNet   = items.sumOf { it.netAmount }
        val totalVat   = items.sumOf { it.vatAmount }
        val totalGross = items.sumOf { it.grossAmount }

        val summaries = items.groupBy { it.vatRate.code }
            .entries
            .sortedBy { it.key }
            .map { (_, groupItems) ->
                VatSummary(
                    vatRate    = groupItems.first().vatRate,
                    netAmount  = groupItems.sumOf { it.netAmount },
                    vatAmount  = groupItems.sumOf { it.vatAmount },
                    grossAmount = groupItems.sumOf { it.grossAmount }
                )
            }

        return InvoiceTotals(totalNet, totalVat, totalGross, summaries)
    }

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
                        tempId       = nextTempId(),
                        ordinal      = ordinal++,
                        name         = "Pokój nr ${res.roomNumber} | ${seg.fromDate} – ${seg.toDate}",
                        quantity     = segNights.toDouble(),
                        unit         = "doba",
                        unitNetPrice = seg.pricePerPersonPerNight * seg.adults,
                        vatRate      = VatRate.RATE_ZW
                    )
                }
            }
        } else if (nights > 0) {
            val unitPrice = if (nights > 0) (res.totalAmount ?: 0.0) / nights else 0.0
            items += InvoiceLineItem(
                tempId       = nextTempId(),
                ordinal      = ordinal++,
                name         = "Pokój nr ${res.roomNumber} | ${res.checkInDate} – ${res.checkOutDate}",
                quantity     = nights.toDouble(),
                unit         = "doba",
                unitNetPrice = unitPrice,
                vatRate      = VatRate.RATE_ZW
            )
        }

        res.priceAdjustments.forEach { adj ->
            if (adj.amount != 0.0) {
                items += InvoiceLineItem(
                    tempId       = nextTempId(),
                    ordinal      = ordinal++,
                    name         = adj.description ?: "Korekta ceny",
                    quantity     = 1.0,
                    unit         = "szt.",
                    unitNetPrice = adj.amount,
                    vatRate      = VatRate.RATE_ZW
                )
            }
        }

        return items
    }
}
