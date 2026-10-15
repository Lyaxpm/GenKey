package com.gen.keymapper

import android.os.Looper

/** Runs as shell through app_process over the authorized ADB stream. */
object AdbInputMain {
    @JvmStatic fun main(args: Array<String>) {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        InputProtocol.serve(System.`in`.bufferedReader(), System.out.bufferedWriter())
    }
}
