package org.julsz.smnt

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.DialogProperties

// On Android, Dialog composables create a new window whose AndroidComposeView resets
// LocalDensity to the system value, ignoring the custom fontScale set in AppRoot.
// Custom CompositionLocals (like LocalFontScale) do propagate through dialog boundaries
// via setParentCompositionContext. We re-apply LocalDensity inside each content slot.

@Composable
private fun ScaledSlot(fontScale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        content()
    }
}

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val fontScale = LocalFontScale.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton    = { ScaledSlot(fontScale) { confirmButton() } },
        modifier         = modifier,
        dismissButton    = dismissButton?.let { d -> { ScaledSlot(fontScale) { d() } } },
        icon             = icon?.let { ic -> { ScaledSlot(fontScale) { ic() } } },
        title            = title?.let { t -> { ScaledSlot(fontScale) { t() } } },
        text             = text?.let { tx -> { ScaledSlot(fontScale) { tx() } } },
        shape            = shape,
        containerColor   = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation   = tonalElevation,
        properties       = properties,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    colors: androidx.compose.material3.DatePickerColors = androidx.compose.material3.DatePickerDefaults.colors(),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    val fontScale = LocalFontScale.current
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton    = { ScaledSlot(fontScale) { confirmButton() } },
        modifier         = modifier,
        dismissButton    = dismissButton?.let { d -> { ScaledSlot(fontScale) { d() } } },
        shape            = shape,
        tonalElevation   = tonalElevation,
        colors           = colors,
        properties       = properties,
    ) {
        val columnScope = this
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            columnScope.content()
        }
    }
}
