package com.framecoach.app

import android.Manifest
import android.content.ContentUris
import android.os.SystemClock
import android.provider.MediaStore
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class CameraSmokeTest {
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun cameraStreamsAndShutterWritesAnOriginalToMediaStore() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        fun photoIds(): Set<Long> = resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("FrameCoach-%"), null)?.use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }.orEmpty()
        val before = photoIds()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withText("开启相机")).perform(click())
                val streaming = AtomicBoolean(false)
                val deadline = SystemClock.elapsedRealtime() + 20000
                while (!streaming.get() && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { activity -> streaming.set(activity.findViewById<PreviewView>(R.id.camera_preview).previewStreamState.value == PreviewView.StreamState.STREAMING) }
                    Thread.sleep(200)
                }
                assertTrue("Camera preview must reach STREAMING", streaming.get())
                onView(withText("●  拍照")).perform(click())
                val saveDeadline = SystemClock.elapsedRealtime() + 15000
                var saved = photoIds() - before
                while (saved.isEmpty() && SystemClock.elapsedRealtime() < saveDeadline) { Thread.sleep(300); saved = photoIds() - before }
                assertTrue("Shutter must create a new image", saved.isNotEmpty())
                saved.forEach { id ->
                    resolver.openInputStream(ContentUris.withAppendedId(collection, id)).use { input ->
                        assertTrue("Output must be a JPEG", input?.read() == 0xff && input.read() == 0xd8)
                    }
                }
            }
        } finally {
            (photoIds() - before).forEach { resolver.delete(ContentUris.withAppendedId(collection, it), null, null) }
        }
    }
}
