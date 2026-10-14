package com.gen.keymapper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Focus-based keyboard capture: not a global keyboard hook. */
class PlayOverlay(private val svc: MapperService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private var sink: InputSink? = null
    private var control: TextView? = null
    private var popup: View? = null
    private var previews: View? = null
    private var captureRequested = false
    var aimMode = false
        private set

    private fun dp(n: Int) = (n * svc.resources.displayMetrics.density).toInt()
    private fun background(fill: String, radius: Int = 16) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.parseColor("#686596"))
    }
    private fun toast(message: String) = Toast.makeText(svc, message, Toast.LENGTH_LONG).show()
    private fun remove(view: View?) {
        if (view != null) runCatching { wm.removeViewImmediate(view) }
    }

    fun show() {
        if (control != null || !Settings.canDrawOverlays(svc)) return
        val button = TextView(svc).apply {
            text = "GEN"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = background("#262642", 18)
            contentDescription = "Kontrol GenKey"
        }
        val lp = WindowManager.LayoutParams(
            dp(52), dp(52), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or ScreenSpace.edgeFlags(),
            PixelFormat.TRANSLUCENT
        )
        ScreenSpace.configure(lp)
        val (width, height) = ScreenSpace.size(svc)
        lp.x = svc.store.bubbleX.coerceIn(0, (width - dp(52)).coerceAtLeast(0))
        lp.y = svc.store.bubbleY.coerceIn(dp(28), (height - dp(80)).coerceAtLeast(dp(28)))
        var originX = 0f
        var originY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    originX = event.rawX
                    originY = event.rawY
                    initialX = lp.x
                    initialY = lp.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - originX
                    val dy = event.rawY - originY
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(9)) dragging = true
                    if (dragging) {
                        lp.x = (initialX + dx).toInt().coerceIn(0, (width - dp(52)).coerceAtLeast(0))
                        lp.y = (initialY + dy).toInt().coerceIn(0, (height - dp(52)).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(button, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        svc.store.bubbleX = lp.x
                        svc.store.bubbleY = lp.y
                    } else showPopup()
                }
            }
            true
        }
        try {
            wm.addView(button, lp)
            control = button
            focusKeyboard()
        } catch (t: Throwable) {
            remove(button)
            control = null
            toast("Overlay gagal: ${t.message}")
            svc.stopSelf()
        }
    }

    fun hide() {
        captureRequested = false
        setAimMode(false)
        dismissPopup()
        remove(previews)
        previews = null
        remove(sink)
        sink = null
        remove(control)
        control = null
        main.removeCallbacksAndMessages(null)
    }

    fun focusKeyboard() {
        if (control == null) return
        setAimMode(false)
        remove(sink)
        val view = InputSink(svc)
        val lp = WindowManager.LayoutParams(
            1, 1, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                ScreenSpace.edgeFlags(),
            PixelFormat.TRANSLUCENT
        )
        ScreenSpace.configure(lp)
        lp.alpha = 0f
        sink = view
        try {
            wm.addView(view, lp)
            // Requesting focus BEFORE the window is attached can silently fail.
            view.post {
                if (sink === view) {
                    view.requestFocus()
                    svc.inputStatus = if (view.hasWindowFocus())
                        "Keyboard siap · tes WASD · F8 mouse"
                    else "Belum fokus · GEN → Fokus keyboard"
                }
            }
        } catch (t: Throwable) {
            sink = null
            svc.inputStatus = "Gagal membuka jendela keyboard: ${t.message}"
        }
    }

    fun setAimMode(enable: Boolean) {
        if (!enable) {
            captureRequested = false
            aimMode = false
            sink?.releasePointerCapture()
            svc.resetInputs()
            control?.text = "GEN"
            return
        }
        if (!ShizukuBridge.isReady() || !svc.store.mappingEnabled) {
            toast("Backend Shizuku/mapping belum aktif")
            return
        }
        if (svc.store.profileFor(svc.currentPkg)?.camera == null) {
            toast("Tambahkan kontrol Kamera pada editor")
            return
        }
        if (sink?.hasWindowFocus() != true) {
            focusKeyboard()
        }
        captureRequested = true
        sink?.post { if (captureRequested) sink?.requestPointerCapture() }
        main.postDelayed({
            if (captureRequested && !aimMode) {
                captureRequested = false
                svc.inputStatus = "Mouse capture ditolak Android/ROM"
                toast("Mouse belum tertangkap. Pastikan mouse fisik terpasang, lalu coba Fokus keyboard dan F8.")
            }
        }, 1400)
    }

    private inner class InputSink(context: Context) : View(context) {
        private var lastButtons = 0
        private var deltaX = 0f
        private var deltaY = 0f
        private var pending = false
        private val frame = Runnable {
            pending = false
            if (aimMode && sink === this) svc.cameraMove(deltaX, deltaY)
            deltaX = 0f
            deltaY = 0f
        }
        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        private fun clearMouse() {
            deltaX = 0f
            deltaY = 0f
            lastButtons = 0
            pending = false
            main.removeCallbacks(frame)
        }
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val consumed = svc.handleKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && sink === this) {
                svc.inputStatus = if (consumed)
                    "Tombol ${KeyNames.label(event.keyCode)} diterima · mapping diproses"
                else "Tombol ${KeyNames.label(event.keyCode)} diterima · belum dipetakan / backend belum siap"
            }
            return consumed || super.dispatchKeyEvent(event)
        }
        override fun onGenericMotionEvent(event: MotionEvent): Boolean =
            svc.gamepad(event) || super.onGenericMotionEvent(event)

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (sink !== this) return
            if (!hasWindowFocus) {
                captureRequested = false
                aimMode = false
                clearMouse()
                svc.resetInputs()
                control?.text = "GEN"
                svc.inputStatus = "Fokus keyboard terlepas · GEN → Fokus keyboard"
            } else {
                svc.inputStatus = "Keyboard siap · tekan WASD"
                if (captureRequested) requestPointerCapture()
            }
        }
        override fun onPointerCaptureChange(hasCapture: Boolean) {
            super.onPointerCaptureChange(hasCapture)
            if (sink !== this) return
            aimMode = hasCapture
            control?.text = if (hasCapture) "AIM" else "GEN"
            if (!hasCapture) {
                captureRequested = false
                clearMouse()
                svc.resetInputs()
            }
            svc.inputStatus = if (hasCapture)
                "Mouse relatif aktif · Esc untuk keluar"
            else if (hasWindowFocus()) "Mouse bebas · F8 untuk capture"
            else "Fokus keyboard terlepas · GEN → Fokus keyboard"
        }
        override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
            if (!aimMode) return false
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                setAimMode(false)
                return true
            }
            val buttons = event.buttonState
            val masks = intArrayOf(
                MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_SECONDARY,
                MotionEvent.BUTTON_TERTIARY, MotionEvent.BUTTON_BACK,
                MotionEvent.BUTTON_FORWARD
            )
            masks.forEachIndexed { index, mask ->
                val now = (buttons and mask) != 0
                val before = (lastButtons and mask) != 0
                if (now != before) svc.routeKey(KeyEvent.KEYCODE_BUTTON_1 + index, now)
            }
            lastButtons = buttons
            if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                val wheel = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (wheel != 0f) svc.wheel(wheel > 0)
            } else if (event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                // Relative deltas: render at most ~60 updates/second to avoid Binder floods.
                deltaX += event.x
                deltaY += event.y
                if (!pending) {
                    pending = true
                    main.postDelayed(frame, 16)
                }
            }
            return true
        }
    }

    private fun dismissPopup() {
        remove(popup)
        popup = null
    }

    private fun showPopup() {
        if (popup != null) {
            dismissPopup()
            return
        }
        val box = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(7))
            background = background("#181C30")
        }
        box.addView(TextView(svc).apply {
            text = "GENKEY / INPUT SESSION"
            textSize = 12f
            setTextColor(Color.parseColor("#69E1CE"))
            setPadding(0, 0, 0, dp(7))
        })
        box.addView(TextView(svc).apply {
            text = svc.inputStatus
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        })
        fun item(label: String, action: () -> Unit) {
            box.addView(TextView(svc).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(42)
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = background("#30344E", 11)
                setOnClickListener { dismissPopup(); action() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        item("Edit kontrol") { svc.openEditor() }
        item("Fokus keyboard") { focusKeyboard() }
        item("Tangkap mouse (F8 / Esc)") { setAimMode(true) }
        item(if (svc.store.mappingEnabled) "Jeda mapping (F9)" else "Aktifkan mapping (F9)") {
            svc.togglePause()
        }
        item(if (svc.store.showChips) "Sembunyikan petunjuk" else "Tampilkan petunjuk") {
            svc.store.showChips = !svc.store.showChips
            refreshPreviews()
        }
        item("Lepaskan input") { svc.resetInputs() }
        item("Hentikan sesi") { svc.stopSelf() }
        val (w, h) = ScreenSpace.size(svc)
        val scroll = ScrollView(svc).apply {
            isVerticalScrollBarEnabled = false
            addView(box)
        }
        val lp = WindowManager.LayoutParams(
            dp(300).coerceAtMost(w - dp(24)),
            dp(470).coerceAtMost(h - dp(72)), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        try {
            wm.addView(scroll, lp)
            popup = scroll
        } catch (t: Throwable) {
            toast("Menu gagal: ${t.message}")
        }
    }

    fun refreshPreviews() {
        remove(previews)
        previews = null
        if (control == null || svc.editorOpen || !svc.store.showChips || !svc.store.mappingEnabled) return
        val profile = svc.store.profileFor(svc.currentPkg) ?: return
        val display = object : View(svc) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                fun point(x: Float, y: Float, label: String, joy: Boolean) {
                    val cx = x * width
                    val cy = y * height
                    val radius = dp(if (joy) 27 else 20).toFloat()
                    paint.color = Color.parseColor(if (joy) "#207E72" else "#6453B6")
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, radius, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(1).toFloat()
                    paint.color = Color.WHITE
                    canvas.drawCircle(cx, cy, radius, paint)
                    paint.style = Paint.Style.FILL
                    paint.textSize = dp(11).toFloat()
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
                }
                profile.keys.forEach { point(it.x, it.y, KeyNames.label(it.keyCode), false) }
                profile.joys.forEach { point(it.x, it.y, "WASD", true) }
                profile.camera?.let { point(it.x, it.y, "CAM", true) }
            }
        }
        val lp = WindowManager.LayoutParams(
            -1, -1, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                ScreenSpace.edgeFlags(),
            PixelFormat.TRANSLUCENT
        )
        ScreenSpace.configure(lp)
        lp.alpha = 0.45f
        runCatching { wm.addView(display, lp); previews = display }
    }
}
