package com.framecoach.app

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.containsString
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DemoSmokeTest {
    @Test fun demoCanGenerateGuidanceSwitchModeAndSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withText("演示场景 · 非实时识别")).check(matches(isDisplayed()))
            // Dwell is deliberately a real-time user behavior, independent of Espresso idling.
            Thread.sleep(2600)
            onView(withText("演示建议已就绪 · 开启相机试试")).check(matches(isDisplayed()))
            screenshot("portrait.png")
            onView(withText("风光")).perform(click())
            Thread.sleep(2600)
            onView(withText(containsString("先决定天空"))).check(matches(isDisplayed()))
            screenshot("landscape.png")
            scenario.recreate()
            onView(withText("演示场景 · 非实时识别")).check(matches(isDisplayed()))
            onView(withText("辅助线 开")).perform(click())
            onView(withText("辅助线 关")).check(matches(isDisplayed()))
            onView(withText("●  拍照")).perform(click())
            // Demo never writes a synthetic image into the user's gallery.
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
