package com.gen.keymapper

/** Android-independent pointer bookkeeping. Pointer IDs stay stable until UP. */
internal class TouchState {
    data class Point(val id: Int, val x: Float, val y: Float)
    data class Frame(val action: Int, val points: List<Point>)
    private val points = linkedMapOf<Int, Point>()
    val size get() = points.size

    fun down(token: Int, x: Float, y: Float): Frame? {
        if (!x.isFinite() || !y.isFinite() || token in points || size >= 10) return null
        val id = (0..31).first { candidate -> points.values.none { it.id == candidate } }
        points[token] = Point(id, x, y)
        return Frame(if (size == 1) 0 else 5 or ((size - 1) shl 8), points.values.toList())
    }
    fun move(token: Int, x: Float, y: Float): Frame? {
        if (!x.isFinite() || !y.isFinite()) return null
        val old = points[token] ?: return null
        points[token] = old.copy(x = x, y = y)
        return Frame(2, points.values.toList())
    }
    fun up(token: Int): Frame? {
        val index = points.keys.indexOf(token)
        if (index < 0) return null
        val frame = Frame(if (size == 1) 1 else 6 or (index shl 8), points.values.toList())
        points.remove(token)
        return frame
    }
    fun cancel(): Frame? {
        val frame = if (size == 0) null else Frame(3, points.values.toList())
        points.clear()
        return frame
    }
}

internal object InputMath {
    fun direction(x: Float, y: Float, deadZone: Float = 0f): Pair<Float, Float> {
        if (!x.isFinite() || !y.isFinite()) return 0f to 0f
        val length = kotlin.math.sqrt(x * x + y * y)
        if (length <= deadZone) return 0f to 0f
        val scale = if (length > 1f) 1f / length else 1f
        return x * scale to y * scale
    }
}
