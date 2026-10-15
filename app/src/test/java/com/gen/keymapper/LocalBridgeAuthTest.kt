package com.gen.keymapper

import org.junit.Assert.*
import org.junit.Test

class LocalBridgeAuthTest {
    @Test fun wrongSecretRoleAndChallengeCannotAuthenticate() {
        val key = "a".repeat(64)
        val expected = LocalBridgeAuth.proof(key, "client:nonce:peer")
        assertTrue(LocalBridgeAuth.matches(expected, LocalBridgeAuth.proof(key, "client:nonce:peer")))
        assertFalse(LocalBridgeAuth.matches(expected, LocalBridgeAuth.proof("b".repeat(64), "client:nonce:peer")))
        assertFalse(LocalBridgeAuth.matches(expected, LocalBridgeAuth.proof(key, "server:nonce:peer")))
        assertFalse(LocalBridgeAuth.matches(expected, LocalBridgeAuth.proof(key, "client:another:peer")))
        assertFalse(LocalBridgeAuth.matches(expected, "bad proof"))
    }
    @Test fun eachActivationGetsFreshSecret() {
        val values = (1..20).map { LocalBridgeAuth.nonce() }
        assertTrue(values.all { LocalBridgeAuth.valid(it) })
        assertEquals(20, values.toSet().size)
    }
    @Test fun commandQuotesApkPathAndValidatesArguments() {
        val command = LocalBridgeAuth.command("/data/app/a'b/base.apk", 12345, "a".repeat(64))
        assertTrue(command.startsWith("CLASSPATH='/data/app/a'\\''b/base.apk' "))
        assertTrue(command.contains("LocalInputMain 12345 "))
        assertFalse(command.contains("0.0.0.0"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInjectedSecret() {
        LocalBridgeAuth.command("/data/app/base.apk", 12345, "bad; command")
    }
}
