package com.mowtiie.writa.core.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreenPrivacyTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun flags() = rule.activity.window.attributes.flags

    private fun secure() = flags() and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun apply(enabled: Boolean) = rule.runOnUiThread { applyScreenPrivacy(rule.activity.window, enabled) }

    @Test
    fun theScreenIsNotProtectedByDefault() {
        assertFalse(secure())
    }

    @Test
    fun turningPrivacyOnBlocksCapture() {
        apply(true)

        assertTrue(secure())
    }

    @Test
    fun turningItOffAgainAllowsCapture() {
        apply(true)

        apply(false)

        assertFalse(secure())
    }

    @Test
    fun applyingTheSameValueTwiceChangesNothing() {
        apply(true)
        apply(true)
        assertTrue(secure())

        apply(false)
        apply(false)
        assertFalse(secure())
    }

    @Test
    fun otherWindowFlagsAreLeftAlone() {
        rule.runOnUiThread { rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val before = flags() and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        apply(true)
        apply(false)

        assertEquals(before, flags() and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
