package com.framecoach.core

enum class Posture(val label: String) { STANDING("站姿"), SITTING("坐姿"), LEANING("靠姿"), MOVING("轻动作") }
enum class Framing(val label: String) { FULL("全身"), HALF("半身") }
enum class PoseView(val label: String) { FRONT("正面"), OBLIQUE("斜侧") }
enum class Prop { CHAIR, WALL }
enum class HandShape { DOWN, HIP, CHEST, HEAD, WAVE, CLASP, LAP, CROSS, BACK, FORWARD }

data class PoseConditions(
    val posture: Posture = Posture.STANDING,
    val framing: Framing = Framing.FULL,
    val view: PoseView = PoseView.FRONT,
    val chair: Boolean = false,
    val wall: Boolean = false,
    val mood: Mood = Mood.NATURAL
)

/** Hand-authored diagrams and instructions; no upstream images, traces or extracted coordinates. */
data class PoseCard(
    val id: String,
    val title: String,
    val posture: Posture,
    val instruction: String,
    val leftHand: HandShape,
    val rightHand: HandShape,
    val variation: Int,
    val moods: Set<Mood>,
    val prop: Prop? = null,
    val fullBodyOnly: Boolean = false,
    val matchingEnabled: Boolean = true
) {
    val composition: String get() = when (posture) {
        Posture.STANDING -> "先保留头顶和脚下空间；全身照避免在脚踝附近意外截断。"
        Posture.SITTING -> "让椅面和身体支撑关系可读；半身照不必强求双腿入画。"
        Posture.LEANING -> "把墙面作为空间线索，检查墙缝是否干扰脸部轮廓。"
        Posture.MOVING -> "沿动作方向留空间，先确认脚下平稳，再抓一个自然瞬间。"
    }
    fun expression(mood: Mood): String = when (mood) {
        Mood.NATURAL -> "轻轻呼气，目光停在镜头旁；不必维持固定笑容。"
        Mood.QUIET -> "目光停在画外一个位置，嘴角放松，停留一个呼吸。"
        Mood.LIVELY -> "和拍摄者打个招呼，在动作结束前后各拍一张比较。"
    }
    val light: String get() = "比较脸部和背景的明暗；窗边、墙边不自动代表光线合适。"
    fun eligible(conditions: PoseConditions): Boolean = posture == conditions.posture &&
        (!fullBodyOnly || conditions.framing == Framing.FULL) &&
        (prop != Prop.CHAIR || conditions.chair) && (prop != Prop.WALL || conditions.wall)

    /** These are schematic 2D coordinates, not measured human anatomy or calibrated 3D poses. */
    fun sketch(view: PoseView): Body {
        val side = if (view == PoseView.FRONT) 1f else .68f
        fun p(x: Float, y: Float) = Point(.5f + (x - .5f) * side, y)
        val joints = mutableMapOf(
            Joint.LEFT_SHOULDER to p(.64f, .29f), Joint.RIGHT_SHOULDER to p(.36f, .29f),
            Joint.LEFT_HIP to p(.59f, .56f), Joint.RIGHT_HIP to p(.41f, .56f)
        )
        fun arm(left: Boolean, shape: HandShape) {
            val sign = if (left) 1f else -1f
            fun a(x: Float, y: Float) = p(.5f + sign * x, y)
            val (elbow, wrist) = when (shape) {
                HandShape.DOWN -> a(.19f, .43f) to a(.20f, .60f)
                HandShape.HIP -> a(.28f, .43f) to a(.12f, .53f)
                HandShape.CHEST -> a(.23f, .43f) to a(.04f, .35f)
                HandShape.HEAD -> a(.28f, .29f) to a(.13f, .17f)
                HandShape.WAVE -> a(.29f, .32f) to a(.31f, .15f)
                HandShape.CLASP -> a(.15f, .44f) to a(.025f, .57f)
                HandShape.LAP -> a(.18f, .45f) to a(.09f, .62f)
                HandShape.CROSS -> a(.20f, .44f) to a(-.08f, .40f)
                HandShape.BACK -> a(.17f, .45f) to a(.04f, .57f)
                HandShape.FORWARD -> a(.24f, .40f) to a(.32f, .43f)
            }
            joints[if (left) Joint.LEFT_ELBOW else Joint.RIGHT_ELBOW] = elbow
            joints[if (left) Joint.LEFT_WRIST else Joint.RIGHT_WRIST] = wrist
        }
        arm(true, leftHand); arm(false, rightHand)
        val offset = (variation % 3 - 1) * .025f
        val seated = posture == Posture.SITTING
        joints[Joint.LEFT_KNEE] = p(if (seated) .68f else .59f + offset, if (seated) .66f else .73f)
        joints[Joint.RIGHT_KNEE] = p(if (seated) .39f else .41f + offset, if (seated) .69f else .73f)
        joints[Joint.LEFT_ANKLE] = p(if (seated) .67f else .61f + offset, .89f)
        joints[Joint.RIGHT_ANKLE] = p(if (seated) .34f else .38f - offset, if (posture == Posture.MOVING) .84f else .89f)
        if (posture == Posture.LEANING) return Body(joints.mapValues { (_, point) -> point.copy(x = point.x + (.56f - point.y) * .12f) })
        return Body(joints)
    }
}

