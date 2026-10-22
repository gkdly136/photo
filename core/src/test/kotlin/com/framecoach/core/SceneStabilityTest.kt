package com.framecoach.core

import org.junit.Assert.*
import org.junit.Test

class SceneStabilityTest {
    private val scene = FloatArray(80) { if (it % 2 == 0) .2f else .6f }

    @Test fun waitsTwoSecondsAndFiresOnlyOnce() {
        val gate = SceneStability()
        assertFalse(gate.update(scene, 0f, 0).ready)
        for (time in 200L..1800L step 200) assertFalse(gate.update(scene, 0f, time).triggered)
        assertTrue(gate.update(scene, 0f, 2000).triggered)
        assertTrue(gate.update(scene, 0f, 2200).ready)
        assertFalse(gate.update(scene, 0f, 2400).triggered)
    }
    @Test fun cameraMotionCancelsOldSuggestionAndRequiresAnotherDwell() {
        val gate = SceneStability()
        for (time in 0L..2000L step 200) gate.update(scene, 0f, time)
        val moved = scene.reversedArray()
        val state = gate.update(moved, 0f, 2200)
        assertTrue(state.moving); assertFalse(state.ready)
        for (time in 2400L..4000L step 200) assertFalse(gate.update(moved, 0f, time).triggered)
        assertTrue(gate.update(moved, 0f, 4200).triggered)
    }
    @Test fun gyroRejectsRotationEvenInUniformScene() {
        val gate = SceneStability()
        val flat = FloatArray(80) { .3f }
        for (time in 0L..2000L step 200) gate.update(flat, 0f, time)
        assertFalse(gate.update(flat, .2f, 2200).moving)
        assertFalse(gate.update(flat, .2f, 2400).moving)
        assertTrue(gate.update(flat, .2f, 2600).moving)
    }
    @Test fun slowAccumulatedPanCannotKeepOldAdviceForever() {
        val gate = SceneStability()
        var moved = false
        for (step in 0..22) {
            val gradual = FloatArray(80) { scene[it] + if (it % 2 == 0) step * .004f else -step * .004f }
            moved = moved || gate.update(gradual, 0f, step * 200L).moving
        }
        assertTrue(moved)
    }
    @Test fun exposureChangeAloneDoesNotLookLikeCameraMotion() {
        val gate = SceneStability()
        gate.update(scene, 0f, 0)
        assertFalse(gate.update(scene.map { it + .1f }.toFloatArray(), 0f, 200).moving)
    }
    @Test fun resumeAfterGapCannotInstantlyGenerateAdvice() {
        val gate = SceneStability()
        gate.update(scene, 0f, 0)
        val state = gate.update(scene, 0f, 5000)
        assertEquals(0f, state.progress)
        assertFalse(state.ready)
    }
    @Test fun resetOrChangedSampleGeometryRestartsDwell() {
        val gate = SceneStability()
        for (time in 0L..2000L step 200) gate.update(scene, 0f, time)
        gate.reset()
        assertFalse(gate.update(scene, 0f, 2200).ready)
        assertFalse(gate.update(FloatArray(20) { .4f }, 0f, 2400).ready)
    }
    @Test fun backwardsClockRestartsDwell() {
        val gate = SceneStability()
        gate.update(scene, 0f, 1000)
        assertEquals(0f, gate.update(scene, 0f, 500).progress)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidPixelsCannotProduceAdvice() {
        SceneStability().update(floatArrayOf(Float.NaN), 0f, 0)
    }

    @Test fun ordinaryHandTremorCanEnterWithinThreeSeconds() {
        val gate = SceneStability(2500)
        val gyro = GyroSmoother()
        var enteredAt = -1L
        for (time in 0L..3000L step 20) {
            val sign = if (time / 20 % 2 == 0L) 1f else -1f
            val speed = gyro.update(sign * .16f, sign * .1f, 0f, time)
            if (time % 200L == 0L) {
                val frame = FloatArray(scene.size) { scene[it] + sign * .025f * if (it % 2 == 0) 1f else -1f }
                val state = gate.update(frame, speed, time)
                assertFalse(state.moving)
                if (state.triggered) enteredAt = time
            }
        }
        assertTrue("handheld jitter must not starve advice", enteredAt in 2500L..3000L)
    }

    @Test fun isolatedSensorSpikePausesInsteadOfErasingDwell() {
        val gate = SceneStability(2500)
        for (time in 0L..1800L step 200) gate.update(scene, 0f, time)
        val spike = gate.update(scene, .8f, 2000)
        assertTrue(spike.progress >= .7f); assertFalse(spike.moving)
        for (time in 2200L..2600L step 200) gate.update(scene, 0f, time)
        assertTrue(gate.update(scene, 0f, 2800).ready)
    }

    @Test fun briefDisturbanceDoesNotDismissVisibleAdvice() {
        val gate = SceneStability()
        for (time in 0L..2000L step 200) gate.update(scene, 0f, time)
        assertTrue(gate.update(scene, .8f, 2200).ready)
        assertTrue(gate.update(scene, 0f, 2400).ready)
    }

    @Test fun manualAssistStillExpiresAfterARealSceneChange() {
        val gate = SceneStability(3000)
        assertFalse(gate.assistNow())
        gate.update(scene, 0f, 0); assertTrue(gate.assistNow())
        assertTrue(gate.update(scene, 0f, 200).ready)
        assertFalse(gate.update(scene.reversedArray(), 0f, 400).ready)
    }

    @Test fun filteredSlowRotationCannotHoldOldAdviceInUniformScene() {
        val gate = SceneStability()
        val flat = FloatArray(80) { .3f }
        for (time in 0L..2000L step 200) gate.update(flat, 0f, time)
        var cancelled = false
        for (time in 2200L..3600L step 200) cancelled = gate.update(flat, .1f, time).moving || cancelled
        assertTrue(cancelled)
    }

    @Test fun signedGyroFilterPreservesContinuousRotation() {
        val filter = GyroSmoother()
        var speed = 0f
        for (time in 0L..1000L step 20) speed = filter.update(.3f, 0f, 0f, time)
        assertTrue(speed > .29f)
        filter.reset()
        assertTrue(filter.update(0f, 0f, 0f, 1100) < .001f)
    }
}
