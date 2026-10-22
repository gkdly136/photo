package com.framecoach.app

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.random.Random

/** Original procedural illustration; never presented as a live camera or model result. */
class DemoView(context: Context) : View(context) {
    var portrait = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.rgb(81, 110, 125), Color.rgb(173, 184, 174), Color.rgb(209, 188, 154)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
        paint.color = Color.argb(175, 247, 221, 174)
        canvas.drawCircle(w * .76f, h * .22f, w * .067f, paint)

        ridge(canvas, w, h, Color.rgb(103, 132, 140), floatArrayOf(0f,.46f,.18f,.23f,.38f,.48f,.63f,.28f,.83f,.44f,1f,.35f))
        ridge(canvas, w, h, Color.rgb(71, 105, 116), floatArrayOf(0f,.57f,.14f,.44f,.34f,.55f,.55f,.39f,.75f,.53f,1f,.46f))
        paint.shader = LinearGradient(0f, h * .57f, 0f, h,
            Color.rgb(114, 143, 145), Color.rgb(42, 67, 74), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .57f, w, h, paint); paint.shader = null
        val random = Random(93)
        paint.strokeWidth = 1f
        repeat(95) {
            val y = h * (.59f + random.nextFloat() * .4f)
            val x = random.nextFloat() * w
            paint.color = Color.argb(20 + random.nextInt(35), 219, 214, 186)
            canvas.drawLine(x, y, x + w * (.015f + random.nextFloat() * .16f), y, paint)
        }
        val bank = Path().apply {
            moveTo(0f, h * .65f); cubicTo(w * .16f, h * .64f, w * .21f, h * .78f, w * .45f, h * .83f)
            cubicTo(w * .6f, h * .88f, w * .75f, h * .94f, w, h * .98f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        paint.color = Color.rgb(47, 65, 61); canvas.drawPath(bank, paint)
        // Fir silhouettes frame the open view without implying scene understanding.
        for (i in 0..8) {
            val x = w * (i * .027f - .015f)
            val base = h * (.72f + i * .012f)
            val height = h * (.19f - i * .012f)
            tree(canvas, x, base, height, Color.rgb(34, 58, 58))
        }
        if (portrait) person(canvas, w, h)
        // Subtle grain gives the illustration a softer material instead of hard UI shapes.
        paint.strokeWidth = 1f
        repeat(1600) {
            paint.color = Color.argb(13, 244, 236, 217)
            canvas.drawPoint(random.nextFloat() * w, random.nextFloat() * h, paint)
        }
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.argb(40, 10, 19, 23), Color.TRANSPARENT, Color.argb(70, 10, 19, 23)),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
    }

    private fun person(canvas: Canvas, w: Float, h: Float) {
        val unit = minOf(w, h)
        fun x(value: Float) = w * .43f + (value - .43f) * unit
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(28, 36, 37); paint.strokeWidth = unit * .033f
        canvas.drawLine(x(.408f), h * .60f, x(.39f), h * .84f, paint)
        canvas.drawLine(x(.455f), h * .60f, x(.49f), h * .83f, paint)
        paint.color = Color.rgb(191, 167, 135)
        val coat = Path().apply {
            moveTo(x(.395f), h * .395f); quadTo(x(.43f), h * .38f, x(.47f), h * .40f)
            lineTo(x(.49f), h * .63f); quadTo(x(.44f), h * .65f, x(.375f), h * .63f); close()
        }
        canvas.drawPath(coat, paint)
        paint.color = Color.rgb(172, 147, 117); paint.strokeWidth = unit * .026f
        canvas.drawLine(x(.393f), h * .421f, x(.355f), h * .61f, paint)
        canvas.drawLine(x(.473f), h * .423f, x(.51f), h * .59f, paint)
        paint.color = Color.rgb(208, 177, 148); paint.strokeWidth = unit * .019f
        canvas.drawLine(x(.355f), h * .61f, x(.35f), h * .63f, paint)
        canvas.drawLine(x(.51f), h * .59f, x(.51f), h * .61f, paint)
        canvas.drawOval(x(.403f), h * .30f, x(.457f), h * .383f, paint)
        paint.color = Color.rgb(42, 42, 37)
        val hair = Path().apply {
            moveTo(x(.403f), h * .35f); cubicTo(x(.38f), h * .28f, x(.48f), h * .28f, x(.456f), h * .352f)
            lineTo(x(.44f), h * .325f); lineTo(x(.408f), h * .329f); close()
        }
        canvas.drawPath(hair, paint)
        paint.color = Color.argb(80, 91, 75, 57); paint.strokeWidth = unit * .004f
        canvas.drawLine(x(.43f), h * .43f, x(.43f), h * .61f, paint)
    }

    private fun ridge(canvas: Canvas, w: Float, h: Float, color: Int, points: FloatArray) {
        val path = Path().apply {
            moveTo(0f, h)
            for (i in points.indices step 2) lineTo(points[i] * w, points[i + 1] * h)
            lineTo(w, h); close()
        }
        paint.color = color; canvas.drawPath(path, paint)
    }
    private fun tree(canvas: Canvas, x: Float, base: Float, height: Float, color: Int) {
        paint.color = color
        val path = Path().apply {
            moveTo(x, base - height); lineTo(x - height * .18f, base); lineTo(x + height * .18f, base); close()
        }
        canvas.drawPath(path, paint)
    }
}

