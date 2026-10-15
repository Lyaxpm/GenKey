package com.gen.keymapper

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Satu kanvas penuh layar: menggambar petunjuk tombol, kursor virtual, dan saat mode edit
 * menangani seret-dan-lepas. Sengaja satu View agar ringan dan mudah di-hit-test.
 */
class BoardView(
    ctx: Context,
    private val store: MappingStore,
    private val engine: InputEngine
) : View(ctx) {

    var profile: Profile? = null
    var editing = false
        set(v) {
            field = v
            if (!v) selected = null
            invalidate()
        }
    var paused = false
        set(v) {
            field = v
            invalidate()
        }
    var banner: String? = null
        set(v) {
            field = v
            invalidate()
        }
    var selected: Binding? = null
        set(v) {
            field = v
            invalidate()
        }

    var onSelect: ((Binding?) -> Unit)? = null
    var onChanged: (() -> Unit)? = null
    var onDrag: (() -> Unit)? = null

    private var cachedGeo: Geo? = null
    private val loc = IntArray(2)

    private val d = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val path = Path()
    private val dash = DashPathEffect(floatArrayOf(14f * d, 10f * d), 0f)

    private fun dp(v: Float) = v * d

    fun geo(): Geo? = cachedGeo

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        refreshGeo()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refreshGeo()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshGeo()
    }

    private fun refreshGeo() {
        if (width <= 0 || height <= 0) return
        getLocationOnScreen(loc)
        val g = cachedGeo
        if (g == null || g.w != width || g.h != height || g.ox != loc[0] || g.oy != loc[1]) {
            cachedGeo = Geo(width, height, loc[0], loc[1])
        }
    }

    private fun colorFor(b: Binding): Int = when (b.type) {
        Bt.AIM -> Ui.WARN
        else -> when (b.action) {
            Act.HOLD -> Ui.BLUE
            Act.TOGGLE -> Ui.WARN
            Act.TURBO -> 0xFFFF7AD9.toInt()
            Act.SWIPE -> 0xFFB48CFF.toInt()
            else -> Ui.ACCENT
        }
    }

    // ---------- gambar ----------

    override fun onDraw(c: Canvas) {
        val p = profile
        val w = width.toFloat()
        val h = height.toFloat()
        if (editing) {
            fill.style = Paint.Style.FILL
            fill.color = 0x66000000
            c.drawRect(0f, 0f, w, h, fill)
        }
        if (p != null && (editing || store.showHints)) {
            val alpha = if (editing) 1f else store.hintOpacity
            // Area besar dulu agar chip kecil tampil di atasnya.
            for (b in p.bindings) if (b.type == Bt.JOY) drawJoy(c, b, w, h, alpha)
            for (b in p.bindings) if (b.type == Bt.CAM) drawCam(c, b, w, h, alpha)
            for (b in p.bindings) if (b.type == Bt.KEY || b.type == Bt.AIM) drawChip(c, b, w, h, alpha)
        }
        if (!editing && !paused && !engine.aim && !engine.cameraActive()) drawCursor(c)
        if (!editing && store.showHints) drawBadge(c)
        val bn = banner
        if (bn != null) drawBanner(c, bn, w)
    }

    private fun drawChip(c: Canvas, b: Binding, w: Float, h: Float, alpha: Float) {
        val x = b.x * w
        val y = b.y * h
        val sel = editing && b === selected
        val r = if (editing) dp(17f) else dp(14f)
        val col = colorFor(b)

        if (b.type == Bt.KEY && b.action == Act.SWIPE) {
            val ex = (b.x + b.dx).coerceIn(0f, 1f) * w
            val ey = (b.y + b.dy).coerceIn(0f, 1f) * h
            line.pathEffect = dash
            line.strokeWidth = dp(2f)
            line.color = Ui.withAlpha(col, alpha)
            c.drawLine(x, y, ex, ey, line)
            line.pathEffect = null
            fill.style = Paint.Style.FILL
            fill.color = Ui.withAlpha(col, alpha * 0.9f)
            c.drawCircle(ex, ey, dp(if (editing) 11f else 8f), fill)
            if (editing) {
                txt.color = 0xFF06281A.toInt()
                txt.textSize = dp(9f)
                c.drawText("END", ex, ey + dp(3f), txt)
            }
        }

        fill.style = Paint.Style.FILL
        fill.color = Ui.withAlpha(0xFF10151C.toInt(), alpha * 0.85f)
        c.drawCircle(x, y, r, fill)
        line.strokeWidth = dp(if (sel) 3.5f else 2f)
        line.color = if (sel) 0xFFFFFFFF.toInt() else Ui.withAlpha(col, alpha)
        c.drawCircle(x, y, r, line)

        val label = KeyNames.label(b.key)
        txt.color = Ui.withAlpha(Ui.TEXT, if (editing) 1f else alpha.coerceAtLeast(0.5f))
        var size = dp(if (editing) 10f else 9f)
        txt.textSize = size
        val maxW = r * 1.65f
        val tw = txt.measureText(label)
        if (tw > maxW) {
            size *= maxW / tw
            txt.textSize = size
        }
        if (b.type == Bt.AIM) {
            txt.textSize = dp(10f)
            c.drawText("BIDIK", x, y - dp(2f), txt)
            txt.textSize = min(size, dp(11f))
            c.drawText(label, x, y + dp(11f), txt)
        } else if (editing) {
            c.drawText(label, x, y + size * 0.1f, txt)
            txt.textSize = dp(6.5f)
            txt.color = Ui.withAlpha(col, 1f)
            c.drawText(Act.label(b.action), x, y + r * 0.62f, txt)
        } else {
            c.drawText(label, x, y + size * 0.35f, txt)
        }
    }

    private fun drawJoy(c: Canvas, b: Binding, w: Float, h: Float, alpha: Float) {
        val x = b.x * w
        val y = b.y * h
        val r = b.radius * min(w, h)
        val sel = editing && b === selected
        fill.style = Paint.Style.FILL
        fill.color = Ui.withAlpha(Ui.ACCENT, alpha * 0.10f)
        c.drawCircle(x, y, r, fill)
        line.strokeWidth = dp(if (sel) 3.5f else 2f)
        line.color = if (sel) 0xFFFFFFFF.toInt() else Ui.withAlpha(Ui.ACCENT, alpha)
        c.drawCircle(x, y, r, line)
        fill.color = Ui.withAlpha(Ui.ACCENT, alpha * 0.9f)
        c.drawCircle(x, y, dp(7f), fill)
        txt.color = Ui.withAlpha(Ui.TEXT, alpha.coerceAtLeast(0.55f))
        txt.textSize = dp(13f)
        c.drawText(KeyNames.label(b.up), x, y - r * 0.62f, txt)
        c.drawText(KeyNames.label(b.down), x, y + r * 0.62f + dp(9f), txt)
        c.drawText(KeyNames.label(b.left), x - r * 0.62f, y + dp(5f), txt)
        c.drawText(KeyNames.label(b.right), x + r * 0.62f, y + dp(5f), txt)
        if (editing) {
            txt.textSize = dp(10f)
            txt.color = Ui.ACCENT
            c.drawText("JOYSTICK", x, y + dp(4f) - r * 0.28f, txt)
        }
    }

    private fun drawCam(c: Canvas, b: Binding, w: Float, h: Float, alpha: Float) {
        val x = b.x * w
        val y = b.y * h
        val r = b.radius * min(w, h)
        val sel = editing && b === selected
        line.pathEffect = dash
        line.strokeWidth = dp(if (sel) 3f else 2f)
        line.color = if (sel) 0xFFFFFFFF.toInt() else Ui.withAlpha(Ui.BLUE, alpha)
        c.drawCircle(x, y, r, line)
        line.pathEffect = null
        line.strokeWidth = dp(2f)
        line.color = Ui.withAlpha(Ui.BLUE, alpha)
        c.drawLine(x - dp(12f), y, x + dp(12f), y, line)
        c.drawLine(x, y - dp(12f), x, y + dp(12f), line)
        if (editing || alpha > 0.35f) {
            txt.textSize = dp(10.5f)
            txt.color = Ui.withAlpha(Ui.BLUE, alpha.coerceAtLeast(0.6f))
            val how = if (b.camHold == KeyNames.NONE) "KAMERA (mode bidik)" else "KAMERA (tahan ${KeyNames.label(b.camHold)})"
            c.drawText(how, x, y - r - dp(6f), txt)
        }
    }

    private fun drawCursor(c: Canvas) {
        val (cx, cy) = engine.cursorOrCenter()
        path.reset()
        path.moveTo(cx, cy)
        path.lineTo(cx, cy + dp(20f))
        path.lineTo(cx + dp(5.5f), cy + dp(15f))
        path.lineTo(cx + dp(10f), cy + dp(23f))
        path.lineTo(cx + dp(13f), cy + dp(21.5f))
        path.lineTo(cx + dp(8.5f), cy + dp(13.5f))
        path.lineTo(cx + dp(15f), cy + dp(13.5f))
        path.close()
        fill.style = Paint.Style.FILL
        fill.color = 0xFFFFFFFF.toInt()
        c.drawPath(path, fill)
        line.strokeWidth = dp(1.4f)
        line.color = 0xFF000000.toInt()
        c.drawPath(path, line)
    }

    private fun drawBadge(c: Canvas) {
        val text = when {
            paused -> "JEDA"
            engine.aim -> "BIDIK"
            else -> if (store.mouseOnlyInCursor) "KURSOR · MOUSE" else "KURSOR"
        }
        val col = when {
            paused -> Ui.WARN
            engine.aim -> Ui.DANGER
            else -> Ui.ACCENT
        }
        txt.textSize = dp(10.5f)
        val tw = txt.measureText(text)
        val padX = dp(8f)
        val x0 = dp(10f)
        val y0 = dp(10f)
        fill.style = Paint.Style.FILL
        fill.color = Ui.withAlpha(0xFF0D1015.toInt(), 0.8f)
        c.drawRoundRect(x0, y0, x0 + tw + padX * 2, y0 + dp(22f), dp(11f), dp(11f), fill)
        fill.color = col
        c.drawCircle(x0 + dp(8f), y0 + dp(11f), dp(3f), fill)
        txt.color = col
        c.drawText(text, x0 + padX + dp(6f) + tw / 2f, y0 + dp(15f), txt)
    }

    private fun drawBanner(c: Canvas, text: String, w: Float) {
        txt.textSize = dp(11f)
        val maxText = (w - dp(44f)).coerceAtLeast(dp(20f))
        val measured = txt.measureText(text)
        if (measured > maxText) txt.textSize *= maxText / measured
        val tw = txt.measureText(text)
        val x = w / 2f
        val y = dp(34f)
        fill.style = Paint.Style.FILL
        fill.color = Ui.withAlpha(0xFF0D1015.toInt(), 0.92f)
        c.drawRoundRect(x - tw / 2f - dp(16f), y - dp(20f), x + tw / 2f + dp(16f), y + dp(14f), dp(18f), dp(18f), fill)
        line.strokeWidth = dp(1.5f)
        line.color = Ui.ACCENT
        c.drawRoundRect(x - tw / 2f - dp(16f), y - dp(20f), x + tw / 2f + dp(16f), y + dp(14f), dp(18f), dp(18f), line)
        txt.color = Ui.ACCENT
        c.drawText(text, x, y + dp(2f), txt)
    }

    // ---------- sentuh (mode edit) ----------

    private var dragging: Binding? = null
    private var dragHandle = false
    private var offX = 0f
    private var offY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    private fun hit(x: Float, y: Float): Pair<Binding, Boolean>? {
        val p = profile ?: return null
        val w = width.toFloat()
        val h = height.toFloat()
        // 1) pegangan ujung geser pada kontrol yang dipilih
        val s = selected
        if (s != null && s.type == Bt.KEY && s.action == Act.SWIPE) {
            val ex = (s.x + s.dx).coerceIn(0f, 1f) * w
            val ey = (s.y + s.dy).coerceIn(0f, 1f) * h
            if (hypot(x - ex, y - ey) <= dp(24f)) return s to true
        }
        // Select the nearest chip instead of an overlapping, later-added hit area.
        p.bindings.filter { it.type == Bt.KEY || it.type == Bt.AIM }
            .minByOrNull { hypot(x - it.x * w, y - it.y * h) }
            ?.takeIf { hypot(x - it.x * w, y - it.y * h) <= dp(24f) }
            ?.let { return it to false }
        // 3) area besar (joystick, kamera)
        for (i in p.bindings.indices.reversed()) {
            val b = p.bindings[i]
            if (b.type != Bt.JOY && b.type != Bt.CAM) continue
            if (hypot(x - b.x * w, y - b.y * h) <= b.radius * min(w, h)) return b to false
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editing) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val h = hit(e.x, e.y)
                downX = e.x
                downY = e.y
                moved = false
                if (h != null) {
                    dragging = h.first
                    dragHandle = h.second
                    val b = h.first
                    if (dragHandle) {
                        offX = 0f; offY = 0f
                    } else {
                        offX = e.x - b.x * width
                        offY = e.y - b.y * height
                    }
                } else {
                    dragging = null
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val b = dragging ?: return true
                if (!moved && hypot(e.x - downX, e.y - downY) > dp(4f)) {
                    moved = true
                    onDrag?.invoke()
                }
                if (moved) {
                    val fx = ((e.x - offX) / width).coerceIn(0f, 1f)
                    val fy = ((e.y - offY) / height).coerceIn(0f, 1f)
                    if (dragHandle) {
                        b.dx = (fx - b.x).coerceIn(-1f, 1f)
                        b.dy = (fy - b.y).coerceIn(-1f, 1f)
                    } else {
                        val radius = if (b.type == Bt.KEY || b.type == Bt.AIM) dp(19f) else b.radius * min(width, height)
                        b.x = EditorBounds.center(fx * width, radius, width.toFloat()) / width
                        b.y = EditorBounds.center(fy * height, radius, height.toFloat()) / height
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val b = dragging
                if (b != null) {
                    if (moved) {
                        selected = b
                        onChanged?.invoke()
                    } else {
                        selected = b
                        onSelect?.invoke(b)
                    }
                } else if (!moved && abs(e.x - downX) < dp(8f)) {
                    selected = null
                    onSelect?.invoke(null)
                }
                dragging = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = null
                return true
            }
        }
        return true
    }
}
