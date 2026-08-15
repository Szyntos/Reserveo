package org.julsz.smnt

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun AppVerticalScrollbar(state: ScrollState, modifier: Modifier) {}

@Composable
actual fun AppHorizontalScrollbar(state: ScrollState, modifier: Modifier) {}

@Composable
actual fun AppVerticalScrollbar(state: LazyListState, modifier: Modifier) {}

@Composable
actual fun AppVerticalScrollbar(state: LazyGridState, modifier: Modifier) {}
