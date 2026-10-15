package com.gen.keymapper

import org.junit.Assert.*
import org.junit.Test

class GestureQueueTest {
    @Test fun releaseOneFingerPreservesOtherFinger() {
        val q = GestureQueue()
        q.offer(1, 0, 10f, 20f); q.offer(2, 0, 30f, 40f)
        q.completed(q.next())
        q.offer(1, 1, 10f, 20f); q.offer(2, 2, 50f, 60f)
        val frame = q.next()
        assertTrue(frame.first { it.token == 1 }.ending)
        assertFalse(frame.first { it.token == 2 }.ending)
        q.completed(frame)
        assertEquals(listOf(2), q.next().map { it.token })
        assertEquals(50f, q.next().single().x)
    }
    @Test fun quickTapIsNotLostBetweenFrames() {
        val q = GestureQueue()
        q.offer(7, 0, 1f, 2f); q.offer(7, 1, 1f, 2f)
        val tap = q.next()
        assertEquals(1, tap.size); assertTrue(tap.single().ending)
        q.completed(tap)
        assertTrue(q.next().isEmpty())
    }
    @Test fun repeatedTapKeepsTwoDistinctPresses() {
        val q = GestureQueue()
        repeat(2) { q.offer(1, 0, 10f, 20f); q.offer(1, 1, 10f, 20f) }
        val a = q.next(); assertTrue(a.single().ending); q.completed(a)
        val b = q.next(); assertTrue(b.single().ending); q.completed(b)
        assertTrue(q.next().isEmpty())
    }
    @Test fun releaseAllDuringFlightFinishesContinuationsAndDropsPendingPress() {
        val q = GestureQueue()
        q.offer(1, 0, 10f, 20f)
        val inflight = q.next()
        q.offer(2, 0, 30f, 40f)
        q.releaseAll(); q.completed(inflight)
        val end = q.next()
        assertEquals(1, end.size); assertTrue(end.single().ending)
        q.completed(end); assertTrue(q.next().isEmpty())
    }
    @Test fun duplicateDownDoesNotBlockRelease() {
        val q = GestureQueue()
        q.offer(1, 0, 1f, 2f); q.completed(q.next())
        q.offer(1, 0, 1f, 2f); q.offer(1, 1, 1f, 2f)
        val end = q.next(); assertTrue(end.single().ending)
        q.completed(end); assertTrue(q.next().isEmpty())
    }
    @Test fun coalescesMovesAndRejectsInvalidInput() {
        val q = GestureQueue()
        q.offer(1, 0, 0f, 0f)
        repeat(2000) { assertTrue(q.offer(1, 2, it.toFloat(), 4f)) }
        assertEquals(1999f, q.next().single().x)
        assertFalse(q.offer(2, 0, Float.NaN, 4f))
    }
}
