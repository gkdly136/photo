package com.framecoach.core

enum class SceneMode { PORTRAIT, LANDSCAPE }
enum class Layout(val label: String) { THIRDS("三分留白"), CENTER("居中对称"), DEPTH("前景层次") }
enum class Mood { NATURAL, QUIET, LIVELY }

data class Point(val x: Float, val y: Float, val confidence: Float = 1f)
data class Face(val center: Point, val width: Float, val height: Float, val yaw: Float, val smile: Float?)
enum class Joint { LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE }
data class Body(val joints: Map<Joint, Point>) {
    fun visible(joint: Joint): Point? = joints[joint]?.takeIf { it.confidence >= .65f && it.x in 0f..1f && it.y in 0f..1f }
}
data class Light(val mean: Float, val shadows: Float, val highlights: Float)
data class Observation(val light: Light, val faces: List<Face> = emptyList(), val body: Body? = null, val detectorError: Boolean = false)
enum class TipBasis(val label: String) { MEASURED("画面测量"), CREATIVE("创作建议") }
data class Tip(val category: String, val title: String, val instruction: String, val evidence: String, val sourceIds: List<String>, val basis: TipBasis = TipBasis.CREATIVE)
data class Guidance(val tips: List<Tip>, val target: Point?, val horizon: Float?)
