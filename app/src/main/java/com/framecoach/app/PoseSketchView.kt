package com.framecoach.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.framecoach.core.*

/** Original vector diagram rendered locally; no network assets or copied reference images. */
class PoseSketchView(context: Context, private val card: PoseCard, private val view: PoseView,
                     private val framing: Framing) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(34, 41, 44))
        val scale = minOf(width / .80f, height / if (framing == Framing.HALF) .65f else 1f)
        val center = width / 2f
        val top = (height - scale * if (framing == Framing.HALF) .65f else .98f) / 2f
        fun x(p: Point) = center + (p.x - .5f) * scale
        fun y(p: Point) = top + p.y * scale
        val body = card.sketch(view)
        paint.strokeWidth = scale * .012f; paint.strokeCap = Paint.Cap.ROUND
        if (card.prop != null) {
            paint.color = Color.rgb(103, 120, 124)
            if (card.prop == Prop.WALL) canvas.drawLine(center - scale * .33f, top + scale * .18f, center - scale * .33f, top + scale * .93f, paint)
            else {
                canvas.drawLine(center - scale * .18f, top + scale * .63f, center + scale * .23f, top + scale * .63f, paint)
                canvas.drawLine(center - scale * .16f, top + scale * .63f, center - scale * .16f, top + scale * .90f, paint)
                canvas.drawLine(center + scale * .21f, top + scale * .63f, center + scale * .21f, top + scale * .90f, paint)
            }
        }
        paint.color = Color.rgb(235, 198, 151)
        GuideOverlay.bones.forEach { (a, b) ->
            val start = body.joints.getValue(a); val end = body.joints.getValue(b)
            if (framing == Framing.FULL || (start.y < .64f && end.y < .64f)) canvas.drawLine(x(start), y(start), x(end), y(end), paint)
        }
        val ls = body.joints.getValue(Joint.LEFT_SHOULDER); val rs = body.joints.getValue(Joint.RIGHT_SHOULDER)
        val neck = Point((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
        canvas.drawLine(x(neck), y(neck), x(neck), y(neck) - scale * .04f, paint)
        paint.style = Paint.Style.STROKE
        canvas.drawOval(x(neck) - scale * .065f, y(neck) - scale * .19f, x(neck) + scale * .065f, y(neck) - scale * .05f, paint)
        paint.style = Paint.Style.FILL
        if (view == PoseView.OBLIQUE) canvas.drawCircle(x(neck) + scale * .035f, y(neck) - scale * .13f, scale * .009f, paint)
    }
    init { contentDescription = "${card.title}，${view.label}，${framing.label}原创动作示意；不是实拍照片" }
}
