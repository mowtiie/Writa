package com.mowtiie.writa.core.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat

/** Blocks the screen from being captured and from showing in the Recents thumbnail while it is on. */
fun applyScreenPrivacy(
    window: Window,
    enabled: Boolean,
) {
    if (enabled) {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Ignores touches that arrive while another window covers this one, so an overlay cannot trick the user into
 * tapping the screen's buttons. Meant for the screens that take server or account details.
 */
@Composable
fun FilterObscuredTouches() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.filterTouchesWhenObscured
        view.filterTouchesWhenObscured = true
        onDispose { view.filterTouchesWhenObscured = previous }
    }
}

/** Whether battery saver is on, kept up to date while the caller is composed. */
@Composable
fun rememberBatterySaver(): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var saving by remember { mutableStateOf(powerManager.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    saving = powerManager.isPowerSaveMode
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        saving = powerManager.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}
