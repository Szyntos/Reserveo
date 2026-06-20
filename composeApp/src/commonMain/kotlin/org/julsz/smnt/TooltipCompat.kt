package org.julsz.smnt

import androidx.compose.runtime.Composable

@Composable
expect fun AppTooltipArea(
    tooltip: @Composable () -> Unit,
    content: @Composable () -> Unit
)
