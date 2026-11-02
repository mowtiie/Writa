package com.mowtiie.writa.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mowtiie.writa.R

private val FORM_HORIZONTAL_PADDING = 36.dp
private val FORM_VERTICAL_PADDING = 16.dp
private val PROGRESS_HEIGHT = 4.dp

/** A scrolling form that is centred vertically while it fits and scrolls once it does not. */
@Composable
fun CenteredForm(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = FORM_HORIZONTAL_PADDING, vertical = FORM_VERTICAL_PADDING),
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** Reserves the indicator's space so the form does not jump when loading starts. */
@Composable
fun FormProgress(
    isLoading: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(PROGRESS_HEIGHT), contentAlignment = Alignment.Center) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().semantics { contentDescription = description })
        }
    }
}

@Composable
fun ClearTextButton(onClear: () -> Unit) {
    IconButton(onClick = onClear) {
        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.field_clear))
    }
}
