package com.framecoach.core

import kotlin.math.abs

/** Original, explainable recommendations. Detector outputs describe geometry, never identity or emotion. */
object Coach {
    fun advise(mode: SceneMode, layout: Layout, mood: Mood, observation: Observation, horizon: Float? = null): Guidance {
        val tips = mutableListOf<Tip>()
        val face = observation.faces.maxByOrNull { it.width * it.height }
        val target = if (mode == SceneMode.PORTRAIT && face != null && layout != Layout.DEPTH) {
            Point(if (layout == Layout.CENTER) .5f else if ((face?.center?.x ?: .4f) > .5f) 2f / 3 else 1f / 3, 1f / 3)
        } else null
        val light = observation.light
        when {
            light.highlights > .12f -> tips += Tip("光线", "先检查亮部细节", "亮区较多，试着降低一点曝光，再看主体是否太暗。", "预览中 ${(light.highlights * 100).toInt()}% 的采样点接近白色；不等于 RAW 已过曝。", listOf("nikon-portrait"))
            light.mean < .2f -> tips += Tip("光线", "画面整体偏暗", "先稳住手机；人像可移近窗边或给脸补柔光。", "预览平均亮度 ${(light.mean * 100).toInt()}%；夜景也可能是有意保留暗调。", listOf("risd-light", "nikon-portrait"))
            light.shadows > .3f && light.highlights > .03f -> tips += Tip("光线", "明暗差较大", "确认你最想保留的区域；人像可用反光板轻补暗部。", "暗部与亮区同时出现；仅凭亮度分布无法确定光源方向。", listOf("risd-light", "nikon-portrait"))
            else -> tips += Tip("光线", "先观察主体上的光", "试着换一个拍摄方向，比较脸部或景物的明暗层次。", "未发现明显的整体亮度异常；不代表主体曝光一定准确。", listOf("risd-light"))
        }
        if (mode == SceneMode.PORTRAIT) {
            val title = if (layout == Layout.DEPTH) "让人物融入环境层次" else if (layout == Layout.CENTER) "把视线放在画面中轴" else "给人物留一点呼吸空间"
            val instruction = if (face == null) "请人物入镜，让面部清晰；识别到人脸后再提供站位参考。" else if (layout == Layout.DEPTH) "试着让人物与背景分开，观察前景是否遮挡面部；不强制套用三分落点。" else {
                val dx = target!!.x - face.center.x
                val dy = target.y - face.center.y
                if (abs(dx) < .07f && abs(dy) > .10f) "保持人物横向位置，调整取景的上下留白，让面部靠近暖色落点。"
                else if (abs(dx) < .07f) "人物已接近建议位置；再检查头顶留白和背景。"
                else "让人物在画面中向${if (dx > 0) "右" else "左"}移一点，靠近暖色落点；这是人物位置方向。"
            }
            tips += Tip("构图", title, instruction, if (face == null) "尚未检测到清晰人脸；使用创作模板。" else "基于最大人脸的位置；多人场景请自行选择主角。", listOf("freeman", "nikon-background"), if (face != null && layout != Layout.DEPTH) TipBasis.MEASURED else TipBasis.CREATIVE)
            val body = observation.body
            val leftWrist = body?.visible(Joint.LEFT_WRIST)
            val leftHip = body?.visible(Joint.LEFT_HIP)
            val rightWrist = body?.visible(Joint.RIGHT_WRIST)
            val rightHip = body?.visible(Joint.RIGHT_HIP)
            val armsDown = leftWrist != null && leftHip != null && rightWrist != null && rightHip != null && leftWrist.y >= leftHip.y && rightWrist.y >= rightHip.y
            tips += when {
                armsDown -> Tip("动作", "给手一个自然的落点", "试着一只手轻扶衣角，另一只手放松；舒适优先。", "双侧手腕与髋部关键点可见，手腕低于髋部；这是二维估计。", listOf("nikon-background"), TipBasis.MEASURED)
                face != null && abs(face.yaw) > 30 -> Tip("动作", "试一张稍微回头的版本", "保持肩膀舒适，脸轻轻转回镜头，再和侧脸版本比较。", "检测到较大的头部转角；侧脸本身也可以是你的选择。", listOf("nikon-background"), TipBasis.MEASURED)
                else -> Tip("动作", "肩膀放松，身体微侧", "试着把重心放在一条腿上，轻轻呼气；不必刻意扭转身体。", if (body?.joints?.values?.any { it.confidence >= .65f } == true) "已获得部分人体关键点；以下为创作建议。" else "人体关键点不足；以下为通用模板，并非当前姿态诊断。", listOf("nikon-background"))
            }
            tips += when (mood) {
                Mood.NATURAL -> Tip("表情", "像和朋友聊天一样", "看向镜头旁的人，呼气后再看回来，抓住自然的一瞬。", "你选择了「自然」；不是从脸部推断情绪。", listOf("moma-posed"))
                Mood.QUIET -> Tip("表情", "让目光停在远处", "试着望向画外，嘴角放松，停留一个呼吸。", "你选择了「安静」；无需刻意压住表情。", listOf("moma-posed"))
                Mood.LIVELY -> Tip("表情", "先动起来，再按快门", "慢走一步、回头打个招呼，连拍比较不同瞬间。", "你选择了「轻快」；这是动作提示而非情绪识别。", listOf("moma-posed"))
            }
            if (observation.faces.size > 1) tips += Tip("多人", "当前以最大人脸为参考", "请确认你想突出的人；合影可切换居中构图，不必让所有人挤在落点上。", "当前检测到 ${observation.faces.size} 张人脸。", listOf("freeman"), TipBasis.MEASURED)
        } else {
            tips += Tip("构图", when (layout) { Layout.THIRDS -> "先决定天空还是地面更重要"; Layout.CENTER -> "寻找真正值得强调的对称"; Layout.DEPTH -> "给远处的风景一个前景" }, when (layout) {
                Layout.THIRDS -> "天空更精彩就把水平线靠下；地面更精彩就靠上。点击画面标记你看到的水平线。"
                Layout.CENTER -> "将视觉中心放在中轴附近；有倒影时，可以尝试上下呼应。"
                Layout.DEPTH -> "在近处找一块石头、树影或小径，再观察它与远景的关系。"
            }, "这是你选择的构图方案；当前未自动识别山峰、道路或天空。", listOf("freeman", "kelby-landscape"))
            tips += Tip("机位", "小范围移动，再比较", "在安全、平稳的位置试着降低一点机位；观察前后景是否分开。", "单个镜头画面不能恢复完整三维空间；不提供盲目的移动距离。", listOf("freeman", "kelby-landscape"))
            tips += Tip("观察", "为这一张找一个理由", "先说出你想留下的光、形状或故事，再决定是否采用辅助线。", "创作提示；优秀作品也常常打破固定构图。", listOf("moma-context", "shore-nature", "swpa-landscape"))
        }
        if (observation.detectorError) tips += Tip("检测", "视觉检测暂不可用", "可以继续参考构图线；重新进入取景可重试检测。", "检测器返回错误；没有把缺失结果当作无人入镜。", listOf("mlkit-face"), TipBasis.MEASURED)
        val abnormalLight = light.highlights > .12f || light.mean < .2f || (light.shadows > .3f && light.highlights > .03f)
        val ordered = tips.map { if (it.category == "光线" && abnormalLight) it.copy(basis = TipBasis.MEASURED) else it }
            .sortedBy { when {
                it.category == "检测" -> 0
                it.category == "光线" && abnormalLight -> 1
                it.category == "构图" -> 2
                it.category == "多人" -> 3
                it.category == "动作" -> 4
                it.category == "表情" -> 5
                else -> 6
            } }
        return Guidance(ordered, target, if (mode == SceneMode.LANDSCAPE) horizon?.coerceIn(0f, 1f) else null)
    }
}
