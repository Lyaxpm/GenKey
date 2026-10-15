package com.gen.keymapper

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.random.Random

/** Toolbar bawah dan panel properti yang tampil di atas BoardView saat mode edit. */
class EditorUi(
    private val ctx: Context,
    private val board: BoardView,
    private val host: Host
) {
    interface Host {
        fun record(prompt: String, cb: (Int) -> Unit)
        fun finishEdit(save: Boolean)
        fun onDirty()
        fun rootHeight(): Int
    }

    private var profile: Profile? = null
    private var collapsed = false

    val toolbar: View
    val panel: ScrollView
    private val panelBody = LinearLayout(ctx)

    private fun dp(v: Int) = Ui.dp(ctx, v)

    init {
        // Compact floating bar: add controls scroll; save/cancel always remain visible.
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun add(label: String, action: () -> Unit) { row.addView(compact(label, action)) }
        add("+ Tap") { addKey(Act.TAP) }
        add("+ Tahan") { addKey(Act.HOLD) }
        add("+ Toggle") { addKey(Act.TOGGLE) }
        add("+ Turbo") { addKey(Act.TURBO) }
        add("+ Geser") { addKey(Act.SWIPE) }
        add("+ Joystick") { addJoystick() }
        add("+ Kamera") { addCamera() }
        add("+ Bidik") { addAim() }
        val hs = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = true
            addView(row)
        }
        val wrap = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
            background = Ui.round(ctx, 0xF0161B22.toInt(), 16, Ui.STROKE)
            layoutParams = FrameLayout.LayoutParams(dp(420), dp(42), Gravity.TOP or Gravity.LEFT)
                .also { it.leftMargin = dp(8); it.topMargin = dp(56) }
        }
        val grip = compact("⋮⋮") {}
        grip.contentDescription = "Seret toolbar; ketuk untuk lipat atau buka"
        wrap.addView(grip)
        wrap.addView(hs, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        wrap.addView(compact("×") { host.finishEdit(false) }.apply { contentDescription = "Batalkan perubahan" })
        wrap.addView(compact("✓") { host.finishEdit(true) }.apply { contentDescription = "Simpan mapping"; setTextColor(Ui.ACCENT) })
        FloatingEditor.drag(grip, wrap) {
            collapsed = !collapsed
            hs.visibility = if (collapsed) View.GONE else View.VISIBLE
            resize()
        }
        toolbar = wrap

        // ----- panel -----
        panelBody.orientation = LinearLayout.VERTICAL
        panelBody.setPadding(dp(9), dp(8), dp(9), dp(9))
        panel = ScrollView(ctx)
        panel.background = Ui.round(ctx, 0xF2161B22.toInt(), 20, Ui.STROKE)
        panel.addView(panelBody)
        val plp = FrameLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT)
        plp.topMargin = dp(104)
        plp.leftMargin = dp(8)
        panel.layoutParams = plp
        panel.visibility = View.GONE
        board.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resize() }
        board.onDrag = { panel.visibility = View.GONE }
    }

    private fun compact(text: String, action: () -> Unit): TextView = Ui.button(ctx, text, onClick = action).apply {
        textSize = 11f
        setPadding(dp(8), dp(4), dp(8), dp(4))
        minHeight = dp(34)
        contentDescription = text
    }

    private fun resize() {
        val parent = toolbar.parent as? View ?: return
        val safe = FloatingEditor.safe(parent)
        val available = (safe.width() - dp(12)).coerceAtLeast(dp(120))
        toolbar.layoutParams = (toolbar.layoutParams as FrameLayout.LayoutParams).also {
            it.width = minOf(dp(if (collapsed) 120 else 420), available)
        }
        panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).also {
            it.width = minOf(dp(220), available)
            it.height = minOf(dp(300), (safe.height() * 0.65f).toInt()).coerceAtLeast(dp(48))
        }
        toolbar.post { FloatingEditor.constrain(toolbar); FloatingEditor.constrain(panel) }
    }

    fun attach(p: Profile) {
        profile = p
        board.profile = p
        select(null)
    }

    // ---------- tambah kontrol ----------

    private fun jitter() = Random.nextFloat() * 0.08f - 0.04f

    private fun addKey(action: String) {
        val p = profile ?: return
        val b = Binding(type = Bt.KEY, action = action, x = 0.5f + jitter(), y = 0.5f + jitter())
        if (action == Act.SWIPE) {
            b.dx = 0.12f
            b.dy = 0f
        }
        p.bindings.add(b)
        host.onDirty()
        select(b)
        host.record("Tekan tombol untuk kontrol ini") {
            b.key = it
            host.onDirty()
            select(b)
        }
    }

    private fun addJoystick() {
        val p = profile ?: return
        val existing = p.bindings.firstOrNull { it.type == Bt.JOY }
        if (existing != null) {
            select(existing)
            return
        }
        val b = Binding(
            type = Bt.JOY, x = 0.17f, y = 0.72f, radius = 0.15f,
            up = KeyEvent.KEYCODE_W, down = KeyEvent.KEYCODE_S,
            left = KeyEvent.KEYCODE_A, right = KeyEvent.KEYCODE_D
        )
        p.bindings.add(b)
        host.onDirty()
        select(b)
    }

    private fun addCamera() {
        val p = profile ?: return
        val existing = p.bindings.firstOrNull { it.type == Bt.CAM }
        if (existing != null) {
            select(existing)
            return
        }
        val b = Binding(type = Bt.CAM, x = 0.72f, y = 0.45f, radius = 0.28f)
        p.bindings.add(b)
        host.onDirty()
        select(b)
    }

    private fun addAim() {
        val p = profile ?: return
        val b = Binding(type = Bt.AIM, x = 0.08f, y = 0.30f)
        p.bindings.add(b)
        host.onDirty()
        select(b)
        host.record("Tombol untuk berpindah Kursor/Bidik") {
            b.key = it
            host.onDirty()
            select(b)
        }
    }

    // ---------- panel ----------

    fun select(b: Binding?) {
        board.selected = b
        if (b == null) {
            panel.visibility = View.GONE
            return
        }
        rebuild(b)
        panel.visibility = View.VISIBLE
        resize()
        panel.post {
            val parent = panel.parent as? View ?: return@post
            val safe = FloatingEditor.safe(parent)
            // Open opposite the selected control, leaving its drag origin exposed.
            panel.x = if (b.x > 0.5f) (safe.left + dp(8)).toFloat()
                else (safe.right - panel.width - dp(8)).toFloat()
            panel.y = (safe.top + dp(8)).toFloat()
            FloatingEditor.constrain(panel)
        }
    }

    private fun typeTitle(b: Binding) = when (b.type) {
        Bt.JOY -> "Joystick (analog)"
        Bt.CAM -> "Kamera (mouse-look)"
        Bt.AIM -> "Tombol Mode Bidik"
        else -> "Tombol → Sentuhan"
    }

    private fun rebuild(b: Binding) {
        panelBody.removeAllViews()
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val t = Ui.text(ctx, typeTitle(b), 15f, Ui.TEXT, true)
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        t.text = "⋮⋮  " + t.text
        t.setPadding(0, dp(6), 0, dp(6))
        FloatingEditor.drag(t, panel)
        head.addView(t)
        head.addView(compact("×") { select(null) })
        panelBody.addView(head)

        when (b.type) {
            Bt.KEY -> buildKey(b)
            Bt.JOY -> buildJoy(b)
            Bt.CAM -> buildCam(b)
            Bt.AIM -> buildAim(b)
        }

        panelBody.addView(Ui.spacer(ctx, 10))
        val actions = LinearLayout(ctx)
        actions.orientation = LinearLayout.HORIZONTAL
        if (b.type == Bt.KEY) {
            val dup = Ui.button(ctx, "Duplikat") {
                val p = profile
                if (p != null) {
                    val c = b.copyWithNewId()
                    c.x = (b.x + 0.04f).coerceIn(0f, 1f)
                    c.y = (b.y + 0.06f).coerceIn(0f, 1f)
                    c.key = KeyNames.NONE
                    p.bindings.add(c)
                    host.onDirty()
                    select(c)
                    host.record("Tekan tombol untuk salinan") {
                        c.key = it
                        host.onDirty()
                        select(c)
                    }
                }
            }
            dup.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.rightMargin = dp(6) }
            actions.addView(dup)
        }
        val del = Ui.button(ctx, "Hapus", danger = true) {
            profile?.bindings?.remove(b)
            host.onDirty()
            select(null)
        }
        del.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        actions.addView(del)
        panelBody.addView(actions)
    }

    private fun keyRow(label: String, code: Int, onPick: (Int) -> Unit, b: Binding): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = Ui.lp().also { it.topMargin = dp(8) }
        val l = Ui.text(ctx, label, 13f, Ui.TEXT)
        l.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(l)
        row.addView(Ui.button(ctx, KeyNames.label(code) + "  ✎", filled = code == KeyNames.NONE) {
            host.record("Tekan tombol untuk: $label") {
                onPick(it)
                host.onDirty()
                select(b)
            }
        })
        return row
    }

    private fun seg(labels: List<String>, sel: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = Ui.lp().also { it.topMargin = dp(6) }
        labels.forEachIndexed { i, s ->
            val tv = Ui.text(ctx, s, 11.5f, if (i == sel) 0xFF06281A.toInt() else Ui.TEXT, true)
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(4), dp(8), dp(4), dp(8))
            tv.background = Ui.round(ctx, if (i == sel) Ui.ACCENT else Ui.CARD2, 10, if (i == sel) 0 else Ui.STROKE)
            tv.isClickable = true
            tv.setOnClickListener { onPick(i) }
            tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.rightMargin = if (i < labels.size - 1) dp(4) else 0 }
            row.addView(tv)
        }
        return row
    }

    private fun label(s: String): TextView {
        val t = Ui.text(ctx, s, 12f, Ui.SUB)
        t.layoutParams = Ui.lp().also { it.topMargin = dp(10) }
        return t
    }

    private fun buildKey(b: Binding) {
        panelBody.addView(keyRow("Tombol pemicu", b.key, { b.key = it }, b))
        panelBody.addView(label("Aksi"))
        val idx = Act.all.indexOf(b.action).coerceAtLeast(0)
        panelBody.addView(seg(Act.all.map { Act.label(it) }, idx) {
            b.action = Act.all[it]
            if (b.action == Act.SWIPE && b.dx == 0f && b.dy == 0f) b.dy = -0.15f
            host.onDirty()
            select(b)
        })
        val hint = when (b.action) {
            Act.TAP -> "Satu ketukan singkat tiap kali ditekan."
            Act.HOLD -> "Jari tetap menekan selama tombol ditahan."
            Act.TOGGLE -> "Tekan sekali untuk menahan, tekan lagi untuk melepas."
            Act.TURBO -> "Mengetuk berulang selama tombol ditahan."
            else -> "Geser dari titik ke titik END. Seret titik END untuk mengatur arah."
        }
        panelBody.addView(Ui.sub(ctx, hint).also { it.layoutParams = Ui.lp().also { l -> l.topMargin = dp(4) } })
        if (b.action == Act.TURBO) {
            panelBody.addView(Ui.slider(ctx, "Interval", 30f, 300f, b.turboMs.toFloat(), { "${it.toInt()} ms" }) {
                b.turboMs = it.toInt()
                host.onDirty()
            })
        }
        if (b.action == Act.SWIPE) {
            panelBody.addView(Ui.slider(ctx, "Durasi geser", 80f, 1000f, b.durMs.toFloat(), { "${it.toInt()} ms" }) {
                b.durMs = it.toInt()
                host.onDirty()
            })
        }
        panelBody.addView(label("Aktif pada"))
        panelBody.addView(seg(listOf("Selalu", "Bidik", "Kursor"), b.scope) {
            b.scope = it
            host.onDirty()
            select(b)
        })
    }

    private fun buildJoy(b: Binding) {
        panelBody.addView(keyRow("Atas", b.up, { b.up = it }, b))
        panelBody.addView(keyRow("Bawah", b.down, { b.down = it }, b))
        panelBody.addView(keyRow("Kiri", b.left, { b.left = it }, b))
        panelBody.addView(keyRow("Kanan", b.right, { b.right = it }, b))
        panelBody.addView(Ui.slider(ctx, "Radius", 0.05f, 0.40f, b.radius, { "${(it * 100).toInt()}%" }) {
            b.radius = it
            board.invalidate()
            host.onDirty()
        })
        panelBody.addView(Ui.sub(ctx, "Geser lingkaran ke pusat joystick di game.").also {
            it.layoutParams = Ui.lp().also { l -> l.topMargin = dp(6) }
        })
    }

    private fun buildCam(b: Binding) {
        panelBody.addView(Ui.slider(ctx, "Radius area", 0.10f, 0.50f, b.radius, { "${(it * 100).toInt()}%" }) {
            b.radius = it
            board.invalidate()
            host.onDirty()
        })
        panelBody.addView(Ui.slider(ctx, "Sensitivitas", 0.2f, 3f, b.sens, { String.format("%.2fx", it) }) {
            b.sens = it
            host.onDirty()
        })
        panelBody.addView(label("Kamera aktif saat"))
        val sel = if (b.camHold == KeyNames.NONE) 0 else 1
        panelBody.addView(seg(listOf("Mode Bidik", "Tombol ditahan"), sel) {
            if (it == 0) {
                b.camHold = KeyNames.NONE
                host.onDirty()
                select(b)
            } else {
                host.record("Tombol yang ditahan untuk memutar kamera") { code ->
                    b.camHold = code
                    host.onDirty()
                    select(b)
                }
            }
        })
        if (b.camHold != KeyNames.NONE) {
            panelBody.addView(keyRow("Tombol tahan", b.camHold, { b.camHold = it }, b))
        }
        panelBody.addView(Ui.sub(ctx, "Taruh lingkaran di area geser kamera pada game, jauhkan dari tombol lain.").also {
            it.layoutParams = Ui.lp().also { l -> l.topMargin = dp(6) }
        })
    }

    private fun buildAim(b: Binding) {
        panelBody.addView(keyRow("Tombol", b.key, { b.key = it }, b))
        panelBody.addView(Ui.sub(ctx, "Menekan tombol ini berpindah antara Mode Kursor dan Mode Bidik.").also {
            it.layoutParams = Ui.lp().also { l -> l.topMargin = dp(6) }
        })
    }
}
