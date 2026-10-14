package com.gen.keymapper

/** Main-thread lifecycle: a late callback cannot revive an expired session. */
internal class ConnectionAttempt {
    enum class Phase { IDLE, CONNECTING, READY, FAILED }
    @Volatile var phase = Phase.IDLE; private set
    private var generation = 0L
    fun begin(): Long? {
        if (phase == Phase.CONNECTING || phase == Phase.READY) return null
        phase = Phase.CONNECTING
        return ++generation
    }
    fun complete(id: Long): Boolean {
        if (id != generation || phase != Phase.CONNECTING) return false
        phase = Phase.READY
        return true
    }
    fun expire(id: Long): Boolean {
        if (id != generation || phase != Phase.CONNECTING) return false
        phase = Phase.FAILED
        return true
    }
    fun fail(id: Long): Boolean {
        if (id != generation || (phase != Phase.CONNECTING && phase != Phase.READY)) return false
        phase = Phase.FAILED
        return true
    }
    fun reset() { generation++; phase = Phase.IDLE }
}
