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
    private lateinit var scroll: ScrollView
    private val main = Handler(Looper.getMainLooper())
    private var statusSignature = ""
    private var tab = 0
    private val poll = object : Runnable {
        override fun run() {
            val signature = "${ShizukuBridge.status}|${ShizukuBridge.error}|${MapperService.instance?.inputStatus}|${android.view.InputDevice.getDeviceIds().joinToString()}"
            if (signature != statusSignature) { statusSignature = signature; draw() }
            main.postDelayed(this, 1000)
        }
    }
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
        ShizukuBridge.initialize()
        scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(32))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
    }
    override fun onResume() { super.onResume(); ShizukuBridge.refresh(); MapperService.instance?.suspendForDashboard(); store.load(); draw(); main.post(poll) }
    override fun onPause() { main.removeCallbacks(poll); super.onPause() }
    override fun onDestroy() { main.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onConfigurationChanged(config: android.content.res.Configuration) { super.onConfigurationChanged(config); draw() }

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

    private fun showConnectionDetails() {
        val details = "GenKey ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
            "${ShizukuBridge.status}\n${ShizukuBridge.diagnostics}"
        AlertDialog.Builder(this).setTitle("Detail koneksi Shizuku").setMessage(details)
            .setPositiveButton("Salin detail") { _, _ ->
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Koneksi GenKey", details))
                toast("Detail koneksi disalin")
            }.setNegativeButton("Tutup", null).show()
    }

    private fun draw() {
        val scrollY = scroll.scrollY
        root.removeAllViews()
        val horizontal = maxOf(dp(18), (resources.displayMetrics.widthPixels - dp(760)) / 2)
        root.setPadding(horizontal, dp(20), horizontal, dp(32))
        root.addView(bar(text("GENKEY", 16f, cyan, true), text("${BuildConfig.VERSION_NAME}  /  CONTROL STUDIO", 10f, muted)))
        root.addView(space(8))
        root.addView(text("Kontrolmu. Permainanmu.", 26f, Color.WHITE, true))
        root.addView(text("Kontrol keyboard & mouse untuk game Android", 13f, muted))
        root.addView(space(18))
        root.addView(bar(
            action("Dashboard", tab == 0) { tab = 0; draw() },
            action("Persiapan", tab == 1) { tab = 1; draw() },
            action("Pengaturan", tab == 2) { tab = 2; draw() }))
        root.addView(space(18))
        if (store.lastError.isNotBlank()) root.addView(text(store.lastError, 12f, Color.rgb(255, 180, 120)))

        if (tab == 0) {
        val hero = panel().apply { background = gradient() }
        hero.addView(text("CONTROL CENTER", 11f, cyan, true))
        hero.addView(space(7))
        hero.addView(text(if (MapperService.instance == null) "Mapping belum aktif" else "Overlay sedang berjalan",
            21f, Color.WHITE, true))
        hero.addView(space(8))
        hero.addView(text(MapperService.instance?.inputStatus ?: "Buat profil, susun kontrol, lalu buka game.", 12f, Color.rgb(198, 202, 235)))
        hero.addView(space(15))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chips.addView(status(if (ShizukuBridge.isReady()) "Input siap" else "Input belum siap", ShizukuBridge.isReady()))
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
        val devices = android.view.InputDevice.getDeviceIds().map { android.view.InputDevice.getDevice(it) }.filterNotNull()
            .filter { !it.isVirtual && (it.supportsSource(android.view.InputDevice.SOURCE_KEYBOARD) || it.supportsSource(android.view.InputDevice.SOURCE_MOUSE) || it.supportsSource(android.view.InputDevice.SOURCE_GAMEPAD)) }
        val deviceCard = panel()
        deviceCard.addView(text("PERANGKAT INPUT", 11f, cyan, true))
        deviceCard.addView(space(8))
        deviceCard.addView(text(if (devices.isEmpty()) "Belum ada keyboard, mouse, atau gamepad terdeteksi." else devices.joinToString("\n") { it.name }, 13f, muted))
        root.addView(deviceCard); root.addView(space(18))
        }

        if (tab == 1) {
        root.addView(sectionTitle("Persiapan", "Dua izin saja, bukan layanan Accessibility"))
        root.addView(space(10))
        val setup = panel()
        setup.addView(text("01   SHIZUKU", 12f, cyan, true))
        val reason = when {
            !ShizukuBridge.isRunning() -> "Belum berjalan — nyalakan Shizuku melalui Wireless debugging."
            !ShizukuBridge.hasPermission() -> "Shizuku ditemukan — berikan akses untuk mengirim gesture."
            ShizukuBridge.isConnecting -> "Izin sudah ada — sedang memeriksa layanan input Android."
            !ShizukuBridge.isReady() -> "Izin Shizuku aktif. Backend input belum siap; tekan Coba lagi."
            else -> "Backend siap. Jalankan Uji input untuk memeriksa penerimaan sentuhan."
        }
        setup.addView(space(5)); setup.addView(text(reason, 12f, muted))
        val connectLabel = when {
            ShizukuBridge.isConnecting -> "Sedang menghubungkan…"
            ShizukuBridge.isReady() -> "✓  Shizuku terhubung"
            ShizukuBridge.hasPermission() -> "Coba lagi"
            else -> "Hubungkan Shizuku"
        }
        setup.addView(space(10)); setup.addView(action(connectLabel) {
            if (!ShizukuBridge.isRunning()) {
                packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { startActivity(it) }
                    ?: toast("Pasang dan mulai aplikasi Shizuku dahulu")
            } else ShizukuBridge.requestPermission()
            draw()
        }.apply { isEnabled = !ShizukuBridge.isConnecting && !ShizukuBridge.isReady() })
        setup.addView(space(8)); setup.addView(action("Detail koneksi") { showConnectionDetails() })
        setup.addView(space(15))
        setup.addView(text("02   OVERLAY", 12f, cyan, true))
        setup.addView(space(5)); setup.addView(text("Diizinkan tampil di atas game agar kontrol bisa digeser langsung.", 12f, muted))
        setup.addView(space(10)); setup.addView(action(
            if (Settings.canDrawOverlays(this)) "✓  Izin overlay aktif" else "Izinkan tampil di atas aplikasi") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        })
        if (ShizukuBridge.error.isNotBlank()) { setup.addView(space(10)); setup.addView(text(ShizukuBridge.error, 12f, Color.rgb(255, 185, 114))) }
        setup.addView(space(16))
        setup.addView(action("Uji tap & multitouch", true) { startActivity(Intent(this, InputTestActivity::class.java)) })
        setup.addView(space(8))
        setup.addView(text("Uji ini memastikan event masuk ke layar tes. Kompatibilitas setiap game tetap perlu dicoba.", 12f, muted))
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            setup.addView(space(12)); setup.addView(action("Izinkan notifikasi sesi") {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 601)
            })
        }
        root.addView(setup)
        root.addView(space(18))
        val guide = panel()
        guide.addView(text("ALUR BERMAIN", 11f, cyan, true)); guide.addView(space(8))
        guide.addView(text("1. Hubungkan Shizuku + izinkan overlay.\n2. Tambah game, pilih preset, geser kontrol.\n3. Mainkan, ketuk GEN → Fokus keyboard.\n4. F8 menangkap mouse; Esc melepas.\n5. F9 untuk jeda. Volume tetap berfungsi.", 13f, muted))
        root.addView(guide)
        }

        if (tab == 0) {
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
                        launchGame(p.pkg, true)
                    }
                },
                action("Mainkan") { selectGame(p.pkg); launchGame(p.pkg) }
            ))
            card.addView(space(8))
            card.addView(bar(
                action("Tambah preset") {
                    AlertDialog.Builder(this).setTitle("Tambahkan preset")
                        .setItems(arrayOf("MMORPG · WASD + skill", "FPS · WASD + M1 tembak + M2 bidik")) { _, preset ->
                            MapperService.instance?.resetInputs()
                            if (preset == 0) store.addMmorpgPreset(p.pkg) else store.addFpsPreset(p.pkg)
                            MapperService.instance?.reloadProfiles(); checkSave(); draw()
                        }.show()
                },
                action("Opsi") {
                    AlertDialog.Builder(this).setTitle(p.name)
                        .setItems(arrayOf("Gunakan profil", "Ubah nama", "Hapus profil")) { _, idx ->
                            if (idx == 0) selectGame(p.pkg)
                            else if (idx == 1) {
                                val input = EditText(this).apply { setText(p.name); maxLines = 1 }
                                AlertDialog.Builder(this).setTitle("Nama profil").setView(input)
                                    .setPositiveButton("Simpan") { _, _ ->
                                        val name = input.text.toString().trim().take(120)
                                        if (name.isNotBlank()) { p.name = name; store.save(); MapperService.instance?.reloadProfiles(); checkSave(); draw() }
                                    }.setNegativeButton("Batal", null).show()
                            } else AlertDialog.Builder(this).setTitle("Hapus profil ${p.name}?")
                                .setPositiveButton("Hapus") { _, _ -> MapperService.instance?.resetInputs(); store.deleteProfile(p.pkg); MapperService.instance?.selectProfile(store.activePackage); checkSave(); draw() }
                                .setNegativeButton("Batal", null).show()
                        }.show()
                }
            ))
            root.addView(card); root.addView(space(10))
        }
        root.addView(space(10))
        }

        if (tab == 2) {
        root.addView(sectionTitle("Pengaturan", "Sesuaikan kontrol dan simpan cadangan"))
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
        settings.addView(space(12))
        settings.addView(action(if (store.gamepadEnabled) "Analog gamepad: AKTIF" else "Analog gamepad: NONAKTIF") {
            MapperService.instance?.resetInputs(); store.gamepadEnabled = !store.gamepadEnabled; draw()
        })
        settings.addView(space(8))
        settings.addView(action(if (store.invertY) "Balik sumbu Y: AKTIF" else "Balik sumbu Y: NONAKTIF") { store.invertY = !store.invertY; draw() })
        settings.addView(space(12))
        settings.addView(text("Analog kiri menggerakkan joystick pertama dalam profil. Tombol gamepad dapat direkam di editor. F8/F9/Esc disediakan untuk kontrol sesi.", 12f, muted))
        settings.addView(space(16)); settings.addView(text("DIAGNOSTIK", 11f, cyan, true))
        settings.addView(space(8)); settings.addView(text(ShizukuBridge.diagnostics, 12f, muted))
        settings.addView(space(8)); settings.addView(action("Segarkan diagnostik") { ShizukuBridge.heartbeat(); main.postDelayed({ draw() }, 300) })
        root.addView(settings)
        }
        root.addView(space(18))
        root.addView(text("GenKey 3.0  ·  Lokal, tanpa akun atau langganan", 11f, muted))
        root.addView(space(5))
        root.addView(text("Kompatibilitas input bergantung pada perangkat, firmware, dan game.", 11f, muted))
        scroll.post { scroll.scrollTo(0, scrollY) }
    }

    private fun checkSave() { if (store.lastError.isNotBlank()) toast(store.lastError) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun selectGame(pkg: String) {
        store.activePackage = pkg
        MapperService.instance?.selectProfile(pkg)
        draw()
    }
    private fun startMapper(edit: Boolean): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            toast("Aktifkan izin overlay terlebih dahulu")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return false
        }
        if (!edit && !ShizukuBridge.isReady()) { tab = 1; draw(); toast("Hubungkan Shizuku dahulu"); return false }
        store.mappingEnabled = true
        val svc = Intent(this, MapperService::class.java).apply {
            putExtra(MapperService.EXTRA_PACKAGE, store.activePackage)
            if (edit) action = MapperService.ACTION_EDIT
        }
        try {
            startForegroundService(svc)
            toast(if (edit) "Editor overlay dibuka" else "Overlay dijalankan")
            main.postDelayed({ draw() }, 200)
            return true
        } catch (e: Exception) { toast("Tidak dapat memulai layanan: ${e.message}"); return false }
    }
    private fun stopMapper() {
        MapperService.instance?.stopSelf()
        main.postDelayed({ draw() }, 200)
    }
    private fun launchGame(pkg: String, edit: Boolean = false) {
        if (pkg == "*") { startMapper(edit); return }
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) { toast("Game tidak ditemukan / tidak bisa dibuka"); return }
        if (!startMapper(false)) return
        main.postDelayed({
            runCatching { startActivity(launch) }.onFailure { toast("Game gagal dibuka: ${it.message}") }
            if (edit) main.postDelayed({ MapperService.instance?.openEditor() }, 1000)
        }, 250)
    }
    private fun pickApp() {
        val pm = packageManager
        val installed = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
            .filter { it.packageName != packageName }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val search = EditText(this).apply { hint = "Cari nama game atau package"; setSingleLine() }
        val list = android.widget.ListView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0)
            addView(search); addView(list, LinearLayout.LayoutParams(-1, dp(340)))
        }
        var filtered = installed
        fun update(query: String) {
            filtered = installed.filter { pm.getApplicationLabel(it).toString().contains(query, true) || it.packageName.contains(query, true) }
            list.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1,
                filtered.map { pm.getApplicationLabel(it).toString() + "\n" + it.packageName })
        }
        update("")
        val dialog = AlertDialog.Builder(this).setTitle("Tambah game").setView(box)
            .setNeutralButton("Profil umum") { _, _ -> store.ensureProfile("*", "Profil umum"); selectGame("*") }
            .setNegativeButton("Batal", null).create()
        list.setOnItemClickListener { _, _, i, _ ->
            val app = filtered[i]
            store.ensureProfile(app.packageName, pm.getApplicationLabel(app).toString().take(120))
            checkSave(); selectGame(app.packageName); dialog.dismiss()
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        dialog.show()
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
                502 -> (contentResolver.openOutputStream(uri) ?: error("File tidak dapat ditulis")).bufferedWriter().use { it.write(store.toJsonText()) }
                503 -> {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val out = StringBuilder(); val buffer = CharArray(8192)
                        while (true) {
                            val count = reader.read(buffer); if (count < 0) break
                            require(out.length + count <= 2_000_000) { "Backup maksimal 2 MB" }
                            out.append(buffer, 0, count)
                        }; out.toString()
                    } ?: error("File tidak dapat dibaca")
                    val count = ProfileCodec.decode(json).size
                    AlertDialog.Builder(this).setTitle("Impor $count profil?")
                        .setMessage("Cadangan ini akan mengganti seluruh profil yang sekarang tersimpan.")
                        .setPositiveButton("Impor") { _, _ ->
                            runCatching {
                                MapperService.instance?.resetInputs(); store.importJsonText(json)
                                MapperService.instance?.selectProfile(store.activePackage); draw()
                            }.onFailure { toast("Impor gagal: ${it.message}") }
                        }.setNegativeButton("Batal", null).show()
                    return
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

        val fJoy = field("Radius joystick (dp, 60–220)", store.joyRadiusDp.toString())
        val fSensitivity = field("Sensitivitas kamera (persen: 50–600)", (store.mouseSensitivity * 100).toInt().toString())
        val fDead = field("Dead zone gamepad (persen 5–50)", (store.deadZone * 100).toInt().toString())
        AlertDialog.Builder(this)
            .setTitle("Pengaturan input")
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Simpan") { _, _ ->
                fTap.text.toString().toLongOrNull()?.let { store.tapMs = it }
                fJoy.text.toString().toIntOrNull()?.let { store.joyRadiusDp = it }
                fSensitivity.text.toString().toIntOrNull()?.let { store.mouseSensitivity = it / 100f }
                fDead.text.toString().toIntOrNull()?.let { store.deadZone = it / 100f }
                MapperService.instance?.resetInputs()
                draw()
                Toast.makeText(this, "Pengaturan tersimpan ✓", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
