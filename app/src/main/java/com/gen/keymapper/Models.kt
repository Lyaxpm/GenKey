package com.gen.keymapper

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Satu tombol: keyCode -> aksi sentuh di posisi (x,y) sebagai fraksi layar. */
data class KeyMap(
    var id: String = UUID.randomUUID().toString(),
    var keyCode: Int = -1,
    var action: String = "tap", // tap | hold | swipe
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var dx: Float = 0f,      // vektor geser (fraksi layar) untuk "swipe"
    var dy: Float = -0.18f,
    var dur: Long = 250,     // durasi ms untuk "swipe"
    var intervalMs: Long = 150 // interval turbo
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("keyCode", keyCode)
        .put("action", action)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("dx", dx.toDouble())
        .put("dy", dy.toDouble())
        .put("dur", dur)
        .put("intervalMs", intervalMs)

    companion object {
        fun fromJson(o: JSONObject): KeyMap = KeyMap(
            id = o.optString("id", UUID.randomUUID().toString()),
            keyCode = o.optInt("keyCode", -1),
            action = o.optString("action", "tap"),
            x = o.optDouble("x", 0.5).toFloat(),
            y = o.optDouble("y", 0.5).toFloat(),
            dx = o.optDouble("dx", 0.0).toFloat(),
            dy = o.optDouble("dy", -0.18).toFloat(),
            dur = o.optLong("dur", 250).coerceIn(50, 3000),
            intervalMs = o.optLong("intervalMs", 150).coerceIn(50, 3000)
        )
    }

    fun actionLabel(): String = when (action) {
        "hold" -> "Tahan"
        "swipe" -> "Geser"
        "toggle" -> "Toggle"
        "repeat" -> "Turbo"
        "double" -> "Double"
        else -> "Tap"
    }
}

/** Joystick virtual: 4 tombol arah -> drag analog dari titik (x,y). */
data class JoyMap(
    var id: String = UUID.randomUUID().toString(),
    var x: Float = 0.2f,
    var y: Float = 0.75f,
    var radiusDp: Int = 0,
    var up: Int = 51,
    var down: Int = 47,
    var left: Int = 29,
    var right: Int = 32
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("radiusDp", radiusDp)
        .put("up", up).put("down", down)
        .put("left", left).put("right", right)

    companion object {
        fun fromJson(o: JSONObject): JoyMap = JoyMap(
            id = o.optString("id", UUID.randomUUID().toString()),
            x = o.optDouble("x", 0.2).toFloat(),
            y = o.optDouble("y", 0.75).toFloat(),
            radiusDp = o.optInt("radiusDp", 0),
            up = o.optInt("up", -1),
            down = o.optInt("down", -1),
            left = o.optInt("left", -1),
            right = o.optInt("right", -1)
        )
    }

    fun slotKey(slot: String): Int = when (slot) {
        "up" -> up; "down" -> down; "left" -> left; else -> right
    }

    fun setSlot(slot: String, keyCode: Int) {
        when (slot) {
            "up" -> up = keyCode
            "down" -> down = keyCode
            "left" -> left = keyCode
            else -> right = keyCode
        }
    }

    fun slotLabel(slot: String): String = when (slot) {
        "up" -> "Atas"; "down" -> "Bawah"; "left" -> "Kiri"; else -> "Kanan"
    }

    fun usesKey(keyCode: Int): String? = when (keyCode) {
        up -> "up"; down -> "down"; left -> "left"; right -> "right"; else -> null
    }
}

data class CameraMap(
    var x: Float = 0.76f,
    var y: Float = 0.45f
) {
    fun toJson(): JSONObject = JSONObject().put("x", x.toDouble()).put("y", y.toDouble())
    companion object {
        fun fromJson(j: JSONObject): CameraMap = CameraMap(
            j.optDouble("x", .76).toFloat().coerceIn(0f, 1f),
            j.optDouble("y", .45).toFloat().coerceIn(0f, 1f))
    }
}

/** Profil mapping untuk satu aplikasi (package name). "*" = default. */
data class Profile(
    var pkg: String,
    var name: String,
    var keys: MutableList<KeyMap> = mutableListOf(),
    var joys: MutableList<JoyMap> = mutableListOf(),
    var camera: CameraMap? = null
) {
    fun toJson(): JSONObject {
        val ka = JSONArray()
        keys.forEach { ka.put(it.toJson()) }
        val ja = JSONArray()
        joys.forEach { ja.put(it.toJson()) }
        return JSONObject()
            .put("pkg", pkg)
            .put("name", name)
            .put("keys", ka)
            .put("joys", ja)
            .put("camera", camera?.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val p = Profile(o.optString("pkg", "*"), o.optString("name", "Default"))
            val ka = o.optJSONArray("keys") ?: JSONArray()
            require(ka.length() <= 300) { "Maksimal 300 tombol per profil" }
            for (i in 0 until ka.length()) p.keys.add(KeyMap.fromJson(ka.getJSONObject(i)))
            val ja = o.optJSONArray("joys") ?: JSONArray()
            require(ja.length() <= 20) { "Maksimal 20 joystick per profil" }
            for (i in 0 until ja.length()) p.joys.add(JoyMap.fromJson(ja.getJSONObject(i)))
            p.camera = o.optJSONObject("camera")?.let { CameraMap.fromJson(it) }
            p.validate()
            return p
        }
    }

    fun validate() {
        require(pkg == "*" || pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) { "Package profil tidak valid" }
        require(pkg.length <= 255 && name.length in 1..120) { "Nama profil tidak valid" }
        val ids = mutableSetOf<String>()
        fun position(x: Float, y: Float) { require(x.isFinite() && y.isFinite()) { "Koordinat tidak valid" } }
        keys.forEach {
            require(it.id.length in 1..128 && ids.add(it.id)) { "ID kontrol duplikat / tidak valid" }
            require(it.action in setOf("tap", "hold", "swipe", "toggle", "repeat", "double")) { "Jenis kontrol tidak dikenal" }
            require(it.keyCode in -1..1000 || it.keyCode in setOf(-1001, -1002)) { "Kode tombol tidak valid" }
            position(it.x, it.y); position(it.dx, it.dy)
            it.x = it.x.coerceIn(0f, 1f); it.y = it.y.coerceIn(0f, 1f)
            it.dx = it.dx.coerceIn(-1f, 1f); it.dy = it.dy.coerceIn(-1f, 1f)
        }
        joys.forEach {
            require(it.id.length in 1..128 && ids.add(it.id)) { "ID joystick duplikat / tidak valid" }
            position(it.x, it.y); it.x = it.x.coerceIn(0f, 1f); it.y = it.y.coerceIn(0f, 1f)
            it.radiusDp = it.radiusDp.coerceIn(0, 220)
            require(listOf(it.up, it.down, it.left, it.right).all { code -> code in -1..1000 }) { "Arah joystick tidak valid" }
        }
        camera?.let { position(it.x, it.y); it.x = it.x.coerceIn(0f, 1f); it.y = it.y.coerceIn(0f, 1f) }
    }

    fun count(): Int = keys.size + joys.size + if (camera != null) 1 else 0
}
