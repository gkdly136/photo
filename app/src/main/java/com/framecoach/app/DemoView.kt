package com.framecoach.app

import android.content.Context
import android.graphics.*
import android.view.View

/** Original vector illustration, explicitly labelled as synthetic demo in the UI. */
class DemoView(context: Context) : View(context) {
    var portrait = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.rgb(139, 164, 153), Color.rgb(215, 216, 182), Color.rgb(56, 83, 70)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
        paint.color = Color.argb(180, 242, 229, 174); canvas.drawCircle(w * .78f, h * .22f, w * .095f, paint)
        mountain(canvas, w, h, .48f, Color.rgb(99, 126, 112), floatArrayOf(0f, .6f, .15f, .2f, .3f, .65f, .45f, .35f, .65f, .9f, 1f, .4f))
        mountain(canvas, w, h, .64f, Color.rgb(59, 91, 78), floatArrayOf(0f, .3f, .2f, .65f, .38f, .35f, .62f, .8f, .85f, .2f, 1f, .5f))
        val path = Path().apply { moveTo(w * .58f, h * .66f); cubicTo(w * .4f, h * .76f, w * .83f, h * .83f, w * .45f, h); lineTo(w * .95f, h); cubicTo(w, h * .8f, w * .52f, h * .74f, w * .58f, h * .66f) }
        paint.color = Color.rgb(165, 184, 154); canvas.drawPath(path, paint)
        if (portrait) {
            paint.color = Color.rgb(45, 51, 38)
            canvas.drawCircle(w * .43f, h * .34f, w * .038f, paint)
            paint.color = Color.rgb(201, 167, 116)
            canvas.drawRoundRect(w * .38f, h * .39f, w * .48f, h * .63f, 20f, 20f, paint)
            paint.color = Color.rgb(44, 53, 46); paint.strokeWidth = w * .035f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(w * .405f, h * .61f, w * .39f, h * .84f, paint)
            canvas.drawLine(w * .455f, h * .61f, w * .49f, h * .83f, paint)
            paint.color = Color.rgb(201, 167, 116); paint.strokeWidth = w * .028f
            canvas.drawLine(w * .385f, h * .42f, w * .35f, h * .63f, paint)
            canvas.drawLine(w * .48f, h * .42f, w * .51f, h * .61f, paint)
        }
    }
    private fun mountain(canvas: Canvas, w: Float, h: Float, top: Float, color: Int, values: FloatArray) {
        val path = Path().apply {
            moveTo(0f, h)
            for (i in values.indices step 2) lineTo(values[i] * w, h * (top + values[i + 1] * .22f))
            lineTo(w, h); close()
        }
        paint.color = color; canvas.drawPath(path, paint)
    }
}
