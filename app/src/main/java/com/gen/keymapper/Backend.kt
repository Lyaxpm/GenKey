package com.gen.keymapper

import android.content.Context

/** Single routing point: a mapping session always uses exactly one transport. */
object Backend {
    enum class Mode { SHIZUKU, PAIRING, OFFLINE, BREVENT }
    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, CONNECTING, READY, ERROR }
    const val SHIZUKU_PKG = ShizukuBridge.SHIZUKU_PKG
    private lateinit var app: Context
    private var initialized = false
    var mode = Mode.SHIZUKU
        private set
    var listener: (() -> Unit)? = null
    val error: String get() = when (mode) {
        Mode.SHIZUKU -> ShizukuBridge.error
        Mode.BREVENT -> BreventBackend.message
        Mode.PAIRING -> AdbBackend.message
        Mode.OFFLINE -> OfflineInputService.instance?.lastError ?: "Aktifkan layanan GenKey di Pengaturan Aksesibilitas."
    }
    fun initialize(context: Context) {
        if (initialized) return
        app = context.applicationContext
        initialized = true
        mode = runCatching { Mode.valueOf(app.getSharedPreferences("backend", 0).getString("mode", "SHIZUKU")!!) }.getOrDefault(Mode.SHIZUKU)
        ShizukuBridge.initialize(app)
        AdbBackend.initialize(app)
        BreventBackend.initialize(app)
        BreventBackend.listener = { listener?.invoke() }
        ShizukuBridge.listener = { listener?.invoke() }
        AdbBackend.listener = { listener?.invoke() }
    }
    fun select(value: Mode) {
        if (mode == value) return
        // Stop mapping before switching to avoid carrying held state across transports.
        releaseAll()
        app.stopService(android.content.Intent(app, MapperService::class.java))
        ShizukuBridge.disconnect()
        AdbBackend.disconnect()
        BreventBackend.disconnect()
        app.stopService(android.content.Intent(app, BreventSessionService::class.java))
        mode = value
        app.getSharedPreferences("backend", 0).edit().putString("mode", mode.name).apply()
        connect()
        listener?.invoke()
    }
    fun state(): State = when (mode) {
        Mode.BREVENT -> if (BreventBackend.ready) State.READY else State.NOT_RUNNING
        Mode.SHIZUKU -> State.valueOf(ShizukuBridge.state().name)
        Mode.PAIRING -> if (AdbBackend.ready) State.READY else if (AdbBackend.busy) State.CONNECTING else State.ERROR
        Mode.OFFLINE -> if (OfflineInputService.instance != null) State.READY else State.NO_PERMISSION
    }
    fun isReady() = state() == State.READY
    fun connect() { when (mode) {
        Mode.SHIZUKU -> ShizukuBridge.connect()
        Mode.PAIRING -> AdbBackend.connectSaved()
        Mode.BREVENT, Mode.OFFLINE -> Unit
    } }
    fun reconnect() { when (mode) {
        Mode.SHIZUKU -> ShizukuBridge.reconnect()
        Mode.PAIRING -> AdbBackend.connectSaved(force = true)
        Mode.BREVENT, Mode.OFFLINE -> listener?.invoke()
    } }
    fun requestPermission() { if (mode == Mode.SHIZUKU) ShizukuBridge.requestPermission() }
    fun pointer(token: Int, action: Int, x: Float, y: Float): Boolean = when (mode) {
        Mode.SHIZUKU -> ShizukuBridge.pointer(token, action, x, y)
        Mode.PAIRING -> AdbBackend.pointer(token, action, x, y)
        Mode.BREVENT -> BreventBackend.pointer(token, action, x, y)
        Mode.OFFLINE -> OfflineInputService.instance?.pointer(token, action, x, y) ?: false
    }
    fun releaseAll() { when (mode) {
        Mode.SHIZUKU -> ShizukuBridge.releaseAll()
        Mode.PAIRING -> AdbBackend.releaseAll()
        Mode.BREVENT -> BreventBackend.releaseAll()
        Mode.OFFLINE -> OfflineInputService.instance?.releaseAll()
    } }
    fun key(code: Int) { when (mode) {
        Mode.SHIZUKU -> ShizukuBridge.key(code)
        Mode.PAIRING -> AdbBackend.key(code)
        Mode.BREVENT -> BreventBackend.key(code)
        Mode.OFFLINE -> OfflineInputService.instance?.key(code)
    } }
    fun diagBlocking(): String? = when (mode) {
        Mode.SHIZUKU -> ShizukuBridge.diagBlocking()
        Mode.PAIRING -> AdbBackend.diagBlocking()
        Mode.BREVENT -> BreventBackend.diagBlocking()
        Mode.OFFLINE -> OfflineInputService.instance?.diag() ?: error
    }
}
