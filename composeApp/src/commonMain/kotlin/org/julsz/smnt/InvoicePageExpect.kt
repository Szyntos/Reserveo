package org.julsz.smnt

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient

@Composable
expect fun InvoicePage(
    client: HttpClient,
    hotel: UserHotelRoleDto,
    initialReservation: ReservationDto?,
    onInitialConsumed: () -> Unit,
    fontScale: Float
)
