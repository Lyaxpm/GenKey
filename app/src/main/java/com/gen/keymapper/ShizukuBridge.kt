package com.gen.keymapper

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.view.MotionEvent
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

/** Jembatan IPC Shizuku v13; selalu ada fallback Accessibility jika Shizuku tidak hidup. */
object ShizukuBridge {
    private const val REQUEST_CODE = 9911
    @Volatile private var remote: IInputBridge? = null
    private var initialized = false
    private var binding = false
    private lateinit var app: Context
    private val worker = Executors.newSingleThreadExecutor()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            remote = IInputBridge.Stub.asInterface(binder)
            binding = false
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            binding = false
        }
    }
    private val binderReceived = Shizuku.OnBinderReceivedListener { connectIfAllowed() }
    private val binderDead = Shizuku.OnBinderDeadListener { remote = null; binding = false }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { request, result ->
        if (request == REQUEST_CODE && result == PackageManager.PERMISSION_GRANTED) connectIfAllowed()
    }

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        app = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
            connectIfAllowed()
        } catch (_: Throwable) { }
    }

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
    fun hasPermission(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }
    fun isReady(): Boolean = try { remote?.ping() == true } catch (_: Throwable) { false }

    /** Dipanggil oleh tombol pengguna, tidak meminta izin diam-diam. */
    fun requestPermission() {
        try {
            if (!isRunning()) return
            if (hasPermission()) connectIfAllowed()
            else if (!Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) { }
    }

    @Synchronized private fun connectIfAllowed() {
        if (!initialized || !hasPermission() || remote != null || binding) return
        try {
            binding = true
            val args = Shizuku.UserServiceArgs(ComponentName(app, ShellInputService::class.java))
                .daemon(false).processNameSuffix("gen_input").tag("gen_input_v2").version(2)
            Shizuku.bindUserService(args, connection)
        } catch (_: Throwable) { binding = false }
    }

    private fun submit(block: (IInputBridge) -> Unit): Boolean {
        if (!isReady()) return false
        worker.execute {
            val target = remote ?: return@execute
            try { block(target) } catch (_: Throwable) { remote = null }
        }
        return true
    }

    fun tap(x: Float, y: Float, ms: Long): Boolean = submit { it.tap(x.toInt(), y.toInt(), ms.toInt()) }
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean = submit {
        it.swipe(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt(), ms.toInt())
    }
    fun pointer(id: Int, action: Int, x: Float, y: Float): Boolean = submit {
        it.pointer(id, action, x, y)
    }
    fun releaseAll() { submit { it.releaseAll() } }
}
