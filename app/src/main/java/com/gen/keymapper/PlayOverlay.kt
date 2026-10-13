package com.gen.keymapper

import android.content.Context
import android.graphics.Color
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
import android.widget.TextView
import android.widget.Toast

/**
 * Overlay mode main:
 * - Tombol kontrol melayang (draggable): buka menu (Mode Mouse / Edit / on-off mapping)
 * - Jendela Mode Mouse (fullscreen, hanya saat aktif): meneruskan sentuhan & hover mouse ke game
 */
class PlayOverlay(private val svc: MapperService) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val overlayType =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    var aimMode = false
        private set
    var lookMode = false
        private set

    private var ctrlView: View? = null
    private var ctrlParams: WindowManager.LayoutParams? = null
    private var aimView: AimPadView? = null
    private var popupView: View? = null
    private val previewViews = mutableListOf<View>()

    private fun pillBg(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(2), Color.parseColor("#9E9E9E"))
    }

    private fun dp(v: Int): Int = (v * svc.resources.displayMetrics.density).toInt()

    // ---------- tombol kontrol ----------

    fun show() {
        if (ctrlView != null || !Settings.canDrawOverlays(svc)) return
        val btn = Button(svc).apply {
            text = "🎮"
            textSize = 20f
            background = pillBg(Color.parseColor("#CC222222"))
            setTextColor(Color.WHITE)
            val s = dp(56)
            layoutParams = FrameLayout.LayoutParams(s, s)
        }
        val params = WindowManager.LayoutParams(
            dp(56), dp(56), overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12); y = dp(160)
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        btn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (kotlin.math.abs(dx) > dp(8) || kotlin.math.abs(dy) > dp(8)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        try { wm.updateViewLayout(btn, params) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) showPopup()
            }
            true
        }
        ctrlView = btn; ctrlParams = params
        try { wm.addView(btn, params) } catch (_: Exception) { ctrlView = null }
        refreshPreviews()
    }

    fun hide() {
        dismissPopup()
        clearPreviews()
        setAimMode(false)
        lookMode = false
        ctrlView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        ctrlView = null; ctrlParams = null
    }

    // ---------- popup menu ----------

    private fun showPopup() {
        dismissPopup()
        val layout = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1B1B1B"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), Color.parseColor("#9E9E9E"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fun addBtn(text: String, onClick: () -> Unit) {
            layout.addView(Button(svc).apply {
                this.text = text
                textSize = 13f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#333333"))
                    cornerRadius = dp(8).toFloat()
                }
                setOnClickListener { onClick(); dismissPopup() }
            }, LinearLayout.LayoutParams(dp(190), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            })
        }
        addBtn(if (aimMode) "🖱️ Mode Mouse: ON" else "🖱️ Mode Mouse: OFF") {
            setAimMode(!aimMode)
            Toast.makeText(svc, if (aimMode) "Mode Mouse AKTIF: klik = tap, drag = geser" else "Mode Mouse mati", Toast.LENGTH_SHORT).show()
        }
        addBtn(if (lookMode) "🎯 Kamera Mouse: ON" else "🎯 Kamera Mouse: OFF") {
            lookMode = !lookMode
            if (lookMode && !aimMode) setAimMode(true)
            Toast.makeText(svc, "Mode kamera berbasis hover: eksperimental; Shizuku disarankan", Toast.LENGTH_SHORT).show()
        }
        addBtn("✏️ Edit Mapping") { svc.openEditor() }
        addBtn(if (svc.store.showChips) "👁️ Petunjuk: ON" else "👁️ Petunjuk: OFF") {
            svc.store.showChips = !svc.store.showChips
            refreshPreviews()
        }
        addBtn(if (svc.store.mappingEnabled) "⏸️ Mapping: ON" else "▶️ Mapping: OFF") {
            svc.store.mappingEnabled = !svc.store.mappingEnabled
            svc.resetInputs()
            refreshPreviews()
            Toast.makeText(svc, if (svc.store.mappingEnabled) "Mapping aktif" else "Mapping nonaktif", Toast.LENGTH_SHORT).show()
        }
        val params = WindowManager.LayoutParams(
            dp(206), WindowManager.LayoutParams.WRAP_CONTENT, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val cp = ctrlParams
            x = (cp?.x ?: dp(12)) + dp(64)
            y = cp?.y ?: dp(160)
        }
        popupView = layout
        try { wm.addView(layout, params) } catch (_: Exception) { popupView = null }
    }

    private fun dismissPopup() {
        popupView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        popupView = null
    }

    // ---------- penanda mapping saat bermain (tidak menangkap sentuhan) ----------

    private fun clearPreviews() {
        previewViews.forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        previewViews.clear()
    }

    fun refreshPreviews() {
        clearPreviews()
        if (!svc.store.showChips || !svc.store.mappingEnabled || !Settings.canDrawOverlays(svc) || svc.editorOpen) return
        if (svc.currentPkg == svc.packageName) return
        val profile = svc.store.profileFor(svc.currentPkg) ?: return
        val dm = svc.resources.displayMetrics
        fun showPoint(x: Float, y: Float, label: String, joy: Boolean) {
            val size = dp(if (joy) 67 else 43)
            val chip = TextView(svc).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = if (joy) 10f else 11f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(if (joy) "#504CAF50" else "#50429EFF"))
                    setStroke(dp(1), Color.WHITE)
                }
            }
            val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = (x * dm.widthPixels - size / 2).toInt().coerceAtLeast(0)
                this.y = (y * dm.heightPixels - size / 2).toInt().coerceAtLeast(0)
                alpha = 0.5f
            }
            try { wm.addView(chip, lp); previewViews.add(chip) } catch (_: Throwable) {}
        }
        profile.keys.forEach { showPoint(it.x, it.y, KeyNames.label(it.keyCode), false) }
        profile.joys.forEach { showPoint(it.x, it.y, "WASD", true) }
        profile.camera?.let { showPoint(it.x, it.y, "CAM", true) }
    }

    // ---------- Mode Mouse ----------

    fun setAimMode(on: Boolean) {
        if (on == aimMode) return
        aimMode = on
        if (!on) lookMode = false
        if (on) {
            if (!Settings.canDrawOverlays(svc)) { aimMode = false; return }
            val v = AimPadView(svc)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            aimView = v
            try {
                wm.addView(v, params)
                // pastikan tombol kontrol tetap di atas
                ctrlView?.let { cv ->
                    try { wm.removeView(cv) } catch (_: Exception) {}
                    ctrlParams?.let { wm.addView(cv, it) }
                }
            } catch (_: Exception) { aimMode = false; aimView = null }
        } else {
            aimView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            aimView = null
        }
    }

    /** Lapisan fullscreen Mode Mouse: teruskan semua sentuhan ke game via gesture. */
    private inner class AimPadView(ctx: Context) : View(ctx) {
        private var capturedMouseKey = -1
        private var lastX = Float.NaN
        private var lastY = Float.NaN
        private var dxSum = 0f
        private var dySum = 0f
        private var lastMoveAt = 0L
        init { isFocusable = false }

        override fun onHoverEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_HOVER_EXIT) {
                lastX = Float.NaN; lastY = Float.NaN
                dxSum = 0f; dySum = 0f
                return true
            }
            if (e.action == MotionEvent.ACTION_HOVER_MOVE) {
                svc.aimCursorX = e.x; svc.aimCursorY = e.y
                if (lookMode && !lastX.isNaN()) {
                    dxSum += e.x - lastX
                    dySum += e.y - lastY
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastMoveAt >= 48 && (kotlin.math.abs(dxSum) + kotlin.math.abs(dySum)) >= 2f) {
                        svc.cameraMove(dxSum, dySum)
                        dxSum = 0f; dySum = 0f; lastMoveAt = now
                    }
                }
                lastX = e.x; lastY = e.y
            }
            return true
        }

        override fun onGenericMotionEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_SCROLL) {
                val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (v > 0) svc.fireVirtualKey(KeyNames.MOUSE_SCROLL_UP)
                else if (v < 0) svc.fireVirtualKey(KeyNames.MOUSE_SCROLL_DOWN)
                return true
            }
            return super.onGenericMotionEvent(e)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    val code = when {
                        (e.buttonState and MotionEvent.BUTTON_SECONDARY) != 0 -> android.view.KeyEvent.KEYCODE_BUTTON_2
                        (e.buttonState and MotionEvent.BUTTON_TERTIARY) != 0 -> android.view.KeyEvent.KEYCODE_BUTTON_3
                        (e.buttonState and MotionEvent.BUTTON_BACK) != 0 -> android.view.KeyEvent.KEYCODE_BUTTON_4
                        (e.buttonState and MotionEvent.BUTTON_FORWARD) != 0 -> android.view.KeyEvent.KEYCODE_BUTTON_5
                        else -> android.view.KeyEvent.KEYCODE_BUTTON_1
                    }
                    if (svc.routeOverlayMouseButton(code, true)) {
                        capturedMouseKey = code
                        return true
                    }
                }
                if (capturedMouseKey != -1) {
                    if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                        svc.routeOverlayMouseButton(capturedMouseKey, false)
                        capturedMouseKey = -1
                    }
                    return true
                }
            }
            svc.forwardPointer(e)
            return true
        }
    }
}
