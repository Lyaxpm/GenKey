package com.gen.keymapper

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import io.github.muntashirakon.adb.AdbStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object AdbBackend {
    private class Session(val manager: AdbIdentity) {
        @Volatile var ended = false
        @Volatile var stream: AdbStream? = null
        @Volatile var output: OutputStream? = null
        val replies = LinkedBlockingQueue<String>()
        fun close() { runCatching { stream?.close() }; runCatching { manager.disconnect() } }
    }
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newCachedThreadPool()
    private val identityLock = Any()
    private val input = InputQueue()
    @Volatile private var session: Session? = null
    @Volatile private var generation = 0
    @Volatile var ready = false
        private set
    @Volatile var busy = false
        private set
    @Volatile var message = "Aktifkan Wireless debugging lalu lakukan pairing (Android 11+)."
        private set
    var listener: (() -> Unit)? = null
    fun initialize(context: Context) { app = context.applicationContext }
    private fun changed() { main.post { listener?.invoke() } }

    @Synchronized fun disconnect() {
        generation++
        ready = false; busy = false
        val old = session
        session = null
        if (old != null) workers.execute { old.close() }
        changed()
    }

    fun start(host: String, pairingPort: Int?, code: String, connectPort: Int, pair: Boolean) {
        if (busy) return
        if (Build.VERSION.SDK_INT < 30) { message = "Pairing mandiri membutuhkan Android 11+."; changed(); return }
        if (host.isBlank() || connectPort !in 1..65535 ||
            (pair && (pairingPort == null || pairingPort !in 1..65535 || !code.matches(Regex("[0-9]{6}"))))) {
            message = "Isi IP, port koneksi 1–65535, serta port pairing dan kode 6 digit bila melakukan pairing."
            changed(); return
        }
        disconnect()
        val attempt = generation
        busy = true
        message = if (pair) "Melakukan pairing…" else "Menghubungkan ADB…"
        changed()
        main.postDelayed({
            if (generation == attempt && busy) {
                disconnect(); message = "Koneksi melewati 30 detik. Periksa kode dan kedua port, lalu coba lagi."; changed()
            }
        }, 30_000)
        workers.execute {
            var current: Session? = null
            try {
                val manager = synchronized(identityLock) { AdbIdentity(app) }
                val s = Session(manager)
                current = s
                val accepted = synchronized(this) {
                    if (attempt == generation) { session = s; true } else false
                }
                if (!accepted) { s.close(); return@execute }
                if (pair) check(manager.pair(host, pairingPort!!, code)) { "Pairing ditolak. Gunakan kode terbaru." }
                check(attempt == generation) { "Koneksi dibatalkan" }
                check(manager.connect(host, connectPort)) { "ADB gagal tersambung. Gunakan port di halaman utama Wireless debugging." }
                check(attempt == generation) { "Koneksi dibatalkan" }
                // APK path is trusted PackageManager data, but quote it for the remote shell.
                val apk = "'" + app.applicationInfo.sourceDir.replace("'", "'\\''") + "'"
                val stream = manager.openStream("shell:CLASSPATH=$apk app_process / com.gen.keymapper.AdbInputMain")
                s.stream = stream
                s.output = stream.openOutputStream()
                workers.execute {
                    try {
                        stream.openInputStream().bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                if (line.startsWith("GENKEY_")) s.replies.offer(line)
                            }
                        }
                    } catch (_: Exception) { }
                    finally {
                        s.ended = true
                        s.replies.offer("GENKEY_ERROR Layanan ADB terputus")
                        main.post { if (session === s && ready) {
                            disconnect(); message = "Layanan ADB terputus. Hubungkan ulang."; changed()
                        } }
                    }
                }
                val reply = s.replies.poll(12, TimeUnit.SECONDS)
                check(reply == "GENKEY_READY") { reply ?: "Layanan input ADB tidak menjawab." }
                main.post {
                    if (generation != attempt) { workers.execute { s.close() }; return@post }
                    if (s.ended) {
                        disconnect(); message = "Layanan input ADB berhenti saat startup."; changed(); return@post
                    }
                    ready = true; busy = false; message = "Pairing mandiri terhubung. Jalankan Uji sentuhan."
                    app.getSharedPreferences("backend", 0).edit().putString("host", host).putInt("port", connectPort).apply()
                    changed()
                }
            } catch (t: Throwable) {
                current?.close()
                main.post { if (generation == attempt) {
                    disconnect(); message = t.message ?: t.javaClass.simpleName; changed()
                } }
            }
        }
    }

    private fun send(command: String, move: Int? = null): Boolean {
        val s = session ?: return false
        if (!ready) return false
        input.submit(move) {
            if (session === s) runCatching {
                s.output!!.write((command + "\n").toByteArray(Charsets.UTF_8)); s.output!!.flush()
            }.onFailure { main.post { if (session === s) {
                disconnect(); message = "Pengiriman input ADB gagal. Hubungkan ulang."; changed()
            } } }
        }
        return true
    }
    fun pointer(token: Int, action: Int, x: Float, y: Float) = send("P $token $action $x $y", if (action == MotionEvent.ACTION_MOVE) token else null)
    fun releaseAll() { send("R") }
    fun key(code: Int) { send("K $code") }
    @Synchronized fun diagBlocking(): String {
        val s = session ?: return message
        if (!send("D")) return message
        return s.replies.poll(3, TimeUnit.SECONDS)?.removePrefix("GENKEY_DIAG ") ?: "Diagnosa ADB timeout"
    }
}
