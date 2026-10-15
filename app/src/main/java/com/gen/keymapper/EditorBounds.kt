package com.gen.keymapper

internal object EditorBounds {
    fun clamp(value: Float, size: Float, min: Float, max: Float): Float =
        value.coerceIn(min, (max - size).coerceAtLeast(min))
    fun center(value: Float, radius: Float, extent: Float): Float =
        value.coerceIn(radius.coerceAtMost(extent / 2), (extent - radius).coerceAtLeast(extent / 2))
}
