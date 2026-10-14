package com.gen.keymapper

import java.util.ArrayDeque
import java.util.concurrent.Executors

/** Coalesce adjacent MOVE samples only. DOWN/UP are ordering barriers. */
class InputQueue {
    private data class Job(val moveToken: Int?, val run: () -> Unit)
    private val jobs = ArrayDeque<Job>()
    private val worker = Executors.newSingleThreadExecutor()
    private var draining = false

    @Synchronized fun submit(moveToken: Int? = null, run: () -> Unit) {
        if (moveToken != null && jobs.peekLast()?.moveToken == moveToken) jobs.removeLast()
        jobs.addLast(Job(moveToken, run))
        if (!draining) {
            draining = true
            worker.execute {
                while (true) {
                    val job = synchronized(this) {
                        if (jobs.isEmpty()) { draining = false; null } else jobs.removeFirst()
                    } ?: break
                    try { job.run() } catch (_: Exception) { /* transport reports its own error */ }
                }
            }
        }
    }
}
