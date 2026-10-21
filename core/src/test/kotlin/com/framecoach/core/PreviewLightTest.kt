package com.framecoach.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewLightTest {
    @Test fun limitedRangeWhiteIsStillReportedAsBright() {
        assertEquals(1f, PreviewLight.fromLuma(FloatArray(20) { 235f / 255f }).highlights)
    }
    @Test fun videoBlackAndFullRangeWhiteAreBothHandled() {
        val light = PreviewLight.fromLuma(floatArrayOf(16f / 255f, 1f, .5f, .5f))
        assertEquals(.25f, light.shadows)
        assertEquals(.25f, light.highlights)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyInputCannotBecomeExposureAdvice() {
        PreviewLight.fromLuma(floatArrayOf())
    }
}