object PoseLibrary {
    private val natural = setOf(Mood.NATURAL, Mood.QUIET)
    private val lively = setOf(Mood.NATURAL, Mood.LIVELY)
    private fun card(n: Int, title: String, posture: Posture, instruction: String, left: HandShape, right: HandShape,
                     mood: Set<Mood> = natural, full: Boolean = false, match: Boolean = true) = PoseCard(
        "fc-${n.toString().padStart(3, '0')}", title, posture, instruction, left, right, n, mood,
        when (posture) { Posture.SITTING -> Prop.CHAIR; Posture.LEANING -> Prop.WALL; else -> null }, full, match
    )
    val cards: List<PoseCard> = listOf(
        card(1, "自然站立", Posture.STANDING, "双手自然垂下，膝盖放松，重心轻轻偏向一侧。", HandShape.DOWN, HandShape.DOWN),
        card(2, "左手轻扶腰", Posture.STANDING, "人物自己的左手轻放腰侧，右手放松，手肘留一点空隙。", HandShape.HIP, HandShape.DOWN),
        card(3, "右手轻扶腰", Posture.STANDING, "人物自己的右手轻放腰侧，左手自然垂下，不必挺紧肩膀。", HandShape.DOWN, HandShape.HIP),
        card(4, "身前轻合手", Posture.STANDING, "双手在身前轻搭，手指松开一点，手腕不要绷紧。", HandShape.CLASP, HandShape.CLASP),
        card(5, "左手轻扶衣领", Posture.STANDING, "左手轻触衣领附近，另一只手放松，先确认衣物舒适。", HandShape.CHEST, HandShape.DOWN),
        card(6, "右手轻扶衣领", Posture.STANDING, "右手轻触衣领附近，手肘自然弯曲，不必用力抓衣服。", HandShape.DOWN, HandShape.CHEST),
        card(7, "左手整理发梢", Posture.STANDING, "左手靠近耳侧整理发梢，手掌别挡住眼睛。", HandShape.HEAD, HandShape.DOWN),
        card(8, "右手整理发梢", Posture.STANDING, "右手轻靠耳侧，另一只手放松，拍动作完成前后的瞬间。", HandShape.DOWN, HandShape.HEAD),
        card(9, "轻搭双臂", Posture.STANDING, "双臂轻搭在身前，肩膀下沉；不需要把手臂抱紧。", HandShape.CROSS, HandShape.CROSS),
        card(10, "左手打招呼", Posture.STANDING, "左手抬起打个小招呼，掌心放松，目光看向拍摄者。", HandShape.WAVE, HandShape.DOWN, lively),
        card(11, "右手打招呼", Posture.STANDING, "右手抬起打个小招呼，另一只手自然垂下。", HandShape.DOWN, HandShape.WAVE, lively),
        card(12, "双手放身后", Posture.STANDING, "双手舒适地放在身后，肩膀放松；不要过度后拉手臂。", HandShape.BACK, HandShape.BACK, match = false),
        card(13, "坐姿双手放膝", Posture.SITTING, "坐稳后双手轻放膝上，双脚在地面找到舒适支撑。", HandShape.LAP, HandShape.LAP),
        card(14, "坐姿身前合手", Posture.SITTING, "双手在腿前轻搭，背部保持舒适，不必刻意挺直。", HandShape.CLASP, HandShape.CLASP),
        card(15, "坐姿左手扶衣领", Posture.SITTING, "先坐稳，再用左手轻扶衣领，右手放在腿上。", HandShape.CHEST, HandShape.LAP),
        card(16, "坐姿右手扶衣领", Posture.SITTING, "右手轻靠衣领，另一只手落在腿上，肩膀保持放松。", HandShape.LAP, HandShape.CHEST),
        card(17, "坐姿左手整理头发", Posture.SITTING, "左手整理耳侧头发，右手放在腿上，保持坐姿稳定。", HandShape.HEAD, HandShape.LAP),
        card(18, "坐姿右手整理头发", Posture.SITTING, "右手靠近耳侧，左手放在腿上，避免手臂挡脸。", HandShape.LAP, HandShape.HEAD),
        card(19, "坐姿轻搭双臂", Posture.SITTING, "双臂轻搭，背靠或坐稳都可以，比较不同的身体朝向。", HandShape.CROSS, HandShape.CROSS),
        card(20, "坐姿左手招呼", Posture.SITTING, "坐稳后左手小幅打招呼，右手保持舒适落点。", HandShape.WAVE, HandShape.LAP, lively),
        card(21, "坐姿右手招呼", Posture.SITTING, "右手轻轻打招呼，视线跟随拍摄者，双脚保持支撑。", HandShape.LAP, HandShape.WAVE, lively),
        card(22, "坐姿左臂打开", Posture.SITTING, "左前臂轻轻向外展开，右手留在腿上，不必伸到极限。", HandShape.FORWARD, HandShape.LAP, lively),
        card(23, "坐姿右臂打开", Posture.SITTING, "右前臂轻轻展开，左手留在腿上，比较动作大小。", HandShape.LAP, HandShape.FORWARD, lively),
        card(24, "坐姿双手落两侧", Posture.SITTING, "双手放松落在腿侧，先确认椅子平稳，不依赖手臂支撑身体。", HandShape.DOWN, HandShape.DOWN),
        card(25, "轻靠墙面", Posture.LEANING, "确认墙面稳固干净，身体轻靠，双手自然放松。", HandShape.DOWN, HandShape.DOWN),
        card(26, "靠墙左手扶腰", Posture.LEANING, "轻靠墙面，左手轻扶腰，另一只手保持自然。", HandShape.HIP, HandShape.DOWN),
        card(27, "靠墙右手扶腰", Posture.LEANING, "右手轻扶腰，身体轻靠墙面，留出手肘轮廓。", HandShape.DOWN, HandShape.HIP),
        card(28, "靠墙身前合手", Posture.LEANING, "双手轻搭在身前，比较离墙一点和轻靠墙的版本。", HandShape.CLASP, HandShape.CLASP),
        card(29, "靠墙左手扶衣领", Posture.LEANING, "左手轻靠衣领，身体与墙面保留舒适角度。", HandShape.CHEST, HandShape.DOWN),
        card(30, "靠墙右手扶衣领", Posture.LEANING, "右手轻靠衣领，另一只手放松，避开墙面杂乱线条。", HandShape.DOWN, HandShape.CHEST),
        card(31, "靠墙轻搭双臂", Posture.LEANING, "双臂轻搭，肩膀不耸起，比较正面和斜侧取景。", HandShape.CROSS, HandShape.CROSS),
        card(32, "靠墙左手整理头发", Posture.LEANING, "左手整理耳侧发梢，保持脚下稳定，不用力倚靠。", HandShape.HEAD, HandShape.DOWN),
        card(33, "慢走一步", Posture.MOVING, "在平坦空地慢走一步，双臂自然摆动，拍动作前后。", HandShape.DOWN, HandShape.DOWN, lively, true, false),
        card(34, "慢走后停下", Posture.MOVING, "慢走后自然停下，先站稳，再看向拍摄者。", HandShape.CLASP, HandShape.CLASP, lively, true, false),
        card(35, "停步左手招呼", Posture.MOVING, "停稳后左手打招呼，身体不必跟着大幅扭转。", HandShape.WAVE, HandShape.DOWN, lively, match = false),
        card(36, "停步右手招呼", Posture.MOVING, "停稳后右手打招呼，拍下抬手和放手之间的变化。", HandShape.DOWN, HandShape.WAVE, lively, match = false),
        card(37, "左手轻展再放下", Posture.MOVING, "左手缓慢向外展开再放下，保持动作轻松。", HandShape.FORWARD, HandShape.DOWN, lively, match = false),
        card(38, "右手轻展再放下", Posture.MOVING, "右手缓慢向外展开，拍摄者比较动作中不同瞬间。", HandShape.DOWN, HandShape.FORWARD, lively, match = false),
        card(39, "停步整理左侧发梢", Posture.MOVING, "停稳后用左手整理发梢，目光自然移回镜头附近。", HandShape.HEAD, HandShape.DOWN, lively, match = false),
        card(40, "停步整理右侧发梢", Posture.MOVING, "停稳后用右手整理发梢，比较看向画外和镜头附近的版本。", HandShape.DOWN, HandShape.HEAD, lively, match = false)
    )
    fun find(id: String?): PoseCard? = cards.find { it.id == id }
    fun filtered(conditions: PoseConditions, favorites: Set<String> = emptySet(), favoritesOnly: Boolean = false): List<PoseCard> =
        cards.filter { it.eligible(conditions) && (!favoritesOnly || it.id in favorites) }
            .sortedWith(compareByDescending<PoseCard> { conditions.mood in it.moods }.thenByDescending { it.id in favorites }.thenBy { it.id })
    fun recommend(conditions: PoseConditions, offset: Int = 0, favorites: Set<String> = emptySet()): List<PoseCard> {
        val eligible = filtered(conditions, favorites)
        if (eligible.isEmpty()) return emptyList()
        val start = Math.floorMod(offset, eligible.size)
        return (eligible.drop(start) + eligible.take(start)).take(3)
    }
}
