package com.gen.keymapper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import kotlin.math.hypot

/**
 * Foreground service. Memegang:
 *  - jendela utama layar penuh (fokus, tidak menghalangi sentuhan) untuk keyboard + mouse,
 *  - bubble "GEN" yang bisa diseret dengan menu cepat,
 *  - editor drag-and-drop.
 * Hanya dimulai oleh tindakan pengguna dari aplikasi.
 */
class MapperService : Service(), CaptureLayout.Sink, InputEngine.Host, EditorUi.Host {

    enum class Mode { PLAY, EDIT, PAUSED }

    private lateinit var store: MappingStore
    private lateinit var engine: InputEngine
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var root: CaptureLayout? = null
    private var board: BoardView? = null
    private var editor: EditorUi? = null
    private var rootLp: WindowManager.LayoutParams? = null

    private var bubbleRoot: LinearLayout? = null
    private var bubbleCircle: TextView? = null
    private var menuPanel: LinearLayout? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var menuOpen = false
    private var savedBubbleX = 0
    private var savedBubbleY = 0

    var mode = Mode.PLAY
        private set
    private var recordCb: ((Int) -> Unit)? = null
    private var editSnapshot: String? = null
    private var currentPkg = ""

    private fun dp(v: Int) = Ui.dp(this, v)

