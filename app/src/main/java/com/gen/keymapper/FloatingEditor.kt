package com.gen.keymapper

import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import kotlin.math.hypot

/** Drag handles stay inside system bars/cutouts without changing mapping coordinates. */
internal object FloatingEditor {
    fun safe(parent: View): Rect {
        val result = Rect(0, 0, parent.width, parent.height)
        val wi = parent.rootWindowInsets ?: return result
        if (Build.VERSION.SDK_INT >= 30) {
            val i = wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            result.set(i.left, i.top, parent.width - i.right, parent.height - i.bottom)
        } else {
            @Suppress("DEPRECATION")
            result.set(wi.systemWindowInsetLeft, wi.systemWindowInsetTop,
                parent.width - wi.systemWindowInsetRight, parent.height - wi.systemWindowInsetBottom)
        }
        return result
    }
    fun constrain(view: View) {
        val parent = view.parent as? View ?: return
        val s = safe(parent)
        val pad = Ui.dp(view.context, 6)
        view.x = EditorBounds.clamp(view.x, view.width.toFloat(), (s.left + pad).toFloat(), (s.right - pad).toFloat())
        view.y = EditorBounds.clamp(view.y, view.height.toFloat(), (s.top + pad).toFloat(), (s.bottom - pad).toFloat())
    }
    fun drag(handle: View, target: View, onTap: (() -> Unit)? = null) {
        var rawX = 0f; var rawY = 0f; var startX = 0f; var startY = 0f; var moved = false
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rawX = e.rawX; rawY = e.rawY; startX = target.x; startY = target.y; moved = false
                    handle.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - rawX; val dy = e.rawY - rawY
                    if (hypot(dx, dy) > Ui.dp(handle.context, 4)) moved = true
                    if (moved) { target.x = startX + dx; target.y = startY + dy; constrain(target) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) onTap?.invoke(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }
}
