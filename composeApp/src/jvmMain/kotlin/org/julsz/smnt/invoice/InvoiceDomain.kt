package org.julsz.smnt.invoice

data class VatRate(val code: String, val label: String, val fraction: Double) {
    companion object {
        val RATE_23 = VatRate("23", "23%", 0.23)
        val RATE_8  = VatRate("8",  "8%",  0.08)
        val RATE_5  = VatRate("5",  "5%",  0.05)
        val RATE_0  = VatRate("0",  "0%",  0.00)
        val RATE_ZW = VatRate("zw", "zw",  0.00)

        val ALL = listOf(RATE_23, RATE_8, RATE_5, RATE_0, RATE_ZW)

        fun fromCode(code: String): VatRate = ALL.find { it.code == code } ?: RATE_8
    }
}

data class InvoiceLineItem(
    val tempId: Int,
    val ordinal: Int,
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitNetPrice: Double,
    val vatRate: VatRate
) {
    val netAmount: Double   get() = quantity * unitNetPrice
    val vatAmount: Double   get() = (netAmount * vatRate.fraction * 100).toLong() / 100.0
    val grossAmount: Double get() = netAmount + vatAmount
}

data class VatSummary(
    val vatRate: VatRate,
    val netAmount: Double,
    val vatAmount: Double,
    val grossAmount: Double
)

data class InvoiceTotals(
    val totalNet: Double,
    val totalVat: Double,
    val totalGross: Double,
    val vatSummaries: List<VatSummary>
)
