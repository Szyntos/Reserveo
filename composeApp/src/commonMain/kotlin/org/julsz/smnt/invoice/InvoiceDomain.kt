package org.julsz.smnt.invoice

data class InvoiceLineItem(
    val tempId: Int,
    val ordinal: Int,
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double
) {
    val amount: Double get() = quantity * unitPrice
}

data class InvoiceTotals(val totalAmount: Double)
