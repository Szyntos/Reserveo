package org.julsz.smnt

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun AppVerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier)

@Composable
expect fun AppHorizontalScrollbar(state: ScrollState, modifier: Modifier = Modifier)
