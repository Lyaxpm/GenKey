package com.gen.keymapper

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.util.LinkedHashMap

/** Shizuku UserService: dijalankan sebagai UID shell (atau root), bukan proses aplikasi biasa. */
class ShellInputService : IInputBridge.Stub() {
    private data class Touch(var id: Int, var x: Float, var y: Float)
    private val touches = LinkedHashMap<Int, Touch>()
    private var downTime = 0L
    private val injector: Any? by lazy {
        try {
            val klass = Class.forName("android.hardware.input.InputManagerGlobal")
            klass.getMethod("getInstance").invoke(null)
        } catch (_: Throwable) {
            try {
                val klass = Class.forName("android.hardware.input.InputManager")
                klass.getMethod("getInstance").invoke(null)
            } catch (_: Throwable) { null }
        }
    }

    override fun ping(): Boolean = injector != null

    private fun send(action: Int): Boolean {
        val manager = injector ?: return false
        if (touches.isEmpty()) return false
        val props = Array(touches.size) { MotionEvent.PointerProperties() }
        val coords = Array(touches.size) { MotionEvent.PointerCoords() }
        touches.values.forEachIndexed { index, touch ->
            props[index].id = touch.id
            props[index].toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[index].x = touch.x.coerceAtLeast(0f)
            coords[index].y = touch.y.coerceAtLeast(0f)
            coords[index].pressure = 1f
            coords[index].size = 1f
        }
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, touches.size,
            props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        return try {
            val method = manager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            (method.invoke(manager, event, 1) as? Boolean) == true
        } catch (_: Throwable) { false } finally { event.recycle() }
    }

    @Synchronized override fun pointer(pointerToken: Int, action: Int, x: Float, y: Float) {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (touches.containsKey(pointerToken) || touches.size >= 10) return
                val id = (0..31).firstOrNull { candidate -> touches.values.none { it.id == candidate } } ?: return
                if (touches.isEmpty()) downTime = SystemClock.uptimeMillis()
                touches[pointerToken] = Touch(id, x, y)
                val i = touches.size - 1
                val a = if (i == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_POINTER_DOWN or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                if (!send(a)) touches.remove(pointerToken)
            }
            MotionEvent.ACTION_MOVE -> {
                val t = touches[pointerToken] ?: return
                t.x = x; t.y = y
                send(MotionEvent.ACTION_MOVE)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val t = touches[pointerToken] ?: return
                t.x = x; t.y = y
                val index = touches.keys.indexOf(pointerToken)
                val a = if (touches.size == 1) MotionEvent.ACTION_UP
                    else MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                send(a)
                touches.remove(pointerToken)
                if (touches.isEmpty()) downTime = 0
            }
        }
    }

    /** Tap/swipe langsung melalui InputManager, bukan memulai proses 'input' tiap tombol. */
    override fun tap(x: Int, y: Int, durationMs: Int) {
        val token = Int.MIN_VALUE + SystemClock.uptimeMillis().toInt()
        pointer(token, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat())
        try { Thread.sleep(durationMs.coerceIn(25, 500).toLong()) } catch (_: InterruptedException) {}
        pointer(token, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat())
    }

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int) {
        val token = Int.MIN_VALUE + SystemClock.uptimeMillis().toInt()
        pointer(token, MotionEvent.ACTION_DOWN, x1.toFloat(), y1.toFloat())
        val ms = durationMs.coerceIn(50, 2500)
        val frames = (ms / 16).coerceAtLeast(2)
        for (n in 1..frames) {
            val t = n.toFloat() / frames
            pointer(token, MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t)
            try { Thread.sleep((ms / frames).toLong()) } catch (_: InterruptedException) { break }
        }
        pointer(token, MotionEvent.ACTION_UP, x2.toFloat(), y2.toFloat())
    }

    @Synchronized override fun releaseAll() {
        touches.keys.toList().forEach { token ->
            val t = touches[token] ?: return@forEach
            pointer(token, MotionEvent.ACTION_UP, t.x, t.y)
        }
    }
    override fun destroy() { releaseAll(); System.exit(0) }
}
