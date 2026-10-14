package com.gen.keymapper

import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

object ShizukuBridge {
    private const val REQUEST = 9911
    private val main = Handler(Looper.getMainLooper())
    private val connections = Executors.newCachedThreadPool { task ->
        Thread(task, "GenKey-connect").apply { isDaemon = true }
    }
    private val attempt = ConnectionAttempt()
    @Volatile private var engine: TouchEngine? = null
    @Volatile private var backend: DirectInputInjector? = null
    @Volatile var error = ""; private set
    private var initialized = false
    private var connectingBinder: IBinder? = null
    private var timeout: Runnable? = null

    val diagnostics: String get() = listOfNotNull(
        backend?.description, engine?.diagnostics(), error.takeIf { it.isNotBlank() }
    ).joinToString("\n").ifBlank { status }
    val status: String get() = when {
        !isRunning() -> "Shizuku belum berjalan"
        !hasPermission() -> "Shizuku aktif · izin GenKey belum diberikan"
        isReady() -> "Shizuku terhubung · backend input siap"
        isConnecting -> "Izin aktif · memeriksa layanan input Android…"
        else -> "Izin aktif · backend input belum siap"
    }
    val isConnecting get() = attempt.phase == ConnectionAttempt.Phase.CONNECTING
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener {
        disconnect()
        error = "Shizuku terputus. Mulai kembali Shizuku; GenKey akan menyambung saat tersedia."
    }
    private val permission = Shizuku.OnRequestPermissionResultListener { request, result ->
        if (request == REQUEST) {
            if (result == PackageManager.PERMISSION_GRANTED) refresh()
            else { disconnect(); error = "Izin Shizuku ditolak. Izinkan GenKey di aplikasi Shizuku." }
        }
    }
    fun initialize() {
        if (initialized) return
        initialized = true
        Shizuku.addBinderReceivedListenerSticky(received, main)
        Shizuku.addBinderDeadListener(dead, main)
        Shizuku.addRequestPermissionResultListener(permission, main)
        refresh()
    }
    fun isRunning() = try { Shizuku.pingBinder() } catch (_: Exception) { false }
    fun hasPermission() = try {
        isRunning() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }
    fun isReady() = attempt.phase == ConnectionAttempt.Phase.READY && backend?.isAlive() == true

    /** Also called on resume to pick up grants made in the Shizuku manager. */
    fun refresh() {
        if (!initialized) return
        if (connectingBinder != null && connectingBinder !== Shizuku.getBinder()) disconnect()
        if (!isRunning() || !hasPermission()) { disconnect(); return }
        if (attempt.phase == ConnectionAttempt.Phase.READY && !isReady()) disconnect()
        connectIfAllowed()
    }
    fun requestPermission() {
        try {
            if (!isRunning()) { disconnect(); error = "Mulai Shizuku terlebih dahulu"; return }
            if (Shizuku.isPreV11()) { error = "Perbarui aplikasi Shizuku ke versi terbaru."; return }
            if (hasPermission()) refresh()
            else if (Shizuku.shouldShowRequestPermissionRationale()) {
                error = "Akses pernah ditolak. Buka Shizuku → Aplikasi yang diizinkan → aktifkan GenKey."
            } else Shizuku.requestPermission(REQUEST)
        } catch (e: Exception) { error = failureMessage(e) }
    }
    private fun connectIfAllowed() {
        val binder = Shizuku.getBinder() ?: return
        val id = attempt.begin() ?: return
        connectingBinder = binder
        error = ""
        val deadline = Runnable {
            if (attempt.expire(id)) {
                timeout = null
                error = "Layanan input Android tidak merespons. Izin Shizuku tetap aktif; tekan Coba lagi."
            }
        }
        timeout = deadline
        main.postDelayed(deadline, 12000)
        connections.execute {
            try {
                val input = DirectInputInjector.connect(binder)
                main.post {
                    if (!input.isAlive()) {
                        failed(id, IllegalStateException("Shizuku berubah saat menghubungkan. Tekan Coba lagi."))
                        return@post
                    }
                    if (attempt.complete(id)) {
                        cancelTimeout()
                        backend = input
                        engine = TouchEngine(input::inject) { problem -> main.post { failed(id, problem) } }
                        error = ""
                    }
                }
            } catch (e: Throwable) { main.post { failed(id, e) } }
        }
    }
    private fun failed(id: Long, cause: Throwable) {
        if (!attempt.fail(id)) return
        cancelTimeout()
        closeEngine()
        error = failureMessage(cause)
    }
    private fun failureMessage(cause: Throwable): String {
        val root = (cause as? java.lang.reflect.InvocationTargetException)?.targetException ?: cause
        return "${root.javaClass.simpleName}: ${root.message ?: "Koneksi input gagal"}"
    }
    private fun cancelTimeout() { timeout?.let(main::removeCallbacks); timeout = null }
    private fun closeEngine() { val old = engine; engine = null; backend = null; old?.close() }
    private fun disconnect() {
        attempt.reset()
        cancelTimeout()
        closeEngine()
        connectingBinder = null
    }
    private fun submit(block: (TouchEngine) -> Unit): Boolean {
        val target = engine ?: return false
        if (!isReady()) return false
        block(target)
        return true
    }
    fun heartbeat() { submit { it.heartbeat() } }
    fun tap(x: Float, y: Float, ms: Long) = submit { it.tap(x.toInt(), y.toInt(), ms.toInt()) }
    fun swipe(x: Float, y: Float, endX: Float, endY: Float, ms: Long) = submit {
        it.swipe(x.toInt(), y.toInt(), endX.toInt(), endY.toInt(), ms.toInt())
    }
    fun pointer(token: Int, action: Int, x: Float, y: Float) = submit { it.pointer(token, action, x, y) }
    fun releaseAll() { engine?.releaseAll() }
}
