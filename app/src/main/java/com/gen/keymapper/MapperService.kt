package com.gen.keymapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

data class AssignTarget(val kind: String, val id: String, val slot: String?)
private data class JoyState(var strokeId: Int = -1, var shizukuDown: Boolean = false, val slots: MutableSet<String> = mutableSetOf(), var tx: Float = 0f, var ty: Float = 0f)
private data class AimStroke(val id: Int, var x: Float, var y: Float)

/**
 * Layanan inti Gen Keymapper.
 * - Menangkap tombol keyboard/mouse/gamepad (onKeyEvent)
 * - Menyuntikkan sentuhan ke game (dispatchGesture)
 * - Mengelola overlay: tombol kontrol, Mode Mouse, editor mapping
 */
class MapperService : AccessibilityService() {

    companion object {
        var instance: MapperService? = null
    }

    lateinit var store: MappingStore
    lateinit var gestures: GestureKit

    var currentPkg: String = ""
    var editorOpen: Boolean = false
    var assignTarget: AssignTarget? = null
    var onAssign: ((Int) -> Unit)? = null

    private var playOverlay: PlayOverlay? = null
    private var editorOverlay: EditorOverlay? = null
    private val joyStates = HashMap<String, JoyState>()
    private val pressedKeys = HashSet<Int>()
    private val aimStrokes = HashMap<Int, AimStroke>()
    private val mainHandler = Handler(Looper.getMainLooper())
    var aimCursorX = 0f
    var aimCursorY = 0f
    private fun privileged(): Boolean = store.useShizuku && ShizukuBridge.isReady()
    private fun pointerToken(key: String): Int = key.hashCode() and 0x7fffffff


    // ---------- lifecycle ----------

    override fun onServiceConnected() {
        instance = this
        store = MappingStore(this)
        store.load()
        ShizukuBridge.initialize(this)
        gestures = GestureKit(this)
        try {
            serviceInfo = serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
        } catch (_: Exception) {
        }
        mainHandler.post { playOverlay?.hide(); playOverlay = PlayOverlay(this); playOverlay?.show() }
        mainHandler.postDelayed(joyWatchdog, 5000)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mainHandler.removeCallbacks(joyWatchdog)
        releaseAllInputs()
        playOverlay?.hide(); playOverlay = null
        editorOverlay?.hide(); editorOverlay = null
        if (instance == this) instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() { releaseAllInputs() }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        releaseAllInputs()
        mainHandler.post { playOverlay?.refreshPreviews() }
    }

