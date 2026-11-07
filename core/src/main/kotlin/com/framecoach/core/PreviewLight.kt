package com.framecoach.core

/** Preview Y is not RAW. Some camera pipelines use video-range Y (white around 235). */
object PreviewLight {
    fun fromLuma(values: FloatArray): Light {
        require(values.isNotEmpty() && values.all { it.isFinite() && it in 0f..1f })
        return Light(
            values.average().toFloat(),
            values.count { it < .09f }.toFloat() / values.size,
            // 230/255 catches near-white in limited-range and full-range preview pipelines.
            values.count { it >= 230f / 255f }.toFloat() / values.size
        )
    }
}
