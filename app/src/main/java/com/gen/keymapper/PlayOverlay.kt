package com.gen.keymapper

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.*

/** A zero-opacity keyboard window captures relative mouse input. No fullscreen mouse interceptor. */
class PlayOverlay(private val svc: MapperService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private var sink: InputSink? = null
    private var control: TextView? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var popup: View? = null
    private var previews: View? = null
    private var captureRequested = false
    var aimMode = false; private set
    private fun dp(n: Int) = (n * svc.resources.displayMetrics.density).toInt()
    private fun round(fill: String, radius: Int = 18) = GradientDrawable().apply {
        setColor(Color.parseColor(fill)); cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.parseColor("#7064A8"))
    }
    fun show() {
        if (control != null || !Settings.canDrawOverlays(svc)) return
        val button = TextView(svc).apply {
            text = "GEN"; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            background = round("#29253F", 20)
            contentDescription = "Buka kontrol GenKey, geser untuk memindahkan"
        }
        val lp = WindowManager.LayoutParams(dp(52), dp(52), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or ScreenSpace.edgeFlags(), PixelFormat.TRANSLUCENT)
        ScreenSpace.configure(lp)
        lp.x = svc.store.bubbleX; lp.y = svc.store.bubbleY
        val (width, height) = ScreenSpace.size(svc)
        lp.x = lp.x.coerceIn(0, (width - dp(52)).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(dp(28), (height - dp(80)).coerceAtLeast(dp(28)))
        var ox = 0; var oy = 0; var x = 0f; var y = 0f; var moved = false
        button.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x = e.rawX; y = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX - x) + kotlin.math.abs(e.rawY - y) > dp(8)) moved = true
                    if (moved) {
                        lp.x = (ox + e.rawX - x).toInt().coerceIn(0, (width - dp(52)).coerceAtLeast(0))
                        lp.y = (oy + e.rawY - y).toInt().coerceIn(0, (height - dp(52)).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(button, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> if (moved) { svc.store.bubbleX = lp.x; svc.store.bubbleY = lp.y } else showPopup()
            }; true
        }
        try { wm.addView(button, lp); control = button; controlParams = lp; focusKeyboard() }
        catch (e: Exception) { toast("Overlay gagal: ${e.message}"); svc.stopSelf() }
    }
    fun hide() {
        main.removeCallbacksAndMessages(null)
        setAimMode(false); dismissPopup(); remove(previews); previews = null
        remove(sink); sink = null; remove(control); control = null
    }
    private fun remove(view: View?) { if (view != null) runCatching { wm.removeView(view) } }
    private fun toast(message: String) = Toast.makeText(svc, message, Toast.LENGTH_LONG).show()
    fun focusKeyboard() {
        setAimMode(false); remove(sink)
        val view = InputSink(svc)
        val lp = WindowManager.LayoutParams(1, 1, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or ScreenSpace.edgeFlags(), PixelFormat.TRANSLUCENT)
        ScreenSpace.configure(lp)
        // Fully transparent SAW windows are exempt from Android 12+ untrusted touch blocking.
        lp.alpha = 0f
        try { wm.addView(view, lp); sink = view; view.requestFocus() }
        catch (e: Exception) { sink = null; svc.inputStatus = "Fokus gagal: ${e.message}" }
    }
    fun setAimMode(on: Boolean) {
        if (!on) {
            captureRequested = false; aimMode = false; sink?.releasePointerCapture(); svc.resetInputs()
            control?.text = "GEN"
            return
        }
        if (!ShizukuBridge.isReady() || !svc.store.mappingEnabled) { toast("Aktifkan Shizuku dan mapping dahulu"); return }
        if (svc.store.profileFor(svc.currentPkg)?.camera == null) { toast("Tambahkan kontrol Kamera di editor dahulu"); return }
        if (sink?.hasWindowFocus() != true) focusKeyboard()
        captureRequested = true
        sink?.post { if (captureRequested) sink?.requestPointerCapture() }
        main.postDelayed({
            if (captureRequested && !aimMode) {
                captureRequested = false
                svc.inputStatus = "Mouse capture tidak diberikan oleh Android"
                toast("Mouse capture belum aktif. Hubungkan mouse fisik lalu pilih Fokus keyboard. F8 untuk mencoba lagi.")
            }
        }, 1200)
    }
    private inner class InputSink(context: Context) : View(context) {
        private var mouseButtons = 0
        init { isFocusable = true; isFocusableInTouchMode = true }
        override fun dispatchKeyEvent(event: KeyEvent) = svc.handleKeyEvent(event) || super.dispatchKeyEvent(event)
        override fun onGenericMotionEvent(event: MotionEvent) = svc.gamepad(event) || super.onGenericMotionEvent(event)
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            svc.inputStatus = if (hasWindowFocus) "Keyboard siap · F8 mouse · F9 jeda" else "Fokus lepas · ketuk GEN → Fokus keyboard"
            if (!hasWindowFocus) { setAimMode(false); mouseButtons = 0; svc.resetInputs() }
            else if (captureRequested) requestPointerCapture()
        }
        override fun onPointerCaptureChange(hasCapture: Boolean) {
            super.onPointerCaptureChange(hasCapture)
            aimMode = hasCapture; control?.text = if (hasCapture) "AIM" else "GEN"
            svc.inputStatus = if (hasCapture) "Mouse relatif aktif · Esc untuk keluar" else "Mouse bebas · F8 untuk capture"
            if (!hasCapture) { captureRequested = false; mouseButtons = 0; svc.resetInputs() }
        }
        override fun onCapturedPointerEvent(e: MotionEvent): Boolean {
            if (!aimMode) return false
            if (e.actionMasked == MotionEvent.ACTION_CANCEL) { setAimMode(false); return true }
            val buttons = e.buttonState
            val masks = intArrayOf(MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_SECONDARY,
                MotionEvent.BUTTON_TERTIARY, MotionEvent.BUTTON_BACK, MotionEvent.BUTTON_FORWARD)
            masks.forEachIndexed { index, mask ->
                if ((buttons and mask) != (mouseButtons and mask))
                    svc.routeKey(KeyEvent.KEYCODE_BUTTON_1 + index, (buttons and mask) != 0)
            }
            mouseButtons = buttons
            if (e.actionMasked == MotionEvent.ACTION_SCROLL) {
                val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (v != 0f) svc.wheel(v > 0)
            } else if (e.actionMasked == MotionEvent.ACTION_MOVE || e.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                svc.cameraMove(e.x, e.y)
            }
            return true
        }
    }
    private fun dismissPopup() { remove(popup); popup = null }
    private fun showPopup() {
        if (popup != null) { dismissPopup(); return }
        setAimMode(false)
        val box = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(8))
            background = round("#171B2B")
        }
        box.addView(TextView(svc).apply {
            text = "GENKEY  /  SESSION"; textSize = 11f; setTextColor(Color.parseColor("#69E1CE"))
            setPadding(0, 0, 0, dp(6))
        })
        box.addView(TextView(svc).apply {
            text = svc.inputStatus; textSize = 12f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(12))
        })
        fun item(label: String, action: () -> Unit) {
            box.addView(TextView(svc).apply {
                text = label; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(46); setPadding(dp(12), dp(8), dp(12), dp(8)); background = round("#292C43", 12)
                setOnClickListener { dismissPopup(); action() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
        item("Edit kontrol") { svc.openEditor() }
        item("Fokus keyboard") { focusKeyboard() }
        item("Tangkap mouse · F8 / Esc") { setAimMode(true) }
        item(if (svc.store.mappingEnabled) "Jeda mapping · F9" else "Lanjutkan mapping · F9") { svc.togglePause() }
        item(if (svc.store.showChips) "Sembunyikan petunjuk" else "Tampilkan petunjuk") {
            svc.store.showChips = !svc.store.showChips; refreshPreviews()
        }
        item("Lepaskan semua input") { svc.resetInputs() }
        item("Hentikan sesi") { svc.stopSelf() }
        val (w, h) = ScreenSpace.size(svc)
        val scroll = ScrollView(svc).apply { addView(box); isVerticalScrollBarEnabled = false }
        val lp = WindowManager.LayoutParams(dp(280).coerceAtMost(w - dp(24)), dp(460).coerceAtMost(h - dp(72)), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        try { wm.addView(scroll, lp); popup = scroll } catch (e: Exception) { toast("Menu: ${e.message}") }
    }
    fun refreshPreviews() {
        remove(previews); previews = null
        if (control == null || svc.editorOpen || !svc.store.showChips || !svc.store.mappingEnabled) return
        val profile = svc.store.profileFor(svc.currentPkg) ?: return
        val view = object : View(svc) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                fun point(x: Float, y: Float, label: String, joy: Boolean) {
                    val cx = x * width; val cy = y * height
                    val radius = dp(if (joy) 27 else 20).toFloat()
                    paint.color = Color.parseColor(if (joy) "#207E72" else "#6453B6"); paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, radius, paint)
                    paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1).toFloat()
                    canvas.drawCircle(cx, cy, radius, paint)
                    paint.style = Paint.Style.FILL; paint.textSize = dp(11).toFloat(); paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
                }
                profile.keys.forEach { point(it.x, it.y, KeyNames.label(it.keyCode), false) }
                profile.joys.forEach { point(it.x, it.y, "JOY", true) }
                profile.camera?.let { point(it.x, it.y, "CAM", true) }
            }
        }
        val lp = WindowManager.LayoutParams(-1, -1, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                ScreenSpace.edgeFlags(), PixelFormat.TRANSLUCENT)
        ScreenSpace.configure(lp)
        // A single window prevents overlapping chips from adding up to opacity > 0.8.
        lp.alpha = 0.45f
        runCatching { wm.addView(view, lp); previews = view }
    }
}
