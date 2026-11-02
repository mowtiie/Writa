package com.mowtiie.writa.feature.notes.list

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.theme.WritaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SchemaOutdatedScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private var checks = 0

    private fun show(state: SchemaUiState) {
        rule.setContent { WritaTheme { SchemaOutdatedScreen(state, onCheckAgain = { checks++ }) } }
    }

    @Test
    fun theScreenExplainsWhatToDo() {
        show(SchemaUiState(sql = "create table notes ();"))

        rule.onNodeWithText(text(R.string.schema_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.schema_message)).assertIsDisplayed()
        rule.onNodeWithText("create table notes ();").assertIsDisplayed()
    }

    @Test
    fun theTitleIsMarkedAsAHeading() {
        show(SchemaUiState(sql = "x"))

        rule
            .onNodeWithText(text(R.string.schema_title))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun whileTheSqlLoadsItCannotBeCopied() {
        show(SchemaUiState())

        rule.onNodeWithText(text(R.string.schema_loading)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.schema_copy)).assertIsNotEnabled()
    }

    @Test
    fun anUnreadableSqlFileSaysSoAndCannotBeCopied() {
        show(SchemaUiState(unreadable = true))

        rule.onNodeWithText(text(R.string.schema_unreadable)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.schema_copy)).assertIsNotEnabled()
    }

    @Test
    fun copyingPutsTheSqlOnTheClipboardAndSaysSo() {
        show(SchemaUiState(sql = "create table notes ();"))
        rule.onNodeWithText(text(R.string.schema_copy)).assertIsEnabled().performClick()

        rule.onNodeWithText(text(R.string.schema_copied)).assertIsDisplayed()
        val clip = copiedText()
        assertEquals("create table notes ();", clip)
    }

    @Test
    fun checkingAgainAsksForARefresh() {
        show(SchemaUiState(sql = "x"))

        rule.onNodeWithText(text(R.string.schema_check_again)).performClick()

        assertEquals(1, checks)
    }

    private fun copiedText(): String? {
        var result: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            result =
                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
        }
        return result
    }
}
