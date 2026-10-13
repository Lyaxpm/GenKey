package com.gen.keymapper

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

object ShizukuBridge {
    private const val REQUEST = 9911
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var remote: IInputBridge? = null
    @Volatile private var ready = false
    @Volatile var error: String = ""; private set
    @Volatile var diagnostics: String = "Belum ada sesi input"; private set
    private var initialized = false
    private var binding = false
    private lateinit var app: Context
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val target = IInputBridge.Stub.asInterface(binder) ?: return
            binding = false
            remote = target
            worker.execute {
                try {
                    val valid = target.ping()
                    // Ignore replies from an older service instance.
                    if (remote === target) {
                        ready = valid
                        error = if (valid) "" else "Backend InputManager tidak tersedia"
                        diagnostics = target.diagnostics()
                    }
                } catch (e: Exception) { failed(target, e) }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { disconnected() }
        override fun onBindingDied(name: ComponentName?) { disconnected() }
    }
    private fun disconnected() { remote = null; ready = false; binding = false }
    private val received = Shizuku.OnBinderReceivedListener { connectIfAllowed() }
    private val dead = Shizuku.OnBinderDeadListener { disconnected(); error = "Shizuku terputus" }
    private val permission = Shizuku.OnRequestPermissionResultListener { request, result ->
        if (request == REQUEST) {
            if (result == PackageManager.PERMISSION_GRANTED) connectIfAllowed()
            else error = "Izin Shizuku ditolak. Izinkan GenKey di aplikasi Shizuku."
        }
    }
    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        initialized = true; app = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        connectIfAllowed()
    }
    fun isRunning() = try { Shizuku.pingBinder() } catch (_: Exception) { false }
    fun hasPermission() = try { isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
        catch (_: Exception) { false }
    fun isReady() = ready && remote?.asBinder()?.isBinderAlive == true
    fun requestPermission() {
        try {
            if (!isRunning()) { error = "Mulai Shizuku terlebih dahulu"; return }
            if (hasPermission()) connectIfAllowed() else Shizuku.requestPermission(REQUEST)
        } catch (e: Exception) { error = e.message ?: "Izin tidak tersedia" }
    }
    @Synchronized private fun connectIfAllowed() {
        if (!initialized || !hasPermission() || remote != null || binding) return
        try {
            binding = true
            Shizuku.bindUserService(Shizuku.UserServiceArgs(ComponentName(app, ShellInputService::class.java))
                .daemon(false).processNameSuffix("gen_input").tag("gen_input_v3").version(3), connection)
            main.postDelayed({ if (binding) { binding = false; error = "Koneksi timeout. Tekan Hubungkan lagi." } }, 8000)
        } catch (e: Exception) { binding = false; error = e.message ?: "Koneksi gagal" }
    }
    private fun failed(target: IInputBridge, e: Exception) {
        if (remote === target) { disconnected(); error = e.message ?: "Backend terputus" }
    }
    private fun submit(block: (IInputBridge) -> Unit): Boolean {
        val target = remote ?: return false
        if (!isReady()) return false
        worker.execute {
            if (remote !== target) return@execute
            try { block(target) } catch (e: Exception) { failed(target, e) }
        }
        return true
    }
    fun heartbeat() {
        submit { it.heartbeat(); diagnostics = it.diagnostics() }
    }
    fun tap(x: Float, y: Float, ms: Long) = submit { it.tap(x.toInt(), y.toInt(), ms.toInt()) }
    fun swipe(x: Float, y: Float, endX: Float, endY: Float, ms: Long) = submit {
        it.swipe(x.toInt(), y.toInt(), endX.toInt(), endY.toInt(), ms.toInt())
    }
    fun pointer(token: Int, action: Int, x: Float, y: Float) = submit { it.pointer(token, action, x, y) }
    fun releaseAll() { submit { it.releaseAll() } }
}
