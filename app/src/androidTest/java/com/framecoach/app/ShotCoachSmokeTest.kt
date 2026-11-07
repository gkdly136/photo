package com.framecoach.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShotCoachSmokeTest {
    @Test fun photographerCanConfirmSkipUndoAndReadEvidenceWithoutResettingDwell() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            Thread.sleep(3400)
            onView(withId(R.id.shot_title)).check(matches(withText("先调整人物在画面中的位置")))
            screenshot("shot-coach.png")
            onView(withId(R.id.shot_skip)).perform(scrollTo(), click())
            onView(withId(R.id.shot_title)).check(matches(withText("检查主体与背景的关系")))
            onView(withId(R.id.shot_done)).perform(scrollTo(), click())
            onView(withId(R.id.shot_title)).check(matches(withText("先让身体放松")))
            onView(withId(R.id.shot_undo)).perform(scrollTo(), click())
            onView(withId(R.id.shot_title)).check(matches(withText("检查主体与背景的关系")))
            onView(withId(R.id.shot_facts)).perform(scrollTo(), click())
            onView(withText("画面观察与依据")).check(matches(isDisplayed()))
            onView(withText(containsString("背景语义、实际光源方向、情绪：尚未识别"))).check(matches(isDisplayed()))
            screenshot("shot-facts.png")
            onView(withText("返回")).perform(click())
        }
    }

    @Test fun intentsSurviveRecreationButSceneProgressDoesNotAndLandscapeCanRequestManualHorizon() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.shot_intent)).perform(click())
            onView(withText("人物与环境")).perform(click())
            scenario.recreate()
            onView(withId(R.id.shot_intent)).check(matches(withText(containsString("人物与环境"))))
            onView(withText("风光")).perform(click())
            onView(withId(R.id.shot_intent)).perform(click())
            onView(withText("突出天空")).perform(click())
            Thread.sleep(3400)
            onView(withId(R.id.shot_instruction)).check(matches(withText(containsString("点击预览标记实际水平线"))))
            screenshot("shot-landscape.png")
            onView(withId(R.id.shot_done)).perform(scrollTo(), click())
            scenario.recreate()
            onView(withId(R.id.shot_intent)).check(matches(withText(containsString("突出天空"))))
            Thread.sleep(3400)
            onView(withId(R.id.shot_title)).check(matches(withText("给天空更多画幅")))
        }
    }

    private fun screenshot(name: String) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
        val resolver = inst.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FrameCoachTestScreenshots"); put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } finally { bitmap.recycle() }
    }
}
