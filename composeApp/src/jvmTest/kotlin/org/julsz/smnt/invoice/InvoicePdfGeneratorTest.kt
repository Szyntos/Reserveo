package org.julsz.smnt.invoice

import org.julsz.smnt.InvoiceDto
import org.julsz.smnt.InvoiceItemDto
import kotlin.test.Test
import kotlin.test.assertTrue

class InvoicePdfGeneratorTest {

    private fun sampleInvoice(items: List<InvoiceItemDto> = defaultItems()) = InvoiceDto(
        id = 1,
        hotelId = 1,
        reservationId = 42,
        invoiceNumber = "FV/2026/07/0001",
        issueDate = "2026-07-25",
        saleDate = "2026-07-25",
        dueDate = "2026-08-01",
        paymentMethod = "transfer",
        sellerName = "Reserveo Sp. z o.o.",
        sellerAddress = "ul. Testowa 1, 00-000 Warszawa",
        sellerNip = "1234567890",
        sellerRegon = "123456789",
        sellerBankAccount = "PL00 0000 0000 0000 0000 0000 0000",
        sellerPhone = "123456789",
        sellerEmail = "biuro@reserveo.pl",
        buyerName = "Jan Kowalski",
        buyerAddress = "ul. Kwiatowa 2, 00-001 Warszawa",
        buyerNip = null,
        buyerRegon = null,
        totalAmount = 250.0,
        notes = "Pobyt 3 noce",
        createdAt = "2026-07-25T10:00:00",
        items = items
    )

    private fun defaultItems() = listOf(
        InvoiceItemDto(
            id = 1, invoiceId = 1, ordinal = 1, name = "Nocleg", quantity = 3.0,
            unit = "noc", unitPrice = 83.33, amount = 250.0
        )
    )

    @Test
    fun `generate produces a well-formed PDF`() {
        val bytes = InvoicePdfGenerator.generate(sampleInvoice())

        assertTrue(bytes.isNotEmpty(), "PDF bytes should not be empty")
        val header = String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)
        assertTrue(header == "%PDF-", "Output should start with a PDF header, was: $header")
    }

    @Test
    fun `generate handles invoice with no items`() {
        val bytes = InvoicePdfGenerator.generate(sampleInvoice(items = emptyList()))

        assertTrue(bytes.isNotEmpty())
        assertTrue(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII) == "%PDF-")
    }

    @Test
    fun `generate handles multiple items and optional fields left null`() {
        val invoice = sampleInvoice(
            items = listOf(
                InvoiceItemDto(1, 1, 1, "Nocleg", 3.0, "noc", 83.33, 250.0),
                InvoiceItemDto(2, 1, 2, "Śniadanie", 6.0, "szt", 20.0, 120.0)
            )
        ).copy(
            sellerAddress = null,
            sellerNip = null,
            sellerRegon = null,
            sellerPhone = null,
            sellerEmail = null,
            sellerBankAccount = null,
            notes = null,
            reservationId = null
        )

        val bytes = InvoicePdfGenerator.generate(invoice)

        assertTrue(bytes.isNotEmpty())
        assertTrue(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII) == "%PDF-")
    }
}
