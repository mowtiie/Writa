package com.mowtiie.writa.core.ui

import android.view.inputmethod.EditorInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * Submits a form when the Enter key of a hardware keyboard goes down. The on-screen Done key is the field's
 * `ImeAction`; this is the other half of [ImeActions.isSubmit]. Only the key-down counts, so one press submits once.
 */
fun Modifier.submitOnHardwareEnter(onSubmit: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        val submit = ImeActions.isSubmit(EditorInfo.IME_NULL, native.keyCode, native.action)
        if (submit) onSubmit()
        submit
    }
