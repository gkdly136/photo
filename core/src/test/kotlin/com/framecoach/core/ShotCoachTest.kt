package com.framecoach.core

import org.junit.Assert.*
import org.junit.Test

class ShotCoachTest {
    private val config = ShotSettings(SceneMode.PORTRAIT, ShotIntent.PERSON, Layout.THIRDS, Mood.NATURAL)
    private val landscape = ShotSettings(SceneMode.LANDSCAPE, ShotIntent.SKY, Layout.THIRDS, Mood.NATURAL)
    private fun face(id: Int = 1, x: Float = .8f, y: Float = .4f) = Face(Point(x, y), .12f, .15f, 0f, null, id)
    private fun obs(vararg faces: Face, at: Long = 1000, session: Long = 1, light: Light = Light(.5f, .1f, .02f)) =
        Observation(light, faces.toList(), atMs = at, sessionId = session, lightAtMs = at)
    private fun inspect(s: ShotCoachSession, o: Observation = obs(face()), settings: ShotSettings = config, now: Long = 1000, session: Long = 1, horizon: Float? = null) = s.inspect(settings, o, now, session, horizon)

    @Test fun multipleFacesRequireExplicitChoiceInsteadOfLargest() {
        val s = ShotCoachSession(); val o = obs(face(), face(2, .2f))
        val p = inspect(s, o)
        assertEquals("choose", p.step.id); assertFalse(p.step.actionable); assertNull(p.step.target)
        assertFalse(s.advance()); assertTrue(s.chooseSubject(2, o, 1000, 1))
        val chosen = inspect(s, o)
        assertTrue(chosen.step.target!!.x < .5f)
        assertTrue(chosen.facts.any { it.contains("手动点选") })
    }
    @Test fun selectedSubjectDisappearingNeverFallsBackToRemainingPerson() {
        val s = ShotCoachSession(); val o = obs(face(), face(2, .2f))
        inspect(s, o); s.chooseSubject(2, o, 1000, 1)
        assertEquals("lost", inspect(s, obs(face())).step.id)
        assertFalse(s.advance())
        assertNotEquals("lost", inspect(s, o).step.id)
    }
    @Test fun subjectSelectionRejectsStaleFutureWrongSessionAndDuplicateTrackIds() {
        val s = ShotCoachSession()
        assertFalse(s.chooseSubject(1, obs(face()), 2300, 1))
        assertFalse(s.chooseSubject(1, obs(face()), 999, 1))
        assertFalse(s.chooseSubject(1, obs(face()), 1000, 2))
        assertFalse(s.chooseSubject(1, obs(face(), face()), 1000, 1))
    }
    @Test fun allStaleGeometryWithdrawsTargetAndCannotBeConfirmed() {
        val s = ShotCoachSession(); assertNotNull(inspect(s).step.target)
        val p = inspect(s, now = 2300)
        assertEquals("stale", p.step.id); assertNull(p.step.target); assertFalse(s.advance())
        assertTrue(p.facts.none { it.contains("人脸框占") })
    }
    @Test fun detectorFailureIsDifferentFromNoFace() {
        assertEquals("error", inspect(ShotCoachSession(), obs(face()).copy(detectorError = true)).step.id)
        assertEquals("no-face", inspect(ShotCoachSession(), obs()).step.id)
    }
    @Test fun completingSkippingAndUndoingOperateOnOneShownStep() {
        val s = ShotCoachSession(); val first = inspect(s)
        assertEquals("framing", first.step.id); assertTrue(s.advance())
        assertFalse(s.advance())
        assertEquals("context", inspect(s).step.id); assertTrue(s.advance(true))
        val p = inspect(s); assertEquals("action", p.step.id)
        assertEquals(1, p.completed); assertEquals(1, p.skipped)
        assertTrue(s.undo()); assertEquals("context", inspect(s).step.id)
        assertTrue(s.undo()); assertEquals("framing", inspect(s).step.id); assertFalse(s.undo())
    }
    @Test fun confirmedGeometryDoesNotKeepDemandingAnotherAdjustment() {
        val s = ShotCoachSession(); inspect(s); s.advance()
        assertEquals("context", inspect(s).step.id)
        assertEquals("context", inspect(s, obs(face(x = .9f))).step.id)
    }
    @Test fun alignedFaceDoesNotCreateUnnecessaryMoveAndWorkflowCanEnd() {
        val s = ShotCoachSession(); val aligned = obs(face(x = 1f / 3, y = 1f / 3))
        assertEquals("context", inspect(s, aligned).step.id)
        repeat(3) { s.advance(); inspect(s, aligned) }
        val end = inspect(s, aligned)
        assertEquals("finished", end.step.id); assertFalse(end.step.actionable); assertNull(end.step.target)
    }
    @Test fun activeFramingConfirmsAlignmentWithoutSilentlyChangingActors() {
        val s = ShotCoachSession(); inspect(s)
        val p = inspect(s, obs(face(x = 2f / 3, y = 1f / 3)))
        assertEquals("framing", p.step.id); assertEquals(ShotActor.CAMERA, p.step.actor)
        assertTrue(p.step.tip.title.contains("接近")); assertEquals(0, p.completed)
    }
    @Test fun framingDirectionsReferToPreviewOneAxisAtATime() {
        val s = ShotCoachSession(); val p = inspect(s, obs(face(x = .95f, y = .33f)))
        assertTrue(p.step.tip.instruction.contains("向左")); assertTrue(p.step.tip.instruction.contains("人物先保持原位"))
        assertFalse(p.step.tip.instruction.contains("向上"))
    }
    @Test fun depthLayoutNeverInventsPreciseSubjectTarget() {
        val p = inspect(ShotCoachSession(), settings = config.copy(layout = Layout.DEPTH))
        assertNull(p.step.target); assertEquals("context", p.step.id)
    }
    @Test fun intentChangesContextWithoutClaimingBackgroundRecognition() {
        val s = ShotCoachSession(); val aligned = obs(face(x = 1f / 3, y = 1f / 3))
        val p = inspect(s, aligned, config.copy(intent = ShotIntent.ENVIRONMENT))
        assertTrue(p.step.tip.instruction.contains("地点"))
        assertTrue(p.step.tip.evidence.contains("尚未测量"))
    }
    @Test fun selectedPoseAndMoodDriveAuthoredScriptsWithoutEmotionInference() {
        val s = ShotCoachSession(); val c = config.copy(layout = Layout.DEPTH, mood = Mood.QUIET, poseId = "fc-013")
        inspect(s, settings = c); s.advance()
        val action = inspect(s, settings = c)
        assertEquals(ShotActor.PERSON, action.step.actor); assertEquals(PoseLibrary.find("fc-013")!!.instruction, action.step.tip.instruction)
        s.advance(); val expression = inspect(s, settings = c)
        assertTrue(expression.step.tip.evidence.contains("不是情绪识别"))
    }
    @Test fun newSubjectSceneAndGoalResetHistoryAndRevision() {
        val s = ShotCoachSession(); val first = inspect(s); s.advance(); inspect(s)
        val newPerson = inspect(s, obs(face(2)))
        assertEquals(0, newPerson.completed); assertTrue(newPerson.revision > first.revision)
        s.advance(); val changed = inspect(s, obs(face(2)), config.copy(poseId = "fc-002"))
        assertEquals(0, changed.completed)
        val newScene = inspect(s, obs(face(), session = 2), session = 2)
        assertEquals(0, newScene.completed); assertTrue(newScene.revision > changed.revision)
    }
    @Test fun landscapeUsesFreshLightRatherThanRequiringAnAbsentFaceTimestamp() {
        val s = ShotCoachSession(); val o = obs().copy(atMs = -1)
        assertEquals("horizon", inspect(s, o, landscape).step.id)
        assertEquals("stale", inspect(s, o.copy(lightAtMs = -1), landscape).step.id)
    }
    @Test fun horizonIsManualAndRespectsSkyVersusGroundIntent() {
        val s = ShotCoachSession(); val sky = inspect(s, obs(), landscape)
        assertTrue(sky.step.tip.instruction.contains("标记")); assertNull(sky.step.target)
        assertTrue(inspect(s, obs(), landscape, horizon = .4f).step.tip.instruction.contains("下三分"))
        assertTrue(inspect(s, obs(), landscape.copy(intent = ShotIntent.GROUND), horizon = .5f).step.tip.instruction.contains("上三分"))
    }
    @Test fun invalidGeometryDoesNotProduceTargets() {
        for (f in listOf(face().copy(width = Float.NaN), face().copy(height = -1f), face().copy(center = Point(Float.NaN, .5f)))) {
            val p = inspect(ShotCoachSession(), obs(f)); assertNull(p.step.target); assertFalse(p.step.actionable)
        }
    }
    @Test fun highlightWarningDoesNotAutomaticallyChangeExposureOrClaimRawClipping() {
        val p = inspect(ShotCoachSession(), obs(face(), light = Light(.7f, .01f, .2f)))
        assertEquals("light", p.step.id); assertEquals(ShotActor.REVIEW, p.step.actor)
        assertTrue(p.step.tip.evidence.contains("不等于 RAW"))
    }
    @Test fun unknownLightTimestampNeverCreatesExposureDiagnosis() {
        val p = inspect(ShotCoachSession(), obs(face(), light = Light(.9f, 0f, .8f)).copy(lightAtMs = -1))
        assertNotEquals("light", p.step.id)
    }
    @Test fun faceBoxAreaKeepsSmallFractionsAndClipsToVisibleViewport() {
        val small = inspect(ShotCoachSession(), obs(face().copy(width = .08f, height = .1f)))
        assertTrue(small.facts.any { it.contains("可见人脸框占画面 0.8%") })
        val clipped = inspect(ShotCoachSession(), obs(face(x = .5f, y = .5f).copy(width = 2f, height = 2f)))
        assertTrue(clipped.facts.any { it.contains("可见人脸框占画面 100.0%") })
    }
}
