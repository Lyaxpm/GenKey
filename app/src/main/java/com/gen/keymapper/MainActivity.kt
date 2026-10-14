package com.gen.keymapper

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var store: MappingStore
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var deviceListener: InputManager.InputDeviceListener? = null

    private fun dp(v: Int) = Ui.dp(this, v)

    // ---------- siklus hidup ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MappingStore(this)
        store.load()
        Backend.initialize(this)

        window.setBackgroundDrawable(ColorDrawable(Ui.BG))
        window.statusBarColor = 0
        window.navigationBarColor = 0

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(8), dp(16), dp(32))
        scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val rootView = FrameLayout(this)
        rootView.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rootView.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }
        setContentView(rootView)
        render()
    }

    override fun onResume() {
        super.onResume()
        store.load()
        Backend.listener = { render() }
        MapperService.onStateChanged = { render() }
        registerDevices()
        Backend.connect()
        render()
    }

    override fun onPause() {
        super.onPause()
        Backend.listener = null
        MapperService.onStateChanged = null
        unregisterDevices()
    }

    private fun registerDevices() {
        val im = getSystemService(InputManager::class.java) ?: return
        val l = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = render()
            override fun onInputDeviceRemoved(deviceId: Int) = render()
            override fun onInputDeviceChanged(deviceId: Int) = render()
        }
        deviceListener = l
        im.registerInputDeviceListener(l, handler)
    }

    private fun unregisterDevices() {
        val im = getSystemService(InputManager::class.java) ?: return
        deviceListener?.let { im.unregisterInputDeviceListener(it) }
        deviceListener = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    // ---------- gambar halaman ----------

    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        header()
        runningBanner()
        setupCard()
        devicesCard()
        gamesCard()
        settingsCard()
        backupCard()
        helpCard()
        val foot = Ui.sub(this, "Tanpa internet · tanpa iklan · tanpa VIP. Semua data tersimpan di perangkat ini.")
        foot.gravity = Gravity.CENTER
        foot.layoutParams = Ui.lp().also { it.topMargin = dp(20) }
        content.addView(foot)
    }

    private fun overlayOk() = Settings.canDrawOverlays(this)

    private fun header() {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = Ui.lp().also { it.topMargin = dp(8) }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        col.addView(Ui.text(this, "GenKeymapper", 26f, Ui.TEXT, true))
        col.addView(Ui.sub(this, "Keyboard & mouse untuk game Android · v3.0"))
        row.addView(col)
        val ready = Backend.isReady() && overlayOk()
        val pill = Ui.text(this, if (ready) "SIAP" else "PERLU SETUP", 11.5f, if (ready) Ui.ACCENT else Ui.WARN, true)
        pill.setPadding(dp(12), dp(6), dp(12), dp(6))
        pill.background = Ui.round(this, Ui.withAlpha(if (ready) Ui.ACCENT else Ui.WARN, 0.12f), 14, if (ready) Ui.ACCENT else Ui.WARN)
        row.addView(pill)
        content.addView(row)
    }

    private fun runningBanner() {
        if (!MapperService.running) return
        val c = Ui.card(this)
        c.background = Ui.round(this, Ui.withAlpha(Ui.ACCENT, 0.08f), 18, Ui.ACCENT)
        c.addView(Ui.text(this, "● Overlay aktif", 15f, Ui.ACCENT, true))
        c.addView(Ui.sub(this, MapperService.currentName() ?: "—"))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = Ui.lp().also { it.topMargin = dp(10) }
        val stop = Ui.button(this, "Hentikan", danger = true) {
            startService(Intent(this, MapperService::class.java).setAction(MapperService.ACTION_STOP))
            handler.postDelayed({ render() }, 400)
        }
        row.addView(stop)
        c.addView(row)
        content.addView(c)
    }

    // ---------- persiapan ----------

    private fun statusRow(parent: LinearLayout, color: Int, title: String, subtitle: String, btn: String?, action: (() -> Unit)?) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = Ui.lp().also { it.topMargin = dp(10) }
        row.addView(Ui.dot(this, color))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        col.addView(Ui.text(this, title, 14f, Ui.TEXT, true))
        col.addView(Ui.sub(this, subtitle))
        row.addView(col)
        if (btn != null && action != null) {
            val b = Ui.button(this, btn, filled = color != Ui.ACCENT) { action() }
            b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.leftMargin = dp(8) }
            row.addView(b)
        }
        parent.addView(row)
    }

    private fun showPairing() {
        val prefs = getSharedPreferences("backend", 0)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        box.addView(Ui.text(this, "Android 11+: aktifkan Opsi pengembang → Wireless debugging. " +
            "Gunakan layar terbagi agar dialog kode pairing tetap terbuka. Port pairing berbeda dari port koneksi di halaman utama.", 13f, Ui.SUB))
        fun field(hintText: String, value: String = "", numeric: Boolean = false): EditText {
            return EditText(this).apply {
                hint = hintText; setText(value); setTextColor(Ui.TEXT); setHintTextColor(Ui.SUB)
                inputType = if (numeric) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT
                isSingleLine = true
                box.addView(this)
            }
        }
        val host = field("IP pada Wireless debugging", prefs.getString("host", "127.0.0.1") ?: "127.0.0.1")
        val port = field("Port koneksi (halaman utama)", prefs.getInt("port", 0).takeIf { it > 0 }?.toString() ?: "", true)
        val pairingPort = field("Port pairing (dialog kode)", numeric = true)
        val code = field("Kode pairing 6 digit", numeric = true).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        box.addView(Ui.button(this, "Buka opsi pengembang") {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                .onFailure { Toast.makeText(this, "Buka Opsi pengembang melalui Pengaturan HP.", Toast.LENGTH_LONG).show() }
        })
        val status = Ui.text(this, "Sudah pairing? Cukup isi port koneksi terbaru dan pilih Hubungkan.", 12f, Ui.SUB)
        box.addView(status)
        val d = dialog().setTitle("Pairing mandiri")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Pair & hubungkan", null)
            .setNeutralButton("Hubungkan", null)
            .setNegativeButton("Tutup", null).create()
        d.setOnShowListener {
            fun start(pair: Boolean) {
                if (AdbBackend.busy) { status.text = "Koneksi masih berjalan. Tunggu hasilnya."; return }
                val cp = port.text.toString().toIntOrNull() ?: 0
                val pp = pairingPort.text.toString().toIntOrNull()
                val pin = code.text.toString()
                if (host.text.isBlank() || cp !in 1..65535 || (pair && (pp == null || pp !in 1..65535 || pin.length != 6))) {
                    status.text = "Periksa IP, port koneksi, port pairing, dan kode 6 digit."
                    return
                }
                AdbBackend.start(host.text.toString().trim(), pp, pin, cp, pair)
                d.dismiss()
            }
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { start(true) }
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { start(false) }
        }
        d.show()
    }

    private fun setupCard() {
        val c = Ui.card(this)
        c.addView(Ui.title(this, "Persiapan"))
        c.addView(Ui.text(this, "Pilih layanan input", 14f, Ui.SUB))
        c.addView(Ui.button(this, (if (Backend.mode == Backend.Mode.SHIZUKU) "✓ " else "") + "Shizuku") {
            Backend.select(Backend.Mode.SHIZUKU)
        })
        c.addView(Ui.button(this, (if (Backend.mode == Backend.Mode.PAIRING) "✓ " else "") + "Pairing mandiri · tanpa Shizuku") {
            Backend.select(Backend.Mode.PAIRING)
        })
        if (Backend.mode == Backend.Mode.PAIRING) {
            statusRow(c, if (Backend.isReady()) Ui.ACCENT else Ui.WARN,
                if (Backend.isReady()) "Pairing mandiri terhubung" else "Wireless debugging",
                AdbBackend.message, "Atur koneksi") { showPairing() }
        } else
        when (Backend.state()) {
            Backend.State.NOT_INSTALLED -> statusRow(
                c, Ui.DANGER, "Shizuku belum terpasang",
                "Dibutuhkan untuk menyuntikkan sentuhan tanpa root.", "Unduh"
            ) { openUrl("https://shizuku.rikka.app/download/") }
            Backend.State.NOT_RUNNING -> statusRow(
                c, Ui.DANGER, "Shizuku belum berjalan",
                "Buka Shizuku lalu mulai lewat Wireless debugging (Android 11+).", "Buka Shizuku"
            ) { launchPackage(Backend.SHIZUKU_PKG) }
            Backend.State.NO_PERMISSION -> statusRow(
                c, Ui.WARN, "Izin Shizuku diperlukan",
                "Beri GenKeymapper akses ke Shizuku.", "Beri izin"
            ) { Backend.requestPermission() }
            Backend.State.CONNECTING -> statusRow(
                c, Ui.WARN, "Menyambung ke layanan input…",
                "Maksimal 12 detik; jalur input sedang diperiksa.", "Coba lagi"
            ) { Backend.reconnect() }
            Backend.State.ERROR -> statusRow(
                c, Ui.DANGER, "Layanan input gagal", Backend.error, "Coba lagi"
            ) { Backend.reconnect() }
            Backend.State.READY -> statusRow(
                c, Ui.ACCENT, "Shizuku terhubung",
                "Layanan input siap. Gunakan Uji sentuhan untuk memastikan.", null, null
            )
        }
        if (overlayOk()) statusRow(c, Ui.ACCENT, "Tampil di atas aplikasi lain", "Diizinkan.", null, null)
        else statusRow(
            c, Ui.DANGER, "Izin overlay diperlukan",
            "Untuk menangkap keyboard/mouse dan menampilkan petunjuk.", "Izinkan"
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) statusRow(c, Ui.ACCENT, "Notifikasi", "Diizinkan.", null, null)
            else statusRow(c, Ui.WARN, "Notifikasi (opsional)", "Agar tombol Berhenti terlihat di bilah notifikasi.", "Izinkan") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 21)
            }
        }
        if (Backend.isReady()) {
            c.addView(Ui.divider(this))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(Ui.button(this, "Uji sentuhan", filled = true) { showInjectTest() })
            val diag = Ui.button(this, "Diagnosa") { showDiag() }
            diag.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.leftMargin = dp(8) }
            row.addView(diag)
            c.addView(row)
        }
        content.addView(c)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Tidak ada browser", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchPackage(pkg: String): Boolean {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) {
            Toast.makeText(this, "Aplikasi tidak ditemukan", Toast.LENGTH_SHORT).show()
            return false
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        return true
    }

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    private fun showInjectTest() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), dp(8))
        val info = Ui.text(this, "Tekan \"Jalankan uji\". GenKeymapper akan mengetuk kotak di bawah lewat layanan input yang dipilih.", 13f, Ui.SUB)
        val pad = TextView(this)
        pad.text = "Belum ada sentuhan"
        pad.setTextColor(Ui.TEXT)
        pad.gravity = Gravity.CENTER
        pad.textSize = 14f
        pad.background = Ui.round(this, Ui.CARD2, 14, Ui.STROKE)
        pad.layoutParams = Ui.lp(h = dp(110)).also { it.topMargin = dp(12) }
        var received = false
        pad.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                received = true
                pad.text = "✅ Sentuhan diterima (${e.x.toInt()}, ${e.y.toInt()})"
                pad.setTextColor(Ui.ACCENT)
            }
            true
        }
        box.addView(info)
        box.addView(pad)
        val dlg = dialog().setTitle("Uji injeksi sentuhan").setView(box)
            .setPositiveButton("Jalankan uji", null)
            .setNegativeButton("Tutup", null).create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            received = false
            pad.text = "Menguji…"
            pad.setTextColor(Ui.TEXT)
            val loc = IntArray(2)
            pad.getLocationOnScreen(loc)
            val x = loc[0] + pad.width / 2f
            val y = loc[1] + pad.height / 2f
            val token = 0x5eed
            Backend.pointer(token, MotionEvent.ACTION_DOWN, x, y)
            handler.postDelayed({ Backend.pointer(token, MotionEvent.ACTION_UP, x, y) }, 70)
            handler.postDelayed({
                if (!received) {
                    pad.text = "❌ Tidak diterima. Buka Diagnosa untuk melihat penyebabnya."
                    pad.setTextColor(Ui.DANGER)
                }
            }, 900)
        }
    }

    private fun showDiag() {
        Thread {
            val s = Backend.diagBlocking() ?: "Layanan input belum terhubung."
            runOnUiThread {
                dialog().setTitle("Diagnosa").setMessage(s.replace("; ", "\n")).setPositiveButton("OK", null).show()
            }
        }.start()
    }

    // ---------- perangkat input ----------

    private fun devicesCard() {
        val c = Ui.card(this)
        c.addView(Ui.title(this, "Perangkat input"))
        var n = 0
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            if (d.isVirtual) continue
            val s = d.sources
            val mouse = (s and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
            val kb = (s and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            val pad = (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            if (!mouse && !kb && !pad) continue
            n++
            val kinds = listOfNotNull(
                if (kb) "Keyboard" else null, if (mouse) "Mouse" else null, if (pad) "Gamepad" else null
            ).joinToString(" · ")
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.layoutParams = Ui.lp().also { it.topMargin = dp(8) }
            row.addView(Ui.dot(this, Ui.ACCENT))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.addView(Ui.text(this, d.name, 14f, Ui.TEXT, true))
            col.addView(Ui.sub(this, kinds))
            row.addView(col)
            c.addView(row)
        }
        if (n == 0) c.addView(Ui.sub(this, "Belum ada keyboard/mouse. Hubungkan lewat OTG atau Bluetooth."))
        content.addView(c)
    }

    // ---------- game ----------

    private fun gamesCard() {
        val c = Ui.card(this)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val t = Ui.title(this, "Game & profil")
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        head.addView(t)
        head.addView(Ui.button(this, "+ Tambah", filled = true) { showAddGame() })
        c.addView(head)
        val list = store.profiles.values.sortedBy { it.name.lowercase() }
        if (list.isEmpty()) {
            c.addView(Ui.sub(this, "Belum ada game. Ketuk + Tambah, pilih game, lalu terapkan preset atau edit sendiri."))
        }
        for (p in list) c.addView(gameRow(p))
        content.addView(c)
    }

    private fun gameIcon(pkg: String): Drawable? = try {
        packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) {
        null
    }

    private fun gameRow(p: Profile): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = Ui.round(this, Ui.CARD2, 14, Ui.STROKE)
        box.setPadding(dp(12), dp(12), dp(12), dp(12))
        box.layoutParams = Ui.lp().also { it.topMargin = dp(10) }

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val icon = ImageView(this)
        icon.layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).also { it.rightMargin = dp(12) }
        gameIcon(p.pkg)?.let { icon.setImageDrawable(it) }
        top.addView(icon)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        col.addView(Ui.text(this, p.name, 15f, Ui.TEXT, true))
        val modeTxt = if (p.startAim) "mulai di Mode Bidik" else "mulai di Mode Kursor"
        col.addView(Ui.sub(this, "${p.count()} kontrol · $modeTxt"))
        top.addView(col)
        box.addView(top)

        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.layoutParams = Ui.lp().also { it.topMargin = dp(10) }
        fun weighted(v: View, last: Boolean = false) {
            v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.rightMargin = if (last) 0 else dp(6) }
            btns.addView(v)
        }
        weighted(Ui.button(this, "▶ Main", filled = true) { startGame(p.pkg, false) })
        weighted(Ui.button(this, "✏ Edit") { startGame(p.pkg, true) })
        weighted(Ui.button(this, "⋯ Lainnya") { showGameMenu(p) }, last = true)
        box.addView(btns)
        return box
    }

    private fun startGame(pkg: String, edit: Boolean) {
        if (!overlayOk()) {
            Toast.makeText(this, "Berikan izin overlay dulu", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!Backend.isReady()) {
            dialog().setTitle("Layanan input belum terhubung")
                .setMessage("Layanan input belum siap; mapping belum bisa mengirim sentuhan. Lanjutkan juga?")
                .setPositiveButton("Lanjutkan") { _, _ -> doStart(pkg, edit) }
                .setNegativeButton("Batal", null).show()
            return
        }
        doStart(pkg, edit)
    }

    private fun doStart(pkg: String, edit: Boolean) {
        if (packageManager.getLaunchIntentForPackage(pkg) == null) {
            Toast.makeText(this, "Game tidak terpasang", Toast.LENGTH_LONG).show()
            return
        }
        store.lastPkg = pkg
        val i = Intent(this, MapperService::class.java).putExtra(MapperService.EXTRA_PKG, pkg).putExtra(MapperService.EXTRA_EDIT, edit)
        startForegroundService(i)
        handler.postDelayed({ launchPackage(pkg) }, 250)
    }

    private fun showGameMenu(p: Profile) {
        val items = arrayOf(
            "Terapkan preset MMORPG",
            "Terapkan preset FPS / Battle Royale",
            "Mulai di mode: ${if (p.startAim) "Kursor" else "Bidik"} (ganti)",
            "Hapus profil"
        )
        dialog().setTitle(p.name).setItems(items) { _, which ->
            when (which) {
                0 -> confirmPreset(p, MappingStore.PRESET_MMORPG)
                1 -> confirmPreset(p, MappingStore.PRESET_FPS)
                2 -> {
                    p.startAim = !p.startAim
                    store.save()
                    render()
                }
                3 -> dialog().setTitle("Hapus profil?").setMessage("Semua kontrol untuk ${p.name} akan hilang.")
                    .setPositiveButton("Hapus") { _, _ ->
                        store.deleteProfile(p.pkg)
                        render()
                    }.setNegativeButton("Batal", null).show()
            }
        }.show()
    }

    private fun confirmPreset(p: Profile, preset: String) {
        val apply = {
            store.applyPreset(p.pkg, preset)
            render()
            Toast.makeText(this, "Preset diterapkan. Buka Edit untuk menyesuaikan posisi.", Toast.LENGTH_LONG).show()
        }
        if (p.bindings.isEmpty()) apply()
        else dialog().setTitle("Ganti semua kontrol?")
            .setMessage("Preset akan menggantikan ${p.count()} kontrol yang ada.")
            .setPositiveButton("Ganti") { _, _ -> apply() }
            .setNegativeButton("Batal", null).show()
    }

    private class AppEntry(val pkg: String, val label: String, val icon: Drawable?)

    private fun showAddGame() {
        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(q, 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), try { it.loadIcon(pm) } catch (_: Exception) { null }) }
                .filter { it.pkg != packageName && !store.profiles.containsKey(it.pkg) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { showPicker(apps) }
        }.start()
    }

    private fun showPicker(all: List<AppEntry>) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), 0)
        val search = EditText(this)
        search.hint = "Cari aplikasi…"
        search.setTextColor(Ui.TEXT)
        search.setHintTextColor(Ui.SUB)
        search.isSingleLine = true
        box.addView(search)
        val lv = ListView(this)
        lv.layoutParams = Ui.lp(h = dp(380))
        box.addView(lv)
        var shown: List<AppEntry> = all
        lateinit var dlg: AlertDialog
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val e = shown[position]
                val row = LinearLayout(this@MainActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(8), 0, dp(8))
                val iv = ImageView(this@MainActivity)
                iv.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).also { it.rightMargin = dp(12) }
                e.icon?.let { iv.setImageDrawable(it) }
                row.addView(iv)
                val col = LinearLayout(this@MainActivity)
                col.orientation = LinearLayout.VERTICAL
                col.addView(Ui.text(this@MainActivity, e.label, 14f, Ui.TEXT, true))
                col.addView(Ui.sub(this@MainActivity, e.pkg))
                row.addView(col)
                return row
            }
        }
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            val e = shown[pos]
            store.ensureProfile(e.pkg, e.label)
            dlg.dismiss()
            render()
            dialog().setTitle("Terapkan preset?")
                .setMessage("Mulai cepat dengan preset untuk ${e.label}, atau kosongkan dan buat sendiri.")
                .setPositiveButton("MMORPG") { _, _ -> store.applyPreset(e.pkg, MappingStore.PRESET_MMORPG); render() }
                .setNeutralButton("FPS / BR") { _, _ -> store.applyPreset(e.pkg, MappingStore.PRESET_FPS); render() }
                .setNegativeButton("Kosong", null).show()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                shown = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }
                adapter.notifyDataSetChanged()
            }
        })
        dlg = dialog().setTitle("Pilih game").setView(box).setNegativeButton("Batal", null).create()
        dlg.show()
    }

    // ---------- pengaturan ----------

    private fun switchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = Ui.lp().also { it.topMargin = dp(10) }
        val l = Ui.text(this, label, 13.5f, Ui.TEXT)
        l.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val sw = Switch(this)
        sw.isChecked = value
        sw.thumbTintList = ColorStateList.valueOf(Ui.ACCENT)
        sw.trackTintList = ColorStateList.valueOf(Ui.ACCENT_DIM)
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        row.addView(l)
        row.addView(sw)
        return row
    }

    private fun choiceRow(label: String, current: String, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = Ui.lp().also { it.topMargin = dp(10) }
        val l = Ui.text(this, label, 13.5f, Ui.TEXT)
        l.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(l)
        row.addView(Ui.button(this, "$current  ✎") { onClick() })
        return row
    }

    private fun pickKey(title: String, choices: List<Pair<String, Int>>, current: Int, onPick: (Int) -> Unit) {
        val idx = choices.indexOfFirst { it.second == current }
        dialog().setTitle(title)
            .setSingleChoiceItems(choices.map { it.first }.toTypedArray(), idx) { d, which ->
                onPick(choices[which].second)
                d.dismiss()
                render()
            }.setNegativeButton("Batal", null).show()
    }

    private fun settingsCard() {
        val c = Ui.card(this)
        c.addView(Ui.title(this, "Pengaturan"))
        c.addView(Ui.slider(this, "Durasi ketukan", 20f, 200f, store.tapMs.toFloat(), { "${it.toInt()} ms" }) { store.tapMs = it.toInt() })
        c.addView(Ui.slider(this, "Kecepatan kursor", 0.4f, 3f, store.cursorSpeed, { String.format("%.1fx", it) }) { store.cursorSpeed = it })
        c.addView(Ui.slider(this, "Sensitivitas kamera", 0.3f, 3f, store.camSens, { String.format("%.2fx", it) }) { store.camSens = it })
        c.addView(Ui.slider(this, "Opasitas petunjuk", 0.15f, 1f, store.hintOpacity, { "${(it * 100).toInt()}%" }) { store.hintOpacity = it })
        c.addView(switchRow("Tampilkan petunjuk tombol di game", store.showHints) { store.showHints = it })
        c.addView(Ui.divider(this))
        c.addView(choiceRow("Tombol Mode Bidik", KeyNames.label(store.aimKey)) {
            pickKey("Tombol berpindah Kursor ↔ Bidik", KeyNames.aimKeyChoices, store.aimKey) { store.aimKey = it }
        })
        c.addView(choiceRow("Tombol Jeda mapping", KeyNames.label(store.pauseKey)) {
            pickKey("Tombol jeda / lanjut", KeyNames.pauseKeyChoices, store.pauseKey) { store.pauseKey = it }
        })
        c.addView(
            Ui.sub(this, "Saat dijeda, keyboard dan mouse kembali ke game. Untuk melanjutkan, ketuk bubble GEN di layar.").also {
                it.layoutParams = Ui.lp().also { l -> l.topMargin = dp(8) }
            }
        )
        content.addView(c)
    }

    // ---------- backup ----------

    private fun backupCard() {
        val c = Ui.card(this)
        c.addView(Ui.title(this, "Cadangan profil"))
        c.addView(Ui.sub(this, "Simpan atau pulihkan semua profil sebagai file JSON."))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = Ui.lp().also { it.topMargin = dp(10) }
        row.addView(Ui.button(this, "Ekspor", filled = true) {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "genkeymapper-profil.json")
            startActivityForResult(i, REQ_EXPORT)
        })
        val imp = Ui.button(this, "Impor") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            startActivityForResult(i, REQ_IMPORT)
        }
        imp.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.leftMargin = dp(8) }
        row.addView(imp)
        c.addView(row)
        content.addView(c)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try {
            when (requestCode) {
                REQ_EXPORT -> {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(store.toJsonText().toByteArray()) }
                    Toast.makeText(this, "Profil diekspor", Toast.LENGTH_SHORT).show()
                }
                REQ_IMPORT -> {
                    val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: return
                    val n = store.importJsonText(text)
                    Toast.makeText(this, "$n profil diimpor", Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- bantuan ----------

    private fun helpCard() {
        val c = Ui.card(this)
        c.addView(Ui.title(this, "Cara pakai"))
        val steps = listOf(
            "1. Selesaikan Persiapan di atas sampai layanan input terhubung, lalu jalankan Uji sentuhan.",
            "2. Tambah game, terapkan preset, lalu ketuk Edit untuk menggeser kontrol ke tombol di layar game.",
            "3. Ketuk Main. Overlay aktif dan bubble GEN muncul. Klik tengah mouse berpindah antara Mode Kursor dan Mode Bidik.",
            "4. Mode Kursor: mouse menggerakkan kursor virtual dan klik kiri mengetuk. Mode Bidik: mouse memutar kamera, klik kiri menembak.",
            "5. Tombol jeda (default F8) mengembalikan input ke game, mis. untuk mengetik chat."
        )
        steps.forEach {
            c.addView(Ui.text(this, it, 13f, Ui.TEXT).also { t -> t.layoutParams = Ui.lp().also { l -> l.topMargin = dp(6) } })
        }
        c.addView(Ui.divider(this))
        c.addView(Ui.title(this, "Keterbatasan"))
        listOf(
            "Overlay memegang fokus jendela agar bisa menerima keyboard/mouse. Sebagian game menjeda diri saat kehilangan fokus.",
            "Game dapat memblokir overlay atau input sintetis. Mengubah input di game online bisa melanggar aturannya; gunakan dengan bijak.",
            "Shizuku via Wireless debugging perlu dinyalakan ulang setelah perangkat restart.",
            "Gerakan analog gamepad belum didukung; hanya tombol."
        ).forEach {
            c.addView(Ui.sub(this, "• $it").also { t -> t.layoutParams = Ui.lp().also { l -> l.topMargin = dp(4) } })
        }
        content.addView(c)
    }

    companion object {
        private const val REQ_EXPORT = 301
        private const val REQ_IMPORT = 302
    }
}
