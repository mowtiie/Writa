package com.mowtiie.writa.core.ui

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

object ImeActions {
    /**
     * The on-screen Done key submits; so does the Enter key of a hardware keyboard, which arrives as `IME_NULL`
     * together with a key event. Only the key-down submits, so a key-up cannot submit a second time.
     */
    fun isSubmit(
        actionId: Int,
        keyCode: Int?,
        keyAction: Int?,
    ): Boolean =
        actionId == EditorInfo.IME_ACTION_DONE ||
            (keyCode == KeyEvent.KEYCODE_ENTER && keyAction == KeyEvent.ACTION_DOWN)
}
