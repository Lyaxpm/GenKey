package com.gen.keymapper

import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Ukuran dan asal jendela overlay di layar (semua dalam piksel). */
class Geo(val w: Int, val h: Int, val ox: Int, val oy: Int) {
    val minSide: Int get() = min(w, h)
    fun sx(fx: Float): Float = ox + fx * w
    fun sy(fy: Float): Float = oy + fy * h
}

/**
 * Mesin pemetaan. Semua dipanggil dari main thread: menerima tombol/mouse,
 * lalu mengirim perintah jari virtual ke Backend.
 */
/** Pengaturan yang dibaca engine. Diimplementasikan oleh MappingStore. */
interface EngineSettings {
    val tapMs: Int
    val cursorSpeed: Float
    val camSens: Float
    val aimKey: Int
    val cursorHoldKey: Int get() = KeyEvent.KEYCODE_ESCAPE
    val freeCursor: Boolean get() = true
    val mouseOnlyInCursor: Boolean get() = true
}

/** Tujuan perintah jari virtual. Default: Shizuku. */
interface PointerOut {
    fun pointer(token: Int, action: Int, x: Float, y: Float)
    fun releaseAll()
}

object ShizukuOut : PointerOut {
    override fun pointer(token: Int, action: Int, x: Float, y: Float) {
        Backend.pointer(token, action, x, y)
    }

    override fun releaseAll() {
        Backend.releaseAll()
    }
}

