package com.mowtiie.writa.feature.notes.list

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.CenteredForm
import kotlinx.coroutines.launch

private val SQL_MAX_HEIGHT = 240.dp
private val HEADER_ICON_SIZE = 125.dp

/**
 * Shown instead of the notes when the user's database lacks part of `schema.sql`. Nothing else works until it is
 * run, so this screen only explains, offers the SQL, and checks again.
 */
@Composable
fun SchemaOutdatedDestination(
    onCheckAgain: () -> Unit,
    viewModel: SchemaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SchemaOutdatedScreen(state, onCheckAgain)
}

@Composable
fun SchemaOutdatedScreen(
    state: SchemaUiState,
    onCheckAgain: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val copied = stringResource(R.string.schema_copied)
    val sqlLabel = stringResource(R.string.schema_sql_label)
    val copy: () -> Unit = {
        state.sql?.let { sql ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(sqlLabel, sql)))
                snackbarHost.showSnackbar(copied)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            CenteredForm {
                Icon(
                    painter = painterResource(R.drawable.ic_database),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(HEADER_ICON_SIZE).align(Alignment.CenterHorizontally),
                )
                Text(
                    text = stringResource(R.string.schema_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.schema_message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                SqlCard(state)
                Button(
                    onClick = copy,
                    enabled = state.sql != null,
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.schema_copy))
                }
                OutlinedButton(onClick = onCheckAgain, modifier = Modifier.padding(top = 4.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.schema_check_again))
                }
            }
            SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SqlCard(state: SchemaUiState) {
    val text =
        state.sql
            ?: stringResource(if (state.unreadable) R.string.schema_unreadable else R.string.schema_loading)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.heightIn(max = SQL_MAX_HEIGHT).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
