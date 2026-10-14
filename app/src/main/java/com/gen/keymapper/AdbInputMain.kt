package com.gen.keymapper

import android.os.Looper

/** Runs as shell through app_process. No listening socket or exported command receiver. */
object AdbInputMain {
    @JvmStatic fun main(args: Array<String>) {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        val service = ShellInputService()
        try {
            check(service.ping()) { service.diag() }
            println("GENKEY_READY")
            System.out.flush()
            System.`in`.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val p = line.split(' ')
                    when (p[0]) {
                        "P" -> { require(p.size == 5); service.pointer(p[1].toInt(), p[2].toInt(), p[3].toFloat(), p[4].toFloat()) }
                        "R" -> service.releaseAll()
                        "K" -> { require(p.size == 2); service.key(p[1].toInt()) }
                        "D" -> { println("GENKEY_DIAG ${service.diag()}"); System.out.flush() }
                        else -> error("Unknown input command")
                    }
                }
            }
        } catch (t: Throwable) {
            println("GENKEY_ERROR ${t.message}")
            System.out.flush()
        } finally {
            service.releaseAll()
        }
    }
}
