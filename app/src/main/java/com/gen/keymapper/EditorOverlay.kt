package com.gen.keymapper

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Editor mapping melayang (fullscreen, transparan):
 * - Chip tombol bisa di-drag ke posisi, tap untuk pasang tombol keyboard/mouse/gamepad
 * - Chip joystick: tap untuk isi 4 arah (WASD)
 * - Toolbar bawah: tambah Tap / Tahan / Geser / Joystick, Simpan, Tutup
 */
class EditorOverlay(
    private val svc: MapperService,
    private val profile: Profile,
    private val onClose: () -> Unit
) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val overlayType =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private var root: FrameLayout? = null
    private var assigningId: String? = null

    private fun dp(v: Int): Int = (v * svc.resources.displayMetrics.density).toInt()

    private fun chipBg(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(2), Color.parseColor("#9E9E9E"))
    }

    fun show() {
        if (root != null || !Settings.canDrawOverlays(svc)) return
        val dm = svc.resources.displayMetrics
        val r = FrameLayout(svc).apply {
            setBackgroundColor(Color.argb(70, 0, 0, 0))
        }
        r.addView(object : View(svc) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#80E1F5FE")
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                profile.keys.filter { it.action == "swipe" }.forEach { map ->
                    val x = map.x * width
                    val y = map.y * height
                    canvas.drawLine(x, y, (map.x + map.dx) * width,
                        (map.y + map.dy) * height, paint)
                }
            }
        }.apply { tag = "mappingLines" }, FrameLayout.LayoutParams(-1, -1))

        // Judul
        val title = TextView(svc).apply {
            text = "✏️ Edit: ${profile.name}\nTap chip lalu tekan tombol • Drag untuk pindah"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(180, 20, 20, 20))
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        r.addView(title, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // Toolbar bawah (geser horizontal biar muat banyak tombol)
        val bar = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(200, 20, 20, 20))
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        val hbar = android.widget.HorizontalScrollView(svc).apply { addView(bar) }
        r.addView(hbar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM))

        fun toolBtn(text: String, onClick: () -> Unit) {
            bar.addView(Button(svc).apply {
                this.text = text
                textSize = 12f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2E7D32")); cornerRadius = dp(8).toFloat()
                }
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                leftMargin = dp(4); rightMargin = dp(4)
            })
        }
        toolBtn("+ Tap") { addKey("tap") }
        toolBtn("+ Tahan") { addKey("hold") }
        toolBtn("+ Geser") { addKey("swipe") }
        toolBtn("+ Joystick") { addJoy() }
        toolBtn("+ Wheel↑") { addWheel(KeyNames.MOUSE_SCROLL_UP) }
        toolBtn("+ Wheel↓") { addWheel(KeyNames.MOUSE_SCROLL_DOWN) }
        toolBtn("+ Kamera") { profile.camera = CameraMap(); svc.store.save(); refreshChips() }
        toolBtn("💾") {
            svc.store.save()
            Toast.makeText(svc, "Mapping tersimpan ✓", Toast.LENGTH_SHORT).show()
        }
        toolBtn("✖ Tutup") {
            svc.store.save()
            hide(); onClose()
        }

        root = r
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(r, params)
        } catch (_: Exception) {
            root = null; return
        }
        refreshChips()
    }

    fun hide() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
    }

    private fun addKey(action: String) {
        profile.keys.add(KeyMap(action = action, x = 0.5f, y = 0.4f))
        svc.store.save()
        refreshChips()
        Toast.makeText(svc, "Chip baru: tap lalu tekan tombol", Toast.LENGTH_SHORT).show()
    }

    private fun addWheel(code: Int) {
        profile.keys.add(KeyMap(keyCode = code, action = "tap", x = 0.5f, y = 0.4f))
        svc.store.save()
        refreshChips()
    }

    private fun addJoy() {
        profile.joys.add(JoyMap())
        svc.store.save()
        refreshChips()
        Toast.makeText(svc, "Joystick baru: tap untuk isi WASD", Toast.LENGTH_SHORT).show()
    }

    private fun refreshChips() {
        val r = root ?: return
        // hapus chip lama (tag = "chip")
        val toRemove = mutableListOf<View>()
        for (i in 0 until r.childCount) {
            val c = r.getChildAt(i)
            if (c.tag == "chip") toRemove.add(c)
        }
        toRemove.forEach { r.removeView(it) }
        profile.keys.forEach { map ->
            addKeyChip(r, map)
            if (map.action == "swipe") addSwipeEndChip(r, map)
        }
        r.findViewWithTag<View>("mappingLines")?.invalidate()
        profile.joys.forEach { addJoyChip(r, it) }
        profile.camera?.let { addCameraChip(r, it) }
    }

    private fun screenWH(): Pair<Int, Int> {
        val dm = svc.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    // ---------- chip tombol ----------

    private fun addKeyChip(root: FrameLayout, km: KeyMap) {
        val (w, h) = screenWH()
        val size = dp(64)
        val chip = FrameLayout(svc).apply {
            tag = "chip"
            background = chipBg(
                if (assigningId == km.id) Color.parseColor("#B71C1C")
                else Color.parseColor("#CC1565C0")
            )
        }
        val label = TextView(svc).apply {
            text = if (assigningId == km.id) "…"
            else "${KeyNames.label(km.keyCode)}\n${km.actionLabel()}"
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
        }
        chip.addView(label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val del = TextView(svc).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor("#D32F2F"))
            }
            setOnClickListener {
                profile.keys.remove(km); svc.store.save(); refreshChips()
            }
        }
        val ds = dp(22)
        chip.addView(del, FrameLayout.LayoutParams(ds, ds, Gravity.TOP or Gravity.END))

        val lp = FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (km.x * w - size / 2).toInt().coerceIn(0, w - size)
            topMargin = (km.y * h - size / 2).toInt().coerceIn(0, h - size)
        }
        makeDraggable(chip, lp, w, h,
            onTap = { beginAssignKey(km) },
            onDrop = { nx, ny ->
                km.x = nx; km.y = ny; svc.store.save()
                root.findViewWithTag<View>("mappingLines")?.invalidate()
            })
        root.addView(chip, lp)
    }

    private fun addCameraChip(root: FrameLayout, camera: CameraMap) {
        val (w, h) = screenWH()
        val size = dp(76)
        val chip = FrameLayout(svc).apply {
            tag = "chip"
            background = chipBg(Color.parseColor("#AA6A1B9A"))
        }
        val label = TextView(svc).apply {
            text = "🖱\nCAM"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 11f
        }
        chip.addView(label, FrameLayout.LayoutParams(-1, -1))
        val del = TextView(svc).apply {
            text = "×"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setOnClickListener { profile.camera = null; svc.store.save(); refreshChips() }
        }
        chip.addView(del, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END))
        val lp = FrameLayout.LayoutParams(size, size).apply {
            leftMargin = (camera.x * w - size / 2).toInt().coerceIn(0, (w - size).coerceAtLeast(0))
            topMargin = (camera.y * h - size / 2).toInt().coerceIn(0, (h - size).coerceAtLeast(0))
        }
        makeDraggable(chip, lp, w, h,
            onTap = { Toast.makeText(svc, "Area swipe kamera. Geser ke sisi game yang dipakai untuk melihat.", Toast.LENGTH_LONG).show() },
            onDrop = { x, y -> camera.x = x; camera.y = y; svc.store.save() })
        root.addView(chip, lp)
    }

    private fun addSwipeEndChip(root: FrameLayout, map: KeyMap) {
        val (w, h) = screenWH()
        val size = dp(42)
        val endChip = TextView(svc).apply {
            text = "END"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = chipBg(Color.parseColor("#CC00838F"))
            tag = "chip"
        }
        val lp = FrameLayout.LayoutParams(size, size).apply {
            leftMargin = ((map.x + map.dx) * w - size / 2).toInt().coerceIn(0, (w - size).coerceAtLeast(0))
            topMargin = ((map.y + map.dy) * h - size / 2).toInt().coerceIn(0, (h - size).coerceAtLeast(0))
        }
        makeDraggable(endChip, lp, w, h,
            onTap = { Toast.makeText(svc, "Drag END untuk mengatur arah swipe", Toast.LENGTH_SHORT).show() },
            onDrop = { x, y ->
                map.dx = (x - map.x).coerceIn(-1f, 1f)
                map.dy = (y - map.y).coerceIn(-1f, 1f)
                svc.store.save()
                root.findViewWithTag<View>("mappingLines")?.invalidate()
            })
        root.addView(endChip, lp)
    }

    // ---------- chip joystick ----------

    private fun addJoyChip(root: FrameLayout, jm: JoyMap) {
        val (w, h) = screenWH()
        val size = dp(104)
        val chip = FrameLayout(svc).apply {
            tag = "chip"
            background = chipBg(
                if (assigningId?.startsWith(jm.id) == true) Color.parseColor("#B71C1C")
                else Color.parseColor("#CC2E7D32")
            )
        }
        val filled = listOf(jm.up, jm.down, jm.left, jm.right).count { it >= 0 }
        val label = TextView(svc).apply {
            text = "🕹\n$filled/4"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
        }
        chip.addView(label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val del = TextView(svc).apply {
            text = "✕"
            setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor("#D32F2F"))
            }
            setOnClickListener {
                profile.joys.remove(jm); svc.store.save(); refreshChips()
            }
        }
        val ds = dp(22)
        chip.addView(del, FrameLayout.LayoutParams(ds, ds, Gravity.TOP or Gravity.END))

        val lp = FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (jm.x * w - size / 2).toInt().coerceIn(0, w - size)
            topMargin = (jm.y * h - size / 2).toInt().coerceIn(0, h - size)
        }
        makeDraggable(chip, lp, w, h,
            onTap = { showJoySlots(jm) },
            onDrop = { nx, ny -> jm.x = nx; jm.y = ny; svc.store.save() })
        root.addView(chip, lp)
    }

    /** Drag & tap untuk chip. */
    private fun makeDraggable(
        chip: View, lp: FrameLayout.LayoutParams, w: Int, h: Int,
        onTap: () -> Unit, onDrop: (Float, Float) -> Unit
    ) {
        var downX = 0f; var downY = 0f
        var startL = 0; var startT = 0
        var moved = false
        val size = lp.width
        chip.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startL = lp.leftMargin; startT = lp.topMargin
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (kotlin.math.abs(dx) > dp(8) || kotlin.math.abs(dy) > dp(8)) moved = true
                    if (moved) {
                        lp.leftMargin = (startL + dx).toInt().coerceIn(0, w - size)
                        lp.topMargin = (startT + dy).toInt().coerceIn(0, h - size)
                        chip.layoutParams = lp
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            true
        }
        // simpan posisi akhir saat drag selesai
        chip.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int,
                                        ol: Int, ot: Int, orr: Int, ob: Int) {
                if (moved) {
                    onDrop(((lp.leftMargin + size / 2).toFloat() / w),
                        ((lp.topMargin + size / 2).toFloat() / h))
                    moved = false
                }
            }
        })
    }

    // ---------- assign tombol ----------

    private fun beginAssignKey(km: KeyMap) {
        val options = arrayOf("Rekam tombol keyboard / gamepad", "Mouse Kiri (M1)",
            "Mouse Kanan (M2)", "Mouse Tengah (M3)", "Mouse Samping (M4)", "Mouse Samping (M5)")
        val dlg = AlertDialog.Builder(svc)
            .setTitle("Atur kontrol")
            .setItems(options) { _, which ->
                if (which == 0) {
                    assigningId = km.id
                    refreshChips()
                    svc.assignTarget = AssignTarget("key", km.id, null)
                    svc.onAssign = { code ->
                        km.keyCode = code
                        assigningId = null
                        svc.store.save()
                        refreshChips()
                    }
                    Toast.makeText(svc, "Tekan tombol keyboard / gamepad sekarang", Toast.LENGTH_LONG).show()
                } else {
                    km.keyCode = android.view.KeyEvent.KEYCODE_BUTTON_1 + which - 1
                    svc.store.save()
                    refreshChips()
                }
            }
            .setNegativeButton("Batal", null).create()
        dlg.window?.setType(overlayType)
        dlg.show()
    }

    private fun beginAssignJoySlot(jm: JoyMap, slot: String) {
        assigningId = jm.id + slot
        refreshChips()
        Toast.makeText(svc, "Tekan tombol untuk arah ${jm.slotLabel(slot)}…", Toast.LENGTH_SHORT).show()
        svc.assignTarget = AssignTarget("joy", jm.id, slot)
        svc.onAssign = { kc ->
            jm.setSlot(slot, kc)
            assigningId = null
            svc.store.save()
            refreshChips()
            Toast.makeText(svc, "${jm.slotLabel(slot)} = ${KeyNames.describe(kc)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showJoySlots(jm: JoyMap) {
        val slots = listOf("up", "down", "left", "right")
        val items = slots.map { s ->
            val cur = jm.slotKey(s)
            "${jm.slotLabel(s)} : ${if (cur >= 0) KeyNames.label(cur) else "—"}"
        }.toTypedArray()
        val dlg = AlertDialog.Builder(svc)
            .setTitle("Joystick — pilih arah untuk dipasang")
            .setItems(items) { _, which -> beginAssignJoySlot(jm, slots[which]) }
            .setNegativeButton("Tutup", null)
            .create()
        try {
            dlg.window?.setType(overlayType)
        } catch (_: Exception) {
        }
        dlg.show()
    }
}
