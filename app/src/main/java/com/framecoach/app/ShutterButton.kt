package com.framecoach.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button

/** Native, accessible shutter; artwork is independent of font rendering. */
class ShutterButton(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true; isFocusable = true; contentDescription = "拍照" }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info); info.className = Button::class.java.name
    }
    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val radius = minOf(width, height) * .43f
        paint.color = Color.rgb(242, 237, 228); paint.alpha = if (isEnabled) 255 else 100
        paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density * 2
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius * if (isPressed) .73f else .82f, paint)
    }
}
