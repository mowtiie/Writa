package com.mowtiie.writa.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.core.datastore.Connection
import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.core.network.ConnectionNotConfiguredException
import com.mowtiie.writa.core.network.NetworkErrors
import com.mowtiie.writa.core.validation.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.inject.Inject

enum class EmailProblem { Required, Invalid }

enum class PasswordProblem { Required }

/** What the user is told after an attempt. Shown once, then cleared. */
sealed interface LoginMessage {
    /** The server's own words, for example "Invalid login credentials". */
    data class FromServer(
        val text: String,
    ) : LoginMessage

    data object ServerUnreachable : LoginMessage

    data object SecureConnectionProblem : LoginMessage

    data object ConnectionNotSetUp : LoginMessage

    /** The account was created, but the email has to be confirmed before signing in. */
    data object ConfirmEmail : LoginMessage

    data object Generic : LoginMessage

    companion object {
        /** Never shows exception text: only the server's message, or a fixed message for the kind of failure. */
        fun of(error: IOException): LoginMessage =
            when {
                error is ConnectionNotConfiguredException -> ConnectionNotSetUp
                error is ApiException -> error.serverMessage?.let(::FromServer) ?: Generic
                NetworkErrors.isSecureConnectionProblem(error) -> SecureConnectionProblem
                NetworkErrors.isConnectivityProblem(error) -> ServerUnreachable
                else -> Generic
            }
    }
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailProblem: EmailProblem? = null,
    val passwordProblem: PasswordProblem? = null,
    val isLoading: Boolean = false,
    val message: LoginMessage? = null,
    /** The host of the server this login goes to, or null when none is set. */
    val serverHost: String? = null,
)

enum class LoginEvent { FocusEmail, FocusPassword }

/**
 * Signs in or creates an account. Success needs no navigation call here: the session it stores moves the app on.
 * The password lives only in memory, so it is never written to the saved instance state.
 */
@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val auth: AuthRepository,
        private val connectionStore: ConnectionStore,
    ) : ViewModel() {
        private val form = MutableStateFlow(LoginUiState(email = savedState[KEY_EMAIL] ?: ""))
        private val _events = Channel<LoginEvent>(Channel.BUFFERED)

        val state: StateFlow<LoginUiState> =
            combine(form, connectionStore.connection) { form, connection ->
                form.copy(serverHost = connection?.let(::hostOf))
            }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

        val events: Flow<LoginEvent> = _events.receiveAsFlow()

        fun onEmailChange(email: String) {
            savedState[KEY_EMAIL] = email
            form.update { it.copy(email = email, emailProblem = null) }
        }

        fun onPasswordChange(password: String) {
            form.update { it.copy(password = password, passwordProblem = null) }
        }

        fun onMessageShown() {
            form.update { it.copy(message = null) }
        }

        fun signIn() = submit(createAccount = false)

        fun signUp() = submit(createAccount = true)

        /** Forgets the server, so the app asks for one again. Notes kept on this device are left for the owner check. */
        fun changeServer() {
            viewModelScope.launch { connectionStore.clear() }
        }

        private fun submit(createAccount: Boolean) {
            val current = form.value
            if (current.isLoading) return

            val email = current.email.trim()
            val emailProblem = emailProblemOf(email)
            val passwordProblem = if (current.password.isEmpty()) PasswordProblem.Required else null
            if (emailProblem != null || passwordProblem != null) {
                form.update { it.copy(emailProblem = emailProblem, passwordProblem = passwordProblem) }
                _events.trySend(if (emailProblem != null) LoginEvent.FocusEmail else LoginEvent.FocusPassword)
                return
            }

            form.update { it.copy(isLoading = true, message = null) }
            viewModelScope.launch {
                val message =
                    try {
                        attempt(createAccount, email, current.password)
                    } catch (e: IOException) {
                        LoginMessage.of(e)
                    }
                val signedIn = message == null
                form.update {
                    it.copy(isLoading = false, message = message, password = if (signedIn) "" else it.password)
                }
            }
        }

        private suspend fun attempt(
            createAccount: Boolean,
            email: String,
            password: String,
        ): LoginMessage? {
            if (!createAccount) {
                auth.signIn(email, password)
                return null
            }
            return when (auth.signUp(email, password)) {
                SignUpResult.SignedIn -> null
                SignUpResult.ConfirmationRequired -> LoginMessage.ConfirmEmail
            }
        }

        private fun emailProblemOf(email: String): EmailProblem? =
            when {
                email.isEmpty() -> EmailProblem.Required
                !Validators.isPlausibleEmail(email) -> EmailProblem.Invalid
                else -> null
            }

        private fun hostOf(connection: Connection): String =
            connection.baseUrl.toHttpUrlOrNull()?.host ?: connection.baseUrl

        private companion object {
            const val KEY_EMAIL = "email"
        }
    }
