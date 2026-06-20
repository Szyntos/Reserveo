package org.julsz.smnt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

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

// ─── Single-month range picker dialog ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRangePickerDialog(
    startDate: String,
    endDate: String,
    onDismiss: () -> Unit,
    onConfirm: (start: String, end: String) -> Unit
) {
    val fontScale = LocalFontScale.current
    val density   = LocalDensity.current
    val initStart = startDate.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val initEnd   = endDate.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    var pendingStart by remember { mutableStateOf(initStart) }
    var pendingEnd   by remember { mutableStateOf(initEnd) }
    var displayMonth by remember { mutableStateOf(YearMonth.from(initStart ?: LocalDate.now())) }
    val s = LocalStrings.current

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ScaledSlot(fontScale) {
                TextButton(
                    onClick = {
                        val a = pendingStart
                        val b = pendingEnd
                        if (a != null && b != null) {
                            if (a.isAfter(b)) onConfirm(b.toString(), a.toString())
                            else              onConfirm(a.toString(), b.toString())
                        }
                    },
                    enabled = pendingStart != null && pendingEnd != null
                ) { Text(s.ok) }
            }
        },
        dismissButton = { ScaledSlot(fontScale) { TextButton(onClick = onDismiss) { Text(s.cancel) } } },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            RangeCalendarBody(
                displayMonth  = displayMonth,
                selectedStart = pendingStart,
                selectedEnd   = pendingEnd,
                onMonthChange = { displayMonth = it },
                onDayClick    = { date ->
                    when {
                        pendingStart == null || pendingEnd != null -> {
                            pendingStart = date; pendingEnd = null
                        }
                        !date.isAfter(pendingStart!!) -> {
                            pendingStart = date; pendingEnd = null
                        }
                        else -> pendingEnd = date
                    }
                }
            )
        }
    }
}

@Composable
private fun RangeCalendarBody(
    displayMonth: YearMonth,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
    val today        = LocalDate.now()
    val primary      = MaterialTheme.colorScheme.primary
    val onPrimary    = MaterialTheme.colorScheme.onPrimary
    val rangeColor   = MaterialTheme.colorScheme.primaryContainer
    val onRange      = MaterialTheme.colorScheme.onPrimaryContainer
    val onSurface    = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant
    val hasRange     = selectedStart != null && selectedEnd != null

    Column(
        Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .widthIn(min = 280.dp)
    ) {
        // Month navigation header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onMonthChange(displayMonth.minusMonths(1)) }) {
                Text("‹", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text  = "${displayMonth.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())} ${displayMonth.year}",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { onMonthChange(displayMonth.plusMonths(1)) }) {
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Day-of-week headers (ISO Mon–Sun)
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.values().forEach { dow ->
                Text(
                    text     = dow.getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = onSurfaceVar
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Calendar grid
        val firstDow    = displayMonth.atDay(1).dayOfWeek.value // 1=Mon ISO
        val offset      = firstDow - 1
        val daysInMonth = displayMonth.lengthOfMonth()
        val weeks       = (offset + daysInMonth + 6) / 7

        repeat(weeks) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val idx = week * 7 + col
                    if (idx < offset || idx >= offset + daysInMonth) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val dayNum  = idx - offset + 1
                        val date    = displayMonth.atDay(dayNum)
                        val isStart = date == selectedStart
                        val isEnd   = date == selectedEnd
                        val inRange = hasRange &&
                            date.isAfter(selectedStart) && date.isBefore(selectedEnd)
                        val isToday = date == today

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .drawBehind {
                                    val w = size.width
                                    val h = size.height
                                    val r = minOf(w, h) / 2f
                                    val top = (h - r * 2f) / 2f

                                    // Range band behind start/end circles
                                    if (hasRange) {
                                        when {
                                            isStart -> drawRect(rangeColor, Offset(w / 2f, top), Size(w / 2f, r * 2f))
                                            isEnd   -> drawRect(rangeColor, Offset(0f, top),     Size(w / 2f, r * 2f))
                                            inRange -> drawRect(rangeColor, Offset(0f, top),     Size(w, r * 2f))
                                        }
                                    }
                                    // Selection circle
                                    if (isStart || isEnd) drawCircle(primary, radius = r)
                                    // Today outline
                                    if (isToday && !isStart && !isEnd) {
                                        drawCircle(primary, radius = r - 1.dp.toPx(), style = Stroke(1.5f))
                                    }
                                }
                                .clickable { onDayClick(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayNum",
                                color = when {
                                    isStart || isEnd -> onPrimary
                                    inRange          -> onRange
                                    isToday          -> primary
                                    else             -> onSurface
                                },
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isStart || isEnd) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
