package com.gen.keymapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Local gesture backend. No network, shell, root, or screen-content access. */
class OfflineInputService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val queue = GestureQueue()
    private data class Stroke(val description: GestureDescription.StrokeDescription, val x: Float, val y: Float)
    private var previous = emptyMap<Int, Stroke>()
    private var busy = false
    private var attached = false
    private var delivered = 0L
    @Volatile var lastError = ""
        private set

    override fun onServiceConnected() {
        attached = true
        instance = this
        Backend.initialize(this)
        Backend.listener?.invoke()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { releaseAll() }
    override fun onDestroy() {
        attached = false
        handler.removeCallbacksAndMessages(null)
        queue.clear()
        previous = emptyMap()
        if (instance === this) instance = null
        Backend.listener?.invoke()
        super.onDestroy()
    }
    fun pointer(token: Int, action: Int, x: Float, y: Float): Boolean {
        if (!attached) return false
        handler.post {
            if (!attached || Backend.mode != Backend.Mode.OFFLINE) return@post
            if (!queue.offer(token, action, x, y)) {
                lastError = "Antrean penuh atau koordinat tidak valid. Lepaskan tombol lalu coba lagi."
                queue.releaseAll()
            }
            pump()
        }
        return true
    }
    fun releaseAll() { handler.post { queue.releaseAll(); pump() } }
    fun key(code: Int) { if (code == KeyEvent.KEYCODE_BACK) handler.post { performGlobalAction(GLOBAL_ACTION_BACK) } }
    fun diag() = "OTG offline · layanan=${if (attached) "aktif" else "mati"} · frame=$delivered · $lastError"

    private fun pump() {
        if (!attached || busy) return
        val frame = queue.next()
        if (frame.isEmpty()) return
        if (frame.size > GestureDescription.getMaxStrokeCount()) {
            lastError = "Terlalu banyak sentuhan bersamaan. Kurangi mapping yang ditahan."
            queue.releaseAll()
            // Finish existing continuations; discard new fingers beyond platform capacity.
            queue.clear()
            previous.forEach { (id, s) -> queue.offer(id, 0, s.x, s.y); queue.offer(id, 1, s.x, s.y) }
            pump()
            return
        }
        val next = linkedMapOf<Int, Stroke>()
        val builder = GestureDescription.Builder()
        try {
            for (c in frame) {
                val old = previous[c.token]
                val path = Path().apply {
                    moveTo(old?.x ?: c.x, old?.y ?: c.y)
                    if (old != null && (old.x != c.x || old.y != c.y)) lineTo(c.x, c.y)
                }
                val stroke = if (old == null) GestureDescription.StrokeDescription(path, 0, 32, !c.ending)
                    else old.description.continueStroke(path, 0, 32, !c.ending)
                builder.addStroke(stroke)
                if (!c.ending) next[c.token] = Stroke(stroke, c.x, c.y)
            }
            busy = true
            val accepted = dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (!attached) return
                    busy = false
                    delivered++
                    previous = next
                    queue.completed(frame)
                    handler.post { pump() }
                }
                override fun onCancelled(gestureDescription: GestureDescription) { failed("Gesture dibatalkan sistem/sentuhan lain. Lepaskan tombol lalu coba lagi.") }
            }, handler)
            if (!accepted) failed("Android menolak gesture. Periksa izin Aksesibilitas GenKey.")
        } catch (t: Exception) { failed(t.message ?: "Gesture gagal") }
    }
    private fun failed(message: String) {
        if (!attached) return
        busy = false
        queue.clear()
        previous = emptyMap()
        lastError = message
        Backend.listener?.invoke()
    }
    companion object {
        @Volatile var instance: OfflineInputService? = null
            private set
    }
}
