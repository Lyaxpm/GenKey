package com.gen.keymapper

import org.junit.Assert.*
import org.junit.Test

class ProfileCodecTest {
    @Test fun oldVersionImportsAndNewActionsRoundTrip() {
        val old = """{"version":2,"profiles":[{"pkg":"com.example.game","name":"Game","keys":[{"keyCode":29,"x":3,"y":-1}]}]}"""
        val p = ProfileCodec.decode(old).values.single()
        assertEquals(1f, p.keys[0].x, 0f); assertEquals(0f, p.keys[0].y, 0f)
        p.keys.add(KeyMap(action = "repeat", keyCode = 46, intervalMs = 200))
        p.keys.add(KeyMap(action = "toggle", keyCode = 189))
        p.joys.add(JoyMap()); p.camera = CameraMap()
        val result = ProfileCodec.decode(ProfileCodec.encode(listOf(p))).values.single()
        assertEquals(200, result.keys[1].intervalMs.toInt())
        assertEquals("toggle", result.keys[2].action)
        assertEquals(51, result.joys[0].up)
        assertNotNull(result.camera)
    }
    @Test(expected = IllegalArgumentException::class) fun unknownVersionRejected() {
        ProfileCodec.decode("""{"version":99,"profiles":[]}""")
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateControlsRejected() {
        ProfileCodec.decode("""{"profiles":[{"pkg":"*","name":"Game","keys":[{"id":"same"},{"id":"same"}]}]}""")
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCoordinateRejected() {
        val p = Profile("*", "Game", mutableListOf(KeyMap(x = Float.NaN)))
        ProfileCodec.encode(listOf(p))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidActionRejected() {
        ProfileCodec.decode("""{"profiles":[{"pkg":"*","name":"Game","keys":[{"action":"unsupported"}]}]}""")
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateProfilesRejected() {
        ProfileCodec.decode("""{"profiles":[{"pkg":"*","name":"One"},{"pkg":"*","name":"Two"}]}""")
    }
}
