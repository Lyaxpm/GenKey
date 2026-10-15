package com.gen.keymapper

import org.junit.Assert.*
import org.junit.Test

class EditorBoundsTest {
    @Test fun toolbarStaysInsideLandscapeInsets() {
        assertEquals(52f, EditorBounds.clamp(-100f, 420f, 52f, 760f))
        assertEquals(340f, EditorBounds.clamp(800f, 420f, 52f, 760f))
    }
    @Test fun oversizePanelDoesNotCrashAfterRotation() {
        assertEquals(16f, EditorBounds.clamp(999f, 420f, 16f, 300f))
    }
    @Test fun chipRemainsFullyVisible() {
        assertEquals(19f, EditorBounds.center(0f, 19f, 360f))
        assertEquals(341f, EditorBounds.center(360f, 19f, 360f))
    }
}
