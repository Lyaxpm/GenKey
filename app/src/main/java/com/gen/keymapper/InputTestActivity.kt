package com.gen.keymapper

import android.app.Activity
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*

/** Receives real injected MotionEvents; accepted Binder calls alone are not treated as a pass. */
class InputTestActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var received = 0
    private var maxPointers = 0
    private lateinit var status: TextView
    private lateinit var pad: View
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapperService.instance?.pause()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 36, 24, 24); setBackgroundColor(Color.rgb(12, 16, 29))
        }
        box.fitsSystemWindows = true
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; text = "Uji penerimaan input\nBelum ada event. Ketuk tes setelah Shizuku terhubung." }
        box.addView(status)
        pad = object : View(this) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val points = mutableListOf<Pair<Float, Float>>()
            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.rgb(26, 32, 52))
                paint.color = Color.rgb(105, 225, 206); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                canvas.drawCircle(width / 2f, height / 2f, 65f, paint)
                paint.style = Paint.Style.FILL
                points.forEach { canvas.drawCircle(it.first, it.second, 24f, paint) }
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                received++; maxPointers = maxOf(maxPointers, e.pointerCount)
                points.clear()
                if (e.actionMasked != MotionEvent.ACTION_UP && e.actionMasked != MotionEvent.ACTION_CANCEL)
                    for (i in 0 until e.pointerCount) points.add(e.getX(i) to e.getY(i))
                status.text = "Event diterima: $received · Maks. jari: $maxPointers\n${MotionEvent.actionToString(e.action)}"
                invalidate(); return true
            }
        }
        box.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 24; bottomMargin = 16 })
        fun button(label: String, action: () -> Unit) {
            box.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        button("Tes tap Shizuku") { test(false) }
        button("Tes multitouch 2 jari") { test(true) }
        button("Kembali") { finish() }
        setContentView(box)
    }
    private fun test(multi: Boolean) {
        if (!ShizukuBridge.isReady()) { status.text = "Hubungkan Shizuku dahulu di dashboard."; return }
        main.removeCallbacksAndMessages(null); ShizukuBridge.releaseAll(); ShizukuBridge.heartbeat()
        received = 0; maxPointers = 0
        val origin = IntArray(2); pad.getLocationOnScreen(origin)
        val x = origin[0] + pad.width / 2f; val y = origin[1] + pad.height / 2f
        if (!multi) ShizukuBridge.tap(x, y, 80)
        else {
            ShizukuBridge.pointer(900001, MotionEvent.ACTION_DOWN, x - 55, y)
            ShizukuBridge.pointer(900002, MotionEvent.ACTION_DOWN, x + 55, y)
            main.postDelayed({ ShizukuBridge.pointer(900001, MotionEvent.ACTION_MOVE, x - 55, y + 40) }, 150)
            main.postDelayed({ ShizukuBridge.pointer(900002, MotionEvent.ACTION_UP, x + 55, y) }, 350)
            main.postDelayed({ ShizukuBridge.pointer(900001, MotionEvent.ACTION_UP, x - 55, y + 40) }, 450)
        }
        main.postDelayed({
            status.text = if (received > 0 && (!multi || maxPointers >= 2))
                "LULUS · $received event, maks. $maxPointers jari\nSentuhan benar-benar diterima layar tes."
            else "BELUM LULUS · $received event, maks. $maxPointers jari\n${ShizukuBridge.diagnostics}"
        }, 900)
    }
    override fun onPause() {
        main.removeCallbacksAndMessages(null); ShizukuBridge.releaseAll(); super.onPause()
    }
}
