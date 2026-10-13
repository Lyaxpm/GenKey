package com.gen.keymapper

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.util.concurrent.atomic.AtomicInteger

/** All touch state and timed gestures run on one shell thread; Binder calls never sleep. */
class ShellInputService : IInputBridge.Stub() {
    private val thread = HandlerThread("GenKey-input").apply { start() }
    private val handler = Handler(thread.looper)
    private val state = TouchState()
    private val tokens = AtomicInteger(-1)
    private var downTime = 0L
    private var generation = 0
    @Volatile private var lastHeartbeat = SystemClock.uptimeMillis()
    @Volatile private var error = ""
    @Volatile private var accepted = 0L
    @Volatile private var rejected = 0L
    private val injector: Any? = try {
        val type = try { Class.forName("android.hardware.input.InputManagerGlobal") }
        catch (_: ClassNotFoundException) { Class.forName("android.hardware.input.InputManager") }
        type.getMethod("getInstance").invoke(null)
    } catch (e: Throwable) { error = "InputManager: ${e.cause?.message ?: e.message}"; null }
    private val injectMethod = try {
        injector?.javaClass?.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    } catch (e: Throwable) { error = "injectInputEvent: ${e.message}"; null }

    init {
        handler.post(object : Runnable {
            override fun run() {
                if (SystemClock.uptimeMillis() - lastHeartbeat > 3000) cancel()
                handler.postDelayed(this, 1000)
            }
        })
    }
    override fun ping() = injectMethod != null
    override fun heartbeat() { lastHeartbeat = SystemClock.uptimeMillis() }
    override fun diagnostics() = "Diterima: $accepted · Ditolak: $rejected" +
        if (error.isBlank()) "" else "\n$error"

    private fun send(frame: TouchState.Frame?) {
        frame ?: return
        if (frame.action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val props = frame.points.map { p -> MotionEvent.PointerProperties().apply {
            id = p.id; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coords = frame.points.map { p -> MotionEvent.PointerCoords().apply {
            x = p.x.coerceAtLeast(0f); y = p.y.coerceAtLeast(0f)
            pressure = if (frame.action == MotionEvent.ACTION_UP || frame.action == MotionEvent.ACTION_CANCEL) 0f else 1f
            size = 1f
        } }.toTypedArray()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), frame.action,
            props.size, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try {
            // ASYNC: never wait for the target game's event loop while holding other pointers.
            if (injectMethod?.invoke(injector, event, 0) == true) accepted++
            else { rejected++; error = "Android menolak injeksi; cek izin Shizuku / firmware."; state.cancel(); generation++ }
        } catch (e: Throwable) {
            rejected++; error = e.cause?.message ?: e.message ?: "Injeksi gagal"
            state.cancel(); generation++
        } finally { event.recycle() }
    }
    override fun pointer(pointerToken: Int, action: Int, x: Float, y: Float) {
        handler.post {
            send(when (action) {
                MotionEvent.ACTION_DOWN -> state.down(pointerToken, x, y)
                MotionEvent.ACTION_MOVE -> state.move(pointerToken, x, y)
                MotionEvent.ACTION_UP -> state.up(pointerToken)
                MotionEvent.ACTION_CANCEL -> state.cancel()
                else -> null
            })
        }
    }
    override fun tap(x: Int, y: Int, durationMs: Int) {
        handler.post {
            val token = tokens.getAndDecrement()
            val start = state.down(token, x.toFloat(), y.toFloat()) ?: return@post
            send(start)
            val epoch = generation
            handler.postDelayed({ if (epoch == generation) send(state.up(token)) }, durationMs.coerceIn(20, 5000).toLong())
        }
    }
    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int) {
        handler.post {
            val token = tokens.getAndDecrement()
            val start = state.down(token, x1.toFloat(), y1.toFloat()) ?: return@post
            send(start)
            val epoch = generation
            val began = SystemClock.uptimeMillis()
            val duration = durationMs.coerceIn(50, 3000)
            val step = object : Runnable {
                override fun run() {
                    if (epoch != generation) return
                    val t = ((SystemClock.uptimeMillis() - began).toFloat() / duration).coerceAtMost(1f)
                    send(state.move(token, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t))
                    if (t >= 1f) send(state.up(token)) else handler.postDelayed(this, 8)
                }
            }
            handler.post(step)
        }
    }
    private fun cancel() { generation++; send(state.cancel()) }
    override fun releaseAll() { handler.post { cancel() } }
    override fun destroy() { handler.post { cancel(); System.exit(0) } }
}
