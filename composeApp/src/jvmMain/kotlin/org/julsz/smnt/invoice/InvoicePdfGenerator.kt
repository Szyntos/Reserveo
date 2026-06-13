package org.julsz.smnt.invoice

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import org.julsz.smnt.InvoiceDto
import org.julsz.smnt.InvoiceItemDto
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object InvoicePdfGenerator {

    private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val HEADER_BG = Color(240, 240, 240)
    private val BORDER_COLOR = Color(180, 180, 180)

    fun generate(invoice: InvoiceDto): ByteArray {
        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 36f, 36f, 54f, 36f)
        PdfWriter.getInstance(document, baos)
        document.open()

        val (normalFont, boldFont, smallFont, smallBoldFont, titleFont) = buildFonts()

        // ── Header: Seller left, Invoice meta right ───────────────────────────
        val headerTable = PdfPTable(2).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(1.1f, 0.9f))
            setSpacingAfter(12f)
        }

        val sellerCell = buildSellerCell(invoice, normalFont, boldFont, smallFont)
        headerTable.addCell(sellerCell)

        val metaCell = buildMetaCell(invoice, normalFont, boldFont, titleFont, smallFont)
        headerTable.addCell(metaCell)

        document.add(headerTable)

        // ── Buyer ─────────────────────────────────────────────────────────────
        document.add(buildBuyerSection(invoice, normalFont, boldFont, smallFont))
        document.add(Paragraph(" "))

        // ── Items table ───────────────────────────────────────────────────────
        document.add(buildItemsTable(invoice.items, normalFont, boldFont, smallFont, smallBoldFont))
        document.add(Paragraph(" "))

        // ── VAT summary + totals ──────────────────────────────────────────────
        val totals = InvoiceCalculator.calculateTotals(
            invoice.items.map { it.toDomain() }
        )
        document.add(buildVatSummary(totals, normalFont, boldFont, smallFont, smallBoldFont))
        document.add(Paragraph(" "))

        // ── Payment info ──────────────────────────────────────────────────────
        document.add(buildPaymentSection(invoice, normalFont, boldFont, smallFont))
        document.add(Paragraph(" "))

        // ── Amount in words ───────────────────────────────────────────────────
        val amountWords = AmountInWords.convert(invoice.totalGross, Locale.forLanguageTag("pl"))
        val amountPara = Paragraph("Słownie: $amountWords", boldFont).apply { spacingAfter = 24f }
        document.add(amountPara)

        // ── Signature lines ───────────────────────────────────────────────────
        document.add(buildSignatureSection(normalFont, smallFont))

        document.close()
        return baos.toByteArray()
    }

    // ── Font setup ────────────────────────────────────────────────────────────

    private data class Fonts(
        val normal: Font, val bold: Font, val small: Font,
        val smallBold: Font, val title: Font
    )

    private fun buildFonts(): Fonts {
        val normalBf = resolveBaseFont(bold = false)
        val boldBf   = resolveBaseFont(bold = true)
        return Fonts(
            normal    = Font(normalBf,  9f),
            bold      = Font(boldBf,    9f,  Font.BOLD),
            small     = Font(normalBf,  8f),
            smallBold = Font(boldBf,    8f,  Font.BOLD),
            title     = Font(boldBf,   13f,  Font.BOLD)
        )
    }

    private fun resolveBaseFont(bold: Boolean): BaseFont {
        val winPaths = if (bold) listOf(
            "C:/Windows/Fonts/arialbd.ttf",
            "C:/Windows/Fonts/calibrib.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
        ) else listOf(
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/calibri.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
        )
        for (path in winPaths) {
            try {
                return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
            } catch (_: Exception) {}
        }
        val fallback = if (bold) BaseFont.TIMES_BOLD else BaseFont.TIMES_ROMAN
        return BaseFont.createFont(fallback, "Cp1250", false)
    }

    // ── Section builders ──────────────────────────────────────────────────────

    private fun buildSellerCell(inv: InvoiceDto, normal: Font, bold: Font, small: Font): PdfPCell {
        val p = Paragraph()
        p.add(Chunk("SPRZEDAWCA:\n", bold))
        p.add(Chunk("${inv.sellerName}\n", bold))
        inv.sellerAddress?.let { p.add(Chunk("$it\n", normal)) }
        inv.sellerNip?.let     { p.add(Chunk("NIP: $it\n", normal)) }
        inv.sellerRegon?.let   { p.add(Chunk("REGON: $it\n", normal)) }
        inv.sellerPhone?.let   { p.add(Chunk("Tel: $it\n", small)) }
        inv.sellerEmail?.let   { p.add(Chunk("E-mail: $it\n", small)) }
        return PdfPCell(p).noBorder().apply { paddingBottom = 8f }
    }

    private fun buildMetaCell(inv: InvoiceDto, normal: Font, bold: Font, title: Font, small: Font): PdfPCell {
        val p = Paragraph()
        p.alignment = Element.ALIGN_RIGHT
        p.add(Chunk("FAKTURA VAT\n", title))
        p.add(Chunk("Nr: ${inv.invoiceNumber}\n\n", bold))
        p.add(Chunk("Data wystawienia:   ${formatDate(inv.issueDate)}\n", normal))
        p.add(Chunk("Data sprzedaży:     ${formatDate(inv.saleDate)}\n", normal))
        p.add(Chunk("Termin płatności:   ${formatDate(inv.dueDate)}\n", normal))
        p.add(Chunk("Sposób płatności:   ${paymentLabel(inv.paymentMethod)}\n", normal))
        inv.reservationId?.let { p.add(Chunk("Rezerwacja: #$it\n", small)) }
        val c = PdfPCell(p).noBorder().apply { paddingBottom = 8f }
        c.horizontalAlignment = Element.ALIGN_RIGHT
        return c
    }

    private fun buildBuyerSection(inv: InvoiceDto, normal: Font, bold: Font, small: Font): PdfPTable {
        val t = PdfPTable(1).apply {
            widthPercentage = 50f
            horizontalAlignment = Element.ALIGN_LEFT
            setSpacingAfter(4f)
        }
        val p = Paragraph()
        p.add(Chunk("NABYWCA:\n", bold))
        p.add(Chunk("${inv.buyerName}\n", bold))
        inv.buyerAddress?.let { p.add(Chunk("$it\n", normal)) }
        inv.buyerNip?.let     { p.add(Chunk("NIP: $it\n", normal)) }
        inv.buyerRegon?.let   { p.add(Chunk("REGON: $it\n", normal)) }
        val c = PdfPCell(p).apply {
            border = Rectangle.BOX
            borderColor = BORDER_COLOR
            setPadding(6f)
            backgroundColor = Color(250, 250, 250)
        }
        t.addCell(c)
        return t
    }

    private fun buildItemsTable(
        items: List<InvoiceItemDto>,
        normal: Font, bold: Font, small: Font, smallBold: Font
    ): PdfPTable {
        val colWidths = floatArrayOf(0.4f, 3f, 0.6f, 0.5f, 1.1f, 1.1f, 0.6f)
        val t = PdfPTable(7).apply {
            widthPercentage = 100f
            setWidths(colWidths)
        }
        val headers = listOf(
            "Lp.", "Nazwa towaru/usługi", "Ilość", "J.m.",
            "Cena jednostkowa\nbez podatku [zł,gr]",
            "Wartość\nbez podatku [zł,gr]",
            "Stawka\nVAT"
        )
        headers.forEach { h ->
            t.addCell(headerCell(h, smallBold))
        }
        items.forEachIndexed { idx, item ->
            val bg = if (idx % 2 == 0) Color.WHITE else Color(248, 248, 248)
            t.addCell(dataCell("${item.ordinal}", small, bg, Element.ALIGN_CENTER))
            t.addCell(dataCell(item.name, small, bg, Element.ALIGN_LEFT))
            t.addCell(dataCell(fmt(item.quantity, 3), small, bg, Element.ALIGN_RIGHT))
            t.addCell(dataCell(item.unit, small, bg, Element.ALIGN_CENTER))
            t.addCell(dataCell(money(item.unitNetPrice), small, bg, Element.ALIGN_RIGHT))
            t.addCell(dataCell(money(item.netAmount), small, bg, Element.ALIGN_RIGHT))
            t.addCell(dataCell(item.vatRate + if (item.vatRate != "zw" && item.vatRate != "0") "%" else "", small, bg, Element.ALIGN_CENTER))
        }
        return t
    }

    private fun buildVatSummary(
        totals: InvoiceTotals,
        normal: Font, bold: Font, small: Font, smallBold: Font
    ): PdfPTable {
        val wrapper = PdfPTable(2).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(1.2f, 0.8f))
        }

        // VAT breakdown (left cell)
        val vatTable = PdfPTable(4).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(0.5f, 1f, 1f, 1f))
        }
        listOf("Stawka", "Netto", "VAT", "Brutto").forEach { vatTable.addCell(headerCell(it, smallBold)) }
        totals.vatSummaries.forEach { s ->
            val rateLabel = s.vatRate.code + if (s.vatRate.code != "zw" && s.vatRate.code != "0") "%" else ""
            vatTable.addCell(dataCell(rateLabel, small, Color.WHITE, Element.ALIGN_CENTER))
            vatTable.addCell(dataCell(money(s.netAmount),   small, Color.WHITE, Element.ALIGN_RIGHT))
            vatTable.addCell(dataCell(money(s.vatAmount),   small, Color.WHITE, Element.ALIGN_RIGHT))
            vatTable.addCell(dataCell(money(s.grossAmount), small, Color.WHITE, Element.ALIGN_RIGHT))
        }
        val leftCell = PdfPCell(vatTable).noBorder().apply { paddingRight = 12f }
        wrapper.addCell(leftCell)

        // Totals (right cell)
        val totalsTable = PdfPTable(2).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(1.2f, 0.8f))
        }
        fun totRow(label: String, value: String, highlight: Boolean = false) {
            val f = if (highlight) bold else small
            totalsTable.addCell(dataCell(label, f, if (highlight) HEADER_BG else Color.WHITE, Element.ALIGN_LEFT))
            totalsTable.addCell(dataCell(value, f, if (highlight) HEADER_BG else Color.WHITE, Element.ALIGN_RIGHT))
        }
        totRow("Razem netto:", money(totals.totalNet))
        totRow("Razem VAT:", money(totals.totalVat))
        totRow("RAZEM DO ZAPŁATY:", "${money(totals.totalGross)} PLN", highlight = true)

        val rightCell = PdfPCell(totalsTable).noBorder()
        wrapper.addCell(rightCell)

        return wrapper
    }

    private fun buildPaymentSection(inv: InvoiceDto, normal: Font, bold: Font, small: Font): PdfPTable {
        val t = PdfPTable(1).apply { widthPercentage = 60f; horizontalAlignment = Element.ALIGN_LEFT }
        val p = Paragraph()
        inv.sellerBankAccount?.let {
            p.add(Chunk("Numer rachunku bankowego:\n", bold))
            p.add(Chunk("$it\n", normal))
        }
        inv.notes?.let { if (it.isNotBlank()) p.add(Chunk("Uwagi: $it\n", small)) }
        if (p.isEmpty()) return t
        val c = PdfPCell(p).apply { border = Rectangle.NO_BORDER }
        t.addCell(c)
        return t
    }

    private fun buildSignatureSection(normal: Font, small: Font): PdfPTable {
        val t = PdfPTable(2).apply {
            widthPercentage = 100f
            setSpacingBefore(40f)
        }
        fun sigCell(label: String): PdfPCell {
            val p = Paragraph()
            p.add(Chunk("_".repeat(32) + "\n", normal))
            p.add(Chunk(label, small))
            val c = PdfPCell(p).noBorder()
            c.horizontalAlignment = Element.ALIGN_CENTER
            return c
        }
        t.addCell(sigCell("Podpis osoby upoważnionej do wystawienia faktury"))
        t.addCell(sigCell("Podpis osoby upoważnionej do odbioru faktury"))
        return t
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun PdfPCell.noBorder(): PdfPCell = apply { border = Rectangle.NO_BORDER }

    private fun headerCell(text: String, font: Font): PdfPCell = PdfPCell(Phrase(text, font)).apply {
        backgroundColor = HEADER_BG
        borderColor     = BORDER_COLOR
        horizontalAlignment = Element.ALIGN_CENTER
        verticalAlignment   = Element.ALIGN_MIDDLE
        paddingTop    = 4f
        paddingBottom = 4f
        paddingLeft   = 3f
        paddingRight  = 3f
    }

    private fun dataCell(text: String, font: Font, bg: Color, align: Int): PdfPCell =
        PdfPCell(Phrase(text, font)).apply {
            backgroundColor = bg
            borderColor     = BORDER_COLOR
            horizontalAlignment = align
            verticalAlignment   = Element.ALIGN_MIDDLE
            paddingTop    = 3f
            paddingBottom = 3f
            paddingLeft   = 3f
            paddingRight  = 3f
        }

    private fun formatDate(isoDate: String): String = runCatching {
        LocalDate.parse(isoDate).format(DATE_FMT)
    }.getOrDefault(isoDate)

    private fun paymentLabel(method: String): String = when (method) {
        "transfer" -> "przelew"
        "cash"     -> "gotówka"
        "card"     -> "karta"
        else       -> method
    }

    private fun money(value: Double): String = "%.2f".format(value).replace('.', ',')

    private fun fmt(value: Double, decimals: Int): String =
        "%.${decimals}f".format(value).trimEnd('0').trimEnd(',').replace('.', ',')

    private fun InvoiceItemDto.toDomain(): InvoiceLineItem = InvoiceLineItem(
        tempId       = id,
        ordinal      = ordinal,
        name         = name,
        quantity     = quantity,
        unit         = unit,
        unitNetPrice = unitNetPrice,
        vatRate      = VatRate.fromCode(vatRate)
    )
}
