package com.framecoach.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

enum class PoseMatchState { NONE, REFERENCE, OBSERVING, ADJUST, CLOSE }
data class PoseMatch(val state: PoseMatchState, val title: String, val instruction: String,
                     val cueKey: String = "", val target: Body? = null, val measured: Boolean = false)
data class PoseTicket(val scene: Long, val target: Long)

/** User selection is persistent within one scene; matching never selects a different reference. */
class PoseSession {
    var conditions = PoseConditions(); private set
    var selected: PoseCard? = null; private set
    private var scene = 0L
    private var targetVersion = 0L
    private var selectedAt = 0L
    private var subject: Int? = null
    private var candidateKey = ""
    private var candidateSince = -1L
    private var candidateSamples = 0
    private var lastSample = -1L
    private var shownAt = -1L
    private var shown: PoseMatch? = null
    fun ticket() = PoseTicket(scene, targetVersion)
    fun accepts(expected: PoseTicket) = expected == ticket()
    fun setConditions(value: PoseConditions, nowMs: Long) {
        if (conditions == value) return
        conditions = value
        if (selected?.eligible(value) == false) clear()
        else { targetVersion++; selectedAt = nowMs; resetFeedback() }
    }
    fun select(id: String, nowMs: Long): Boolean {
        val card = PoseLibrary.find(id)?.takeIf { it.eligible(conditions) } ?: return false
        selected = card; targetVersion++; selectedAt = nowMs; subject = null; resetFeedback()
        return true
    }
    fun clear() { selected = null; scene++; targetVersion++; subject = null; resetFeedback() }
    private fun resetFeedback() { candidateKey = ""; candidateSince = -1; candidateSamples = 0; lastSample = -1; shownAt = -1; shown = null }
    private fun reference(reason: String): PoseMatch {
        resetFeedback()
        return PoseMatch(PoseMatchState.REFERENCE, "参考动作 · ${selected?.title.orEmpty()}", reason)
    }

    fun update(observation: Observation, nowMs: Long, active: Boolean, expectedSession: Long = observation.sessionId): PoseMatch {
        val card = selected ?: return PoseMatch(PoseMatchState.NONE, "选择一个想尝试的姿势", "根据全身或半身、站姿或坐姿选择参考。")
        if (!active) return reference("取景稳定后再对照手臂；也可手动请求辅助。")
        if (observation.sessionId != expectedSession || observation.atMs < selectedAt || observation.atMs > nowMs || nowMs - observation.atMs > 900)
            return reference("正在等待当前取景的新画面，暂不提供关节纠正。")
        if (observation.detectorError) return reference("检测暂不可用，可以继续查看参考图。")
        if (observation.faces.size != 1) return reference(if (observation.faces.size > 1) "多人时仅展示参考，不自动关联某个人的骨架。" else "需要看清一张人脸；背身或遮挡时只展示参考。")
        val face = observation.faces.single()
        val track = face.trackingId
        if (track != null && subject != null && track != subject) {
            clear()
            return PoseMatch(PoseMatchState.NONE, "主角可能已改变", "请重新选择姿势，旧动作提示已撤回。")
        }
        if (track != null) subject = track
        if (!card.matchingEnabled) return reference("动态或遮挡动作只作示范；先站稳，再尝试，不做关节纠正。")
        val yaw = abs(face.yaw)
        if (!yaw.isFinite() || (conditions.view == PoseView.FRONT && yaw > 28) ||
            (conditions.view == PoseView.OBLIQUE && (yaw < 12 || yaw > 65)))
            return reference("当前头向与所选视角差异较大；可改选视角或只参考示意。")
        val body = observation.body ?: return reference("人体关键点不足，继续查看参考动作。")
        val raw = PoseGeometry.compare(card, conditions.view, body, observation.frameAspect)
            ?: return reference("肩、髋或手臂未看清，暂不显示精确关节提示。")
        fun presentation(): PoseMatch = shown?.takeIf { it.cueKey == raw.cueKey }?.copy(target = raw.target)
            ?: PoseMatch(PoseMatchState.OBSERVING, "正在观察手臂", "保持舒适，连续看清后只提示一个部位。")
        if (observation.atMs <= lastSample) return presentation()
        val gap = if (lastSample < 0) 0 else observation.atMs - lastSample
        lastSample = observation.atMs
        if (raw.cueKey != candidateKey || gap > 450) { candidateKey = raw.cueKey; candidateSince = observation.atMs; candidateSamples = 0 }
        candidateSamples++
        if (candidateSamples >= 3 && observation.atMs - candidateSince >= 400 && (shown == null || raw.cueKey == shown?.cueKey || observation.atMs - shownAt >= 1200)) {
            if (raw.cueKey != shown?.cueKey) shownAt = observation.atMs
            shown = raw
        }
        return presentation()
    }
}

