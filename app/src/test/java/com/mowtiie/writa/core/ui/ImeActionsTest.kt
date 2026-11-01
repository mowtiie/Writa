package com.mowtiie.writa.core.ui

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeActionsTest {
    @Test
    fun keyboardDoneKeySubmits() {
        assertTrue(ImeActions.isSubmit(EditorInfo.IME_ACTION_DONE, null, null))
    }

    @Test
    fun hardwareEnterKeyDownSubmits() {
        assertTrue(ImeActions.isSubmit(EditorInfo.IME_NULL, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN))
    }

    @Test
    fun enterKeyUpDoesNotSubmitASecondTime() {
        assertFalse(ImeActions.isSubmit(EditorInfo.IME_NULL, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP))
    }

    @Test
    fun otherKeysAndActionsDoNotSubmit() {
        assertFalse(ImeActions.isSubmit(EditorInfo.IME_NULL, KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN))
        assertFalse(ImeActions.isSubmit(EditorInfo.IME_ACTION_NEXT, null, null))
        assertFalse(ImeActions.isSubmit(EditorInfo.IME_NULL, null, null))
    }
}
