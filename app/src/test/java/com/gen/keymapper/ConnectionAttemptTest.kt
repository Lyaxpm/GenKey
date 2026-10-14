package com.gen.keymapper

import org.junit.Assert.*
import org.junit.Test

class ConnectionAttemptTest {
    @Test fun repeatedConnectDoesNotReplacePendingAttempt() {
        val state = ConnectionAttempt()
        val id = state.begin()!!
        assertNull(state.begin())
        assertTrue(state.complete(id))
        assertNull(state.begin())
    }
    @Test fun timeoutCannotDisconnectSuccessfulSession() {
        val state = ConnectionAttempt()
        val id = state.begin()!!
        assertTrue(state.complete(id))
        assertFalse(state.expire(id))
        assertEquals(ConnectionAttempt.Phase.READY, state.phase)
    }
    @Test fun timedOutSessionCanRetryAndIgnoresOldCallbacks() {
        val state = ConnectionAttempt()
        val old = state.begin()!!
        assertTrue(state.expire(old))
        assertFalse(state.complete(old))
        val current = state.begin()!!
        assertFalse(state.expire(old))
        assertFalse(state.fail(old))
        assertFalse(state.complete(old))
        assertTrue(state.complete(current))
    }
    @Test fun deadBinderInvalidatesPendingAndReadySessions() {
        val state = ConnectionAttempt()
        val pending = state.begin()!!
        state.reset()
        assertFalse(state.complete(pending))
        val ready = state.begin()!!
        assertTrue(state.complete(ready))
        state.reset()
        assertFalse(state.fail(ready))
        assertNotNull(state.begin())
    }
    @Test fun backendFailureAndInjectionFailureBothAllowRetry() {
        val state = ConnectionAttempt()
        assertTrue(state.fail(state.begin()!!))
        val ready = state.begin()!!
        assertTrue(state.complete(ready))
        assertTrue(state.fail(ready))
        assertFalse(state.complete(ready))
        assertNotNull(state.begin())
    }
}
