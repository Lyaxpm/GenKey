package com.gen.keymapper

import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Shizuku UserService: berjalan di proses terpisah dengan UID shell (atau root),
 * bukan di proses aplikasi. Di sinilah sentuhan multi-jari disuntikkan ke sistem.
 * Proses ini tidak terkena pembatasan hidden API, jadi refleksi di sini aman.
 */
class ShellInputService : IInputBridge.Stub() {

    private class Touch(val id: Int, var x: Float, var y: Float)

    private class Injector(
        val target: Any,
        val method: Method,
        val withUid: Boolean,
        val name: String
    )

    private val touches = LinkedHashMap<Int, Touch>()
    private var downTime = 0L
    private var okCount = 0L
    private var failCount = 0L
    private var lastError = ""
    private var discovery = ""

    private val injector: Injector? by lazy { findInjector() }

    // ---------- penemuan jalur injeksi ----------

    private fun findInjector(): Injector? {
        val log = StringBuilder()
        val evType = InputEvent::class.java
        val intType = Int::class.javaPrimitiveType

        fun pick(target: Any, cls: Class<*>, label: String): Injector? {
            try {
                return Injector(target, cls.getMethod("injectInputEvent", evType, intType), false, "$label.injectInputEvent")
            } catch (t: Throwable) {
                log.append("$label.injectInputEvent: ${t.javaClass.simpleName}; ")
            }
            try {
                return Injector(
                    target, cls.getMethod("injectInputEventToTarget", evType, intType, intType),
                    true, "$label.injectInputEventToTarget"
                )
            } catch (t: Throwable) {
                log.append("$label.injectInputEventToTarget: ${t.javaClass.simpleName}; ")
            }
            return null
        }

        // 1) IInputManager langsung dari ServiceManager (paling stabil lintas versi).
        try {
            val sm = Class.forName("android.os.ServiceManager")
            val binder = sm.getMethod("getService", String::class.java).invoke(null, "input") as? IBinder
            if (binder != null) {
                val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
                val iface = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                val cls = Class.forName("android.hardware.input.IInputManager")
                if (iface != null) pick(iface, cls, "IInputManager")?.let { discovery = log.toString(); return it }
            } else {
                log.append("ServiceManager(input)=null; ")
            }
        } catch (t: Throwable) {
            log.append("IInputManager: ${root(t)}; ")
        }

        // 2) InputManagerGlobal (Android 14+).
        try {
            val cls = Class.forName("android.hardware.input.InputManagerGlobal")
            val inst = cls.getMethod("getInstance").invoke(null)
            if (inst != null) pick(inst, cls, "InputManagerGlobal")?.let { discovery = log.toString(); return it }
        } catch (t: Throwable) {
            log.append("InputManagerGlobal: ${root(t)}; ")
        }

        // 3) InputManager.getInstance() (Android lama).
        try {
            val cls = Class.forName("android.hardware.input.InputManager")
            val inst = cls.getMethod("getInstance").invoke(null)
            if (inst != null) pick(inst, cls, "InputManager")?.let { discovery = log.toString(); return it }
        } catch (t: Throwable) {
            log.append("InputManager: ${root(t)}; ")
        }
        discovery = log.toString()
        return null
    }

    private fun root(t: Throwable): String {
        var c: Throwable = t
        if (t is InvocationTargetException && t.targetException != null) c = t.targetException
        return "${c.javaClass.simpleName}: ${c.message}"
    }

    private fun setDisplay(ev: InputEvent) {
        try {
            InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType).invoke(ev, 0)
        } catch (_: Throwable) {
        }
    }

    /** mode 0 = async, 1 = tunggu hasil. */
    private fun inject(ev: InputEvent, mode: Int): Boolean {
        val inj = injector ?: run {
            lastError = "tidak ada jalur injeksi: $discovery"
            failCount++
            return false
        }
        setDisplay(ev)
        return try {
            val r = if (inj.withUid) inj.method.invoke(inj.target, ev, mode, -1)
            else inj.method.invoke(inj.target, ev, mode)
            val ok = (r as? Boolean) != false
            if (ok) okCount++ else {
                failCount++
                lastError = "injectInputEvent mengembalikan false"
            }
            ok
        } catch (t: Throwable) {
            failCount++
            lastError = root(t)
            false
        }
    }

    // ---------- sentuhan multi-jari ----------

    private fun send(action: Int, mode: Int): Boolean {
        if (touches.isEmpty()) return false
        val n = touches.size
        val props = Array(n) { MotionEvent.PointerProperties() }
        val coords = Array(n) { MotionEvent.PointerCoords() }
        var i = 0
        for (t in touches.values) {
            props[i].id = t.id
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i].x = t.x.coerceAtLeast(0f)
            coords[i].y = t.y.coerceAtLeast(0f)
            coords[i].pressure = 1f
            coords[i].size = 1f
            i++
        }
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(
            downTime, now, action, n, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        return try {
            inject(ev, mode)
        } finally {
            ev.recycle()
        }
    }

    private fun freeId(): Int {
        for (c in 0..31) if (touches.values.none { it.id == c }) return c
        return -1
    }

    @Synchronized
    override fun pointer(token: Int, action: Int, x: Float, y: Float) {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val existing = touches[token]
                if (existing != null) {
                    existing.x = x; existing.y = y
                    send(MotionEvent.ACTION_MOVE, 0)
                    return
                }
                if (touches.size >= 10) return
                val id = freeId()
                if (id < 0) return
                if (touches.isEmpty()) downTime = SystemClock.uptimeMillis()
                touches[token] = Touch(id, x, y)
                val index = touches.size - 1
                val a = if (index == 0) MotionEvent.ACTION_DOWN
                else MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                if (!send(a, 1)) {
                    touches.remove(token)
                    if (touches.isEmpty()) downTime = 0
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val t = touches[token] ?: return
                t.x = x; t.y = y
                send(MotionEvent.ACTION_MOVE, 0)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val t = touches[token] ?: return
                t.x = x; t.y = y
                var index = 0
                for (k in touches.keys) {
                    if (k == token) break
                    index++
                }
                val a = if (touches.size == 1) MotionEvent.ACTION_UP
                else MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                send(a, 1)
                touches.remove(token)
                if (touches.isEmpty()) downTime = 0
            }
        }
    }

    @Synchronized
    override fun releaseAll() {
        val tokens = touches.keys.toList()
        for (tk in tokens) {
            val t = touches[tk] ?: continue
            pointer(tk, MotionEvent.ACTION_UP, t.x, t.y)
        }
        touches.clear()
        downTime = 0
    }

    // ---------- tombol ----------

    @Synchronized
    override fun key(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(
            now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD
        )
        inject(down, 1)
        val up = KeyEvent(
            now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD
        )
        inject(up, 1)
    }

    // ---------- umum ----------

    override fun ping(): Boolean = injector != null

    @Synchronized
    override fun diag(): String =
        "jalur=${injector?.name ?: "TIDAK ADA"}; ok=$okCount; gagal=$failCount; " +
            "terakhir=${lastError.ifEmpty { "-" }}; android=${Build.VERSION.SDK_INT}; uid=${Process.myUid()}" +
            if (injector == null) "; catatan=$discovery" else ""

    override fun destroy() {
        try {
            releaseAll()
        } catch (_: Throwable) {
        }
        System.exit(0)
    }
}
