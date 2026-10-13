package com.gen.keymapper

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

/** Editor, previews and injection all use the full physical display coordinate space. */
internal object ScreenSpace {
    @Suppress("DEPRECATION")
    fun size(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            return b.width() to b.height()
        }
        val p = Point()
        wm.defaultDisplay.getRealSize(p)
        return p.x to p.y
    }
    fun edgeFlags() = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    fun configure(lp: WindowManager.LayoutParams) {
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        if (Build.VERSION.SDK_INT >= 30) lp.setFitInsetsTypes(0)
        if (Build.VERSION.SDK_INT >= 28)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}
