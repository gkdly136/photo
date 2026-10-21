package com.framecoach.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.framecoach.core.*

class GuideOverlay(context: Context) : View(context) {
    var layout: Layout = Layout.THIRDS
    var guidance: Guidance? = null
    var body: Body? = null
    var showGuides = true
    var landscape = false
    var onHorizon: ((Float) -> Unit)? = null
    private val lime = Color.rgb(214, 245, 134)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showGuides) return
        val w = width.toFloat(); val h = height.toFloat()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = density
        paint.color = Color.argb(125, 255, 255, 255)
        when (layout) {
            Layout.THIRDS -> for (i in 1..2) {
                canvas.drawLine(w * i / 3, 0f, w * i / 3, h, paint)
                canvas.drawLine(0f, h * i / 3, w, h * i / 3, paint)
            }
            Layout.CENTER -> {
                canvas.drawLine(w / 2, 0f, w / 2, h, paint)
                canvas.drawLine(0f, h / 2, w, h / 2, paint)
            }
            Layout.DEPTH -> {
                canvas.drawLine(0f, h, w / 2, h / 3, paint)
                canvas.drawLine(w, h, w / 2, h / 3, paint)
                canvas.drawLine(0f, h * 2 / 3, w, h * 2 / 3, paint)
            }
        }
        guidance?.target?.let { target ->
            paint.color = lime; paint.strokeWidth = density * 2
            canvas.drawCircle(target.x * w, target.y * h, 23 * density, paint)
            canvas.drawLine(target.x * w - 8 * density, target.y * h, target.x * w + 8 * density, target.y * h, paint)
            canvas.drawLine(target.x * w, target.y * h - 8 * density, target.x * w, target.y * h + 8 * density, paint)
        }
        guidance?.horizon?.let { y ->
            paint.color = lime; paint.strokeWidth = density * 2
            paint.pathEffect = DashPathEffect(floatArrayOf(10 * density, 7 * density), 0f)
            canvas.drawLine(0f, y * h, w, y * h, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL; paint.textSize = 12 * density
            canvas.drawText("手动标记的水平线", 12 * density, (y * h - 8 * density).coerceAtLeast(20 * density), paint)
        }
        val skeleton = body ?: return
        paint.color = Color.argb(190, 214, 245, 134); paint.strokeWidth = density * 2
        bones.forEach { (a, b) ->
            val start = skeleton.visible(a); val end = skeleton.visible(b)
            if (start != null && end != null) canvas.drawLine(start.x * w, start.y * h, end.x * w, end.y * h, paint)
        }
        paint.style = Paint.Style.FILL
        skeleton.joints.values.filter { it.confidence >= .65f && it.x in 0f..1f && it.y in 0f..1f }.forEach {
            canvas.drawCircle(it.x * w, it.y * h, 3 * density, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!landscape || !showGuides) return false
        if (event.action == MotionEvent.ACTION_UP) { performClick(); onHorizon?.invoke((event.y / height).coerceIn(0f, 1f)) }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    companion object {
        val bones = listOf(
            Joint.LEFT_SHOULDER to Joint.RIGHT_SHOULDER, Joint.LEFT_SHOULDER to Joint.LEFT_ELBOW,
            Joint.LEFT_ELBOW to Joint.LEFT_WRIST, Joint.RIGHT_SHOULDER to Joint.RIGHT_ELBOW,
            Joint.RIGHT_ELBOW to Joint.RIGHT_WRIST, Joint.LEFT_SHOULDER to Joint.LEFT_HIP,
            Joint.RIGHT_SHOULDER to Joint.RIGHT_HIP, Joint.LEFT_HIP to Joint.RIGHT_HIP,
            Joint.LEFT_HIP to Joint.LEFT_KNEE, Joint.LEFT_KNEE to Joint.LEFT_ANKLE,
            Joint.RIGHT_HIP to Joint.RIGHT_KNEE, Joint.RIGHT_KNEE to Joint.RIGHT_ANKLE
        )
    }
}
