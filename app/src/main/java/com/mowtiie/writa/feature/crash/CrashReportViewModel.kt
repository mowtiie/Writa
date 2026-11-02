package com.mowtiie.writa.feature.crash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.core.di.IoDispatcher
import com.mowtiie.writa.feature.notes.attempt
import com.mowtiie.writa.feature.settings.backup.DocumentOpener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import javax.inject.Inject

enum class CrashMessage { Saved, SaveFailed, NoEmailApp }

sealed interface CrashEvent {
    data class SendEmail(
        val report: String,
    ) : CrashEvent

    data class ChooseSaveFile(
        val fileName: String,
    ) : CrashEvent

    data class Show(
        val message: CrashMessage,
    ) : CrashEvent
}

/**
 * Offers the report of the last crash on the next start. It stays until the user has emailed it, saved it, or
 * dismissed it, and is then deleted: a report is never kept after the user has dealt with it.
 */
@HiltViewModel
class CrashReportViewModel
    @Inject
    constructor(
        private val store: CrashReportStore,
        private val documents: DocumentOpener,
        private val clock: Clock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _hasReport = MutableStateFlow(false)

        /** Whether there is a report to offer. */
        val hasReport: StateFlow<Boolean> = _hasReport.asStateFlow()

        private val _events = Channel<CrashEvent>(Channel.BUFFERED)
        val events: Flow<CrashEvent> = _events.receiveAsFlow()

        init {
            viewModelScope.launch { _hasReport.value = withContext(ioDispatcher) { store.hasReport() } }
        }

        fun onEmail() {
            viewModelScope.launch {
                val report = withContext(ioDispatcher) { store.read() }
                if (report == null) dealtWith() else _events.send(CrashEvent.SendEmail(report))
            }
        }

        fun onEmailOpened() = dealtWith()

        fun onNoEmailApp() {
            _events.trySend(CrashEvent.Show(CrashMessage.NoEmailApp))
        }

        fun onSave() {
            _events.trySend(CrashEvent.ChooseSaveFile(CrashReportStore.suggestedFileName(clock.instant())))
        }

        fun onSaveFileChosen(uri: String) {
            viewModelScope.launch {
                val saved =
                    attempt {
                        documents.write(uri) { out -> if (!store.copyTo(out)) throw IOException("No report") }
                        true
                    }
                _events.send(CrashEvent.Show(if (saved) CrashMessage.Saved else CrashMessage.SaveFailed))
                if (saved) dealtWith()
            }
        }

        fun onDismiss() = dealtWith()

        private fun dealtWith() {
            _hasReport.value = false
            viewModelScope.launch { withContext(ioDispatcher) { store.delete() } }
        }
    }
