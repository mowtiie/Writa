package com.mowtiie.writa.feature.crash

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.theme.WritaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CrashReportDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private val calls = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            WritaTheme {
                CrashReportDialog(onEmail = { calls += "email" }, onSave = { calls += "save" }, onDismiss = {
                    calls +=
                        "dismiss"
                })
            }
        }
    }

    @Test
    fun theDialogExplainsAndOffersThreeChoices() {
        show()

        for (
        id in
        listOf(
            R.string.crash_title,
            R.string.crash_message,
            R.string.crash_email,
            R.string.crash_save,
            R.string.crash_dismiss,
        )
        ) {
            rule.onNodeWithText(text(id)).assertIsDisplayed()
        }
    }

    @Test
    fun theMessageWarnsThatTheReportCanNameTheServer() {
        assertTrue(text(R.string.crash_message).contains("hostname"))
    }

    @Test
    fun eachButtonDoesWhatItSays() {
        show()

        rule.onNodeWithText(text(R.string.crash_email)).performClick()
        rule.onNodeWithText(text(R.string.crash_save)).performClick()
        rule.onNodeWithText(text(R.string.crash_dismiss)).performClick()

        assertEquals(listOf("email", "save", "dismiss"), calls)
    }

    @Test
    fun theEmailGoesToTheDeveloper() {
        assertTrue(text(R.string.developer_email).contains("@"))
    }
}
