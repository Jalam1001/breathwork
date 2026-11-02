package com.javed.breathwork

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.javed.breathwork.domain.Techniques
import com.javed.breathwork.engine.RunState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class UiFlowTest {
    init {
        val c = ApplicationProvider.getApplicationContext<BreathWorkApp>().container
        c.serviceEnabled = false; c.cues.enabled = false
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = File(System.getProperty("breathwork.shots") ?: "build/shots").apply { mkdirs() }
    private val container get() = rule.activity.container

    private fun shot(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun has(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(t: String) = rule.waitUntil(10_000) { has(t) }
    private fun advance(ms: Long) {
        rule.mainClock.autoAdvance = false
        repeat((ms / 100).toInt()) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); rule.mainClock.advanceTimeBy(100) }
        rule.mainClock.autoAdvance = true; rule.waitForIdle()
    }

    @Test fun startsEveryTechnique() {
        waitFor("Streak"); shot("01-home")
        Techniques.all.forEachIndexed { i, t ->
            rule.onNodeWithTag("techniques").performScrollToNode(hasText(t.name)); rule.onNodeWithText(t.name, useUnmergedTree = true).performClick()
            waitFor("Start ${t.name}")
            if (i == 0) {
                rule.onNodeWithContentDescription("1 minutes").performScrollTo().performClick()
                shot("02-detail-${t.id}")
            }
            if (t.id == "box") { rule.onNodeWithText("Custom").performScrollTo().performClick(); waitFor("Minutes (1–120)"); shot("03-detail-box-custom") ; rule.onNodeWithContentDescription("3 minutes").performScrollTo().performClick() }
            rule.onNodeWithText("Start ${t.name}").performClick()
            if (t.needsWarning) {
                waitFor("Safety first"); shot("08-warning")
                rule.onNodeWithText("I have read and understand this").performClick()
                rule.onNodeWithText("Start").performClick()
            }
            waitFor("Get ready")
            rule.runOnUiThread { container.controller.tick() }
            assertEquals(RunState.LEAD_IN, container.controller.state.value.run)
            // Real time: let the lead-in finish and get into the first phase.
            advance(3400)
            val st = container.controller.state.value
            assertEquals(t.id, RunState.RUNNING to t.id, st.run to st.timeline!!.config.technique.id)
            waitFor(st.segment!!.step.label)
            if (t.id == "cyclic_sigh" || t.id == "box" || t.id == "cyclic_hyper" || t.id == "478") {
                advance(if (t.id == "box") 6000 else 1500)
                shot("${if (t.id == "box") "05" else if (t.id == "478") "06" else if (t.id == "cyclic_hyper") "09" else "04"}-session-${t.id}")
            }
            if (t.id == "extended_exhale") {
                rule.onNodeWithText("Pause").performClick(); waitFor("Resume"); shot("07-paused")
                rule.onNodeWithText("Resume").performClick(); waitFor("Pause")
            }
            rule.onNodeWithText("End").performClick()
            waitFor("Session ended"); if (i == 0) shot("10-ended")
            rule.onNodeWithText("Done").performClick(); waitFor("Start ${t.name}"); rule.onNodeWithContentDescription("Back").performClick(); waitFor("Streak")
        }
        rule.onNodeWithContentDescription("History").performClick(); waitFor("Current streak"); shot("11-history")
    }
}
