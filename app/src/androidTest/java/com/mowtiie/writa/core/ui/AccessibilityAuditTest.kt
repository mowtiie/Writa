package com.mowtiie.writa.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.core.ui.theme.WritaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What a screen reader and a finger need from every screen: each control says what it does, is big enough to hit,
 * and each screen has a heading to navigate by. Run at the normal font size and at the largest one.
 */
class AccessibilityAuditTest {
    @get:Rule
    val rule = createComposeRule()

    private var index by mutableIntStateOf(0)

    private fun audit(fontScale: Float) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WritaTheme(darkTheme = false) { Box(Modifier.testTag("frame")) { SampleScreens.all[index].second() } }
            }
        }
        for ((i, screen) in SampleScreens.all.withIndex()) {
            rule.runOnIdle { index = i }
            rule.waitForIdle()
            check(screen.first)
        }
    }

    private val density =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources.displayMetrics.density

    private fun check(screen: String) {
        val root = rule.onRoot().fetchSemanticsNode()
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val problems = mutableListOf<String>()
        for (node in nodes) {
            if (node.config.getOrNull(SemanticsActions.OnClick) == null) continue
            val name = describe(node)
            if (name.isBlank()) problems += "[$screen] a control has no text or description at ${node.boundsInRoot}"
            val minPx = MIN_TARGET_DP * density
            val touch = node.touchBoundsInRoot
            val scrolledOut = touch.isEmpty
            if (!scrolledOut && (touch.width < minPx || touch.height < minPx)) {
                problems += "[$screen] '$name' can only be hit in ${touch.width}x${touch.height}px"
            }
        }
        val hasHeading = allNodes(root).any { it.config.getOrNull(SemanticsProperties.Heading) != null }
        if (!hasHeading) problems += "[$screen] has no heading"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun describe(node: SemanticsNode): String {
        val description =
            node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString(" ")
                .orEmpty()
        val text =
            node.config
                .getOrNull(SemanticsProperties.Text)
                ?.joinToString(" ") { it.text }
                .orEmpty()
        return listOf(description, text).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun allNodes(node: SemanticsNode): Sequence<SemanticsNode> =
        sequenceOf(node) + node.children.asSequence().flatMap(::allNodes)

    @Test
    fun everyScreenIsUsableAtTheNormalFontSize() = audit(fontScale = 1f)

    @Test
    fun everyScreenIsUsableAtTheLargestFontSize() = audit(fontScale = 2f)

    private companion object {
        const val MIN_TARGET_DP = 48
    }
}
