package com.framecoach.core

import org.junit.Assert.*
import org.junit.Test

class PoseSessionTest {
    private val light = Light(.5f, .1f, .02f)
    private fun card(id: Int) = PoseLibrary.find("fc-${id.toString().padStart(3, '0')}")!!
    private fun observation(body: Body = card(2).sketch(PoseView.FRONT), at: Long = 1000, tracking: Int = 1) =
        Observation(light, listOf(Face(Point(.5f, .2f), .1f, .12f, 0f, null, tracking)), body, atMs = at, sessionId = 7)
    private fun session(id: Int = 2) = PoseSession().apply { assertTrue(select(card(id).id, 1000)) }
    private fun settle(session: PoseSession, body: Body = card(2).sketch(PoseView.FRONT)): PoseMatch {
        session.update(observation(body, 1000), 1000, true)
        session.update(observation(body, 1200), 1200, true)
        return session.update(observation(body, 1400), 1400, true)
    }
    @Test fun catalogHasFortyOriginalCardsInThePlannedFourGroups() {
        assertEquals(40, PoseLibrary.cards.size); assertEquals(40, PoseLibrary.cards.map { it.id }.toSet().size)
        assertEquals(mapOf(Posture.STANDING to 12, Posture.SITTING to 12, Posture.LEANING to 8, Posture.MOVING to 8), PoseLibrary.cards.groupingBy { it.posture }.eachCount())
        PoseLibrary.cards.forEach { c -> PoseView.entries.forEach { v -> assertEquals(12, c.sketch(v).joints.size) } }
    }
    @Test fun propsAndFramingAreHardConditionsNotSoftRankingHints() {
        assertTrue(PoseLibrary.filtered(PoseConditions(posture = Posture.SITTING)).isEmpty())
        assertEquals(12, PoseLibrary.filtered(PoseConditions(posture = Posture.SITTING, chair = true)).size)
        assertTrue(PoseLibrary.filtered(PoseConditions(posture = Posture.LEANING)).isEmpty())
        assertEquals(8, PoseLibrary.filtered(PoseConditions(posture = Posture.LEANING, wall = true)).size)
        assertFalse(PoseLibrary.filtered(PoseConditions(posture = Posture.MOVING, framing = Framing.HALF)).any { it.fullBodyOnly })
    }
    @Test fun favoritesCannotBypassConditionsAndPagingDoesNotChooseTheTarget() {
        assertTrue(PoseLibrary.filtered(PoseConditions(), setOf("fc-013"), true).isEmpty())
        val s = session(); val before = s.ticket()
        assertEquals(3, PoseLibrary.recommend(s.conditions, 3).size)
        assertEquals("fc-002", s.selected?.id); assertEquals(before, s.ticket())
        assertNotEquals(PoseLibrary.recommend(s.conditions), PoseLibrary.recommend(s.conditions, 3))
    }
    @Test fun exactArmReferenceIsCloseAndNoLegAccuracyIsClaimed() {
        val result = PoseGeometry.compare(card(2), PoseView.FRONT, card(2).sketch(PoseView.FRONT), 1f)!!
        assertEquals(PoseMatchState.CLOSE, result.state); assertTrue(result.instruction.contains("腿部"))
        assertEquals(8, result.target!!.joints.size)
    }
    @Test fun translationScaleAndViewportAspectDoNotChangeTheArmDecision() {
        val original = card(7).sketch(PoseView.FRONT)
        for (aspect in listOf(.55f, 1f, 1.8f)) {
            val transformed = Body(original.joints.mapValues { (_, p) -> p.copy(x = .5f + (p.x - .5f) * .6f / aspect, y = .2f + p.y * .6f) })
            assertEquals(PoseMatchState.CLOSE, PoseGeometry.compare(card(7), PoseView.FRONT, transformed, aspect)!!.state)
        }
    }
    @Test fun mirroredPreviewPreservesAnatomicalHandButReversesScreenDirection() {
        val down = card(1).sketch(PoseView.FRONT)
        val a = PoseGeometry.compare(card(2), PoseView.FRONT, down, 1f)!!
        val b = PoseGeometry.compare(card(2), PoseView.FRONT, Body(down.joints.mapValues { (_, p) -> p.copy(x = 1 - p.x) }), 1f)!!
        assertTrue(a.instruction.contains("左手")); assertTrue(b.instruction.contains("左手"))
        assertTrue(a.instruction.contains("画面左侧")); assertTrue(b.instruction.contains("画面右侧"))
    }
    @Test fun missingLowConfidenceOrNonFiniteArmDoesNotInventACorrection() {
        val body = card(2).sketch(PoseView.FRONT)
        assertNull(PoseGeometry.compare(card(2), PoseView.FRONT, Body(body.joints - Joint.LEFT_WRIST), 1f))
        for (bad in listOf(Point(.6f, .5f, .1f), Point(Float.NaN, .5f), Point(.6f, .5f, Float.POSITIVE_INFINITY))) {
            assertNull(PoseGeometry.compare(card(2), PoseView.FRONT, Body(body.joints + (Joint.LEFT_WRIST to bad)), 1f))
        }
    }
    @Test fun edgeOnOrOutOfFrameTargetIsReferenceOnly() {
        val body = card(2).sketch(PoseView.FRONT)
        val edge = Body(body.joints.mapValues { (_, p) -> p.copy(x = .5f + (p.x - .5f) * .05f) })
        assertNull(PoseGeometry.compare(card(2), PoseView.FRONT, edge, 1f))
        val border = Body(body.joints.mapValues { (_, p) -> p.copy(x = p.x - .35f) })
        assertNull(PoseGeometry.compare(card(10), PoseView.FRONT, border, 1f))
    }
    @Test fun continuousNewFramesAreRequiredBeforeShowingAMeasuredCue() {
        val s = session()
        assertFalse(s.update(observation(), 1000, true).measured)
        assertFalse(s.update(observation(), 1400, true).measured)
        assertFalse(s.update(observation(at = 1400), 1400, true).measured)
        assertTrue(s.update(observation(at = 1600), 1600, true).measured)
    }
    @Test fun lossOfEvidenceImmediatelyWithdrawsAShownCueAndTarget() {
        val s = session(); assertTrue(settle(s).measured)
        val result = s.update(observation(at = 1600).copy(body = null), 1600, true)
        assertEquals(PoseMatchState.REFERENCE, result.state); assertNull(result.target); assertFalse(result.measured)
        assertEquals("fc-002", s.selected?.id)
    }
    @Test fun staleFutureAndWrongSessionFramesNeverProduceMeasuredGuidance() {
        val s = session(); settle(s)
        assertFalse(s.update(observation(at = 1400), 2500, true).measured)
        assertFalse(s.update(observation(at = 3000), 2000, true).measured)
        assertFalse(s.update(observation(at = 2000), 2000, true, expectedSession = 8).measured)
        s.select("fc-007", 2100)
        assertFalse(s.update(observation(at = 2000), 2200, true).measured)
    }
    @Test fun multipleFacesAndNoFaceDoNotAssociateTheSdkSkeletonToAChosenPerson() {
        val s = session(); settle(s)
        val one = observation(at = 1600)
        val multiple = s.update(one.copy(faces = one.faces + one.faces), 1600, true)
        assertFalse(multiple.measured); assertTrue(multiple.instruction.contains("多人")); assertNull(multiple.target)
        assertFalse(s.update(one.copy(faces = emptyList()), 1600, true).measured)
    }
    @Test fun subjectChangeClearsSelectionAndInvalidatesOldTargetTicket() {
        val s = session(); settle(s); val ticket = s.ticket()
        assertEquals(PoseMatchState.NONE, s.update(observation(at = 1600, tracking = 2), 1600, true).state)
        assertNull(s.selected); assertFalse(s.accepts(ticket))
    }
    @Test fun sceneClearAndIncompatibleConditionsInvalidateSelection() {
        val s = session(); val ticket = s.ticket(); s.clear()
        assertNull(s.selected); assertFalse(s.accepts(ticket))
        s.select("fc-002", 1000); s.setConditions(PoseConditions(posture = Posture.SITTING, chair = true), 1200)
        assertNull(s.selected)
        assertFalse(s.select("fc-025", 1300))
    }
    @Test fun viewChangesInvalidateFeedbackWithoutSilentlyChoosingAnotherCard() {
        val s = session(); settle(s); val ticket = s.ticket()
        s.setConditions(s.conditions.copy(view = PoseView.OBLIQUE), 1600)
        assertFalse(s.accepts(ticket)); assertEquals("fc-002", s.selected?.id)
        assertFalse(s.update(observation(at = 1600), 1600, true).measured)
    }
    @Test fun movingAndHiddenHandsAreNeverAutomaticallyCorrected() {
        val s = PoseSession(); s.setConditions(PoseConditions(posture = Posture.MOVING), 0); s.select("fc-035", 1000)
        val match = s.update(observation(at = 1400), 1400, true)
        assertFalse(match.measured); assertNull(match.target)
        assertNull(PoseGeometry.compare(card(12), PoseView.FRONT, card(12).sketch(PoseView.FRONT), 1f))
    }
    @Test fun inactiveSceneAndDetectorFailureWithdrawOldGuidance() {
        val s = session(); settle(s)
        assertFalse(s.update(observation(at = 1600), 1600, false).measured)
        assertFalse(s.update(observation(at = 1800).copy(detectorError = true), 1800, true).measured)
    }
    @Test fun targetDoesNotChaseCurrentPoseAndFeedbackHasACooldown() {
        val s = session(); assertEquals(PoseMatchState.CLOSE, settle(s).state)
        val down = card(1).sketch(PoseView.FRONT)
        s.update(observation(down, 1600), 1600, true)
        assertEquals(PoseMatchState.OBSERVING, s.update(observation(down, 1800), 1800, true).state)
        assertEquals(PoseMatchState.OBSERVING, s.update(observation(down, 2000), 2000, true).state)
        s.update(observation(down, 2200), 2200, true)
        s.update(observation(down, 2400), 2400, true)
        assertEquals(PoseMatchState.ADJUST, s.update(observation(down, 2600), 2600, true).state)
        assertEquals("fc-002", s.selected?.id)
    }
}
