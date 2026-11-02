package com.mowtiie.writa.feature.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun LoginDestination(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        events = viewModel.events,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignIn = viewModel::signIn,
        onCreateAccount = viewModel::signUp,
        onChangeServer = viewModel::changeServer,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onChangeServer: () -> Unit,
    onMessageShown: () -> Unit,
    events: Flow<LoginEvent> = emptyFlow(),
) {
    FilterObscuredTouches()
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    CollectEvents(events) { event ->
        when (event) {
            LoginEvent.FocusEmail -> emailFocus.requestFocus()
            LoginEvent.FocusPassword -> passwordFocus.requestFocus()
        }
    }

    val snackbarHost = remember { SnackbarHostState() }
    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHost.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(snackbarHost = {
        SnackbarHost(snackbarHost)
    }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        CenteredForm(Modifier.padding(padding)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_TITLE_SIZE, maxFontSize = MAX_TITLE_SIZE),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Text(
                text = stringResource(R.string.app_slogan),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp).fillMaxWidth(),
            )

            val emailError = state.emailProblem?.let { emailMessage(it) }
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.login_email_label)) },
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it) } },
                leadingIcon = { Icon(painterResource(R.drawable.ic_mail), contentDescription = null) },
                trailingIcon =
                    if (state.email.isNotEmpty() && !state.isLoading) {
                        { ClearTextButton { onEmailChange("") } }
                    } else {
                        null
                    },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                modifier =
                    Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                        .focusRequester(emailFocus)
                        .semantics {
                            contentType = ContentType.EmailAddress
                            emailError?.let { error(it) }
                        },
            )

            var showPassword by rememberSaveable { mutableStateOf(false) }
            val passwordError = state.passwordProblem?.let { passwordMessage(it) }
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.login_password_label)) },
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
                singleLine = true,
                enabled = !state.isLoading,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(
                            stringResource(
                                if (showPassword) R.string.login_hide_password else R.string.login_show_password,
                            ),
                        )
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { onSignIn() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocus)
                        .submitOnHardwareEnter(onSignIn)
                        .semantics {
                            contentType = ContentType.Password
                            passwordError?.let { error(it) }
                        },
            )

            FormProgress(state.isLoading, stringResource(R.string.login_working), Modifier.padding(top = 16.dp))
            Button(
                onClick = onSignIn,
                enabled = !state.isLoading,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_sign_in))
            }
            TextButton(onClick = onCreateAccount, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.login_create_account))
            }

            state.serverHost?.let { host ->
                Text(
                    text = stringResource(R.string.login_server, host),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                )
            }
            TextButton(onClick = onChangeServer, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.login_change_server))
            }
        }
    }
}

private val MIN_TITLE_SIZE = 24.sp
private val MAX_TITLE_SIZE = 45.sp

@Composable
private fun emailMessage(problem: EmailProblem): String =
    when (problem) {
        EmailProblem.Required -> stringResource(R.string.error_field_required)
        EmailProblem.Invalid -> stringResource(R.string.error_email_invalid)
    }

@Composable
private fun passwordMessage(problem: PasswordProblem): String =
    when (problem) {
        PasswordProblem.Required -> stringResource(R.string.error_field_required)
    }

@Composable
private fun messageText(message: LoginMessage): String =
    when (message) {
        is LoginMessage.FromServer -> message.text
        LoginMessage.ServerUnreachable -> stringResource(R.string.error_server_unreachable)
        LoginMessage.SecureConnectionProblem -> stringResource(R.string.error_secure_connection)
        LoginMessage.ConnectionNotSetUp -> stringResource(R.string.error_connection_not_set_up)
        LoginMessage.ConfirmEmail -> stringResource(R.string.message_confirm_email)
        LoginMessage.Generic -> stringResource(R.string.error_generic_try_again)
    }
