package com.gen.keymapper

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Tema gelap bergaya gaming, dibangun lewat kode (tanpa XML layout). */
object Ui {
    const val BG = 0xFF0D1015.toInt()
    const val CARD = 0xFF161B22.toInt()
    const val CARD2 = 0xFF1E2430.toInt()
    const val STROKE = 0xFF2B3340.toInt()
    const val TEXT = 0xFFE9EDF2.toInt()
    const val SUB = 0xFF8D98A8.toInt()
    const val ACCENT = 0xFF3DDC97.toInt()
    const val ACCENT_DIM = 0xFF1F6B4A.toInt()
    const val BLUE = 0xFF5B8CFF.toInt()
    const val WARN = 0xFFFFB020.toInt()
    const val DANGER = 0xFFFF5C6C.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun round(ctx: Context, color: Int, radiusDp: Int, stroke: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dpf(ctx, radiusDp.toFloat())
        d.setColor(color)
        if (stroke != 0) d.setStroke(dp(ctx, 1), stroke)
        return d
    }

    fun ripple(ctx: Context, content: GradientDrawable): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, null)

    fun text(ctx: Context, s: CharSequence, sp: Float = 14f, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s
        t.setTextColor(color)
        t.textSize = sp
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(w, h)

    fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = round(ctx, CARD, 18, STROKE)
        val p = dp(ctx, 16)
        c.setPadding(p, p, p, p)
        val lp = lp()
        lp.topMargin = dp(ctx, 12)
        c.layoutParams = lp
        return c
    }

    fun title(ctx: Context, s: String): TextView {
        val t = text(ctx, s, 16f, TEXT, true)
        t.layoutParams = lp().also { it.bottomMargin = dp(ctx, 8) }
        return t
    }

    fun sub(ctx: Context, s: CharSequence): TextView = text(ctx, s, 12.5f, SUB)

    /** Tombol pil. filled = tombol utama (hijau), selain itu tombol outline. */
    fun button(ctx: Context, label: String, filled: Boolean = false, danger: Boolean = false, onClick: () -> Unit): TextView {
        val t = text(ctx, label, 13f, if (filled) 0xFF06281A.toInt() else if (danger) DANGER else TEXT, true)
        t.gravity = Gravity.CENTER
        val ph = dp(ctx, 14)
        val pv = dp(ctx, 8)
        t.setPadding(ph, pv, ph, pv)
        val bg = when {
            filled -> round(ctx, ACCENT, 20)
            danger -> round(ctx, 0x22FF5C6C, 20, DANGER)
            else -> round(ctx, CARD2, 20, STROKE)
        }
        t.background = ripple(ctx, bg)
        t.isClickable = true
        t.setOnClickListener { onClick() }
        return t
    }

    fun spacer(ctx: Context, hDp: Int): View {
        val v = View(ctx)
        v.layoutParams = lp(h = dp(ctx, hDp))
        return v
    }

    fun divider(ctx: Context): View {
        val v = View(ctx)
        v.setBackgroundColor(STROKE)
        v.layoutParams = lp(h = 1).also {
            it.topMargin = dp(ctx, 10)
            it.bottomMargin = dp(ctx, 10)
        }
        return v
    }

    fun dot(ctx: Context, color: Int, sizeDp: Int = 10): View {
        val v = View(ctx)
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        v.background = d
        v.layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp)).also {
            it.rightMargin = dp(ctx, 10)
        }
        return v
    }

    /** Slider dengan label nilai; rentang float dipetakan ke 0..1000. */
    fun slider(
        ctx: Context, label: String, min: Float, max: Float, value: Float,
        fmt: (Float) -> String, onChange: (Float) -> Unit
    ): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.layoutParams = lp().also { it.topMargin = dp(ctx, 8) }
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val l = text(ctx, label, 13f, TEXT)
        l.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val v = text(ctx, fmt(value), 13f, ACCENT, true)
        row.addView(l)
        row.addView(v)
        val sb = SeekBar(ctx)
        sb.max = 1000
        sb.progress = (((value - min) / (max - min)) * 1000f).toInt().coerceIn(0, 1000)
        sb.progressTintList = ColorStateList.valueOf(ACCENT)
        sb.thumbTintList = ColorStateList.valueOf(ACCENT)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val f = min + (max - min) * (p / 1000f)
                v.text = fmt(f)
                if (fromUser) onChange(f)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(row)
        box.addView(sb)
        return box
    }

    fun withAlpha(color: Int, a: Float): Int =
        Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
