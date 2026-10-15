package com.gen.keymapper

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.MotionEvent
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

object ShizukuBridge {
    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, CONNECTING, READY, ERROR }
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 9911
    private val main = Handler(Looper.getMainLooper())
    private val checks = Executors.newCachedThreadPool()
    private val input = InputQueue()
    private lateinit var app: Context
    private var initialized = false
    private var generation = 0
    private var connection: ServiceConnection? = null
    private var args: Shizuku.UserServiceArgs? = null
    @Volatile private var remote: IInputBridge? = null
    @Volatile private var binding = false
    @Volatile var error = ""
        private set
    var listener: (() -> Unit)? = null
    private fun changed() { main.post { listener?.invoke() } }

    fun initialize(context: Context) {
        if (initialized) return
        app = context.applicationContext
        initialized = true
        Shizuku.addBinderReceivedListenerSticky { main.post { if (Backend.mode == Backend.Mode.SHIZUKU) { error = ""; connect() }; changed() } }
        Shizuku.addBinderDeadListener { main.post { disconnect(); changed() } }
        Shizuku.addRequestPermissionResultListener { request, result ->
            if (request == REQUEST_CODE) main.post {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    if (Backend.mode == Backend.Mode.SHIZUKU) reconnect()
                }
                else { error = "Izin ditolak. Izinkan GenKey di aplikasi Shizuku."; changed() }
            }
        }
    }

    fun isRunning() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun hasPermission() = runCatching {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    fun isInstalled() = runCatching { app.packageManager.getPackageInfo(SHIZUKU_PKG, 0); true }.getOrDefault(false)
    fun isReady() = remote?.asBinder()?.isBinderAlive == true
    fun state(): State = when {
        isReady() -> State.READY
        !isRunning() -> if (isInstalled()) State.NOT_RUNNING else State.NOT_INSTALLED
        !hasPermission() -> State.NO_PERMISSION
        error.isNotEmpty() -> State.ERROR
        else -> State.CONNECTING
    }

    fun requestPermission() {
        runCatching {
            if (!isRunning()) return
            if (hasPermission()) reconnect()
            else if (Shizuku.shouldShowRequestPermissionRationale()) {
                error = "Buka Shizuku > Aplikasi berizin, lalu izinkan GenKey."; changed()
            } else Shizuku.requestPermission(REQUEST_CODE)
        }.onFailure { error = it.message ?: it.toString(); changed() }
    }

    fun connect() {
        if (!initialized || isReady() || binding || error.isNotEmpty() || !hasPermission()) return
        binding = true
        val attempt = ++generation
        val serviceArgs = Shizuku.UserServiceArgs(ComponentName(app, ShellInputService::class.java))
            .daemon(false).processNameSuffix("gen_input").tag("gen_input").version(5)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                // Never perform synchronous input-service IPC on the UI thread.
                checks.execute {
                    val target = binder?.let { IInputBridge.Stub.asInterface(it) }
                    val result = runCatching {
                        check(target != null && target.ping()) { target?.diag() ?: "Binder input kosong" }
                        target
                    }
                    main.post {
                        if (generation != attempt) return@post
                        result.onSuccess {
                            remote = it
                            binding = false
                            runCatching { binder?.linkToDeath({ main.post {
                                if (generation == attempt) fail("Layanan input terputus. Tekan Coba lagi.")
                            } }, 0) }
                            changed()
                        }.onFailure { fail(it.message ?: it.toString()) }
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                main.post { if (generation == attempt) fail("Layanan input terputus. Tekan Coba lagi.") }
            }
        }
        args = serviceArgs
        connection = conn
        runCatching { Shizuku.bindUserService(serviceArgs, conn) }
            .onFailure { fail(it.message ?: it.toString()) }
        main.postDelayed({
            if (generation == attempt && binding) fail("Layanan input tidak menjawab dalam 12 detik. Periksa Shizuku dan USB debugging (Setelan keamanan). Untuk tanpa Wi-Fi pilih OTG offline.")
        }, 12_000)
        changed()
    }

    private fun fail(message: String) { disconnect(); error = message; changed() }
    fun disconnect() {
        generation++
        val old = remote
        remote = null
        binding = false
        if (old != null) input.submit { runCatching { old.releaseAll() } }
        val a = args
        val c = connection
        args = null; connection = null
        if (a != null && c != null) runCatching { Shizuku.unbindUserService(a, c, true) }
        error = ""
    }
    fun reconnect() { disconnect(); connect() }
    private fun submit(move: Int? = null, action: (IInputBridge) -> Unit): Boolean {
        val target = remote ?: return false
        input.submit(move) {
            if (remote === target) runCatching { action(target) }.onFailure { t ->
                main.post { if (remote === target) fail(t.message ?: "Input gagal") }
            }
        }
        return true
    }
    fun pointer(token: Int, action: Int, x: Float, y: Float) =
        submit(if (action == MotionEvent.ACTION_MOVE) token else null) { it.pointer(token, action, x, y) }
    fun releaseAll() { submit { it.releaseAll() } }
    fun key(code: Int) { submit { it.key(code) } }
    fun diagBlocking(): String? = runCatching { remote?.diag() }.getOrNull() ?: error
}
