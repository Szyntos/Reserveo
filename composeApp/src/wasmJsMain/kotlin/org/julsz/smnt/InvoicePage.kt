package org.julsz.smnt

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient

@Composable
actual fun InvoicePage(
    client: HttpClient,
    hotel: UserHotelRoleDto,
    initialReservation: ReservationDto?,
    onInitialConsumed: () -> Unit,
    fontScale: Float
) {
    Text("Invoices not available on this platform")
}
