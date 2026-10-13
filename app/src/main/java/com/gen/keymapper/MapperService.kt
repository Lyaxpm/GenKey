package com.gen.keymapper

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.*
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

data class AssignTarget(val kind: String, val id: String, val slot: String?)

/** User-started foreground session. All routing occurs on the main thread. */
class MapperService : Service(), InputManager.InputDeviceListener {
    companion object {
        var instance: MapperService? = null; private set
        const val ACTION_STOP = "com.gen.keymapper.STOP"
        const val ACTION_EDIT = "com.gen.keymapper.EDIT"
        const val EXTRA_PACKAGE = "package"
    }
    lateinit var store: MappingStore
    var currentPkg = "*"; private set
    var editorOpen = false; private set
    var assignTarget: AssignTarget? = null
    var onAssign: ((Int) -> Unit)? = null
    var inputStatus = "Menunggu fokus keyboard"; internal set
    private var play: PlayOverlay? = null
    private var editor: EditorOverlay? = null
    private val main = Handler(Looper.getMainLooper())
    private val timers = Handler(Looper.getMainLooper())
    private val pressed = mutableSetOf<Int>()
    private val toggled = mutableSetOf<String>()
    private val held = mutableSetOf<String>()
    private val tokens = mutableMapOf<String, Int>()
    private val repeaters = mutableMapOf<String, Runnable>()
    private var nextToken = 1
    private var cameraDown = false
    private var cameraX = 0f
    private var cameraY = 0f
    private var sessionReady = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { pause() }
    }
    private fun token(id: String) = tokens.getOrPut(id) { nextToken++ }
    private fun profile() = store.profileFor(currentPkg)
    private fun ready() = store.mappingEnabled && !editorOpen && ShizukuBridge.isReady()
    private fun coords(x: Float, y: Float): Pair<Float, Float> {
        val (w, h) = ScreenSpace.size(this)
        return (x * w).coerceIn(0f, (w - 1).toFloat()) to (y * h).coerceIn(0f, (h - 1).toFloat())
    }
    override fun onCreate() {
        super.onCreate(); instance = this
        store = MappingStore(this).also { it.load() }; currentPkg = store.activePackage
        ShizukuBridge.initialize(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("gen_mapper", "Sesi GenKey", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, MapperService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "gen_mapper").setSmallIcon(R.drawable.ic_genkey)
            .setContentTitle("GenKey · sesi mapping")
            .setContentText("Buka GEN untuk editor · F8 mouse · F9 jeda")
            .setContentIntent(open).addAction(Notification.Action.Builder(null, "Hentikan", stop).build())
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(82, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(82, notification)
        getSystemService(InputManager::class.java).registerInputDeviceListener(this, main)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        main.post(object : Runnable {
            override fun run() {
                val connected = ShizukuBridge.isReady()
                if (sessionReady && !connected) { resetInputs(); play?.setAimMode(false); inputStatus = "Shizuku terputus" }
                sessionReady = connected
                if (!Settings.canDrawOverlays(this@MapperService)) { stopSelf(); return }
                ShizukuBridge.heartbeat()
                main.postDelayed(this, 800)
            }
        })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        intent?.getStringExtra(EXTRA_PACKAGE)?.let { selectProfile(it) }
        if (intent?.action == ACTION_EDIT) openEditor() else refreshPlayOverlay()
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        resetInputs(); play?.hide(); editor?.hide(); main.removeCallbacksAndMessages(null)
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(this)
        unregisterReceiver(screenReceiver)
        if (instance === this) instance = null
        super.onDestroy()
    }
    override fun onInputDeviceAdded(deviceId: Int) = Unit
    override fun onInputDeviceChanged(deviceId: Int) { resetInputs() }
    override fun onInputDeviceRemoved(deviceId: Int) { resetInputs(); play?.setAimMode(false) }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetInputs()
        if (editorOpen) { editor?.hide(); editor = null; editorOpen = false; openEditor() }
        else { play?.hide(); play?.show() }
    }
    fun selectProfile(pkg: String) {
        resetInputs()
        if (editorOpen) closeEditor()
        store.load(); currentPkg = pkg; store.activePackage = pkg
        play?.refreshPreviews()
    }
    fun reloadProfiles() { resetInputs(); if (editorOpen) closeEditor(); store.load(); play?.refreshPreviews() }
    fun suspendForDashboard() { resetInputs(); play?.hide() }
    fun pause() {
        store.mappingEnabled = false; resetInputs(); play?.setAimMode(false); play?.refreshPreviews()
    }
    fun togglePause() {
        if (store.mappingEnabled) pause() else { store.mappingEnabled = true; play?.refreshPreviews() }
    }
    fun handleKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val code = event.keyCode
        if (code in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME)) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        if (assignTarget != null) {
            if (down && event.repeatCount == 0) {
                if (code == KeyEvent.KEYCODE_F8 || code == KeyEvent.KEYCODE_F9) {
                    android.widget.Toast.makeText(this, "F8 dan F9 dipakai untuk kontrol sesi. Pilih tombol lain.", android.widget.Toast.LENGTH_SHORT).show()
                    return true
                }
                val callback = onAssign; assignTarget = null; onAssign = null
                if (code != KeyEvent.KEYCODE_ESCAPE) callback?.invoke(code)
            }
            return true
        }
        if (editorOpen) return false
        if (code == KeyEvent.KEYCODE_F8 || code == KeyEvent.KEYCODE_F9 || (code == KeyEvent.KEYCODE_ESCAPE && play?.aimMode == true)) {
            if (down && event.repeatCount == 0) when (code) {
                KeyEvent.KEYCODE_F8 -> play?.setAimMode(play?.aimMode != true)
                KeyEvent.KEYCODE_F9 -> togglePause()
                else -> play?.setAimMode(false)
            }
            return true
        }
        return routeKey(code, down, event.repeatCount)
    }
    fun routeKey(code: Int, down: Boolean, repeat: Int = 0): Boolean {
        if (!ready()) return false
        val p = profile() ?: return false
        val keys = p.keys.filter { it.keyCode == code }
        val joys = p.joys.filter { it.usesKey(code) != null }
        if (keys.isEmpty() && joys.isEmpty()) return false
        if (down && (repeat > 0 || !pressed.add(code))) return true
        if (!down) pressed.remove(code)
        keys.forEach { map ->
            when (map.action) {
                "hold" -> hold("key:${map.id}", map.x, map.y, down)
                "toggle" -> if (down) {
                    val enable = toggled.add(map.id)
                    if (!enable) toggled.remove(map.id)
                    hold("key:${map.id}", map.x, map.y, enable)
                }
                "repeat" -> if (down) {
                    val job = object : Runnable {
                        override fun run() {
                            if (map.keyCode !in pressed || !ready()) return
                            fire(map); timers.postDelayed(this, map.intervalMs.coerceAtLeast(store.tapMs + 20))
                        }
                    }
                    repeaters[map.id] = job; timers.post(job)
                } else repeaters.remove(map.id)?.let { timers.removeCallbacks(it) }
                "double" -> if (down) { fire(map); timers.postDelayed({ if (ready()) fire(map) }, store.tapMs + 65) }
                else -> if (down) fire(map)
            }
        }
        joys.forEach { joy ->
            val x = (if (joy.right in pressed) 1f else 0f) - (if (joy.left in pressed) 1f else 0f)
            val y = (if (joy.down in pressed) 1f else 0f) - (if (joy.up in pressed) 1f else 0f)
            moveJoy(joy, x, y)
        }
        return true
    }
    private fun hold(id: String, x: Float, y: Float, down: Boolean) {
        val (px, py) = coords(x, y)
        if (down && held.add(id)) ShizukuBridge.pointer(token(id), MotionEvent.ACTION_DOWN, px, py)
        if (!down && held.remove(id)) ShizukuBridge.pointer(token(id), MotionEvent.ACTION_UP, px, py)
    }
    private fun fire(map: KeyMap) {
        val (x, y) = coords(map.x, map.y)
        if (map.action == "swipe") {
            val (ex, ey) = coords(map.x + map.dx, map.y + map.dy)
            ShizukuBridge.swipe(x, y, ex, ey, map.dur)
        } else ShizukuBridge.tap(x, y, store.tapMs)
    }
    fun wheel(up: Boolean) {
        val code = if (up) KeyNames.MOUSE_SCROLL_UP else KeyNames.MOUSE_SCROLL_DOWN
        routeKey(code, true); routeKey(code, false)
    }
    private fun moveJoy(joy: JoyMap, x: Float, y: Float) {
        val (vx, vy) = InputMath.direction(x, y)
        val id = "joy:${joy.id}"
        val (cx, cy) = coords(joy.x, joy.y)
        if (vx == 0f && vy == 0f) { hold(id, joy.x, joy.y, false); return }
        hold(id, joy.x, joy.y, true)
        val (w, h) = ScreenSpace.size(this)
        val r = (if (joy.radiusDp > 0) joy.radiusDp else store.joyRadiusDp) * resources.displayMetrics.density
        ShizukuBridge.pointer(token(id), MotionEvent.ACTION_MOVE,
            (cx + vx * r).coerceIn(0f, w - 1f), (cy + vy * r).coerceIn(0f, h - 1f))
    }
    fun gamepad(event: MotionEvent): Boolean {
        if (!ready() || !event.isFromSource(InputDevice.SOURCE_JOYSTICK) || !store.gamepadEnabled) return false
        val joy = profile()?.joys?.firstOrNull() ?: return false
        val (x, y) = InputMath.direction(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), store.deadZone)
        moveJoy(joy, x, y)
        return true
    }
    fun cameraMove(dx: Float, dy: Float) {
        if (!ready() || !dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return
        val camera = profile()?.camera ?: return
        val (cx, cy) = coords(camera.x, camera.y)
        val (w, h) = ScreenSpace.size(this)
        if (!cameraDown) {
            cameraX = cx; cameraY = cy; cameraDown = true
            ShizukuBridge.pointer(token("camera"), MotionEvent.ACTION_DOWN, cx, cy)
        }
        cameraX = (cameraX + dx * store.mouseSensitivity).coerceIn(1f, w - 2f)
        cameraY = (cameraY + dy * store.mouseSensitivity * if (store.invertY) -1f else 1f).coerceIn(1f, h - 2f)
        ShizukuBridge.pointer(token("camera"), MotionEvent.ACTION_MOVE, cameraX, cameraY)
        // Recenter the touch when nearing the edge, while capture keeps relative mouse motion.
        if (cameraX <= 2f || cameraX >= w - 3 || cameraY <= 2f || cameraY >= h - 3 ||
            kotlin.math.abs(cameraX - cx) > w * .28f || kotlin.math.abs(cameraY - cy) > h * .28f) endCamera()
        timers.removeCallbacks(cameraIdle); timers.postDelayed(cameraIdle, 100)
    }
    private val cameraIdle = Runnable { endCamera() }
    private fun endCamera() {
        if (cameraDown) ShizukuBridge.pointer(token("camera"), MotionEvent.ACTION_UP, cameraX, cameraY)
        cameraDown = false
    }
    fun resetInputs() {
        timers.removeCallbacksAndMessages(null); repeaters.clear(); pressed.clear(); held.clear(); toggled.clear()
        cameraDown = false; ShizukuBridge.releaseAll()
    }
    fun openEditor() {
        if (editorOpen || !Settings.canDrawOverlays(this)) return
        val p = store.ensureProfile(currentPkg, store.appLabel(currentPkg))
        resetInputs(); play?.hide(); editorOpen = true
        editor = EditorOverlay(this, p) { closeEditor() }
        editor?.show()
    }
    fun closeEditor() {
        editorOpen = false; assignTarget = null; onAssign = null
        editor?.hide(); editor = null; refreshPlayOverlay()
    }
    fun refreshPlayOverlay() {
        if (editorOpen) return
        if (!Settings.canDrawOverlays(this)) { play?.hide(); return }
        if (play == null) play = PlayOverlay(this)
        play?.show(); play?.refreshPreviews()
    }
    fun isServiceReady() = ::store.isInitialized
}
