package com.framecoach.core

import kotlin.math.abs

data class Stability(val progress: Float, val ready: Boolean, val triggered: Boolean, val moving: Boolean)

/** Monotonic time only. Compare against an anchor as well as adjacent frames to catch slow pans. */
class SceneStability(private val dwellMs: Long = 2000) {
    private var anchor: FloatArray? = null
    private var previous: FloatArray? = null
    private var since = 0L
    private var lastAt = -1L
    private var fired = false

    fun reset() { anchor = null; previous = null; fired = false; lastAt = -1L }

    fun update(sample: FloatArray, angularSpeed: Float, nowMs: Long): Stability {
        require(sample.isNotEmpty() && sample.all { it.isFinite() && it in 0f..1f })
        if (lastAt < 0 || nowMs < lastAt || nowMs - lastAt > 1000 || anchor?.size != sample.size) {
            anchor = sample.copyOf(); previous = sample.copyOf(); since = nowMs; fired = false
        }
        lastAt = nowMs
        val moved = angularSpeed > .09f || difference(previous!!, sample) > .026f || difference(anchor!!, sample) > .045f
        previous = sample.copyOf()
        if (moved) {
            anchor = sample.copyOf(); since = nowMs; fired = false
            return Stability(0f, false, false, true)
        }
        val progress = ((nowMs - since).toFloat() / dwellMs).coerceIn(0f, 1f)
        val trigger = progress >= 1f && !fired
        if (trigger) fired = true
        return Stability(progress, fired, trigger, false)
    }

    /** Subtract uniform exposure changes so auto-exposure does not act like a camera pan. */
    private fun difference(a: FloatArray, b: FloatArray): Float {
        val offset = a.indices.sumOf { (a[it] - b[it]).toDouble() }.toFloat() / a.size
        return a.indices.sumOf { abs(a[it] - b[it] - offset).toDouble() }.toFloat() / a.size
    }
}
