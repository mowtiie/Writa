package com.mowtiie.writa.feature.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.BuildConfig
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.PreferenceRow

/** What the user can do on the About screen, apart from going back. */
class AboutActions(
    val onLicenseOpen: () -> Unit = {},
    val onLicenseClose: () -> Unit = {},
    val onVersionTap: () -> Unit = {},
    val onEasterEggShown: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
)

@Composable
fun AboutDestination(
    onBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val actions =
        remember(viewModel, uriHandler) {
            AboutActions(
                onLicenseOpen = viewModel::onLicenseOpen,
                onLicenseClose = viewModel::onLicenseClose,
                onVersionTap = viewModel::onVersionTap,
                onEasterEggShown = viewModel::onEasterEggShown,
                onOpenLink = uriHandler::openUri,
            )
        }
    AboutScreen(state, actions, versionName = BuildConfig.VERSION_NAME, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    state: AboutUiState,
    actions: AboutActions,
    versionName: String,
    onBack: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val easterEgg = stringResource(R.string.about_easter_egg)
    LaunchedEffect(state.easterEgg) {
        if (state.easterEgg) {
            snackbarHost.showSnackbar(easterEgg)
            actions.onEasterEggShown()
        }
    }

    val sourceCode = stringResource(R.string.url_source_code)
    val support = stringResource(R.string.url_support)
    val author = stringResource(R.string.url_author)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
        ) {
            PreferenceRow(
                title = stringResource(R.string.about_license),
                summary = stringResource(R.string.about_license_name),
                onClick = actions.onLicenseOpen,
                icon = R.drawable.ic_license,
            )
            PreferenceRow(
                title = stringResource(R.string.about_source_code),
                summary = stringResource(R.string.about_source_code_summary),
                onClick = { actions.onOpenLink(sourceCode) },
                icon = R.drawable.ic_source_code,
            )
            PreferenceRow(
                title = stringResource(R.string.about_support),
                summary = stringResource(R.string.about_support_summary),
                onClick = { actions.onOpenLink(support) },
                icon = R.drawable.ic_donate,
            )
            PreferenceRow(
                title = stringResource(R.string.about_author),
                summary = stringResource(R.string.about_author_name),
                onClick = { actions.onOpenLink(author) },
                icon = R.drawable.ic_author,
            )
            PreferenceRow(
                title = stringResource(R.string.about_version),
                summary = versionName,
                onClick = actions.onVersionTap,
                icon = R.drawable.ic_info,
            )
        }
    }

    when (val license = state.license) {
        LicenseState.Hidden -> {
            Unit
        }

        is LicenseState.Shown -> {
            LicenseDialog(license.text, actions.onLicenseClose)
        }

        LicenseState.Unreadable -> {
            LicenseDialog(
                stringResource(R.string.about_license_unreadable),
                actions.onLicenseClose,
            )
        }
    }
}

@Composable
private fun LicenseDialog(
    text: String,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(painterResource(R.drawable.ic_license), contentDescription = null) },
        title = { Text(stringResource(R.string.about_license)) },
        text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.about_close)) } },
    )
}
