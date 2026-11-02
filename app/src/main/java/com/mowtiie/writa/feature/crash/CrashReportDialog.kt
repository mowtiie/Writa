package com.mowtiie.writa.feature.crash

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.CollectEvents
import com.mowtiie.writa.core.ui.FilterObscuredTouches

private const val REPORT_MIME_TYPE = "text/plain"

/** Offers the report of the last crash, when there is one, above whatever screen the app is showing. */
@Composable
fun CrashReportHost(viewModel: CrashReportViewModel = hiltViewModel()) {
    val hasReport by viewModel.hasReport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveFile =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(REPORT_MIME_TYPE)) { uri ->
            uri?.let { viewModel.onSaveFileChosen(it.toString()) }
        }
    CollectEvents(viewModel.events) { event ->
        when (event) {
            is CrashEvent.SendEmail -> {
                if (sendEmail(
                        context,
                        event.report,
                    )
                ) {
                    viewModel.onEmailOpened()
                } else {
                    viewModel.onNoEmailApp()
                }
            }

            is CrashEvent.ChooseSaveFile -> {
                saveFile.launch(event.fileName)
            }

            is CrashEvent.Show -> {
                Toast
                    .makeText(
                        context,
                        messageText(context, event.message),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }
    if (hasReport) {
        CrashReportDialog(viewModel::onEmail, viewModel::onSave, viewModel::onDismiss)
    }
}

@Composable
fun CrashReportDialog(
    onEmail: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    FilterObscuredTouches()
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column {
                Text(stringResource(R.string.crash_message))
                Button(onClick = onEmail, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.crash_email))
                }
                Button(onClick = onSave, modifier = Modifier.padding(top = 4.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.crash_save))
                }
                Button(
                    onClick = onDismiss,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.crash_dismiss))
                }
            }
        },
        confirmButton = {},
    )
}

/** Opens an email to the developer with [report] in it. False if the device has no email app. */
private fun sendEmail(
    context: Context,
    report: String,
): Boolean {
    val intent =
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.developer_email)))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_email_subject))
            putExtra(Intent.EXTRA_TEXT, report)
        }
    return try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.crash_email_chooser)))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun messageText(
    context: Context,
    message: CrashMessage,
): String =
    context.getString(
        when (message) {
            CrashMessage.Saved -> R.string.crash_saved
            CrashMessage.SaveFailed -> R.string.crash_save_failed
            CrashMessage.NoEmailApp -> R.string.crash_no_email_app
        },
    )
