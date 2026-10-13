package com.gen.keymapper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Layar utama Gen Keymapper — 100% GRATIS, semua fitur terbuka, tanpa VIP.
 * Status layanan, panduan, daftar profil per game, dan pengaturan.
 */
class MainActivity : Activity() {

    private lateinit var store: MappingStore
    private lateinit var root: LinearLayout
    private var profileAdapter: ProfileAdapter? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MappingStore(this)
        store.load()
        ShizukuBridge.initialize(this)

        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
            setBackgroundColor(Color.parseColor("#121212"))
        }
        scroll.addView(root)
        setContentView(scroll)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        store.load()
        buildUi()
    }

    // ---------- UI ----------

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E1E"))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.parseColor("#9E9E9E"))
        }
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun title(text: String, size: Float = 15f, color: String = "#FFFFFF"): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.parseColor(color))
        }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#BDBDBD"))
    }

    private fun btn(text: String, color: String = "#2E7D32", onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(color)); cornerRadius = dp(8).toFloat()
            }
            setOnClickListener { onClick() }
        }

    private fun spacer(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(h))
    }

    private fun buildUi() {
        root.removeAllViews()

        // Header
        root.addView(title("🎮 Gen Keymapper", 24f))
        root.addView(title("100% GRATIS • SEMUA FITUR TERBUKA • TANPA VIP", 12f, "#66BB6A"))
        root.addView(spacer(12))

        // Status
        val accOn = isAccessibilityOn()
        val ovOn = Settings.canDrawOverlays(this)
        val svcOn = MapperService.instance?.isServiceReady() == true
        val status = card()
        status.addView(title("Status", 15f))
        status.addView(body(
            "• Layanan Aksesibilitas: ${if (accOn) "✅ AKTIF" else "❌ BELUM"}\n" +
            "• Izin Tampil di Atas Aplikasi: ${if (ovOn) "✅ DIBERIKAN" else "❌ BELUM"}\n" +
            "• Mapping: ${if (store.mappingEnabled) "▶️ ON" else "⏸️ OFF"}${if (svcOn) "" else " (layanan belum jalan)"}"
        ))
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (!accOn) row.addView(btn("Aktifkan Aksesibilitas", "#1565C0") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        if (!ovOn) row.addView(btn("Izinkan Overlay", "#1565C0") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        })
        if (row.childCount > 0) { status.addView(spacer(8)); status.addView(row) }
        root.addView(status)
        root.addView(spacer(12))

        val shizuku = card()
        shizuku.addView(title("⚡ Shizuku • Multi-touch MMORPG", 15f))
        val statusShizuku = when {
            !ShizukuBridge.isRunning() -> "Tidak berjalan. Buka aplikasi Shizuku dan mulai via Wireless Debugging."
            !ShizukuBridge.hasPermission() -> "Shizuku hidup, izin Gen Keymapper belum diberikan."
            ShizukuBridge.isReady() -> "Terhubung. InputManager shell siap digunakan."
            else -> "Izin diberikan; service sedang terhubung atau backend input belum tersedia."
        }
        shizuku.addView(body(statusShizuku))
        shizuku.addView(spacer(8))
        shizuku.addView(btn(if (store.useShizuku) "Backend utama: Shizuku" else "Backend utama: Accessibility", "#3949AB") {
            MapperService.instance?.resetInputs()
            store.useShizuku = !store.useShizuku
            MapperService.instance?.store?.useShizuku = store.useShizuku
            buildUi()
        })
        shizuku.addView(spacer(6))
        shizuku.addView(btn("Hubungkan / Minta Izin Shizuku", "#1565C0") {
            ShizukuBridge.requestPermission()
            Handler(Looper.getMainLooper()).postDelayed({ buildUi() }, 1200)
        })
        shizuku.addView(spacer(6))
        shizuku.addView(btn("Periksa Status Lagi", "#455A64") { buildUi() })
        shizuku.addView(body("Tanpa Shizuku aplikasi tetap bisa memakai gesture Accessibility, namun multi-touch dan tombol tahan bisa lebih terbatas."))
        root.addView(shizuku)
        root.addView(spacer(12))

        // Cara pakai
        val guide = card()
        guide.addView(title("📖 Cara Pakai", 15f))
        guide.addView(body(
            "1. Aktifkan Aksesibilitas + Izin Overlay di atas.\n" +
            "2. Sambungkan keyboard/mouse/gamepad (OTG/Bluetooth).\n" +
            "3. Buka game, tap tombol 🎮 melayang → \"Edit Mapping\".\n" +
            "4. Tap chip, tekan tombol di keyboard/mouse, drag chip ke posisi.\n" +
            "5. Untuk joystick: + Joystick, tap chip, isi 4 arah (WASD).\n" +
            "6. Mode mouse: overlay menangani hover, klik dan drag; scroll bisa dipetakan.\n" +
            "7. Tambahkan chip Kamera, aktifkan Mouse Mode + Kamera Mouse di menu overlay.\n" +
            "8. Untuk multi-touch dan hold real-time gunakan Shizuku. Atur semua chip sesuai posisi HUD game."
        ))
        root.addView(guide)
        root.addView(spacer(12))

        // Profil
        val pc = card()
        pc.addView(title("🗂️ Profil per Game (${store.profiles.size})", 15f))
        pc.addView(body("Profil otomatis dibuat saat kamu buka Edit Mapping di dalam game."))
        pc.addView(spacer(8))
        val list = ListView(this)
        profileAdapter = ProfileAdapter()
        list.adapter = profileAdapter
        val listH = (store.profiles.size.coerceAtLeast(1) * dp(64)).coerceAtMost(dp(320))
        pc.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, listH))
        pc.addView(spacer(8))
        pc.addView(btn("＋ Tambah Profil dari Daftar Aplikasi", "#1565C0") { pickApp() })
        pc.addView(spacer(6))
        pc.addView(btn("Ekspor Profil JSON", "#455A64") { exportProfiles() })
        pc.addView(spacer(6))
        pc.addView(btn("Impor Profil JSON", "#455A64") { importProfiles() })
        root.addView(pc)
        root.addView(spacer(12))

        // Pengaturan
        val sc = card()
        sc.addView(title("⚙️ Pengaturan (semua gratis)", 15f))
        sc.addView(body(
            "• Durasi tap: ${store.tapMs} ms\n" +
            "• Durasi tahan: ${store.holdMs} ms\n" +
            "• Radius joystick: ${store.joyRadiusDp} dp\n" +
            "• Sensitivitas kamera: ${store.mouseSensitivity}x"
        ))
        sc.addView(spacer(8))
        sc.addView(btn("Ubah Pengaturan", "#455A64") { settingsDialog() })
        root.addView(sc)
        root.addView(spacer(12))

        root.addView(body("Gen Keymapper v2.0 • Android 15 • semua fitur gratis • tanpa iklan, akun, atau VIP.\nCatatan: beberapa game dapat memblokir input sintetis atau overlay."))
    }

    // ---------- status ----------

    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(packageName, ignoreCase = true)
    }

    // ---------- profil ----------

    private inner class ProfileAdapter : BaseAdapter() {
        private val items get() = store.profiles.values.toList()
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(pos: Int, cv: View?, parent: ViewGroup?): View {
            val p = items[pos]
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }
            val tv = TextView(this@MainActivity).apply {
                text = "📦 ${p.name}\n${p.count()} mapping • ${p.pkg}"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(tv)
            row.addView(btn("MMORPG", "#3949AB") {
                store.addMmorpgPreset(p.pkg)
                MapperService.instance?.store?.load()
                Toast.makeText(this@MainActivity, "Preset WASD dan skill dibuat. Geser chip sesuai game.", Toast.LENGTH_LONG).show()
                buildUi()
            })
            row.addView(Button(this@MainActivity).apply {
                text = "Hapus"
                textSize = 11f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#C62828")); cornerRadius = dp(6).toFloat()
                }
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Hapus profil?")
                        .setMessage("Hapus profil \"${p.name}\"?")
                        .setPositiveButton("Hapus") { _, _ ->
                            store.deleteProfile(p.pkg); store.load(); buildUi()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            })
            return row
        }
    }

    private fun pickApp() {
        try {
            val pm = packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            val names = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Pilih aplikasi untuk dibuatkan profil")
                .setItems(names) { _, which ->
                    val ai = apps[which]
                    val label = pm.getApplicationLabel(ai).toString()
                    store.ensureProfile(ai.packageName, label)
                    Toast.makeText(this, "Profil \"$label\" dibuat ✓", Toast.LENGTH_SHORT).show()
                    buildUi()
                }
                .setNegativeButton("Batal", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca daftar aplikasi", Toast.LENGTH_SHORT).show()
        }
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
            buildUi()
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
                buildUi()
                Toast.makeText(this, "Pengaturan tersimpan ✓", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
