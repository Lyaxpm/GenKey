package com.gen.keymapper

import android.os.Looper
import android.os.Process
import java.net.InetSocketAddress
import java.net.Socket

/** Started explicitly by an already-authorized shell in Brevent Exec Command. */
object LocalInputMain {
    @JvmStatic fun main(args: Array<String>) {
        try {
            check(Process.myUid() == 2000 || Process.myUid() == 0) { "An authorized shell is required" }
            require(args.size == 2)
            val port = args[0].toInt(); val secret = args[1]
            require(port in 1..65535 && LocalBridgeAuth.valid(secret))
            if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 5000)
                socket.soTimeout = 5000
                socket.tcpNoDelay = true
                val input = socket.getInputStream().bufferedReader()
                val output = socket.getOutputStream().bufferedWriter()
                val challenge = (InputProtocol.readLine(input) ?: error("No challenge")).split(' ')
                check(challenge.size == 2 && challenge[0] == "GENKEY_CHALLENGE" && LocalBridgeAuth.valid(challenge[1]))
                val nonce = LocalBridgeAuth.nonce()
                InputProtocol.write(output, "GENKEY_AUTH $nonce ${LocalBridgeAuth.proof(secret, "client:${challenge[1]}:$nonce")}")
                val server = (InputProtocol.readLine(input) ?: error("No server proof")).split(' ')
                check(server.size == 2 && server[0] == "GENKEY_SERVER" &&
                    LocalBridgeAuth.matches(LocalBridgeAuth.proof(secret, "server:${challenge[1]}:$nonce"), server[1])) {
                    "Server authentication failed"
                }
                socket.soTimeout = 0
                InputProtocol.serve(input, output)
            }
        } finally {
            // app_process must not keep Binder threads alive after the UI disconnects.
            kotlin.system.exitProcess(0)
        }
    }
}
