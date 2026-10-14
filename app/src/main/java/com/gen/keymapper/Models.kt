package com.gen.keymapper

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Jenis kontrol. */
object Bt {
    const val KEY = "key"     // satu tombol -> sentuhan di titik (x,y)
    const val JOY = "joy"     // 4 tombol arah -> analog virtual
    const val CAM = "cam"     // gerakan mouse -> geser kamera
    const val AIM = "aim"     // tombol untuk berpindah mode Kursor <-> Bidik
}

/** Aksi untuk kontrol KEY. */
object Act {
    const val TAP = "tap"
    const val HOLD = "hold"
    const val TOGGLE = "toggle"
    const val TURBO = "turbo"
    const val SWIPE = "swipe"

    val all = listOf(TAP, HOLD, TOGGLE, TURBO, SWIPE)
    fun label(a: String) = when (a) {
        TAP -> "Tap"
        HOLD -> "Tahan"
        TOGGLE -> "Toggle"
        TURBO -> "Turbo"
        SWIPE -> "Geser"
        else -> a
    }
}

/** Kapan kontrol aktif. */
object Scope {
    const val ANY = 0
    const val AIM = 1
    const val CURSOR = 2
    fun label(s: Int) = when (s) {
        AIM -> "Mode Bidik"
        CURSOR -> "Mode Kursor"
        else -> "Selalu"
    }
}

/**
 * Satu kontrol di layar. Semua posisi berupa fraksi layar (0..1) supaya tidak
 * bergantung pada resolusi. Radius berupa fraksi dari sisi layar terpendek.
 */
data class Binding(
    var id: String = UUID.randomUUID().toString().substring(0, 8),
    var type: String = Bt.KEY,
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var key: Int = KeyNames.NONE,
    var action: String = Act.TAP,
    var dx: Float = 0f,
    var dy: Float = -0.15f,
    var durMs: Int = 250,
    var turboMs: Int = 70,
    var scope: Int = Scope.ANY,
    var radius: Float = 0.16f,
    var sens: Float = 1f,
    var up: Int = KeyNames.NONE,
    var down: Int = KeyNames.NONE,
    var left: Int = KeyNames.NONE,
    var right: Int = KeyNames.NONE,
    var camHold: Int = KeyNames.NONE
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type)
        .put("x", x.toDouble()).put("y", y.toDouble())
        .put("key", key).put("action", action)
        .put("dx", dx.toDouble()).put("dy", dy.toDouble())
        .put("durMs", durMs).put("turboMs", turboMs).put("scope", scope)
        .put("radius", radius.toDouble()).put("sens", sens.toDouble())
        .put("up", up).put("down", down).put("left", left).put("right", right)
        .put("camHold", camHold)

    fun copyWithNewId(): Binding = copy(id = UUID.randomUUID().toString().substring(0, 8))

    companion object {
        fun fromJson(o: JSONObject): Binding = Binding(
            id = o.optString("id", UUID.randomUUID().toString().substring(0, 8)),
            type = o.optString("type", Bt.KEY),
            x = o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
            y = o.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
            key = o.optInt("key", KeyNames.NONE),
            action = o.optString("action", Act.TAP).let { if (it in Act.all) it else Act.TAP },
            dx = o.optDouble("dx", 0.0).toFloat().coerceIn(-1f, 1f),
            dy = o.optDouble("dy", -0.15).toFloat().coerceIn(-1f, 1f),
            durMs = o.optInt("durMs", 250).coerceIn(50, 3000),
            turboMs = o.optInt("turboMs", 70).coerceIn(30, 1000),
            scope = o.optInt("scope", Scope.ANY).coerceIn(0, 2),
            radius = o.optDouble("radius", 0.16).toFloat().coerceIn(0.03f, 0.6f),
            sens = o.optDouble("sens", 1.0).toFloat().coerceIn(0.1f, 5f),
            up = o.optInt("up", KeyNames.NONE),
            down = o.optInt("down", KeyNames.NONE),
            left = o.optInt("left", KeyNames.NONE),
            right = o.optInt("right", KeyNames.NONE),
            camHold = o.optInt("camHold", KeyNames.NONE)
        )
    }
}

/** Profil pemetaan untuk satu aplikasi/game. */
data class Profile(
    var pkg: String,
    var name: String,
    var bindings: MutableList<Binding> = mutableListOf(),
    var startAim: Boolean = false
) {
    fun count(): Int = bindings.size

    fun toJson(): JSONObject {
        val arr = JSONArray()
        bindings.forEach { arr.put(it.toJson()) }
        return JSONObject().put("pkg", pkg).put("name", name)
            .put("startAim", startAim).put("bindings", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val p = Profile(o.optString("pkg", "*"), o.optString("name", "Profil"))
            p.startAim = o.optBoolean("startAim", false)
            val arr = o.optJSONArray("bindings")
            if (arr != null) {
                for (i in 0 until arr.length()) p.bindings.add(Binding.fromJson(arr.getJSONObject(i)))
            } else {
                legacyImport(o, p)
            }
            return p
        }

        /** Format v2.x: keys / joys / camera. */
        private fun legacyImport(o: JSONObject, p: Profile) {
            val ka = o.optJSONArray("keys")
            if (ka != null) for (i in 0 until ka.length()) {
                val k = ka.getJSONObject(i)
                p.bindings.add(
                    Binding(
                        type = Bt.KEY,
                        x = k.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                        y = k.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                        key = k.optInt("keyCode", KeyNames.NONE),
                        action = k.optString("action", Act.TAP).let { if (it in Act.all) it else Act.TAP },
                        dx = k.optDouble("dx", 0.0).toFloat().coerceIn(-1f, 1f),
                        dy = k.optDouble("dy", -0.15).toFloat().coerceIn(-1f, 1f),
                        durMs = k.optInt("dur", 250).coerceIn(50, 3000)
                    )
                )
            }
            val ja = o.optJSONArray("joys")
            if (ja != null) for (i in 0 until ja.length()) {
                val j = ja.getJSONObject(i)
                p.bindings.add(
                    Binding(
                        type = Bt.JOY,
                        x = j.optDouble("x", 0.2).toFloat().coerceIn(0f, 1f),
                        y = j.optDouble("y", 0.75).toFloat().coerceIn(0f, 1f),
                        up = j.optInt("up", KeyNames.NONE), down = j.optInt("down", KeyNames.NONE),
                        left = j.optInt("left", KeyNames.NONE), right = j.optInt("right", KeyNames.NONE)
                    )
                )
            }
            val cam = o.optJSONObject("camera")
            if (cam != null) {
                p.bindings.add(
                    Binding(
                        type = Bt.CAM, radius = 0.28f,
                        x = cam.optDouble("x", 0.76).toFloat().coerceIn(0f, 1f),
                        y = cam.optDouble("y", 0.45).toFloat().coerceIn(0f, 1f)
                    )
                )
            }
        }
    }
}
