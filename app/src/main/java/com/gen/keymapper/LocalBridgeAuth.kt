package com.gen.keymapper

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Mutual challenge response; never send the activation secret on the socket. */
internal object LocalBridgeAuth {
    fun nonce(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    fun valid(value: String) = value.matches(Regex("[a-f0-9]{64}"))
    fun proof(secret: String, message: String): String {
        require(valid(secret))
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
            doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
    fun matches(expected: String, supplied: String): Boolean = valid(supplied) &&
        MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), supplied.toByteArray(Charsets.US_ASCII))
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    fun command(apk: String, port: Int, secret: String): String {
        require(port in 1..65535 && valid(secret))
        return "CLASSPATH=${quote(apk)} /system/bin/setsid /system/bin/app_process / " +
            "com.gen.keymapper.LocalInputMain $port $secret </dev/null >/dev/null 2>&1 &"
    }
}