    fun resetInputs() = releaseAllInputs()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { pkg ->
                if (pkg != currentPkg) {
                    releaseAllInputs()
                    currentPkg = pkg
                    playOverlay?.refreshPreviews()
                }
            }
        }
    }

    // ---------- key routing ----------

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        val kc = event.keyCode
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        // Jangan pernah ganggu tombol volume.
        if (kc == KeyEvent.KEYCODE_VOLUME_DOWN || kc == KeyEvent.KEYCODE_VOLUME_UP ||
            kc == KeyEvent.KEYCODE_VOLUME_MUTE
        ) return false

        // Mode assign di editor: tangkap 1 tombol.
        if (assignTarget != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                kc != KeyEvent.KEYCODE_POWER
            ) {
                val cb = onAssign
                assignTarget = null
                onAssign = null
                cb?.invoke(kc)
            }
            return true
        }
        if (editorOpen) return false
        if (currentPkg == packageName) return false
        if (!store.mappingEnabled) return false
        val prof = store.profileFor(currentPkg) ?: return false
        val down = event.action == KeyEvent.ACTION_DOWN

        // 1) Tombol joystick (analog)
        for (joy in prof.joys) {
            val slot = joy.usesKey(kc)
            if (slot != null) {
                if (down) {
                    if (event.repeatCount == 0) joyKeyDown(joy, slot)
                } else {
                    joyKeyUp(joy, slot)
                }
                return true
            }
        }

        // 2) Tombol biasa
        val km = prof.keys.find { it.keyCode == kc }
        if (km != null) {
            if (down) {
                if (event.repeatCount == 0 && pressedKeys.add(kc)) {
                    if (km.action == "hold" && privileged()) {
                        val dm = resources.displayMetrics
                        ShizukuBridge.pointer(pointerToken("key:${km.id}"), MotionEvent.ACTION_DOWN,
                            km.x * dm.widthPixels, km.y * dm.heightPixels)
                    } else fireKey(km)
                }
            } else {
                pressedKeys.remove(kc)
                if (km.action == "hold" && privileged()) {
                    val dm = resources.displayMetrics
                    ShizukuBridge.pointer(pointerToken("key:${km.id}"), MotionEvent.ACTION_UP,
                        km.x * dm.widthPixels, km.y * dm.heightPixels)
                }
            }
            return true
        }

        // 3) Mode Mouse: klik kiri = tap di posisi kursor bila belum dipetakan.
        val play = playOverlay
        if (play != null && play.aimMode && down && event.repeatCount == 0) {
            val dm = resources.displayMetrics
            when (kc) {
                KeyEvent.KEYCODE_BUTTON_1 -> {
                    injectTap(aimCursorX.coerceIn(0f, dm.widthPixels.toFloat()), aimCursorY.coerceIn(0f, dm.heightPixels.toFloat()), store.tapMs)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_2 -> {
                    injectHold(aimCursorX.coerceIn(0f, dm.widthPixels.toFloat()), aimCursorY.coerceIn(0f, dm.heightPixels.toFloat()), 400)
                    return true
                }
            }
        }
        return false
    }

    private fun injectTap(x: Float, y: Float, ms: Long) {
        if (!privileged() || !ShizukuBridge.tap(x, y, ms)) gestures.tap(x, y, ms)
    }

    private fun injectHold(x: Float, y: Float, ms: Long) {
        if (!privileged() || !ShizukuBridge.tap(x, y, ms)) gestures.hold(x, y, ms)
    }

    private fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        if (!privileged() || !ShizukuBridge.swipe(x1, y1, x2, y2, ms)) gestures.swipe(x1, y1, x2, y2, ms)
    }

    fun routeOverlayMouseButton(keyCode: Int, down: Boolean): Boolean {
        if (editorOpen || !store.mappingEnabled) return false
        val profile = store.profileFor(currentPkg) ?: return false
        if (profile.keys.none { it.keyCode == keyCode }) return false
        return onKeyEvent(KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode))
    }

    fun fireVirtualKey(keyCode: Int) {
        if (!store.mappingEnabled || editorOpen) return
        store.profileFor(currentPkg)?.keys?.find { it.keyCode == keyCode }?.let { fireKey(it) }
    }

    fun cameraMove(deltaX: Float, deltaY: Float) {
        if (!store.mappingEnabled) return
        val camera = store.profileFor(currentPkg)?.camera ?: return
        val dm = resources.displayMetrics
        val x = camera.x * dm.widthPixels
        val y = camera.y * dm.heightPixels
        val sx = (x + deltaX * store.mouseSensitivity).coerceIn(0f, dm.widthPixels.toFloat())
        val sy = (y + deltaY * store.mouseSensitivity).coerceIn(0f, dm.heightPixels.toFloat())
        injectSwipe(x, y, sx, sy, 50)
    }

    private fun fireKey(km: KeyMap) {
        val dm = resources.displayMetrics
        val x = km.x * dm.widthPixels
        val y = km.y * dm.heightPixels
        when (km.action) {
            "hold" -> injectHold(x, y, store.holdMs)
            "swipe" -> injectSwipe(x, y, x + km.dx * dm.widthPixels, y + km.dy * dm.heightPixels, km.dur)
            else -> injectTap(x, y, store.tapMs)
        }
    }

    // ---------- joystick ----------

    private fun joyVector(joy: JoyMap, slots: Set<String>): Pair<Float, Float> {
        var vx = 0f; var vy = 0f
        if ("up" in slots) vy -= 1f
        if ("down" in slots) vy += 1f
        if ("left" in slots) vx -= 1f
        if ("right" in slots) vx += 1f
        val len = kotlin.math.sqrt(vx * vx + vy * vy)
        if (len > 1f) { vx /= len; vy /= len }
        return vx to vy
    }

    private fun joyKeyDown(joy: JoyMap, slot: String) {
        val st = joyStates.getOrPut(joy.id) { JoyState() }
        st.slots.add(slot)
        updateJoy(joy, st)
    }

    private fun joyKeyUp(joy: JoyMap, slot: String) {
        val st = joyStates[joy.id] ?: return
        st.slots.remove(slot)
        updateJoy(joy, st)
    }

    private fun updateJoy(joy: JoyMap, st: JoyState) {
        val dm = resources.displayMetrics
        val density = dm.density
        val cx = joy.x * dm.widthPixels
        val cy = joy.y * dm.heightPixels
        val radius = (if (joy.radiusDp > 0) joy.radiusDp else store.joyRadiusDp) * density
        if (st.slots.isEmpty()) {
            if (st.shizukuDown) {
                ShizukuBridge.pointer(pointerToken("joy:${joy.id}"), MotionEvent.ACTION_UP, st.tx, st.ty)
                st.shizukuDown = false
            }
            if (st.strokeId != -1) {
                gestures.dragTo(st.strokeId, st.tx, st.ty, st.tx, st.ty, true)
                st.strokeId = -1
            }
            return
        }
        val (vx, vy) = joyVector(joy, st.slots)
        val tx = (cx + vx * radius).coerceIn(0f, dm.widthPixels.toFloat())
        val ty = (cy + vy * radius).coerceIn(0f, dm.heightPixels.toFloat())
        if (privileged()) {
            val id = pointerToken("joy:${joy.id}")
            if (!st.shizukuDown) {
                ShizukuBridge.pointer(id, MotionEvent.ACTION_DOWN, cx, cy)
                st.shizukuDown = true
            }
            ShizukuBridge.pointer(id, MotionEvent.ACTION_MOVE, tx, ty)
            st.tx = tx; st.ty = ty
            return
        }
        if (!gestures.supportsContinue) {
            // Fallback API <26: geser diskrit tiap ada perubahan arah.
            gestures.swipe(cx, cy, tx, ty, 300)
            st.tx = tx; st.ty = ty
            return
        }
        if (st.strokeId == -1) {
            val id = gestures.startDrag(cx, cy, tx, ty)
            if (id != null) { st.strokeId = id; st.tx = tx; st.ty = ty }
        } else {
            gestures.dragTo(st.strokeId, st.tx, st.ty, tx, ty, false)
            st.tx = tx; st.ty = ty
        }
    }

    /** Watchdog: jaga drag joystick tetap hidup selama tombol ditahan. */
    private val joyWatchdog = object : Runnable {
        override fun run() {
            try {
                for ((id, st) in joyStates) {
                    if (st.strokeId != -1 && st.slots.isNotEmpty()) {
                        gestures.dragTo(st.strokeId, st.tx, st.ty, st.tx, st.ty, false)
                    }
                }
            } catch (_: Exception) {
            }
            mainHandler.postDelayed(this, 5000)
        }
    }

    // ---------- Mode Mouse: teruskan sentuhan ke game ----------

    /** Dipanggil dari overlay Mode Mouse untuk setiap MotionEvent. */
    fun forwardPointer(e: MotionEvent) {
        try {
            if (privileged()) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        val i = e.actionIndex
                        ShizukuBridge.pointer(100000 + e.getPointerId(i), MotionEvent.ACTION_DOWN, e.getX(i), e.getY(i))
                    }
                    MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                        ShizukuBridge.pointer(100000 + e.getPointerId(i), MotionEvent.ACTION_MOVE, e.getX(i), e.getY(i))
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        val i = e.actionIndex
                        ShizukuBridge.pointer(100000 + e.getPointerId(i), MotionEvent.ACTION_UP, e.getX(i), e.getY(i))
                    }
                    MotionEvent.ACTION_CANCEL -> ShizukuBridge.releaseAll()
                }
                return
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = e.actionIndex
                    val pid = e.getPointerId(i)
                    val x = e.getX(i); val y = e.getY(i)
                    val id = gestures.startDrag(x, y, x, y)
                    if (id != null) {
                        aimStrokes[pid] = AimStroke(id, x, y)
                    } else {
                        // Fallback API <26: simpan titik awal, geser diskrit saat MOVE.
                        aimStrokes[pid] = AimStroke(-1, x, y)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until e.pointerCount) {
                        val pid = e.getPointerId(i)
                        val st = aimStrokes[pid] ?: continue
                        val x = e.getX(i); val y = e.getY(i)
                        if (st.id != -1) {
                            gestures.dragTo(st.id, st.x, st.y, x, y, false)
                        } else {
                            gestures.swipe(st.x, st.y, x, y, 120)
                        }
                        st.x = x; st.y = y
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    aimStrokes.values.forEach { gestures.dragTo(it.id, it.x, it.y, it.x, it.y, true) }
                    aimStrokes.clear()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val i = e.actionIndex
                    val pid = e.getPointerId(i)
                    val st = aimStrokes.remove(pid) ?: return
                    gestures.dragTo(st.id, st.x, st.y, st.x, st.y, true)
                }
            }
        } catch (_: Exception) {
        }
    }

    // ---------- overlay control ----------

    fun openEditor() {
        if (editorOpen) return
        val pkg = currentPkg.ifEmpty { "*" }
        val profile = store.ensureProfile(pkg, store.appLabel(pkg))
        editorOpen = true
        releaseAllInputs()
        playOverlay?.hide()
        editorOverlay = EditorOverlay(this, profile) { closeEditor() }
        editorOverlay?.show()
    }

    fun closeEditor() {
        editorOpen = false
        assignTarget = null
        onAssign = null
        editorOverlay?.hide()
        editorOverlay = null
        playOverlay?.show()
        playOverlay?.refreshPreviews()
    }

    private fun releaseAllInputs() {
        pressedKeys.clear()
        joyStates.clear()
        aimStrokes.clear()
        if (::gestures.isInitialized) gestures.releaseAll()
        ShizukuBridge.releaseAll()
    }

    fun setAimMode(on: Boolean) {
        playOverlay?.setAimMode(on)
    }

    fun refreshPlayOverlay() {
        mainHandler.post {
            val can = Settings.canDrawOverlays(this)
            if (can) {
                if (playOverlay == null) playOverlay = PlayOverlay(this)
                playOverlay?.show()
            } else {
                playOverlay?.hide()
            }
        }
    }

    fun isServiceReady(): Boolean = ::store.isInitialized
}
