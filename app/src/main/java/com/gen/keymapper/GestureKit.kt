package com.gen.keymapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper

/** Backend Accessibility; drag hanya satu per satu. Untuk multi-touch gunakan Shizuku. */
class GestureKit(private val svc: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private data class Drag(
        var last: GestureDescription.StrokeDescription,
        var x: Float, var y: Float,
        var targetX: Float, var targetY: Float,
        var ending: Boolean = false
    )
    private val drags = HashMap<Int, Drag>()
    private var seq = 1
    val supportsContinue = android.os.Build.VERSION.SDK_INT >= 26

    private fun send(sd: GestureDescription.StrokeDescription, completed: (Boolean) -> Unit = {}) {
        main.post {
            try {
                if (!svc.dispatchGesture(GestureDescription.Builder().addStroke(sd).build(),
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) = completed(true)
                        override fun onCancelled(gestureDescription: GestureDescription?) = completed(false)
                    }, main)) completed(false)
            } catch (_: Throwable) { completed(false) }
        }
    }

    fun tap(x: Float, y: Float, dur: Long = 60) {
        val p = Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
        send(GestureDescription.StrokeDescription(p, 0, dur.coerceIn(20, 500)))
    }

    fun hold(x: Float, y: Float, dur: Long) {
        val p = Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
        send(GestureDescription.StrokeDescription(p, 0, dur.coerceIn(100, 5000)))
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long) {
        val p = Path().apply {
            moveTo(x1.coerceAtLeast(0f), y1.coerceAtLeast(0f))
            lineTo(x2.coerceAtLeast(0f), y2.coerceAtLeast(0f))
        }
        send(GestureDescription.StrokeDescription(p, 0, dur.coerceIn(50, 3000)))
    }

    fun startDrag(x1: Float, y1: Float, x2: Float, y2: Float): Int? {
        if (!supportsContinue) return null
        val p = Path().apply { moveTo(x1.coerceAtLeast(0f), y1.coerceAtLeast(0f)); lineTo(x2.coerceAtLeast(0f), y2.coerceAtLeast(0f)) }
        val stroke = GestureDescription.StrokeDescription(p, 0, 120, true)
        val id = seq++
        drags[id] = Drag(stroke, x2, y2, x2, y2)
        send(stroke) { ok -> next(id, ok) }
        return id
    }

    /** Stroke berikut dikirim setelah callback stroke sebelumnya untuk mencegah cancel sendiri. */
    private fun next(id: Int, ok: Boolean) {
        val drag = drags[id] ?: return
        if (!ok) { drags.remove(id); return }
        val end = drag.ending
        val tx = drag.targetX.coerceAtLeast(0f)
        val ty = drag.targetY.coerceAtLeast(0f)
        try {
            val p = Path().apply { moveTo(drag.x.coerceAtLeast(0f), drag.y.coerceAtLeast(0f)); lineTo(tx, ty) }
            val stroke = drag.last.continueStroke(p, 0, 130, !end)
            drag.last = stroke
            drag.x = tx; drag.y = ty
            if (end) drags.remove(id)
            send(stroke) { finished -> if (!end) next(id, finished) }
        } catch (_: Throwable) { drags.remove(id) }
    }

    fun dragTo(strokeId: Int, x1: Float, y1: Float, x2: Float, y2: Float, finish: Boolean) {
        drags[strokeId]?.apply {
            targetX = x2; targetY = y2
            if (finish) ending = true
        }
    }

    fun releaseAll() {
        drags.values.forEach { it.ending = true }
    }
}
