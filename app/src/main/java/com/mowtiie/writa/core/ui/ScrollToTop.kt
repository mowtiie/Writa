package com.mowtiie.writa.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Keeps the top of a list in view when something new appears above it. A lazy list holds on to the item that was
 * first, so notes inserted above it (after an import, or a new note) would otherwise be out of sight. This only
 * scrolls when the user was already at the top; a user who has scrolled down is left where they are.
 */
@Composable
fun ScrollToTopWhenFirstItemChanges(
    listState: LazyListState,
    firstKey: Any?,
) {
    var wasAtTop by remember { mutableStateOf(true) }
    var previousFirstKey by remember { mutableStateOf(firstKey) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .collect { wasAtTop = it }
    }
    LaunchedEffect(firstKey) {
        if (firstKey != previousFirstKey && wasAtTop) listState.scrollToItem(0)
        previousFirstKey = firstKey
    }
}
