package com.gen.keymapper

import org.junit.Assert.*
import org.junit.Test

class TouchStateTest {
    @Test fun simultaneousKeysPreserveIdsAfterMiddlePointerReleased() {
        val s = TouchState()
        assertEquals(0, s.down(10, 50f, 60f)!!.action)
        assertEquals(5 or (1 shl 8), s.down(20, 70f, 80f)!!.action)
        s.down(30, 90f, 100f)
        val release = s.up(20)!!
        assertEquals(6 or (1 shl 8), release.action)
        assertEquals(listOf(0, 1, 2), release.points.map { it.id })
        val move = s.move(30, 95f, 105f)!!
        assertEquals(listOf(0, 2), move.points.map { it.id })
        assertEquals(95f, move.points.last().x, 0f)
        assertEquals(6, s.up(10)!!.action)
        assertEquals(1, s.up(30)!!.action)
        assertEquals(0, s.size)
    }
    @Test fun cancellationDropsLateMovesAndUps() {
        val s = TouchState(); s.down(1, 0f, 0f); s.down(2, 1f, 1f)
        assertEquals(3, s.cancel()!!.action)
        assertNull(s.move(1, 4f, 4f)); assertNull(s.up(2)); assertNull(s.cancel())
        assertEquals(0, s.down(1, 5f, 5f)!!.points.single().id)
    }
    @Test fun rejectsDuplicateInvalidAndExcessPointers() {
        val s = TouchState()
        assertNull(s.down(100, Float.NaN, 0f))
        repeat(10) { assertNotNull(s.down(it, it.toFloat(), 0f)) }
        assertNull(s.down(11, 10f, 10f)); assertNull(s.down(0, 10f, 10f))
        assertEquals(10, s.size)
        assertNull(s.move(1, Float.POSITIVE_INFINITY, 0f))
    }
    @Test fun diagonalDirectionAndDeadZone() {
        val (x, y) = InputMath.direction(1f, 1f)
        assertEquals(1f, x * x + y * y, .0001f)
        assertEquals(0f to 0f, InputMath.direction(.1f, .1f, .18f))
        assertEquals(.5f to 0f, InputMath.direction(.5f, 0f, .18f))
        assertEquals(0f to 0f, InputMath.direction(Float.NaN, 0f))
    }
}