class InputEngine(
    private val store: EngineSettings,
    private val host: Host,
    private val out: PointerOut = ShizukuOut,
    private val handler: InputScheduler = AndroidInputScheduler()
) {

    interface Host {
        fun geo(): Geo?
        fun onAimChanged(aim: Boolean)
        fun onVisualChanged()
    }

    private class BRt {
        var pressed = false
        var toggled = false
        var turbo: Runnable? = null
        var swipe: Runnable? = null
    }

    private class JoyRt {
        val dirs = BooleanArray(4) // atas, bawah, kiri, kanan
        var down = false
        var lx = 0f
        var ly = 0f
    }

    private class CamRt {
        var down = false
        var token = 0
        var x = 0f
        var y = 0f
        var ax = 0f
        var ay = 0f
        var lastMove = 0L
        var ticking = false
    }

    private val bRt = HashMap<String, BRt>()
    private val joyRt = HashMap<String, JoyRt>()
    private val camRt = CamRt()
    private val held = HashSet<Int>()
    private val modeKeysDown = HashSet<Int>()
    private var cursorHeld = false
    private var camHoldBinding: Binding? = null
    private var cursorDown = false

    var profile: Profile? = null
        private set
    var aim = false
        private set
    var cursorX = -1f
        private set
    var cursorY = -1f
        private set

    private fun rt(b: Binding): BRt = bRt.getOrPut(b.id) { BRt() }
    private fun jrt(b: Binding): JoyRt = joyRt.getOrPut(b.id) { JoyRt() }
    private fun tok(b: Binding): Int = (b.id.hashCode() and 0x00ffffff) * 4 + 1

    private fun ptr(token: Int, action: Int, x: Float, y: Float) {
        out.pointer(token, action, x, y)
    }

    // ---------- siklus hidup ----------

    fun setProfile(p: Profile?) {
        releaseAll()
        profile = p
        aim = p?.startAim == true
        cursorX = -1f
        cursorY = -1f
        host.onAimChanged(aim)
    }

    /** Lepas semua jari dan hentikan semua timer (jeda, edit, atau berhenti). */
    fun releaseAll() {
        clearInput()
        modeKeysDown.clear()
        if (cursorHeld) {
            cursorHeld = false
            aim = true
            host.onAimChanged(aim)
        }
    }

    private fun clearInput() {
        handler.clear()
        wheelReleases.clear()
        bRt.clear()
        joyRt.clear()
        camRt.down = false
        camRt.ax = 0f
        camRt.ay = 0f
        camRt.ticking = false
        camHoldBinding = null
        cursorDown = false
        held.clear()
        out.releaseAll()
        host.onVisualChanged()
    }

    private fun ensureCursor(): Boolean {
        val g = host.geo() ?: return false
        if (cursorX < 0f || cursorY < 0f) {
            cursorX = g.w / 2f
            cursorY = g.h / 2f
        }
        cursorX = cursorX.coerceIn(0f, (g.w - 1).toFloat())
        cursorY = cursorY.coerceIn(0f, (g.h - 1).toFloat())
        return true
    }

    fun cursorOrCenter(): Pair<Float, Float> {
        ensureCursor()
        return cursorX to cursorY
    }

    fun cameraActive(): Boolean = activeCamera() != null

    // ---------- mode ----------

    fun toggleAim() { if (!cursorHeld) setAim(!aim) }

    fun setAim(on: Boolean) {
        if (cursorHeld && on) return
        if (on == aim) return
        // Stop joystick, turbo, swipe timers, mouse drag and camera together.
        // Keep mode-key latches so repeats cannot toggle again or lose Esc's release.
        clearInput()
        aim = on
        host.onAimChanged(aim)
        host.onVisualChanged()
    }

    private fun isToggleKey(code: Int): Boolean = code == store.aimKey ||
        ((store.aimKey == KeyEvent.KEYCODE_CTRL_LEFT || store.aimKey == KeyEvent.KEYCODE_CTRL_RIGHT) &&
            (code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT))

    private fun modeKey(code: Int, down: Boolean): Boolean {
        if (code == store.cursorHoldKey && code != KeyNames.NONE) {
            if (down) {
                if (modeKeysDown.add(code)) { cursorHeld = true; if (aim) setAim(false) else clearInput() }
            } else if (modeKeysDown.remove(code) && cursorHeld) {
                cursorHeld = false
                setAim(true)
            }
            return true
        }
        if (isToggleKey(code)) {
            if (down) {
                val alreadyHeld = modeKeysDown.any { isToggleKey(it) }
                if (modeKeysDown.add(code) && !alreadyHeld) toggleAim()
            } else modeKeysDown.remove(code)
            return true
        }
        return false
    }

    // ---------- masukan ----------

    /** Mengembalikan true jika tombol dikonsumsi. */
    fun onKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (KeyNames.isSystemKey(code)) return false
        val isDown = e.action == KeyEvent.ACTION_DOWN
        if (isDown && e.repeatCount > 0) return modeKeysDown.contains(code) || held.contains(code) ||
            (!aim && (store.mouseOnlyInCursor || cursorHeld))
        return onButton(code, isDown)
    }

    /** Shared keyboard path, also exercised without Android KeyEvent objects in tests. */
    fun onButton(code: Int, down: Boolean): Boolean {
        if (KeyNames.isSystemKey(code)) return false
        if (modeKey(code, down)) return true
        return trigger(code, down)
    }

    fun onMouseButton(code: Int, isDown: Boolean): Boolean {
        if (code == KeyNames.NONE) return false
        if (modeKey(code, isDown)) return true
        if (!aim && (store.freeCursor || cursorHeld)) {
            if (code == KeyNames.MOUSE_LEFT) cursorClick(isDown)
            return true
        }
        val consumed = trigger(code, isDown)
        if (!consumed && code == KeyNames.MOUSE_LEFT && !aim && activeCamera() == null) {
            cursorClick(isDown)
            return true
        }
        return consumed
    }

    private val wheelReleases = HashMap<Int, Runnable>()

    fun onWheel(v: Float, h: Float = 0f) {
        // In menu mode wheel must never fire game bindings.
        if (!aim && (store.freeCursor || cursorHeld)) return
        fun pulse(value: Float, positive: Int, negative: Int) {
            if (value == 0f) return
            val code = if (value > 0f) positive else negative
            wheelReleases.remove(code)?.let { handler.removeCallbacks(it) }
            // A new wheel event must not be swallowed by the previous held pulse.
            trigger(code, false)
            if (trigger(code, true)) {
                val release = Runnable { trigger(code, false); wheelReleases.remove(code) }
                wheelReleases[code] = release
                handler.postDelayed(release, store.tapMs.coerceAtLeast(16).toLong())
            }
        }
        pulse(v, KeyNames.WHEEL_UP, KeyNames.WHEEL_DOWN)
        pulse(h, KeyNames.WHEEL_RIGHT, KeyNames.WHEEL_LEFT)
    }

    fun onMouseMove(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        val cam = activeCamera()
        if (cam != null) {
            val s = store.camSens * cam.sens
            camRt.ax += dx * s
            camRt.ay += dy * s
            scheduleCam()
            return
        }
        if (aim) return
        if (!ensureCursor()) return
        val g = host.geo() ?: return
        cursorX = (cursorX + dx * store.cursorSpeed).coerceIn(0f, (g.w - 1).toFloat())
        cursorY = (cursorY + dy * store.cursorSpeed).coerceIn(0f, (g.h - 1).toFloat())
        if (cursorDown) ptr(CURSOR_TOKEN, MotionEvent.ACTION_MOVE, g.ox + cursorX, g.oy + cursorY)
        host.onVisualChanged()
    }

    // ---------- pemicu ----------

    private fun trigger(code: Int, isDown: Boolean): Boolean {
        if (!isDown) {
            if (!held.remove(code)) return false
            dispatch(code, false)
            return true
        }
        if (held.contains(code)) return true
        val matched = dispatch(code, true)
        if (matched) held.add(code)
        return matched
    }

    private fun scopeOk(b: Binding): Boolean = when (b.scope) {
        Scope.AIM -> aim
        Scope.CURSOR -> !aim
        else -> true
    }

    private fun dispatch(code: Int, isDown: Boolean): Boolean {
        val p = profile ?: return false
        var matched = false
        if (p.bindings.any { it.type == Bt.AIM && it.key == code }) {
            if (isDown) toggleAim()
            return true
        }
        if (!aim && (store.mouseOnlyInCursor || cursorHeld) && KeyNames.isKeyboard(code)) return true
        if (!aim && (store.freeCursor || cursorHeld) && (KeyNames.isMouseButton(code) || KeyNames.isWheel(code))) return false
        for (b in p.bindings) {
            when (b.type) {
                Bt.JOY -> {
                    val d = joyDir(b, code)
                    if (d >= 0) {
                        jrt(b).dirs[d] = isDown
                        updateJoy(b)
                        matched = true
                    }
                }
                Bt.CAM -> if (b.camHold != KeyNames.NONE && b.camHold == code) {
                    if (isDown) {
                        camHoldBinding = b
                        host.onVisualChanged()
                    } else if (camHoldBinding === b) {
                        camHoldBinding = null
                        endCamera()
                        host.onVisualChanged()
                    }
                    matched = true
                }
                Bt.KEY -> if (b.key == code && (scopeOk(b) || (!isDown && rt(b).pressed))) {
                    keyAction(b, isDown)
                    matched = true
                }
            }
        }
        return matched
    }

    // ---------- aksi tombol ----------

    private fun keyAction(b: Binding, isDown: Boolean) {
        val g = host.geo() ?: return
        val r = rt(b)
        val x = g.sx(b.x)
        val y = g.sy(b.y)
        val token = tok(b)
        when (b.action) {
            Act.TAP -> if (isDown && !r.pressed) {
                r.pressed = true
                ptr(token, MotionEvent.ACTION_DOWN, x, y)
                handler.postDelayed({
                    ptr(token, MotionEvent.ACTION_UP, x, y)
                    r.pressed = false
                }, store.tapMs.toLong())
            }
            Act.HOLD -> if (isDown) {
                if (!r.pressed) {
                    r.pressed = true
                    ptr(token, MotionEvent.ACTION_DOWN, x, y)
                }
            } else if (r.pressed) {
                r.pressed = false
                ptr(token, MotionEvent.ACTION_UP, x, y)
            }
            Act.TOGGLE -> if (isDown) {
                if (!r.toggled) {
                    r.toggled = true
                    ptr(token, MotionEvent.ACTION_DOWN, x, y)
                } else {
                    r.toggled = false
                    ptr(token, MotionEvent.ACTION_UP, x, y)
                }
            }
            Act.TURBO -> if (isDown) startTurbo(b, r, x, y, token) else stopTurbo(r)
            Act.SWIPE -> if (isDown) startSwipe(b, r, g, token)
        }
    }

    private fun startTurbo(b: Binding, r: BRt, x: Float, y: Float, token: Int) {
        if (r.turbo != null) return
        val down = min(store.tapMs, b.turboMs / 2).coerceAtLeast(15).toLong()
        val loop = object : Runnable {
            override fun run() {
                if (r.turbo !== this) return
                ptr(token, MotionEvent.ACTION_DOWN, x, y)
                handler.postDelayed({ ptr(token, MotionEvent.ACTION_UP, x, y) }, down)
                handler.postDelayed(this, b.turboMs.toLong())
            }
        }
        r.turbo = loop
        r.pressed = true
        handler.post(loop)
    }

    private fun stopTurbo(r: BRt) {
        r.turbo?.let { handler.removeCallbacks(it) }
        r.turbo = null
        r.pressed = false
    }

    private fun startSwipe(b: Binding, r: BRt, g: Geo, token: Int) {
        if (r.swipe != null) return
        val x = g.sx(b.x)
        val y = g.sy(b.y)
        val ex = g.sx((b.x + b.dx).coerceIn(0f, 1f))
        val ey = g.sy((b.y + b.dy).coerceIn(0f, 1f))
        val frames = max(2, b.durMs / 16)
        var i = 0
        ptr(token, MotionEvent.ACTION_DOWN, x, y)
        val step = object : Runnable {
            override fun run() {
                i++
                val t = min(1f, i / frames.toFloat())
                val px = x + (ex - x) * t
                val py = y + (ey - y) * t
                if (i >= frames) {
                    ptr(token, MotionEvent.ACTION_MOVE, ex, ey)
                    ptr(token, MotionEvent.ACTION_UP, ex, ey)
                    r.swipe = null
                } else {
                    ptr(token, MotionEvent.ACTION_MOVE, px, py)
                    handler.postDelayed(this, 16)
                }
            }
        }
        r.swipe = step
        handler.postDelayed(step, 16)
    }

    // ---------- joystick ----------

    private fun joyDir(b: Binding, code: Int): Int = when (code) {
        b.up -> 0
        b.down -> 1
        b.left -> 2
        b.right -> 3
        else -> -1
    }

    private fun updateJoy(b: Binding) {
        val g = host.geo() ?: return
        val r = jrt(b)
        val vx = (if (r.dirs[3]) 1f else 0f) - (if (r.dirs[2]) 1f else 0f)
        val vy = (if (r.dirs[1]) 1f else 0f) - (if (r.dirs[0]) 1f else 0f)
        val token = tok(b)
        if (vx == 0f && vy == 0f) {
            if (r.down) {
                ptr(token, MotionEvent.ACTION_UP, r.lx, r.ly)
                r.down = false
            }
            return
        }
        val cx = g.sx(b.x)
        val cy = g.sy(b.y)
        val len = hypot(vx, vy)
        val radius = b.radius * g.minSide
        val nx = cx + vx / len * radius
        val ny = cy + vy / len * radius
        if (!r.down) {
            ptr(token, MotionEvent.ACTION_DOWN, cx, cy)
            r.down = true
        }
        ptr(token, MotionEvent.ACTION_MOVE, nx, ny)
        r.lx = nx
        r.ly = ny
    }

    // ---------- kamera ----------

    private fun activeCamera(): Binding? {
        val p = profile ?: return null
        if (aim) return p.bindings.firstOrNull { it.type == Bt.CAM }
        if (store.freeCursor || cursorHeld) return null
        val h = camHoldBinding
        if (h != null && held.contains(h.camHold)) return h
        return null
    }

    private val camTick = object : Runnable {
        override fun run() {
            camRt.ticking = false
            val cam = activeCamera()
            val g = host.geo()
            if (cam == null || g == null) {
                endCamera()
                return
            }
            val now = handler.now()
            val ax = camRt.ax
            val ay = camRt.ay
            if (ax == 0f && ay == 0f) {
                // Diam: lepas jari setelah jeda singkat agar game tidak mengira masih menggeser.
                if (camRt.down) {
                    if (now - camRt.lastMove > 160) endCamera() else scheduleCam()
                }
                return
            }
            camRt.ax = 0f
            camRt.ay = 0f
            camRt.lastMove = now
            val cx = g.sx(cam.x)
            val cy = g.sy(cam.y)
            val radius = cam.radius * g.minSide
            if (!camRt.down) {
                camRt.token = tok(cam) + 1
                camRt.x = cx
                camRt.y = cy
                camRt.down = true
                ptr(camRt.token, MotionEvent.ACTION_DOWN, cx, cy)
            }
            var nx = camRt.x + ax
            var ny = camRt.y + ay
            val ddx = nx - cx
            val ddy = ny - cy
            val dist = hypot(ddx, ddy)
            if (dist > radius && dist > 0f) {
                // Sampai tepi area: geser sampai batas, lalu angkat jari; sentuhan berikut mulai dari tengah.
                val k = radius / dist
                nx = cx + ddx * k
                ny = cy + ddy * k
                ptr(camRt.token, MotionEvent.ACTION_MOVE, nx, ny)
                ptr(camRt.token, MotionEvent.ACTION_UP, nx, ny)
                camRt.down = false
            } else {
                camRt.x = nx
                camRt.y = ny
                ptr(camRt.token, MotionEvent.ACTION_MOVE, nx, ny)
            }
            scheduleCam()
        }
    }

    private fun scheduleCam() {
        if (camRt.ticking) return
        camRt.ticking = true
        handler.postDelayed(camTick, 8)
    }

    private fun endCamera() {
        handler.removeCallbacks(camTick)
        camRt.ticking = false
        camRt.ax = 0f
        camRt.ay = 0f
        if (camRt.down) {
            ptr(camRt.token, MotionEvent.ACTION_UP, camRt.x, camRt.y)
            camRt.down = false
        }
    }

    // ---------- klik kursor ----------

    private fun cursorClick(isDown: Boolean) {
        if (!ensureCursor()) return
        val g = host.geo() ?: return
        if (isDown) {
            if (cursorDown) return
            cursorDown = true
            ptr(CURSOR_TOKEN, MotionEvent.ACTION_DOWN, g.ox + cursorX, g.oy + cursorY)
        } else if (cursorDown) {
            cursorDown = false
            ptr(CURSOR_TOKEN, MotionEvent.ACTION_UP, g.ox + cursorX, g.oy + cursorY)
        }
    }

    companion object {
        private const val CURSOR_TOKEN = 0x7ffffff0
    }
}
