package com.gen.keymapper

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Akar jendela overlay layar penuh. Saat berfokus ia menerima SEMUA tombol keyboard,
 * dan dengan pointer capture ia menerima gerakan mouse relatif, tombol, dan scroll
 * tanpa Accessibility. Sentuhan layar tetap tembus ke game (jendela NOT_TOUCHABLE).
 */
class CaptureLayout(ctx: Context) : FrameLayout(ctx) {

    interface Sink {
        fun onKey(e: KeyEvent): Boolean
        fun onMouseButton(code: Int, down: Boolean): Boolean
        fun onMouseMove(dx: Float, dy: Float)
        fun onWheel(v: Float)
        fun onFocus(has: Boolean)
        fun onCapture(has: Boolean)
    }

    var sink: Sink? = null
    private var lastTx = 0f
    private var lastTy = 0f
    private var haveLast = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val s = sink ?: return super.dispatchKeyEvent(event)
        return s.onKey(event) || super.dispatchKeyEvent(event)
    }

    override fun onCapturedPointerEvent(e: MotionEvent): Boolean {
        val s = sink ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS ->
                s.onMouseButton(KeyNames.fromActionButton(e.actionButton), true)
            MotionEvent.ACTION_BUTTON_RELEASE ->
                s.onMouseButton(KeyNames.fromActionButton(e.actionButton), false)
            MotionEvent.ACTION_SCROLL ->
                s.onWheel(e.getAxisValue(MotionEvent.AXIS_VSCROLL))
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                if ((e.source and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE) {
                    // Mouse: nilai x/y adalah selisih relatif; jumlahkan semua sampel yang dibatch.
                    var dx = e.x
                    var dy = e.y
                    for (i in 0 until e.historySize) {
                        dx += e.getHistoricalX(i)
                        dy += e.getHistoricalY(i)
                    }
                    s.onMouseMove(dx, dy)
                } else {
                    // Touchpad: koordinat absolut, hitung selisih sendiri.
                    if (haveLast) s.onMouseMove(e.x - lastTx, e.y - lastTy)
                    lastTx = e.x
                    lastTy = e.y
                    haveLast = true
                }
            }
            MotionEvent.ACTION_DOWN -> haveLast = false
        }
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        sink?.onFocus(hasWindowFocus)
    }

    override fun onPointerCaptureChange(hasCapture: Boolean) {
        super.onPointerCaptureChange(hasCapture)
        if (!hasCapture) haveLast = false
        sink?.onCapture(hasCapture)
    }
}
