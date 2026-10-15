package com.gen.keymapper

import java.io.BufferedReader
import java.io.BufferedWriter

/** Only input operations; no arbitrary shell commands or file access. */
internal object InputProtocol {
    fun readLine(input: BufferedReader): String? {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (line.isEmpty()) null else error("Truncated message")
            if (c == 10) return line.toString().removeSuffix("\r")
            check(line.length < 2048) { "Message too long" }
            line.append(c.toChar())
        }
    }
    fun write(output: BufferedWriter, message: String) { output.write(message); output.newLine(); output.flush() }
    fun serve(input: BufferedReader, output: BufferedWriter) {
        val service = ShellInputService()
        try {
            check(service.ping()) { service.diag() }
            write(output, "GENKEY_READY")
            while (true) {
                val p = (readLine(input) ?: break).split(' ')
                when (p[0]) {
                    "P" -> {
                        require(p.size == 5)
                        val action = p[2].toInt(); val x = p[3].toFloat(); val y = p[4].toFloat()
                        require(action in 0..3 && x.isFinite() && y.isFinite() && x >= 0 && y >= 0)
                        service.pointer(p[1].toInt(), action, x, y)
                    }
                    "R" -> service.releaseAll()
                    "K" -> { require(p.size == 2); service.key(p[1].toInt()) }
                    "D" -> write(output, "GENKEY_DIAG ${service.diag().replace('\n', ' ')}")
                    else -> error("Unknown input command")
                }
            }
        } catch (t: Throwable) {
            runCatching { write(output, "GENKEY_ERROR ${(t.message ?: t.javaClass.simpleName).replace('\n', ' ')}") }
        } finally { service.releaseAll() }
    }
}
