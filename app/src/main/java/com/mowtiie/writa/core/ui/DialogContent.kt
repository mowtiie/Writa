package com.mowtiie.writa.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The body of a dialog. It scrolls, so a long message, a large font or a landscape screen never push the end of the
 * dialog out of reach.
 */
@Composable
fun DialogContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.verticalScroll(rememberScrollState()), content = content)
}
