package com.framecoach.core

import kotlin.math.abs

enum class ShotIntent(val label: String, val mode: SceneMode) {
    PERSON("突出人物", SceneMode.PORTRAIT), ENVIRONMENT("人物与环境", SceneMode.PORTRAIT),
    SKY("突出天空", SceneMode.LANDSCAPE), GROUND("突出地景", SceneMode.LANDSCAPE), BALANCE("自由观察", SceneMode.LANDSCAPE)
}
enum class ShotActor(val label: String) { CAMERA("拍摄者 · 调整取景"), PERSON("人物 · 尝试动作"), REVIEW("拍摄者 · 观察确认") }
data class ShotStep(val id: String, val actor: ShotActor, val tip: Tip, val actionable: Boolean = true, val target: Point? = null)
data class ShotPlan(val step: ShotStep, val facts: List<String>, val completed: Int, val skipped: Int, val canUndo: Boolean, val revision: Long)
data class ShotSettings(val mode: SceneMode, val intent: ShotIntent, val layout: Layout, val mood: Mood, val poseId: String? = null)

/** A conservative offline coach. Geometry is evidence, intent is chosen, aesthetics are not inferred. */
class ShotCoachSession {
    private var settings: ShotSettings? = null
    private var generation: Long? = null
    private var subjectId: Int? = null
    private var automaticId: Int? = null
    private var targetX: Float? = null
    private var active: String? = null
    private var shown: ShotStep? = null
    private var revision = 0L
    private val history = mutableListOf<Pair<String, Boolean>>()

