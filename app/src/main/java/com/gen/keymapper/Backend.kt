package com.gen.keymapper

import android.content.Context

/** Single routing point: a mapping session always uses exactly one transport. */
object Backend {
    enum class Mode { SHIZUKU, PAIRING }
    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, CONNECTING, READY, ERROR }
    const val SHIZUKU_PKG = ShizukuBridge.SHIZUKU_PKG
    private lateinit var app: Context
    private var initialized = false
    var mode = Mode.SHIZUKU
        private set
    var listener: (() -> Unit)? = null
    val error: String get() = if (mode == Mode.SHIZUKU) ShizukuBridge.error else AdbBackend.message
    fun initialize(context: Context) {
        if (initialized) return
        app = context.applicationContext
        initialized = true
        mode = runCatching { Mode.valueOf(app.getSharedPreferences("backend", 0).getString("mode", "SHIZUKU")!!) }.getOrDefault(Mode.SHIZUKU)
        ShizukuBridge.initialize(app)
        AdbBackend.initialize(app)
        ShizukuBridge.listener = { listener?.invoke() }
        AdbBackend.listener = { listener?.invoke() }
    }
    fun select(value: Mode) {
        if (mode == value) return
        // Stop mapping before switching to avoid carrying held state across transports.
        app.stopService(android.content.Intent(app, MapperService::class.java))
        ShizukuBridge.disconnect()
        AdbBackend.disconnect()
        mode = value
        app.getSharedPreferences("backend", 0).edit().putString("mode", mode.name).apply()
        connect()
        listener?.invoke()
    }
    fun state(): State = if (mode == Mode.SHIZUKU) State.valueOf(ShizukuBridge.state().name)
        else if (AdbBackend.ready) State.READY else if (AdbBackend.busy) State.CONNECTING else State.ERROR
    fun isReady() = if (mode == Mode.SHIZUKU) ShizukuBridge.isReady() else AdbBackend.ready
    fun connect() { if (mode == Mode.SHIZUKU) ShizukuBridge.connect() }
    fun reconnect() { if (mode == Mode.SHIZUKU) ShizukuBridge.reconnect() }
    fun requestPermission() { if (mode == Mode.SHIZUKU) ShizukuBridge.requestPermission() }
    fun pointer(token: Int, action: Int, x: Float, y: Float): Boolean =
        if (mode == Mode.SHIZUKU) ShizukuBridge.pointer(token, action, x, y) else AdbBackend.pointer(token, action, x, y)
    fun releaseAll() { if (mode == Mode.SHIZUKU) ShizukuBridge.releaseAll() else AdbBackend.releaseAll() }
    fun key(code: Int) { if (mode == Mode.SHIZUKU) ShizukuBridge.key(code) else AdbBackend.key(code) }
    fun diagBlocking(): String? = if (mode == Mode.SHIZUKU) ShizukuBridge.diagBlocking() else AdbBackend.diagBlocking()
}
