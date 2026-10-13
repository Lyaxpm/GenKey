package com.gen.keymapper

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent

data class AssignTarget(val kind: String, val id: String, val slot: String?)
private data class JoyState(var shizukuDown: Boolean = false, val slots: MutableSet<String> = mutableSetOf(), var tx: Float = 0f, var ty: Float = 0f)

/**
 * Layanan inti Gen Keymapper.
 * - Menangkap tombol keyboard/mouse/gamepad (onKeyEvent)
 * - Menyuntikkan sentuhan melalui Shizuku (tanpa Accessibility)
 * - Mengelola overlay: tombol kontrol, Mode Mouse, editor mapping
 */
class MapperService : Service() {

    companion object {
        var instance: MapperService? = null
        const val ACTION_STOP = "com.gen.keymapper.STOP"
        const val ACTION_EDIT = "com.gen.keymapper.EDIT"
        const val EXTRA_PACKAGE = "package"
    }

    lateinit var store: MappingStore

    var currentPkg: String = ""
    var editorOpen: Boolean = false
    var assignTarget: AssignTarget? = null
    var onAssign: ((Int) -> Unit)? = null

    private var playOverlay: PlayOverlay? = null
    private var editorOverlay: EditorOverlay? = null
    private val joyStates = HashMap<String, JoyState>()
    private val pressedKeys = HashSet<Int>()
    private val mainHandler = Handler(Looper.getMainLooper())
    var aimCursorX = 0f
    var aimCursorY = 0f
    private fun privileged(): Boolean = ShizukuBridge.isReady()
    private fun pointerToken(key: String): Int = key.hashCode() and 0x7fffffff


    // ---------- lifecycle ----------

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = MappingStore(this).also { it.load() }
        currentPkg = store.activePackage
        ShizukuBridge.initialize(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            "gen_mapper", "GenKeymapper • Mapping aktif", NotificationManager.IMPORTANCE_LOW))
        val launch = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "gen_mapper")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("GenKeymapper berjalan")
            .setContentText("Profil: ${store.appLabel(currentPkg)} · tekan untuk pengaturan")
            .setContentIntent(launch)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(82, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(82, notification)
        refreshPlayOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_EDIT -> openEditor()
            else -> {
                intent?.getStringExtra(EXTRA_PACKAGE)?.let { selectProfile(it) }
                refreshPlayOverlay()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseAllInputs()
        playOverlay?.hide(); playOverlay = null
        editorOverlay?.hide(); editorOverlay = null
        mainHandler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun selectProfile(pkg: String) {
        if (currentPkg == pkg) return
        releaseAllInputs()
        currentPkg = pkg
        store.activePackage = pkg
        playOverlay?.refreshPreviews()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        releaseAllInputs()
        mainHandler.post { playOverlay?.refreshPreviews() }
    }

    fun resetInputs() = releaseAllInputs()

    // ---------- key routing ----------

    fun handleKeyEvent(event: KeyEvent?): Boolean {
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
        if (privileged()) {
            playOverlay?.passthroughFor(ms + 90)
            ShizukuBridge.tap(x, y, ms)
        }
    }
    private fun injectHold(x: Float, y: Float, ms: Long) {
        if (privileged()) {
            playOverlay?.passthroughFor(ms + 90)
            ShizukuBridge.tap(x, y, ms)
        }
    }
    private fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        if (privileged()) {
            playOverlay?.passthroughFor(ms + 90)
            ShizukuBridge.swipe(x1, y1, x2, y2, ms)
        }
    }

    fun routeOverlayMouseButton(keyCode: Int, down: Boolean): Boolean {
        if (editorOpen || !store.mappingEnabled) return false
        val profile = store.profileFor(currentPkg) ?: return false
        if (profile.keys.none { it.keyCode == keyCode }) return false
        return handleKeyEvent(KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode))
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
        val id = pointerToken("joy:${joy.id}")
        if (st.slots.isEmpty()) {
            if (st.shizukuDown) {
                ShizukuBridge.pointer(id, MotionEvent.ACTION_UP, st.tx, st.ty)
                st.shizukuDown = false
            }
            return
        }
        if (!privileged()) return
        val (vx, vy) = joyVector(joy, st.slots)
        val tx = (cx + vx * radius).coerceIn(0f, dm.widthPixels.toFloat())
        val ty = (cy + vy * radius).coerceIn(0f, dm.heightPixels.toFloat())
        if (!st.shizukuDown) {
            ShizukuBridge.pointer(id, MotionEvent.ACTION_DOWN, cx, cy)
            st.shizukuDown = true
        }
        ShizukuBridge.pointer(id, MotionEvent.ACTION_MOVE, tx, ty)
        st.tx = tx; st.ty = ty
    }

    // ---------- Mode Mouse: teruskan sentuhan ke game ----------

    /** Pointer dari layer mouse. Backend Shizuku diwajibkan. */
    fun forwardPointer(e: MotionEvent) {
        if (!privileged()) return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                ShizukuBridge.pointer(100000 + e.getPointerId(i), MotionEvent.ACTION_DOWN,
                    e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                ShizukuBridge.pointer(100000 + e.getPointerId(i), MotionEvent.ACTION_MOVE,
                    e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                ShizukuBridge.pointer(100000 + e.getPointerId(i), MotionEvent.ACTION_UP,
                    e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> ShizukuBridge.releaseAll()
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
