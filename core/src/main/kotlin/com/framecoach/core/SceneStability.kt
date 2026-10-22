package com.framecoach.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

data class Stability(val progress: Float, val ready: Boolean, val triggered: Boolean, val moving: Boolean)

/** A short disturbance pauses acquisition; sustained motion clears stale advice. Monotonic time. */
class SceneStability(private val dwellMs: Long = 2000) {
    init { require(dwellMs > 0) }
    private var anchor: FloatArray? = null
    private var previous: FloatArray? = null
    private var lastAt = -1L
    private var motionSince = -1L
    private var quietMs = 0L
    private var slowTurnMs = 0L
    private var fired = false

    fun reset() {
        anchor = null; previous = null; lastAt = -1L; motionSince = -1L; quietMs = 0; slowTurnMs = 0; fired = false
    }

    /** Manual assistance uses the latest frame and still expires on a subsequent scene change. */
    fun assistNow(): Boolean {
        if (anchor == null) return false
        fired = true; quietMs = dwellMs
        return true
    }

    fun update(sample: FloatArray, angularSpeed: Float, nowMs: Long): Stability {
        require(sample.isNotEmpty() && sample.all { it.isFinite() && it in 0f..1f })
        require(angularSpeed.isFinite() && angularSpeed >= 0)
        if (lastAt < 0 || nowMs < lastAt || nowMs - lastAt > 1500 || anchor?.size != sample.size) {
            reset(); anchor = sample.copyOf(); previous = sample.copyOf(); lastAt = nowMs
            return Stability(0f, false, false, false)
        }
        val dt = nowMs - lastAt
        lastAt = nowMs
        val step = difference(previous!!, sample)
        val drift = difference(anchor!!, sample)
        previous = sample.copyOf()
        // ~10 degrees/s after vector filtering. Ordinary hand tremor is allowed.
        slowTurnMs = if (angularSpeed > .07f) slowTurnMs + dt else 0L
        val suspect = angularSpeed > .18f || slowTurnMs >= 800 || step > .075f || drift > .06f
        if (suspect && motionSince < 0) motionSince = nowMs
        if (!suspect) motionSince = -1L
        val moving = step > .22f || (motionSince >= 0 && nowMs - motionSince >= 400)
        if (moving) {
            anchor = sample.copyOf(); quietMs = 0; fired = false; motionSince = -1L
            return Stability(0f, false, false, true)
        }
        if (!suspect) quietMs += dt.coerceAtMost(400)
        val progress = (quietMs.toFloat() / dwellMs).coerceIn(0f, 1f)
        val trigger = progress >= 1f && !suspect && !fired
        if (trigger) fired = true
        return Stability(progress, fired, trigger, false)
    }

    private fun difference(a: FloatArray, b: FloatArray): Float {
        val offset = a.indices.sumOf { (a[it] - b[it]).toDouble() }.toFloat() / a.size
        return a.indices.sumOf { abs(a[it] - b[it] - offset).toDouble() }.toFloat() / a.size
    }
}

/** Filter signed angular velocity before magnitude, so opposite tremors cancel. */
class GyroSmoother {
    private val velocity = FloatArray(3)
    private var lastAt = -1L
    fun reset() { velocity.fill(0f); lastAt = -1L }
    fun update(x: Float, y: Float, z: Float, nowMs: Long): Float {
        require(listOf(x, y, z).all { it.isFinite() })
        val dt = if (lastAt < 0 || nowMs < lastAt || nowMs - lastAt > 500) {
            velocity.fill(0f); 16L
        } else nowMs - lastAt
        lastAt = nowMs
        val alpha = (1 - exp(-dt / 160.0)).toFloat()
        val raw = floatArrayOf(x, y, z)
        for (i in 0..2) velocity[i] += alpha * (raw[i] - velocity[i])
        return sqrt(velocity.sumOf { (it * it).toDouble() }).toFloat()
    }
}

