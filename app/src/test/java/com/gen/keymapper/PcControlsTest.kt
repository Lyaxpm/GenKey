package com.gen.keymapper

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class PcControlsTest {
    private class Settings : EngineSettings {
        override val tapMs = 45
        override val cursorSpeed = 1f
        override val camSens = 1f
        override var aimKey = KeyEvent.KEYCODE_CTRL_LEFT
        override var freeCursor = true
        override var mouseOnlyInCursor = true
    }
    private class Scheduler : InputScheduler {
        data class Task(val at: Long, val run: Runnable)
        val tasks = mutableListOf<Task>()
        private var time = 0L
        override fun post(task: Runnable) = postDelayed(task, 0)
        override fun postDelayed(task: Runnable, delay: Long) { tasks.add(Task(time + delay, task)) }
        override fun removeCallbacks(task: Runnable) { tasks.removeAll { it.run === task } }
        override fun clear() { tasks.clear() }
        override fun now() = time
        fun advance(ms: Long) {
            val until = time + ms
            var guard = 1000
            while (tasks.any { it.at <= until }) {
                check(guard-- > 0)
                val task = tasks.minBy { it.at }; tasks.remove(task); time = task.at; task.run.run()
            }
            time = until
        }
    }
    private data class Touch(val action: Int, val x: Float, val y: Float)
    private class Output : PointerOut {
        val touches = mutableListOf<Touch>()
        val active = mutableSetOf<Int>()
        override fun pointer(token: Int, action: Int, x: Float, y: Float) {
            touches.add(Touch(action, x, y))
            if (action == 0) active.add(token) else if (action == 1 || action == 3) active.remove(token)
        }
        override fun releaseAll() { active.clear() }
    }
    private class Rig {
        val settings = Settings(); val scheduler = Scheduler(); val output = Output()
        val host = object : InputEngine.Host {
            override fun geo() = Geo(1000, 600, 0, 0)
            override fun onAimChanged(aim: Boolean) = Unit
            override fun onVisualChanged() = Unit
        }
        val engine = InputEngine(settings, host, output, scheduler)
        val profile = Profile("test.game", "Game", mutableListOf(
            Binding(id="joy", type=Bt.JOY, x=0.2f, y=0.7f, up=KeyEvent.KEYCODE_W, down=KeyEvent.KEYCODE_S, left=KeyEvent.KEYCODE_A, right=KeyEvent.KEYCODE_D),
            Binding(id="fire", key=KeyNames.MOUSE_LEFT, x=0.9f, y=0.8f, action=Act.HOLD),
            Binding(id="right", key=KeyNames.MOUSE_RIGHT, action=Act.HOLD),
            Binding(id="wheel", key=KeyNames.WHEEL_UP, action=Act.HOLD),
            Binding(id="cam", type=Bt.CAM, camHold=KeyNames.MOUSE_RIGHT),
            Binding(id="swipe", key=KeyEvent.KEYCODE_Q, action=Act.SWIPE),
            Binding(id="turbo", key=KeyEvent.KEYCODE_E, action=Act.TURBO)
        ), startAim=true)
        init { engine.setProfile(profile) }
        fun key(code: Int, down: Boolean) = engine.onButton(code, down)
        fun ctrl() { key(KeyEvent.KEYCODE_CTRL_LEFT, true); key(KeyEvent.KEYCODE_CTRL_LEFT, false) }
    }
    @Test fun ctrlTogglesOncePerPressIncludingBothCtrlKeys() {
        val r = Rig()
        r.key(KeyEvent.KEYCODE_CTRL_LEFT, true); assertFalse(r.engine.aim)
        repeat(5) { r.key(KeyEvent.KEYCODE_CTRL_LEFT, true) }; assertFalse(r.engine.aim)
        r.key(KeyEvent.KEYCODE_CTRL_RIGHT, true); assertFalse(r.engine.aim)
        r.key(KeyEvent.KEYCODE_CTRL_LEFT, false); r.key(KeyEvent.KEYCODE_CTRL_RIGHT, false)
        r.ctrl(); assertTrue(r.engine.aim)
    }
    @Test fun escHoldReleasesEverythingAndMouseClicksCursorPosition() {
        val r = Rig()
        r.key(KeyEvent.KEYCODE_W, true); r.engine.onMouseButton(KeyNames.MOUSE_LEFT, true)
        assertEquals(2, r.output.active.size)
        r.key(KeyEvent.KEYCODE_ESCAPE, true)
        assertFalse(r.engine.aim); assertTrue(r.output.active.isEmpty())
        r.engine.onMouseMove(100f, 20f)
        r.engine.onMouseButton(KeyNames.MOUSE_LEFT, true)
        val click = r.output.touches.last()
        assertEquals(600f, click.x, 0.01f); assertEquals(320f, click.y, 0.01f)
        r.key(KeyEvent.KEYCODE_ESCAPE, false)
        assertTrue(r.engine.aim); assertTrue(r.output.active.isEmpty())
        r.engine.onMouseButton(KeyNames.MOUSE_LEFT, false)
        assertTrue(r.output.active.isEmpty())
    }
    @Test fun escDominatesCtrlWhileHeldAndAlwaysReturnsToAim() {
        val r = Rig(); r.ctrl(); assertFalse(r.engine.aim)
        r.key(KeyEvent.KEYCODE_ESCAPE, true); r.ctrl(); assertFalse(r.engine.aim)
        r.key(KeyEvent.KEYCODE_ESCAPE, false); assertTrue(r.engine.aim)
    }
    @Test fun cursorDisablesKeyboardAndMouseMappingsButStillDrags() {
        val r = Rig(); r.ctrl(); r.output.touches.clear()
        r.key(KeyEvent.KEYCODE_W, true); r.key(KeyEvent.KEYCODE_Q, true)
        r.engine.onMouseButton(KeyNames.MOUSE_RIGHT, true); r.engine.onWheel(1f)
        r.scheduler.advance(500)
        assertTrue(r.output.touches.isEmpty()); assertFalse(r.engine.cameraActive())
        r.engine.onMouseButton(KeyNames.MOUSE_LEFT, true)
        r.engine.onMouseMove(5f, 8f)
        r.engine.onMouseButton(KeyNames.MOUSE_LEFT, false)
        assertEquals(listOf(0, 2, 1), r.output.touches.map { it.action })
    }
    @Test fun switchingModeCancelsSwipeTurboAndCameraTimers() {
        val r = Rig()
        r.key(KeyEvent.KEYCODE_Q, true); r.key(KeyEvent.KEYCODE_E, true)
        r.engine.onMouseMove(10f, 5f); r.scheduler.advance(20)
        r.ctrl(); r.output.touches.clear(); r.scheduler.advance(500)
        assertTrue(r.output.active.isEmpty()); assertTrue(r.output.touches.isEmpty())
    }
    @Test fun focusLossCannotLeaveEscLatchedOrResumeOldJoystick() {
        val r = Rig(); r.key(KeyEvent.KEYCODE_W, true); r.key(KeyEvent.KEYCODE_ESCAPE, true)
        r.engine.releaseAll(); assertTrue(r.engine.aim)
        r.key(KeyEvent.KEYCODE_ESCAPE, false); r.key(KeyEvent.KEYCODE_W, false)
        assertTrue(r.output.active.isEmpty()); r.ctrl(); assertFalse(r.engine.aim)
    }
    @Test fun keyboardMappingCanRemainEnabledInCursorWhenOptionDisabled() {
        val r = Rig(); r.settings.mouseOnlyInCursor = false; r.ctrl()
        r.key(KeyEvent.KEYCODE_W, true); assertEquals(1, r.output.active.size)
        r.key(KeyEvent.KEYCODE_W, false); assertTrue(r.output.active.isEmpty())
    }
    @Test fun escStillFreesCursorWithLegacyMouseOption() {
        val r = Rig(); r.settings.freeCursor = false
        r.key(KeyEvent.KEYCODE_ESCAPE, true)
        r.engine.onMouseButton(KeyNames.MOUSE_RIGHT, true); assertFalse(r.engine.cameraActive())
        r.engine.onMouseButton(KeyNames.MOUSE_LEFT, true)
        assertEquals(500f, r.output.touches.last().x, 0.01f)
    }
}
