package com.framecoach.core

import org.junit.Assert.*
import org.junit.Test

class CoachTest {
    private val light = Light(.5f, .1f, .02f)
    private fun advise(observation: Observation, mode: SceneMode = SceneMode.PORTRAIT, layout: Layout = Layout.THIRDS, mood: Mood = Mood.NATURAL) = Coach.advise(mode, layout, mood, observation)

    @Test fun missingFaceIsExplicitAndDoesNotInventPoseDiagnosis() {
        val guidance = advise(Observation(light))
        assertTrue(guidance.tips.any { it.evidence.contains("尚未检测到") })
        assertTrue(guidance.tips.any { it.evidence.contains("通用模板") })
        assertNotNull(guidance.target)
    }
    @Test fun lowConfidenceJointsNeverTriggerArmsDownInstruction() {
        val body = Body(mapOf(Joint.LEFT_WRIST to Point(.3f, .8f, .1f), Joint.RIGHT_WRIST to Point(.6f, .8f, .1f), Joint.LEFT_HIP to Point(.4f, .6f), Joint.RIGHT_HIP to Point(.5f, .6f)))
        assertFalse(advise(Observation(light, body = body)).tips.any { it.title == "给手一个自然的落点" })
    }
    @Test fun highConfidenceArmsDownCanPromptHandPlacement() {
        val body = Body(mapOf(Joint.LEFT_WRIST to Point(.3f, .8f), Joint.RIGHT_WRIST to Point(.6f, .8f), Joint.LEFT_HIP to Point(.4f, .6f), Joint.RIGHT_HIP to Point(.5f, .6f)))
        assertTrue(advise(Observation(light, body = body)).tips.any { it.title == "给手一个自然的落点" })
    }
    @Test fun largestFaceGuidesPlacementAndWarnsAboutMultiplePeople() {
        val small = Face(Point(.2f, .3f), .1f, .1f, 0f, null)
        val large = Face(Point(.9f, .3f), .3f, .3f, 0f, null)
        val guidance = advise(Observation(light, listOf(small, large)))
        assertEquals(2f / 3, guidance.target!!.x)
        assertTrue(guidance.tips.any { it.category == "多人" })
        assertTrue(guidance.tips.first { it.category == "构图" }.instruction.contains("向左"))
    }
    @Test fun centerLayoutIsAnAlternativeToThirds() {
        assertEquals(.5f, advise(Observation(light), layout = Layout.CENTER).target!!.x)
    }
    @Test fun landscapeHasNoPortraitOrExpressionInstructions() {
        val guidance = advise(Observation(light), SceneMode.LANDSCAPE)
        assertNull(guidance.target)
        assertNull(guidance.horizon)
        assertFalse(guidance.tips.any { it.category == "表情" || it.category == "动作" })
    }
    @Test fun userMarkedHorizonIsClampedButNeverInvented() {
        assertEquals(1f, Coach.advise(SceneMode.LANDSCAPE, Layout.THIRDS, Mood.NATURAL, Observation(light), 1.4f).horizon!!)
        assertNull(Coach.advise(SceneMode.PORTRAIT, Layout.THIRDS, Mood.NATURAL, Observation(light), .5f).horizon)
    }
    @Test fun lightHintsDistinguishPreviewFromRawExposure() {
        val tip = advise(Observation(Light(.8f, 0f, .2f))).tips.first()
        assertTrue(tip.title.contains("亮部")); assertTrue(tip.evidence.contains("RAW"))
    }
    @Test fun expressionComesFromUserPreferenceNotSmileProbability() {
        val face = Face(Point(.3f, .3f), .1f, .1f, 0f, .99f)
        val tip = advise(Observation(light, listOf(face)), mood = Mood.QUIET).tips.first { it.category == "表情" }
        assertTrue(tip.evidence.contains("你选择了")); assertTrue(tip.title.contains("远处"))
    }
    @Test fun detectorFailureIsVisibleRatherThanSilentNoPerson() {
        assertTrue(advise(Observation(light, detectorError = true)).tips.any { it.category == "检测" })
    }
    @Test fun allRecommendationsIncludeRationaleAndSources() {
        for (mode in SceneMode.entries) for (layout in Layout.entries) for (mood in Mood.entries) {
            Coach.advise(mode, layout, mood, Observation(light)).tips.forEach {
                assertTrue(it.instruction.isNotBlank()); assertTrue(it.evidence.isNotBlank()); assertTrue(it.sourceIds.isNotEmpty())
            }
        }
    }
}
