package com.gen.keymapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InputQueueTest {
    @Test fun coalescesMovesWithoutCrossingDownUpOrOtherPointers() {
        val queue = InputQueue()
        val started = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val done = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        queue.submit { started.countDown(); unblock.await(2, TimeUnit.SECONDS) }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        queue.submit { events.add("down") }
        queue.submit(1) { events.add("stale") }
        queue.submit(1) { events.add("latest") }
        queue.submit(2) { events.add("other") }
        queue.submit(1) { events.add("after-other") }
        queue.submit { events.add("up") }
        queue.submit(1) { events.add("after-up") }
        queue.submit { done.countDown() }
        unblock.countDown()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("down", "latest", "other", "after-other", "up", "after-up"), events)
    }
}
