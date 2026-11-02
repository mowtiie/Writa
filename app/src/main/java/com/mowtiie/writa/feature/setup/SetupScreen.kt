package com.mowtiie.writa.feature.setup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.CenteredForm
import com.mowtiie.writa.core.ui.ClearTextButton
import com.mowtiie.writa.core.ui.CollectEvents
import com.mowtiie.writa.core.ui.FilterObscuredTouches
import com.mowtiie.writa.core.ui.FormProgress
import com.mowtiie.writa.core.ui.submitOnHardwareEnter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun SetupDestination(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SetupScreen(
        state = state,
        events = viewModel.events,
        onUrlChange = viewModel::onUrlChange,
        onKeyChange = viewModel::onKeyChange,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun SetupScreen(
    state: SetupUiState,
    onUrlChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    events: Flow<SetupEvent> = emptyFlow(),
) {
    FilterObscuredTouches()
    val urlFocus = remember { FocusRequester() }
    val keyFocus = remember { FocusRequester() }
    CollectEvents(events) { event ->
        when (event) {
            SetupEvent.FocusUrl -> urlFocus.requestFocus()
            SetupEvent.FocusKey -> keyFocus.requestFocus()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CenteredForm(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Icon(
                painter = painterResource(R.drawable.ic_database),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(HEADER_ICON_SIZE).align(Alignment.CenterHorizontally),
            )
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            DescriptionCard(Modifier.padding(top = 16.dp, bottom = 24.dp))

            val urlError = state.urlProblem?.let { urlMessage(it) }
            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.setup_url_label)) },
                isError = urlError != null,
                supportingText = { Text(urlError ?: stringResource(R.string.setup_url_helper)) },
                trailingIcon = clearButtonFor(state.url, state.isLoading) { onUrlChange("") },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions = KeyboardActions(onNext = { keyFocus.requestFocus() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(urlFocus)
                        .semantics { urlError?.let { error(it) } },
            )

            val keyError = state.keyProblem?.let { keyMessage(it) }
            OutlinedTextField(
                value = state.key,
                onValueChange = onKeyChange,
                label = { Text(stringResource(R.string.setup_key_label)) },
                isError = keyError != null,
                supportingText = keyError?.let { { Text(it) } },
                trailingIcon = clearButtonFor(state.key, state.isLoading) { onKeyChange("") },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier =
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .focusRequester(keyFocus)
                        .submitOnHardwareEnter(onSubmit)
                        .semantics { keyError?.let { error(it) } },
            )

            FormProgress(state.isLoading, stringResource(R.string.setup_connecting), Modifier.padding(top = 16.dp))
            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            ) {
                Text(stringResource(R.string.setup_connect))
            }
        }
    }
}

@Composable
private fun DescriptionCard(modifier: Modifier = Modifier) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = null)
            Text(
                text = stringResource(R.string.setup_description),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun clearButtonFor(
    value: String,
    isLoading: Boolean,
    onClear: () -> Unit,
): (@Composable () -> Unit)? = if (value.isNotEmpty() && !isLoading) ({ ClearTextButton(onClear) }) else null

private val HEADER_ICON_SIZE = 125.dp

@Composable
private fun urlMessage(problem: UrlProblem): String =
    when (problem) {
        UrlProblem.Required -> stringResource(R.string.error_field_required)
        UrlProblem.SchemeRequired -> stringResource(R.string.error_server_url_scheme_required)
        UrlProblem.Invalid -> stringResource(R.string.error_server_url_invalid)
        UrlProblem.NotReached -> stringResource(R.string.error_server_not_reached)
        UrlProblem.SecureConnection -> stringResource(R.string.error_secure_connection)
        is UrlProblem.UnexpectedResponse -> stringResource(R.string.error_server_unexpected_response, problem.code)
    }

@Composable
private fun keyMessage(problem: KeyProblem): String =
    when (problem) {
        KeyProblem.Required -> stringResource(R.string.error_field_required)
        KeyProblem.Invalid -> stringResource(R.string.error_key_invalid)
        is KeyProblem.Rejected -> stringResource(R.string.error_key_rejected, problem.code)
    }
