package com.gen.keymapper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Dashboard orisinal, bebas Accessibility. */
class MainActivity : Activity() {
    private lateinit var store: MappingStore
    private lateinit var root: LinearLayout
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private val bg = Color.rgb(10, 14, 29)
    private val surface = Color.rgb(21, 28, 48)
    private val accent = Color.rgb(139, 125, 255)
    private val muted = Color.rgb(157, 169, 199)
    private val cyan = Color.rgb(78, 215, 219)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        store = MappingStore(this)
        ShizukuBridge.initialize(this)
        val scroll = ScrollView(this).apply { fillViewport = true; isVerticalScrollBarEnabled = false }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(32))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        setContentView(scroll)
    }
    override fun onResume() { super.onResume(); store.load(); draw() }

    private fun shape(fill: Int, rad: Int = 18, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(fill); cornerRadius = dp(rad).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    private fun gradient() = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(48, 38, 103), Color.rgb(20, 42, 75))).apply {
        cornerRadius = dp(22).toFloat()
        setStroke(dp(1), Color.rgb(77, 74, 137))
    }
    private fun text(value: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
    private fun space(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }
    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(18), dp(17), dp(18))
        background = shape(surface, 20, Color.rgb(38, 48, 78))
    }
    private fun action(label: String, primary: Boolean = false, click: () -> Unit): TextView =
        text(label, 13f, if (primary) Color.WHITE else Color.rgb(218, 224, 255), true).apply {
            gravity = Gravity.CENTER; minHeight = dp(44)
            setPadding(dp(15), dp(10), dp(15), dp(10))
            background = shape(if (primary) accent else Color.rgb(41, 50, 78), 13)
            setOnClickListener { click() }
        }
    private fun bar(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { i, view ->
            if (i > 0) addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
            addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
    private fun sectionTitle(value: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(value, 18f, Color.WHITE, true))
        addView(space(4))
        addView(text(subtitle, 12f, muted))
    }
    private fun status(label: String, ready: Boolean): TextView =
        text("${if (ready) "●" else "○"}  $label", 11.5f,
            if (ready) cyan else Color.rgb(255, 185, 114), true).apply {
            background = shape(if (ready) Color.rgb(25, 73, 77) else Color.rgb(76, 55, 47), 18)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }

    private fun draw() {
        root.removeAllViews()
        root.addView(text("GEN  /  KEYMAPPER", 12f, cyan, true))
        root.addView(space(8))
        root.addView(text("Play your way.", 29f, Color.WHITE, true))
        root.addView(text("Kontrol keyboard & mouse untuk game Android", 13f, muted))
        root.addView(space(22))

        val hero = panel().apply { background = gradient() }
        hero.addView(text("CONTROL CENTER", 11f, cyan, true))
        hero.addView(space(7))
        hero.addView(text(if (MapperService.instance == null) "Mapping belum aktif" else "Overlay sedang berjalan",
            21f, Color.WHITE, true))
        hero.addView(space(8))
        hero.addView(text("Backend Shizuku · tanpa Accessibility · profil game mandiri", 12f, Color.rgb(198, 202, 235)))
        hero.addView(space(15))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chips.addView(status("Shizuku", ShizukuBridge.isReady()))
        chips.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        chips.addView(status("Overlay", Settings.canDrawOverlays(this)))
        hero.addView(chips)
        hero.addView(space(15))
        hero.addView(bar(
            action("▶  Mulai overlay", true) { startMapper(false) },
            action("■  Hentikan") { stopMapper() }
        ))
        root.addView(hero)
        root.addView(space(18))

        root.addView(sectionTitle("Persiapan", "Dua izin saja, bukan layanan Accessibility"))
        root.addView(space(10))
        val setup = panel()
        setup.addView(text("01   SHIZUKU", 12f, cyan, true))
        val reason = when {
            !ShizukuBridge.isRunning() -> "Belum berjalan — nyalakan Shizuku melalui Wireless debugging."
            !ShizukuBridge.hasPermission() -> "Shizuku ditemukan — berikan akses untuk mengirim gesture."
            !ShizukuBridge.isReady() -> "Izin sudah ada — UserService sedang menghubungkan backend."
            else -> "Terhubung — input sintetis tersedia."
        }
        setup.addView(space(5)); setup.addView(text(reason, 12f, muted))
        setup.addView(space(10)); setup.addView(action("Hubungkan Shizuku") {
            if (!ShizukuBridge.isRunning()) {
                packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { startActivity(it) }
                    ?: toast("Pasang dan mulai aplikasi Shizuku dahulu")
            } else ShizukuBridge.requestPermission()
            Handler(Looper.getMainLooper()).postDelayed({ draw() }, 1300)
        })
        setup.addView(space(15))
        setup.addView(text("02   OVERLAY", 12f, cyan, true))
        setup.addView(space(5)); setup.addView(text("Diizinkan tampil di atas game agar kontrol bisa digeser langsung.", 12f, muted))
        setup.addView(space(10)); setup.addView(action(
            if (Settings.canDrawOverlays(this)) "✓  Izin overlay aktif" else "Izinkan tampil di atas aplikasi") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        })
        root.addView(setup)
        root.addView(space(22))

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val heading = sectionTitle("Game & Profil", "Pilih satu game untuk sesi mapping").apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        header.addView(heading)
        header.addView(action("+ Game", true) { pickApp() })
        root.addView(header)
        root.addView(space(10))
        if (store.profiles.isEmpty()) {
            val empty = panel()
            empty.addView(text("Belum ada profil", 17f, Color.WHITE, true))
            empty.addView(space(5)); empty.addView(text("Tambahkan MMORPG atau game lain untuk mulai menyusun kontrol.", 12f, muted))
            empty.addView(space(10)); empty.addView(action("Tambahkan game", true) { pickApp() })
            root.addView(empty)
        }
        store.profiles.values.toList().forEach { p ->
            val card = panel()
            val active = store.activePackage == p.pkg
            card.addView(text("${if (active) "◆" else "◇"}   ${p.name}", 16f, Color.WHITE, true))
            card.addView(space(4))
            card.addView(text("${p.count()} kontrol  ·  ${p.pkg}", 11.5f, muted))
            card.addView(space(12))
            card.addView(bar(
                action(if (active) "✎  Edit mapping" else "▶  Pilih & edit", true) {
                    selectGame(p.pkg)
                    if (p.pkg == "*") startMapper(true) else {
                        startMapper(false)
                        launchGame(p.pkg)
                        Handler(Looper.getMainLooper()).postDelayed({
                            MapperService.instance?.openEditor()
                        }, 900)
                    }
                },
                action("🎮  Mainkan") { selectGame(p.pkg); launchGame(p.pkg) }
            ))
            card.addView(space(8))
            card.addView(bar(
                action("Preset MMORPG") {
                    store.addMmorpgPreset(p.pkg); MapperService.instance?.store?.load(); draw()
                },
                action("Opsi") {
                    AlertDialog.Builder(this).setTitle(p.name)
                        .setItems(arrayOf("Gunakan profil", "Hapus profil")) { _, idx ->
                            if (idx == 0) selectGame(p.pkg)
                            else AlertDialog.Builder(this).setTitle("Hapus profil ${p.name}?")
                                .setPositiveButton("Hapus") { _, _ -> store.deleteProfile(p.pkg); draw() }
                                .setNegativeButton("Batal", null).show()
                        }.show()
                }
            ))
            root.addView(card); root.addView(space(10))
        }
        root.addView(space(10))

        root.addView(sectionTitle("Pengaturan", "Personalasi kontrol dan pencadangan"))
        root.addView(space(10))
        val settings = panel()
        settings.addView(text("Tap ${store.tapMs} ms     •     Joystick ${store.joyRadiusDp} dp", 12f, muted))
        settings.addView(space(5))
        settings.addView(text("Kamera ${"%.2f".format(store.mouseSensitivity)}×     •     ${if (store.showChips) "Indikator aktif" else "Indikator disembunyikan"}", 12f, muted))
        settings.addView(space(12))
        settings.addView(bar(action("Atur sensitivitas") { settingsDialog() },
            action(if (store.showChips) "Sembunyikan chip" else "Tampilkan chip") {
                store.showChips = !store.showChips
                MapperService.instance?.refreshPlayOverlay(); draw()
            }))
        settings.addView(space(8))
        settings.addView(bar(action("Ekspor JSON") { exportProfiles() }, action("Impor JSON") { importProfiles() }))
        root.addView(settings)
        root.addView(space(18))
        root.addView(text("GenKeymapper 2.1  ·  Semua fitur lokal, tanpa VIP", 11f, muted))
        root.addView(space(5))
        root.addView(text("Input game tertentu mungkin membatasi overlay atau sintesis sentuhan. Gunakan pada game yang mengizinkan keymapping.", 11f, muted))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun selectGame(pkg: String) {
        store.activePackage = pkg
        MapperService.instance?.selectProfile(pkg)
        draw()
    }
    private fun startMapper(edit: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            toast("Aktifkan izin overlay terlebih dahulu")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val svc = Intent(this, MapperService::class.java).apply {
            putExtra(MapperService.EXTRA_PACKAGE, store.activePackage)
            if (edit) action = MapperService.ACTION_EDIT
        }
        try {
            startForegroundService(svc)
            if (edit) Handler(Looper.getMainLooper()).postDelayed({
                MapperService.instance?.selectProfile(store.activePackage)
                MapperService.instance?.openEditor()
            }, 180)
            toast(if (edit) "Editor overlay dibuka" else "Overlay dijalankan")
            draw()
        } catch (e: Exception) { toast("Tidak dapat memulai layanan: ${e.message}") }
    }
    private fun stopMapper() {
        MapperService.instance?.stopSelf()
        draw()
    }
    private fun launchGame(pkg: String) {
        if (pkg == "*") { toast("Pilih profil game yang ingin dibuka"); return }
        if (MapperService.instance == null) startMapper(false)
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) { toast("Game tidak ditemukan / tidak bisa dibuka"); return }
        Handler(Looper.getMainLooper()).postDelayed({ startActivity(launch) }, 250)
    }
    private fun pickApp() {
        val pm = packageManager
        val installed = pm.getInstalledApplications(0)
            .filter { it.packageName != packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val labels = installed.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        if (labels.isEmpty()) { toast("Aplikasi belum terdeteksi"); return }
        AlertDialog.Builder(this).setTitle("Tambahkan game")
            .setItems(labels) { _, index ->
                val item = installed[index]; val name = labels[index]
                store.ensureProfile(item.packageName, name)
                selectGame(item.packageName)
            }.setNegativeButton("Batal", null).show()
    }

    // ---------- backup profil ----------

    private fun exportProfiles() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "GenKeymapper-profiles.json")
        }
        startActivityForResult(intent, 502)
    }

    private fun importProfiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, 503)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                502 -> contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.toJsonText()) }
                503 -> {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                    store.importJsonText(json)
                    MapperService.instance?.store?.load()
                }
            }
            draw()
            Toast.makeText(this, "Berhasil", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- pengaturan ----------

    private fun settingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        fun field(label: String, value: String): EditText {
            layout.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(Color.parseColor("#BDBDBD"))
            })
            return EditText(this).apply {
                setText(value)
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(Color.WHITE)
                layout.addView(this)
            }
        }
        val fTap = field("Durasi tap (ms, 20–500)", store.tapMs.toString())
        val fHold = field("Durasi tahan (ms, 200–5000)", store.holdMs.toString())
        val fJoy = field("Radius joystick (dp, 60–220)", store.joyRadiusDp.toString())
        val fSensitivity = field("Sensitivitas kamera (persen: 50–600)", (store.mouseSensitivity * 100).toInt().toString())
        AlertDialog.Builder(this)
            .setTitle("Pengaturan")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                fTap.text.toString().toLongOrNull()?.let { store.tapMs = it }
                fHold.text.toString().toLongOrNull()?.let { store.holdMs = it }
                fJoy.text.toString().toIntOrNull()?.let { store.joyRadiusDp = it }
                fSensitivity.text.toString().toIntOrNull()?.let { store.mouseSensitivity = it / 100f }
                draw()
                Toast.makeText(this, "Pengaturan tersimpan ✓", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
