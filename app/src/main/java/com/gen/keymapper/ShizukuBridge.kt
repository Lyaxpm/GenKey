package com.gen.keymapper

import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The UI reads cached state only. Potentially blocking Shizuku/remote Binder calls
 * are made from a worker and never from MainActivity's polling loop.
 */
object ShizukuBridge {
    private const val REQUEST = 9911
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "GenKey-shizuku").apply { isDaemon = true }
    }
    private val probeBusy = AtomicBoolean(false)
    private val connectBusy = AtomicBoolean(false)
    private val attempt = ConnectionAttempt()

    @Volatile private var running = false
    @Volatile private var granted = false
    @Volatile private var checking = false
    @Volatile private var engine: TouchEngine? = null
    @Volatile private var backend: DirectInputInjector? = null
    @Volatile var error: String = ""
        private set
    private var initialized = false
    private var activeBinder: IBinder? = null
    private var timeout: Runnable? = null

    val isConnecting: Boolean
        get() = checking || attempt.phase == ConnectionAttempt.Phase.CONNECTING

    val status: String
        get() = when {
            !running -> "Shizuku belum berjalan"
            checking -> "Memeriksa izin dan layanan Shizuku…"
            !granted -> "Shizuku berjalan · izin GenKey belum diberikan"
            isReady() -> "Shizuku terhubung · backend input siap"
            attempt.phase == ConnectionAttempt.Phase.CONNECTING -> "Menghubungkan layanan input Android…"
            else -> "Izin aktif · backend input belum siap"
        }

    val diagnostics: String
        get() = listOfNotNull(
            backend?.description,
            engine?.diagnostics(),
            error.takeIf { it.isNotBlank() }
        ).joinToString("\n").ifBlank { status }

    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener {
        disconnect()
        error = "Shizuku terputus. Jalankan lagi Shizuku, kemudian hubungkan ulang GenKey."
    }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST) {
            granted = result == PackageManager.PERMISSION_GRANTED
            if (granted) refresh()
            else {
                disconnectSession()
                error = "Izin Shizuku ditolak. Izinkan GenKey dari aplikasi Shizuku."
            }
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

    fun isRunning(): Boolean = running
    fun hasPermission(): Boolean = running && granted
    fun isReady(): Boolean = attempt.phase == ConnectionAttempt.Phase.READY && engine != null && backend != null

    /** Can be called on Activity.onResume, from binder callbacks, or manually. */
    fun refresh() {
        if (!initialized) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { refresh() }
            return
        }
        val binder = try { Shizuku.getBinder() } catch (_: Throwable) { null }
        if (binder == null || !binder.isBinderAlive) {
            disconnect()
            return
        }
        if (activeBinder !== binder) {
            disconnectSession()
            activeBinder = binder
        }
        running = true
        if (!probeBusy.compareAndSet(false, true)) return
        checking = true
        worker.execute {
            var allowed = false
            var problem: Throwable? = null
            try {
                allowed = Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (t: Throwable) {
                problem = t
            } finally {
                probeBusy.set(false)
            }
            main.post {
                if (activeBinder !== binder || !binder.isBinderAlive) {
                    if (activeBinder != null) refresh()
                    return@post
                }
                checking = false
                granted = allowed
                problem?.let { error = message(it) }
                if (allowed) connectIfAllowed(binder)
                else disconnectSession()
            }
        }
    }

    fun requestPermission() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { requestPermission() }
            return
        }
        if (!running) {
            error = "Mulai Shizuku dahulu."
            refresh()
            return
        }
        if (granted) {
            refresh()
            return
        }
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                error = "Izin pernah ditolak. Buka Shizuku → Aplikasi yang diizinkan → GenKey."
            } else {
                Shizuku.requestPermission(REQUEST)
            }
        } catch (t: Throwable) {
            error = message(t)
        }
    }

    private fun connectIfAllowed(binder: IBinder) {
        if (!granted || activeBinder !== binder || isReady()) return
        if (attempt.phase == ConnectionAttempt.Phase.CONNECTING) return
        if (!connectBusy.compareAndSet(false, true)) {
            error = "Pemeriksaan input sebelumnya masih berjalan. Jangan mengetuk Hubungkan berulang kali."
            return
        }
        val id = attempt.begin()
        if (id == null) {
            connectBusy.set(false)
            return
        }
        error = ""
        val deadline = Runnable {
            if (attempt.expire(id)) {
                timeout = null
                error = "Layanan input tidak merespons dalam 12 detik. Pemeriksaan lama masih dapat berlangsung; coba lagi setelah selesai."
            }
        }
        timeout = deadline
        main.postDelayed(deadline, 12_000)
        worker.execute {
            var connection: DirectInputInjector? = null
            var problem: Throwable? = null
            try {
                connection = DirectInputInjector.connect(binder)
            } catch (t: Throwable) {
                problem = t
            } finally {
                connectBusy.set(false)
            }
            main.post {
                if (activeBinder !== binder) return@post
                val input = connection
                val failure = problem
                if (failure != null) {
                    failed(id, failure)
                } else if (input == null || !input.isAlive()) {
                    failed(id, IllegalStateException("Shizuku berubah saat menghubungkan input"))
                } else if (attempt.complete(id)) {
                    cancelTimeout()
                    backend = input
                    engine = TouchEngine(input::inject) { failure ->
                        main.post { failed(id, failure) }
                    }
                    error = ""
                }
            }
        }
    }

    private fun failed(id: Long, cause: Throwable) {
        if (!attempt.fail(id)) return
        cancelTimeout()
        closeEngine()
        error = message(cause)
    }

    private fun message(t: Throwable): String {
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        return "${cause.javaClass.simpleName}: ${cause.message ?: "koneksi input gagal"}"
    }

    private fun cancelTimeout() {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
    }

    private fun closeEngine() {
        val old = engine
        engine = null
        backend = null
        old?.close()
    }

    private fun disconnectSession() {
        attempt.reset()
        cancelTimeout()
        closeEngine()
    }

    private fun disconnect() {
        disconnectSession()
        activeBinder = null
        checking = false
        running = false
        granted = false
    }

    private fun submit(action: (TouchEngine) -> Unit): Boolean {
        val target = engine ?: return false
        if (!isReady()) return false
        action(target)
        return true
    }

    fun heartbeat() { submit { it.heartbeat() } }
    fun tap(x: Float, y: Float, ms: Long): Boolean = submit { it.tap(x.toInt(), y.toInt(), ms.toInt()) }
    fun swipe(x: Float, y: Float, ex: Float, ey: Float, ms: Long): Boolean =
        submit { it.swipe(x.toInt(), y.toInt(), ex.toInt(), ey.toInt(), ms.toInt()) }
    fun pointer(token: Int, action: Int, x: Float, y: Float): Boolean =
        submit { it.pointer(token, action, x, y) }
    fun releaseAll() { engine?.releaseAll() }
}