    fun reset() {
        settings = null; generation = null; subjectId = null; automaticId = null
        restart()
    }
    private fun restart() { active = null; shown = null; targetX = null; history.clear(); revision++ }
    fun chooseSubject(id: Int, observation: Observation, now: Long, session: Long): Boolean {
        if (!fresh(observation.atMs, now) || observation.sessionId != session || observation.detectorError ||
            observation.faces.count { it.trackingId == id && valid(it) } != 1) return false
        subjectId = id; automaticId = null; restart()
        return true
    }
    fun advance(skipped: Boolean = false): Boolean {
        val step = shown?.takeIf { it.actionable } ?: return false
        history += step.id to skipped; active = null; shown = null
        return true
    }
    fun undo(): Boolean {
        if (history.isEmpty()) return false
        active = history.removeAt(history.lastIndex).first; shown = null
        return true
    }
    fun inspect(config: ShotSettings, observation: Observation, now: Long, session: Long, horizon: Float? = null): ShotPlan {
        require(config.intent.mode == config.mode)
        if (settings != config || generation != session) {
            val newScene = generation != null && generation != session
            val newMode = settings != null && settings?.mode != config.mode
            restart(); if (newScene || newMode) { subjectId = null; automaticId = null }
            settings = config; generation = session
        }
        val facts = mutableListOf("意图：${config.intent.label}（手动选择）")
        fun wait(id: String, title: String, instruction: String): ShotPlan = result(
            ShotStep(id, ShotActor.REVIEW, tip(title, instruction, "当前缺少可靠的连续取景依据。"), false), facts)
        val lightFresh = fresh(observation.lightAtMs, now)
        if (lightFresh && listOf(observation.light.mean, observation.light.highlights, observation.light.shadows).all { it in 0f..1f }) {
            facts += "预览平均亮度 ${(observation.light.mean * 100).toInt()}%；近白采样 ${(observation.light.highlights * 100).toInt()}%（非 RAW 曝光）"
        } else facts += "亮度测量暂不可用或已过期"
        val portrait = config.mode == SceneMode.PORTRAIT
        var face: Face? = null
        if (portrait) {
            facts += "背景语义、实际光源方向、情绪：尚未识别"
            if (observation.detectorError) return wait("error", "检测暂不可用", "暂停人物指导；可以拍照，或重新观察。")
            if (!fresh(observation.atMs, now) || observation.sessionId != session) return wait("stale", "等待新画面", "人物位置已过期，收到新画面后继续。")
            val faces = observation.faces.filter(::valid)
            facts += "检测到 ${faces.size} 张人脸；不等于完整人数"
            if (subjectId != null) {
                face = faces.singleOrNull { it.trackingId == subjectId }
                if (face == null) return wait("lost", "主角暂时丢失", "等待原主角回来，或在画面上重新点选主角；不会自动切换到其他人。")
            } else {
                if (faces.size > 1) return wait("choose", "请点选主角", "点击画面中想突出的人脸；多人时不默认选择最大人脸。")
                face = faces.singleOrNull() ?: return wait("no-face", "等待主角入镜", "让面部清晰入镜，再给出人物取景参考。")
                if (automaticId != null && automaticId != face.trackingId) restart()
                automaticId = face.trackingId
            }
            facts += "主角：${if (subjectId != null) "手动点选，临时追踪" else "当前单张人脸"}；人脸框占画面 ${(face.width * face.height * 100).toInt()}%"
            facts += "人脸框上缘 ${(100 * (face.center.y - face.height / 2)).toInt()}%；头发边界和视线留白未测量"
        } else {
            facts += "天空、道路与前景：尚未自动识别；水平线由你标记"
            if (!lightFresh) return wait("stale", "等待新画面", "亮度画面已过期，收到新画面后继续。")
            horizon?.takeIf { it.isFinite() && it in 0f..1f }?.let { facts += "手动水平线位于画面高度 ${(it * 100).toInt()}%" }
        }
        val proposals = mutableListOf<ShotStep>()
        val light = observation.light
        if (lightFresh && (light.highlights > .12f || active == "light")) proposals += ShotStep("light", ShotActor.REVIEW,
            tip("检查亮部是否需要保留", "先查看重要亮区的细节；只有需要保留而细节丢失时，再尝试降低曝光。", "近白像素不等于 RAW 过曝；未判断这些亮区是否是主体。", TipBasis.MEASURED))
        if (lightFresh && (light.mean < .2f || active == "dark")) proposals += ShotStep("dark", ShotActor.REVIEW,
            tip("确认是否要保留暗调", "查看主体细节；如果本来就在拍夜景或剪影，可以跳过。", "只测量预览平均亮度，尚未测量主体局部曝光。", TipBasis.MEASURED))
        if (face != null && config.layout != Layout.DEPTH) {
            if (targetX == null) targetX = if (config.layout == Layout.CENTER) .5f else if (face.center.x > .5f) 2f / 3 else 1f / 3
            val y = maxOf(1f / 3, face.height / 2 + .04f).coerceAtMost(.8f)
            val target = Point(targetX!!, y)
            val dx = target.x - face.center.x; val dy = target.y - face.center.y
            val near = abs(dx) < .08f && abs(dy) < .10f
            // One axis at a time; directions refer to the displayed preview, including the selfie mirror.
            val instruction = if (near) "人脸位置已接近所选参考。检查画面边缘，满意后确认；这不是照片质量评分。"
                else if (abs(dx) / .08f > abs(dy) / .10f) "只调整手机取景，让主角在画面中向${if (dx > 0) "右" else "左"}靠近落点；人物先保持原位。"
                else "只调整手机取景，让主角在画面中向${if (dy > 0) "下" else "上"}靠近落点；人物先保持原位。"
            if (!near || active == "framing") proposals += ShotStep("framing", ShotActor.CAMERA,
                tip(if (near) "位置接近参考" else "先调整人物在画面中的位置", instruction,
                    "基于主角人脸框和你选择的${config.layout.label}；不是唯一正确的构图。", TipBasis.MEASURED), target = target)
        }
        if (!portrait && config.intent != ShotIntent.BALANCE) {
            val marked = horizon?.takeIf { it.isFinite() && it in 0f..1f }
            val desired = if (config.intent == ShotIntent.SKY) 2f / 3 else 1f / 3
            proposals += ShotStep("horizon", if (marked == null) ShotActor.REVIEW else ShotActor.CAMERA,
                tip("给${if (config.intent == ShotIntent.SKY) "天空" else "地景"}更多画幅", if (marked == null)
                    "点击预览标记实际水平线；没有清晰水平线时，跳过这步。"
                else if (abs(marked - desired) < .08f) "你标记的位置接近三分参考。自行检查是否有利于表达，再确认。"
                else "只调整手机取景，让实际水平线靠近画面${if (config.intent == ShotIntent.SKY) "下" else "上"}三分线；调整后重新点击标记。",
                    "依据手选意图与手动标记；未自动检测地平线，也不判断天空是否精彩。"))
        }
        proposals += ShotStep("context", ShotActor.REVIEW, tip("检查主体与背景的关系", when (config.intent) {
            ShotIntent.PERSON -> "观察脸部轮廓旁是否有杂乱线条；先确认，再决定是否需要换取景。"
            ShotIntent.ENVIRONMENT -> "检查能交代地点的环境是否保留；不要只为了填满画面而把人物拍大。"
            else -> "确认画面中想突出的景物；检查边缘是否混入无关物体。"
        }, "按你的意图提供观察任务；App 尚未测量背景干扰。"))
        if (portrait) {
            val card = PoseLibrary.find(config.poseId)
            proposals += ShotStep("action", ShotActor.PERSON, tip(card?.title ?: "先让身体放松", card?.instruction ?: "保持现在的位置，肩膀放松，轻轻呼气。", "原创引导脚本；舒适优先，不表示已诊断动作问题。"))
            proposals += ShotStep("expression", ShotActor.PERSON, tip("再尝试一个目光落点", card?.expression(config.mood) ?: when (config.mood) {
                Mood.NATURAL -> "目光停在镜头旁，轻轻呼气，不必固定笑容。"
                Mood.QUIET -> "看向画外一个位置，停留一个呼吸。"
                Mood.LIVELY -> "看向拍摄者，打个小招呼，捕捉回应的一瞬。"
            }, "来自你选择的氛围，不是情绪识别。"))
        }
        val done = history.map { it.first }.toSet()
        val step = proposals.firstOrNull { it.id == active } ?: proposals.firstOrNull { it.id !in done }
        return if (step != null) { active = step.id; result(step, facts) }
        else result(ShotStep("finished", ShotActor.REVIEW, tip("暂未找到可确认的新改进", "可以拍照比较，或重新观察。完成步骤只表示你确认过，不代表照片一定更好。", "几何测量不足以判断摄影效果；没有强制要求继续移动。"), false), facts)
    }
    private fun result(step: ShotStep, facts: List<String>): ShotPlan {
        shown = step
        return ShotPlan(step, facts, history.count { !it.second }, history.count { it.second }, history.isNotEmpty(), revision)
    }
    private fun tip(title: String, instruction: String, evidence: String, basis: TipBasis = TipBasis.CREATIVE) =
        Tip("本次指导", title, instruction, evidence, listOf("framecoach-workflow"), basis)
    private fun fresh(at: Long, now: Long) = at >= 0 && now >= at && now - at <= 1200
    private fun valid(face: Face) = face.center.x in 0f..1f && face.center.y in 0f..1f &&
        face.width in .005f..2f && face.height in .005f..2f
}