/** Torso-relative arm geometry in aspect-correct metric coordinates. No beauty score or 3D claim. */
object PoseGeometry {
    private val anchors = listOf(Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER, Joint.LEFT_HIP, Joint.RIGHT_HIP)
    private val arms = listOf(Joint.LEFT_WRIST, Joint.RIGHT_WRIST, Joint.LEFT_ELBOW, Joint.RIGHT_ELBOW)
    fun compare(card: PoseCard, view: PoseView, body: Body, aspect: Float): PoseMatch? {
        if (!aspect.isFinite() || aspect !in .2f..5f || !card.matchingEnabled) return null
        val needed = anchors + arms
        if (needed.any { body.visible(it) == null }) return null
        fun metric(p: Point) = Point(p.x * aspect, p.y)
        fun mid(a: Point, b: Point) = Point((a.x + b.x) / 2, (a.y + b.y) / 2)
        val actual = needed.associateWith { metric(body.visible(it)!!) }
        val shoulders = mid(actual.getValue(Joint.LEFT_SHOULDER), actual.getValue(Joint.RIGHT_SHOULDER))
        val hips = mid(actual.getValue(Joint.LEFT_HIP), actual.getValue(Joint.RIGHT_HIP))
        // Vertical scale preserves an actual body lean instead of rotating it away.
        val torso = hips.y - shoulders.y
        val span = abs(actual.getValue(Joint.LEFT_SHOULDER).x - actual.getValue(Joint.RIGHT_SHOULDER).x)
        if (torso !in .06f.. .7f || span < torso * .22f || span > torso * 2.5f || abs(hips.x - shoulders.x) > torso * .65f) return null
        val handedness = sign(actual.getValue(Joint.LEFT_SHOULDER).x - actual.getValue(Joint.RIGHT_SHOULDER).x)
        val reference = card.sketch(view)
        val rs = mid(reference.joints.getValue(Joint.LEFT_SHOULDER), reference.joints.getValue(Joint.RIGHT_SHOULDER))
        val rh = mid(reference.joints.getValue(Joint.LEFT_HIP), reference.joints.getValue(Joint.RIGHT_HIP))
        val referenceTorso = rh.y - rs.y
        val referenceSpan = abs(reference.joints.getValue(Joint.LEFT_SHOULDER).x - reference.joints.getValue(Joint.RIGHT_SHOULDER).x)
        val target = needed.associateWith { joint ->
            if (joint in anchors) body.visible(joint)!! else {
                val p = reference.joints.getValue(joint)
                Point((shoulders.x + (p.x - rs.x) / referenceSpan * span * handedness) / aspect,
                    shoulders.y + (p.y - rs.y) / referenceTorso * torso)
            }
        }
        if (target.values.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return null
        val errors = arms.associateWith { j ->
            val current = actual.getValue(j); val goal = metric(target.getValue(j))
            hypot(current.x - goal.x, current.y - goal.y) / torso
        }
        val worst = errors.maxBy { it.value }
        val targetBody = Body(target)
        if (worst.value < .30f) return PoseMatch(PoseMatchState.CLOSE, "手臂已接近参考", "保持舒适，可拍一张比较；腿部、手指和神态仍由你决定。", "close", targetBody, true)
        val joint = worst.key
        val current = actual.getValue(joint); val goal = metric(target.getValue(joint))
        val dx = goal.x - current.x; val dy = goal.y - current.y
        val part = when (joint) {
            Joint.LEFT_WRIST -> "左手"; Joint.RIGHT_WRIST -> "右手"
            Joint.LEFT_ELBOW -> "左手肘"; else -> "右手肘"
        }
        val direction = if (abs(dy) >= abs(dx)) { if (dy < 0) "向上抬一点" else "向下放一点" }
            else if (dx < 0) "向画面左侧移一点" else "向画面右侧移一点"
        val key = "${joint.name}:${if (abs(dy) >= abs(dx)) if (dy < 0) "up" else "down" else if (dx < 0) "left" else "right"}"
        return PoseMatch(PoseMatchState.ADJUST, "试着调整$part", "$part（人物自己的左右）$direction，幅度以舒适为准。", key, targetBody, true)
    }
}
