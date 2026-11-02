package com.mowtiie.writa.core.ui

import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class)
class NoPersonalizedLearningTest {
    private val view = View(InstrumentationRegistry.getInstrumentation().targetContext)

    private val connection: InputConnection = BaseInputConnection(view, false)

    private val plain =
        object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttrs: EditorInfo): InputConnection = connection
        }

    @Test
    fun theKeyboardIsToldNotToLearnFromTheField() {
        val info = EditorInfo()

        plain.withoutPersonalizedLearning().createInputConnection(info)

        assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
    }

    @Test
    fun theOtherOptionsOfTheFieldAreKept() {
        val info = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_DONE }

        plain.withoutPersonalizedLearning().createInputConnection(info)

        assertEquals(EditorInfo.IME_ACTION_DONE, info.imeOptions and EditorInfo.IME_MASK_ACTION)
    }

    @Test
    fun theConnectionOfTheFieldIsUsedAsItIs() {
        assertSame(connection, plain.withoutPersonalizedLearning().createInputConnection(EditorInfo()))
    }
}
