package com.gen.keymapper

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import rikka.shizuku.Shizuku

/** Jembatan ke Shizuku v13: izin, bind UserService, dan antrean perintah sentuh. */
object ShizukuBridge {

    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, CONNECTING, READY }

    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 9911
    private const val SERVICE_VERSION = 3

    @Volatile private var remote: IInputBridge? = null
    @Volatile private var initialized = false
    @Volatile private var binding = false
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val pendingMoves = AtomicInteger(0)

    /** Dipanggil di main thread setiap status berubah. */
    @Volatile var listener: (() -> Unit)? = null

    private fun notifyChanged() {
        main.post { listener?.invoke() }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            remote = if (binder != null && binder.pingBinder()) IInputBridge.Stub.asInterface(binder) else null
            binding = false
            notifyChanged()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            binding = false
            notifyChanged()
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        connect()
        notifyChanged()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        remote = null
        binding = false
        notifyChanged()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { request, result ->
        if (request == REQUEST_CODE && result == PackageManager.PERMISSION_GRANTED) connect()
        notifyChanged()
    }

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        app = context.applicationContext
        initialized = true
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        } catch (_: Throwable) {
        }
        connect()
    }

    // ---------- status ----------

    fun isInstalled(): Boolean = try {
        app.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (_: Throwable) {
        false
    }

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun isReady(): Boolean = remote != null

    fun state(): State = when {
        remote != null -> State.READY
        !isRunning() -> if (isInstalled()) State.NOT_RUNNING else State.NOT_INSTALLED
        !hasPermission() -> State.NO_PERMISSION
        else -> State.CONNECTING
    }

    // ---------- koneksi ----------

    /** Dipanggil tombol pengguna; tidak meminta izin diam-diam. */
    fun requestPermission() {
        try {
            if (!isRunning()) return
            if (hasPermission()) connect()
            else if (!Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun connect() {
        if (!initialized || remote != null || binding) return
        if (!hasPermission()) return
        try {
            binding = true
            val args = Shizuku.UserServiceArgs(ComponentName(app, ShellInputService::class.java))
                .daemon(false)
                .processNameSuffix("gen_input")
                .tag("gen_input_v$SERVICE_VERSION")
                .version(SERVICE_VERSION)
            Shizuku.bindUserService(args, connection)
        } catch (_: Throwable) {
            binding = false
        }
    }

    /** Paksa sambung ulang (mis. setelah Shizuku dimulai ulang). */
    fun reconnect() {
        remote = null
        binding = false
        connect()
        notifyChanged()
    }

    // ---------- perintah ----------

    private fun submit(block: (IInputBridge) -> Unit): Boolean {
        val target = remote ?: return false
        worker.execute {
            try {
                block(target)
            } catch (_: Throwable) {
                if (remote === target) {
                    remote = null
                    binding = false
                    notifyChanged()
                }
            }
        }
        return true
    }

    fun pointer(token: Int, action: Int, x: Float, y: Float): Boolean {
        val isMove = action == android.view.MotionEvent.ACTION_MOVE
        if (isMove) {
            // Jangan menumpuk MOVE jika Shizuku lambat: buang yang kedaluwarsa.
            if (pendingMoves.get() > 24) return true
            pendingMoves.incrementAndGet()
        }
        val ok = submit {
            try {
                it.pointer(token, action, x, y)
            } finally {
                if (isMove) pendingMoves.decrementAndGet()
            }
        }
        if (!ok && isMove) pendingMoves.decrementAndGet()
        return ok
    }

    fun releaseAll() {
        submit { it.releaseAll() }
    }

    fun key(keyCode: Int) {
        submit { it.key(keyCode) }
    }

    /** Diagnosa sinkron (dipanggil di thread latar). Mengembalikan null jika belum terhubung. */
    fun diagBlocking(): String? = try {
        remote?.diag()
    } catch (_: Throwable) {
        null
    }
}
