package com.framecoach.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoseLibrarySmokeTest {
    @Test fun originalReferenceCanBeSelectedLockedAndClearedWithoutCameraPermission() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(3100)
            onView(withId(R.id.pose_library)).perform(click())
            onView(withText("姿势库 · 40 个原创动作")).check(matches(isDisplayed()))
            onView(withId(R.id.pose_count)).check(matches(withText(containsString("符合条件 12 / 40"))))
            screenshot("pose-library.png")
            onView(withContentDescription("查看姿势：fc-001")).perform(click())
            onView(withId(R.id.pose_lock)).perform(click())
            onView(withId(R.id.pose_status)).check(matches(withText(containsString("已锁定：自然站立"))))
            // New demo observations must not replace the user's selected target.
            Thread.sleep(1600)
            onView(withId(R.id.pose_status)).check(matches(withText(containsString("已锁定：自然站立"))))
            Thread.sleep(2100)
            screenshot("pose-reference.png")
            scenario.recreate()
            onView(withId(R.id.pose_status)).check(matches(withText("选一个想尝试的动作")))
            onView(withText("风光")).perform(click())
            onView(withId(R.id.pose_library)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test fun requiredChairAndFavoritesRemainExplicitInTheLibrary() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.pose_library)).perform(click())
            onView(withId(R.id.pose_posture)).perform(click()); onData(equalTo("坐姿")).perform(click())
            onView(withId(R.id.pose_count)).check(matches(withText(containsString("符合条件 0 / 40"))))
            onView(withId(R.id.pose_chair)).perform(click())
            onView(withId(R.id.pose_count)).check(matches(withText(containsString("符合条件 12 / 40"))))
            onView(withContentDescription("坐姿双手放膝，收藏")).perform(click())
            onView(withId(R.id.pose_favorites)).perform(click())
            onView(withId(R.id.pose_count)).check(matches(withText(containsString("符合条件 1 / 40"))))
            screenshot("pose-filters.png")
            onView(withContentDescription("坐姿双手放膝，已收藏")).perform(click())
            onView(withId(R.id.pose_count)).check(matches(withText(containsString("符合条件 0 / 40"))))
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FrameCoachTestScreenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } finally { bitmap.recycle() }
    }
}
