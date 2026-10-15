package com.gen.keymapper

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Allows deterministic tests of delayed taps, swipes and camera release. */
interface InputScheduler {
    fun post(task: Runnable)
    fun postDelayed(task: Runnable, delay: Long)
    fun removeCallbacks(task: Runnable)
    fun clear()
    fun now(): Long
}
class AndroidInputScheduler : InputScheduler {
    private val handler = Handler(Looper.getMainLooper())
    override fun post(task: Runnable) { handler.post(task) }
    override fun postDelayed(task: Runnable, delay: Long) { handler.postDelayed(task, delay) }
    override fun removeCallbacks(task: Runnable) { handler.removeCallbacks(task) }
    override fun clear() { handler.removeCallbacksAndMessages(null) }
    override fun now() = SystemClock.uptimeMillis()
}
