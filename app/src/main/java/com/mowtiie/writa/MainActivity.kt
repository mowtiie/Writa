package com.mowtiie.writa

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mowtiie.writa.core.ui.applyScreenPrivacy
import com.mowtiie.writa.core.ui.rememberBatterySaver
import com.mowtiie.writa.core.ui.theme.WritaTheme
import com.mowtiie.writa.feature.crash.CrashReportHost
import com.mowtiie.writa.navigation.WritaNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        applyScreenPrivacyWhileResumed()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val authState by viewModel.authState.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent

            val darkTheme = current.theme.isDark(isSystemInDarkTheme(), rememberBatterySaver())
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            WritaTheme(contrast = current.contrast, darkTheme = darkTheme, dynamicColor = current.dynamicColor) {
                WritaNavHost(authState)
                CrashReportHost()
            }
        }
    }

    /** The flag is set again on every resume, so nothing can leave it behind in the wrong state. */
    private fun applyScreenPrivacyWhileResumed() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.settings
                    .filterNotNull()
                    .map { it.screenPrivacy }
                    .collect { applyScreenPrivacy(window, it) }
            }
        }
    }
}
