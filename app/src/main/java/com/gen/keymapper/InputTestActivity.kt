package com.gen.keymapper

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Verifies real MotionEvents, without permanently disabling mappingEnabled.
 * Unlike MapperService.pause(), resetInputs() does not change saved settings.
 */
class InputTestActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var received = 0
    private var maxPointers = 0
    private lateinit var status: TextView
    private lateinit var pad: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapperService.instance?.resetInputs()
        MapperService.instance?.suspendForDashboard()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 36, 24, 24)
            setBackgroundColor(Color.rgb(12, 16, 29))
            fitsSystemWindows = true
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            text = "Tes injeksi Shizuku\nBelum ada sentuhan yang diterima."
        }
        box.addView(status)
        pad = object : View(this) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val touches = mutableListOf<Pair<Float, Float>>()
            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.rgb(26, 32, 52))
                paint.color = Color.rgb(105, 225, 206)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawCircle(width / 2f, height / 2f, 65f, paint)
                paint.style = Paint.Style.FILL
                touches.forEach { canvas.drawCircle(it.first, it.second, 24f, paint) }
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                received++
                maxPointers = maxOf(maxPointers, event.pointerCount)
                touches.clear()
                if (event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL) {
                    for (i in 0 until event.pointerCount) touches.add(event.getX(i) to event.getY(i))
                }
                status.text = "Event: $received · Jari terbanyak: $maxPointers\n${MotionEvent.actionToString(event.action)}"
                invalidate()
                return true
            }
        }
        box.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = 24
            bottomMargin = 16
        })
        fun button(label: String, onClick: () -> Unit) {
            box.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick() }
            })
        }
        button("Tes tap Shizuku") { test(false) }
        button("Tes multitouch 2 jari") { test(true) }
        button("Kembali") { finish() }
        setContentView(box)
    }

    private fun test(multi: Boolean) {
        if (!ShizukuBridge.isReady()) {
            status.text = "Shizuku belum siap.\n${ShizukuBridge.diagnostics}"
            return
        }
        main.removeCallbacksAndMessages(null)
        ShizukuBridge.releaseAll()
        ShizukuBridge.heartbeat()
        received = 0
        maxPointers = 0
        val origin = IntArray(2)
        pad.getLocationOnScreen(origin)
        val x = origin[0] + pad.width / 2f
        val y = origin[1] + pad.height / 2f
        if (!multi) {
            ShizukuBridge.tap(x, y, 80)
        } else {
            ShizukuBridge.pointer(900001, MotionEvent.ACTION_DOWN, x - 55, y)
            ShizukuBridge.pointer(900002, MotionEvent.ACTION_DOWN, x + 55, y)
            main.postDelayed({ ShizukuBridge.pointer(900001, MotionEvent.ACTION_MOVE, x - 55, y + 40) }, 150)
            main.postDelayed({ ShizukuBridge.pointer(900002, MotionEvent.ACTION_UP, x + 55, y) }, 350)
            main.postDelayed({ ShizukuBridge.pointer(900001, MotionEvent.ACTION_UP, x - 55, y + 40) }, 450)
        }
        main.postDelayed({
            status.text = if (received > 0 && (!multi || maxPointers >= 2)) {
                "LULUS · $received event · $maxPointers jari.\nSentuhan berhasil diterima layar tes."
            } else {
                "GAGAL · $received event · $maxPointers jari.\n${ShizukuBridge.diagnostics}"
            }
        }, 950)
    }

    override fun onPause() {
        main.removeCallbacksAndMessages(null)
        ShizukuBridge.releaseAll()
        super.onPause()
    }
}