    // ---------- siklus hidup ----------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = MappingStore(this)
        store.load()
        engine = InputEngine(store, this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ShizukuBridge.initialize(this)
        notifyState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startFg()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izin \"Tampil di atas aplikasi lain\" belum diberikan", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        ShizukuBridge.initialize(this)
        ShizukuBridge.connect()

        val pkg = intent?.getStringExtra(EXTRA_PKG)
        val wantEdit = intent?.getBooleanExtra(EXTRA_EDIT, false) == true
        if (pkg != null && pkg != currentPkg && mode == Mode.EDIT) finishEdit(true)
        if (mode != Mode.EDIT) {
            // Aktivitas dan service memakai MappingStore terpisah: muat ulang dari disk.
            store.load()
            val target = pkg ?: currentPkg
            if (target.isNotEmpty()) loadProfile(target)
        }
        ensureWindows()
        if (wantEdit) enterEdit() else if (mode != Mode.EDIT) enterPlay()
        refreshNotification()
        handler.removeCallbacks(statusTick)
        handler.postDelayed(statusTick, 2000)
        notifyState()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            engine.releaseAll()
        } catch (_: Throwable) {
        }
        removeWindows()
        instance = null
        notifyState()
        super.onDestroy()
    }

    private fun notifyState() {
        val cb = onStateChanged
        if (cb != null) Handler(Looper.getMainLooper()).post { cb() }
    }

    private fun loadProfile(pkg: String) {
        val p = store.ensureProfile(pkg, store.appLabel(pkg))
        currentPkg = pkg
        store.lastPkg = pkg
        engine.setProfile(p)
        board?.profile = p
    }

    // ---------- notifikasi ----------

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MapperService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val name = store.profileFor(currentPkg)?.name ?: "GenKeymapper"
        val state = when (mode) {
            Mode.PLAY -> "Aktif — tekan ${KeyNames.label(store.pauseKey)} untuk jeda"
            Mode.EDIT -> "Mode edit"
            Mode.PAUSED -> "Dijeda — ketuk bubble GEN untuk lanjut"
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gen)
            .setContentTitle("GenKeymapper · $name")
            .setContentText(state)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_gen), "Berhenti", stop).build())
            .build()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "GenKeymapper aktif", NotificationManager.IMPORTANCE_LOW)
        )
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, buildNotification())
    }

    // ---------- jendela ----------

    private fun flagsFor(m: Mode): Int {
        val base = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return when (m) {
            Mode.PLAY -> base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            Mode.EDIT -> base or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            Mode.PAUSED -> base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
    }

    private fun applyWindowFlags() {
        val r = root ?: return
        val lp = rootLp ?: return
        lp.flags = flagsFor(mode)
        // Android 12+: sentuhan hanya tembus jendela overlay jika opasitasnya <= 0.8.
        lp.alpha = if (mode == Mode.EDIT) 0.97f else 0.8f
        try {
            wm.updateViewLayout(r, lp)
        } catch (_: Throwable) {
        }
    }

    private fun ensureWindows() {
        if (root != null) return
        val b = BoardView(this, store, engine)
        b.profile = store.profileFor(currentPkg)
        b.onChanged = { onDirty() }
        val r = CaptureLayout(this)
        r.sink = this
        r.addView(b, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val ed = EditorUi(this, b, this)
        b.onSelect = { sel -> ed.select(sel) }
        r.addView(ed.toolbar)
        r.addView(ed.panel)
        ed.toolbar.visibility = View.GONE
        ed.panel.visibility = View.GONE

        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flagsFor(Mode.PLAY), PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.alpha = 0.8f
        lp.title = "GenKeymapper"
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        board = b
        editor = ed
        root = r
        rootLp = lp
        wm.addView(r, lp)
        buildBubble()
    }

    private fun removeWindows() {
        try {
            root?.let { wm.removeViewImmediate(it) }
        } catch (_: Throwable) {
        }
        try {
            bubbleRoot?.let { wm.removeViewImmediate(it) }
        } catch (_: Throwable) {
        }
        root = null
        board = null
        editor = null
        bubbleRoot = null
    }

    // ---------- mode ----------

    private fun requestCapture() {
        val r = root ?: return
        r.post {
            if (mode == Mode.PLAY) {
                r.requestFocus()
                if (r.hasWindowFocus()) r.requestPointerCapture()
            }
        }
    }

    private fun enterPlay() {
        mode = Mode.PLAY
        recordCb = null
        board?.editing = false
        board?.paused = false
        board?.banner = null
        editor?.toolbar?.visibility = View.GONE
        editor?.panel?.visibility = View.GONE
        collapseMenu()
        applyWindowFlags()
        requestCapture()
        refreshNotification()
        rebuildMenu()
    }

    private fun enterPause() {
        mode = Mode.PAUSED
        recordCb = null
        engine.releaseAll()
        root?.releasePointerCapture()
        board?.paused = true
        applyWindowFlags()
        refreshNotification()
        rebuildMenu()
    }

    private fun togglePause() {
        if (mode == Mode.PLAY) enterPause() else if (mode == Mode.PAUSED) enterPlay()
    }

    private fun enterEdit() {
        if (mode == Mode.EDIT) return
        val p = store.profileFor(currentPkg)
        if (p == null) {
            Toast.makeText(this, "Pilih game dulu di GenKeymapper", Toast.LENGTH_SHORT).show()
            return
        }
        engine.releaseAll()
        root?.releasePointerCapture()
        editSnapshot = p.toJson().toString()
        mode = Mode.EDIT
        board?.paused = false
        board?.editing = true
        editor?.attach(p)
        editor?.toolbar?.visibility = View.VISIBLE
        collapseMenu()
        applyWindowFlags()
        editBanner()
        refreshNotification()
        rebuildMenu()
        root?.requestFocus()
    }

    private fun editBanner() {
        if (mode == Mode.EDIT && recordCb == null) board?.banner = "Seret untuk memindah · ketuk untuk mengatur"
    }

    // EditorUi.Host
    override fun finishEdit(save: Boolean) {
        if (mode != Mode.EDIT) return
        cancelRecord()
        if (!save) {
            val snap = editSnapshot
            if (snap != null) {
                try {
                    val restored = Profile.fromJson(JSONObject(snap))
                    store.profiles[restored.pkg] = restored
                    engine.setProfile(restored)
                    board?.profile = restored
                } catch (_: Throwable) {
                }
            }
        } else {
            store.save()
        }
        editSnapshot = null
        board?.banner = null
        editor?.select(null)
        enterPlay()
        // Pastikan engine memakai objek profil terkini.
        store.profileFor(currentPkg)?.let { engine.setProfile(it) }
    }

    override fun onDirty() {
        board?.invalidate()
    }

    override fun rootHeight(): Int = root?.height ?: 0

    // ---------- perekaman tombol (editor) ----------

    override fun record(prompt: String, cb: (Int) -> Unit) {
        recordCb = cb
        board?.banner = "$prompt — tekan tombol / klik mouse · Esc batal"
        root?.requestFocus()
        root?.requestPointerCapture()
    }

    private fun finishRecord(code: Int) {
        val cb = recordCb ?: return
        recordCb = null
        root?.releasePointerCapture()
        editBanner()
        if (board?.banner?.startsWith("Seret") != true && mode == Mode.EDIT) board?.banner = null
        cb(code)
        board?.invalidate()
    }

    private fun cancelRecord() {
        if (recordCb == null) return
        recordCb = null
        root?.releasePointerCapture()
        editBanner()
    }

    // ---------- CaptureLayout.Sink ----------

    override fun onKey(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        val kc = e.keyCode
        if (recordCb != null) {
            if (KeyNames.isSystemKey(kc)) return false
            if (down && e.repeatCount == 0) {
                if (kc == KeyEvent.KEYCODE_ESCAPE || kc == KeyEvent.KEYCODE_BACK) cancelRecord() else finishRecord(kc)
            }
            return true
        }
        return when (mode) {
            Mode.EDIT -> kc == KeyEvent.KEYCODE_BACK
            Mode.PAUSED -> false
            Mode.PLAY -> {
                if (kc == KeyEvent.KEYCODE_BACK) {
                    if (down && e.repeatCount == 0) handleBack()
                    true
                } else if (kc == store.pauseKey) {
                    if (down && e.repeatCount == 0) togglePause()
                    true
                } else {
                    engine.onKey(e)
                }
            }
        }
    }

    override fun onMouseButton(code: Int, down: Boolean): Boolean {
        if (recordCb != null) {
            if (down && code != KeyNames.NONE) finishRecord(code)
            return true
        }
        return if (mode == Mode.PLAY) engine.onMouseButton(code, down) else false
    }

    override fun onMouseMove(dx: Float, dy: Float) {
        if (mode == Mode.PLAY && recordCb == null) engine.onMouseMove(dx, dy)
    }

    override fun onWheel(v: Float) {
        if (v == 0f) return
        if (recordCb != null) {
            finishRecord(if (v > 0f) KeyNames.WHEEL_UP else KeyNames.WHEEL_DOWN)
            return
        }
        if (mode == Mode.PLAY) engine.onWheel(v)
    }

    override fun onFocus(has: Boolean) {
        if (mode != Mode.PLAY) return
        if (has) requestCapture() else engine.releaseAll()
    }

    override fun onCapture(has: Boolean) {
        if (!has && mode == Mode.PLAY && root?.hasWindowFocus() == true) {
            handler.postDelayed({ requestCapture() }, 300)
        }
    }

    /**
     * Tombol Kembali sistem ikut tertangkap karena overlay fokus. Lepas fokus sesaat
     * lalu kirim BACK lewat Shizuku agar game menerimanya.
     */
    private fun handleBack() {
        engine.releaseAll()
        val r = root ?: return
        val lp = rootLp ?: return
        r.releasePointerCapture()
        lp.flags = flagsFor(Mode.PAUSED)
        try {
            wm.updateViewLayout(r, lp)
        } catch (_: Throwable) {
        }
        handler.postDelayed({ ShizukuBridge.key(KeyEvent.KEYCODE_BACK) }, 90)
        handler.postDelayed({
            if (mode == Mode.PLAY) {
                applyWindowFlags()
                requestCapture()
            }
        }, 450)
    }

    // ---------- InputEngine.Host ----------

    override fun geo(): Geo? = board?.geo()

    override fun onAimChanged(aim: Boolean) {
        board?.postInvalidateOnAnimation()
        if (menuOpen) rebuildMenu()
    }

    override fun onVisualChanged() {
        board?.postInvalidateOnAnimation()
    }

    // ---------- bubble ----------

    private var dragX = 0f
    private var dragY = 0f
    private var startX = 0
    private var startY = 0
    private var dragMoved = false

    private fun buildBubble() {
        val circle = TextView(this)
        circle.text = "GEN"
        circle.gravity = Gravity.CENTER
        circle.textSize = 11f
        circle.setTextColor(0xFF06281A.toInt())
        circle.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Ui.ACCENT)
        bg.setStroke(dp(2), 0xFF0D1015.toInt())
        circle.background = bg
        circle.layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))

        val menu = LinearLayout(this)
        menu.orientation = LinearLayout.VERTICAL
        menu.background = Ui.round(this, 0xF2161B22.toInt(), 16, Ui.STROKE)
        menu.setPadding(dp(8), dp(8), dp(8), dp(8))
        menu.visibility = View.GONE
        menu.layoutParams = LinearLayout.LayoutParams(dp(210), ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.topMargin = dp(6) }

        val rootView = LinearLayout(this)
        rootView.orientation = LinearLayout.VERTICAL
        rootView.addView(circle)
        rootView.addView(menu)

        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = store.bubbleX
        lp.y = store.bubbleY
        lp.title = "GenKeymapper Bubble"

        circle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragX = e.rawX
                    dragY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - dragX
                    val dy = e.rawY - dragY
                    if (!dragMoved && hypot(dx, dy) > dp(8)) {
                        dragMoved = true
                        if (menuOpen) collapseMenu()
                    }
                    if (dragMoved) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        try {
                            wm.updateViewLayout(rootView, lp)
                        } catch (_: Throwable) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragMoved) {
                        store.bubbleX = lp.x
                        store.bubbleY = lp.y
                    } else toggleMenu()
                    true
                }
                else -> false
            }
        }
        rootView.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE && menuOpen) collapseMenu()
            false
        }

        bubbleRoot = rootView
        bubbleCircle = circle
        menuPanel = menu
        bubbleLp = lp
        wm.addView(rootView, lp)
        rebuildMenu()
    }

    private fun toggleMenu() {
        if (menuOpen) collapseMenu() else expandMenu()
    }

    private fun expandMenu() {
        val lp = bubbleLp ?: return
        val rv = bubbleRoot ?: return
        rebuildMenu()
        menuPanel?.visibility = View.VISIBLE
        menuOpen = true
        savedBubbleX = lp.x
        savedBubbleY = lp.y
        val g = board?.geo()
        val sw = g?.w ?: resources.displayMetrics.widthPixels
        val sh = g?.h ?: resources.displayMetrics.heightPixels
        lp.x = lp.x.coerceAtMost((sw - dp(220)).coerceAtLeast(0))
        lp.y = lp.y.coerceAtMost((sh - dp(330)).coerceAtLeast(0))
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        try {
            wm.updateViewLayout(rv, lp)
        } catch (_: Throwable) {
        }
    }

    private fun collapseMenu() {
        val lp = bubbleLp ?: return
        val rv = bubbleRoot ?: return
        if (!menuOpen) return
        menuPanel?.visibility = View.GONE
        menuOpen = false
        lp.x = savedBubbleX
        lp.y = savedBubbleY
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        try {
            wm.updateViewLayout(rv, lp)
        } catch (_: Throwable) {
        }
    }

    private fun menuItem(label: String, danger: Boolean = false, onClick: () -> Unit): TextView {
        val t = Ui.text(this, label, 13.5f, if (danger) Ui.DANGER else Ui.TEXT, true)
        t.setPadding(dp(12), dp(10), dp(12), dp(10))
        t.background = Ui.ripple(this, Ui.round(this, Ui.CARD2, 10))
        t.isClickable = true
        t.setOnClickListener {
            collapseMenu()
            onClick()
        }
        t.layoutParams = Ui.lp().also { it.topMargin = dp(4) }
        return t
    }

    private fun rebuildMenu() {
        val m = menuPanel ?: return
        m.removeAllViews()
        val name = store.profileFor(currentPkg)?.name ?: "—"
        val head = Ui.text(this, name, 12f, Ui.SUB)
        head.setPadding(dp(6), dp(2), dp(6), dp(2))
        m.addView(head)
        when (mode) {
            Mode.EDIT -> {
                m.addView(menuItem("✔ Simpan & selesai edit") { finishEdit(true) })
                m.addView(menuItem("✖ Batalkan perubahan") { finishEdit(false) })
            }
            Mode.PAUSED -> {
                m.addView(menuItem("▶ Lanjutkan mapping") { togglePause() })
                m.addView(menuItem("✏ Edit mapping") { enterEdit() })
            }
            Mode.PLAY -> {
                m.addView(menuItem("⏸ Jeda (pakai input asli)") { togglePause() })
                m.addView(
                    menuItem(if (engine.aim) "🎯 Mode: Bidik → ke Kursor" else "🖱 Mode: Kursor → ke Bidik") {
                        engine.toggleAim()
                    }
                )
                m.addView(menuItem("✏ Edit mapping") { enterEdit() })
                m.addView(
                    menuItem(if (store.showHints) "👁 Sembunyikan petunjuk" else "👁 Tampilkan petunjuk") {
                        store.showHints = !store.showHints
                        board?.invalidate()
                    }
                )
            }
        }
        m.addView(menuItem("⚙ Buka GenKeymapper") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        m.addView(menuItem("⏹ Berhenti", danger = true) { stopSelf() })
    }

    // ---------- pemeriksaan berkala ----------

    private val statusTick = object : Runnable {
        override fun run() {
            val b = board
            if (b != null && recordCb == null) {
                if (!ShizukuBridge.isReady()) {
                    ShizukuBridge.connect()
                    if (mode == Mode.PLAY) b.banner = "Shizuku belum terhubung — buka GenKeymapper"
                } else if (b.banner?.startsWith("Shizuku") == true) {
                    b.banner = null
                }
            }
            handler.postDelayed(this, 2000)
        }
    }

    companion object {
        const val ACTION_STOP = "com.gen.keymapper.STOP"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_EDIT = "edit"
        private const val CHANNEL_ID = "genkey_service"
        private const val NOTIF_ID = 7

        @Volatile
        var instance: MapperService? = null
            private set

        val running: Boolean get() = instance != null

        /** Dipanggil di main thread saat service mulai/berhenti. */
        @Volatile
        var onStateChanged: (() -> Unit)? = null

        fun currentName(): String? = instance?.let { it.store.profileFor(it.currentPkg)?.name }
    }
}
