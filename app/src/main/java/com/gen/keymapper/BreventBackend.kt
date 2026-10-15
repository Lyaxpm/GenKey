package com.gen.keymapper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import java.io.BufferedWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Per-session loopback transport. No Wi-Fi binding, public port, or exported receiver. */
object BreventBackend {
    const val PACKAGE = "me.piebridge.brevent"
    const val ACTION = "me.piebridge.brevent.intent.action.COMMAND"
    const val EXTRA = "me.piebridge.brevent.intent.extra.COMMAND"
    private class Session(val server: ServerSocket, val secret: String) {
        @Volatile var socket: Socket? = null
        @Volatile var output: BufferedWriter? = null
        @Volatile var active = true
        val replies = LinkedBlockingQueue<String>()
        fun close() { active = false; runCatching { server.close() }; runCatching { socket?.close() } }
    }
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newCachedThreadPool()
    private val input = InputQueue()
    @Volatile private var session: Session? = null
    @Volatile var ready = false
        private set
    @Volatile var message = "Siapkan perintah lalu jalankan melalui Brevent yang sudah aktif."
        private set
    var listener: (() -> Unit)? = null
    fun initialize(context: Context) { app = context.applicationContext }
    private fun changed() { main.post { listener?.invoke(); BreventSessionService.instance?.refresh() } }

    @Synchronized fun disconnect() {
        ready = false
        val old = session
        session = null
        old?.active = false
        if (old != null) workers.execute { old.close() }
        message = "Sesi Brevent dihentikan. Siapkan perintah baru untuk mengaktifkan lagi."
        changed()
    }

    /** Called only by the user's Prepare/Open/Copy activation action. */
    @Synchronized fun prepare(): String {
        if (session == null) {
            check(java.io.File("/system/bin/setsid").exists()) { "Perangkat tidak menyediakan setsid. Gunakan OTG offline." }
            val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
            val s = Session(server, LocalBridgeAuth.nonce())
            session = s
            message = "Menunggu perintah Brevent. Kembali ke GenKey setelah menjalankannya."
            workers.execute { listen(s) }
            changed()
        }
        val s = session!!
        return LocalBridgeAuth.command(app.applicationInfo.sourceDir, s.server.localPort, s.secret)
    }

    private fun listen(s: Session) {
        try {
            while (s.active && session === s) {
                val socket = s.server.accept()
                // Authenticate before exposing the input transport. Invalid clients cannot replace a session.
                s.socket = socket
                try {
                    socket.soTimeout = 3000
                    socket.tcpNoDelay = true
                    val reader = socket.getInputStream().bufferedReader()
                    val writer = socket.getOutputStream().bufferedWriter()
                    val nonce = LocalBridgeAuth.nonce()
                    InputProtocol.write(writer, "GENKEY_CHALLENGE $nonce")
                    val auth = (InputProtocol.readLine(reader) ?: "").split(' ')
                    check(auth.size == 3 && auth[0] == "GENKEY_AUTH" && LocalBridgeAuth.valid(auth[1]) &&
                        LocalBridgeAuth.matches(LocalBridgeAuth.proof(s.secret, "client:$nonce:${auth[1]}"), auth[2]))
                    InputProtocol.write(writer, "GENKEY_SERVER ${LocalBridgeAuth.proof(s.secret, "server:$nonce:${auth[1]}")}")
                    socket.soTimeout = 12_000
                    val reply = InputProtocol.readLine(reader)
                    check(reply == "GENKEY_READY") { reply ?: "Helper tidak menjawab" }
                    if (!s.active || session !== s) break
                    socket.soTimeout = 0
                    s.output = writer
                    main.post { if (session === s && s.active) {
                        ready = true
                        message = "Brevent terhubung lokal. Jalankan Uji sentuhan."
                        changed()
                    } }
                    // No replacement clients; retain the listener only so the command retains its port.
                    while (s.active) {
                        val line = InputProtocol.readLine(reader) ?: break
                        if (line.startsWith("GENKEY_DIAG ")) s.replies.offer(line)
                        else if (line.startsWith("GENKEY_ERROR ")) error(line.removePrefix("GENKEY_ERROR "))
                    }
                    break
                } catch (e: Exception) {
                    if (s.output != null) throw e
                    // Keep accepting after bad authentication; the next legitimate launch may still succeed.
                    main.post { if (session === s && s.active) {
                        message = if (e.message?.startsWith("GENKEY_ERROR ") == true) e.message!!.removePrefix("GENKEY_ERROR ")
                            else "Aktivasi belum selesai. Periksa Exec Command dan jalankan ulang; gunakan Uji sentuhan setelah terhubung."
                        changed()
                    } }
                } finally { runCatching { socket.close() } }
            }
        } catch (e: Exception) {
            if (s.active) main.post { if (session === s) { message = e.message ?: "Koneksi lokal gagal"; changed() } }
        } finally {
            s.active = false
            s.close()
            main.post { if (session === s) {
                ready = false
                session = null
                message = "Layanan lokal berhenti. Buka Aktivasi Brevent dan jalankan perintah baru."
                changed()
                BreventSessionService.instance?.stopSelf()
            } }
        }
    }
    private fun send(command: String, move: Int? = null): Boolean {
        val s = session ?: return false
        if (!ready || !s.active) return false
        input.submit(move) {
            if (session === s && s.active) runCatching { InputProtocol.write(s.output!!, command) }
                .onFailure { s.close() }
        }
        return true
    }
    fun pointer(token: Int, action: Int, x: Float, y: Float) = send("P $token $action $x $y", if (action == MotionEvent.ACTION_MOVE) token else null)
    fun releaseAll() { send("R") }
    fun key(code: Int) { send("K $code") }
    @Synchronized fun diagBlocking(): String {
        val s = session ?: return message
        if (!send("D")) return message
        return s.replies.poll(3, TimeUnit.SECONDS)?.removePrefix("GENKEY_DIAG ") ?: "Diagnosa lokal timeout"
    }
}
