package org.julsz.smnt

import androidx.compose.runtime.Composable

@Composable
actual fun AppTooltipArea(
    tooltip: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    content()
}
