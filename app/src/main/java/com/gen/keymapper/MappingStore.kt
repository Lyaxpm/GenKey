package com.gen.keymapper

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Penyimpanan profil mapping (JSON) + pengaturan. Tanpa internet, tanpa akun. */
class MappingStore(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val prefs = appCtx.getSharedPreferences("genkey", Context.MODE_PRIVATE)
    private val file = File(appCtx.filesDir, "profiles.json")

    val profiles = LinkedHashMap<String, Profile>()

    var useShizuku: Boolean
        get() = prefs.getBoolean("useShizuku", true)
        set(v) = prefs.edit().putBoolean("useShizuku", v).apply()

    var showChips: Boolean
        get() = prefs.getBoolean("showChips", true)
        set(v) = prefs.edit().putBoolean("showChips", v).apply()

    var mappingEnabled: Boolean
        get() = prefs.getBoolean("mappingEnabled", true)
        set(v) = prefs.edit().putBoolean("mappingEnabled", v).apply()

    var tapMs: Long
        get() = prefs.getLong("tapMs", 60)
        set(v) = prefs.edit().putLong("tapMs", v.coerceIn(20, 500)).apply()

    var holdMs: Long
        get() = prefs.getLong("holdMs", 600)
        set(v) = prefs.edit().putLong("holdMs", v.coerceIn(200, 5000)).apply()

    var mouseSensitivity: Float
        get() = prefs.getFloat("mouseSensitivity", 1.75f)
        set(v) = prefs.edit().putFloat("mouseSensitivity", v.coerceIn(0.5f, 6.0f)).apply()

    var joyRadiusDp: Int
        get() = prefs.getInt("joyRadiusDp", 110)
        set(v) = prefs.edit().putInt("joyRadiusDp", v.coerceIn(60, 220)).apply()

    fun load() {
        profiles.clear()
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("profiles") ?: return
            for (i in 0 until arr.length()) {
                val p = Profile.fromJson(arr.getJSONObject(i))
                profiles[p.pkg] = p
            }
        } catch (_: Exception) {
        }
    }

    fun toJsonText(): String {
        val root = JSONObject()
        val arr = org.json.JSONArray()
        profiles.values.forEach { arr.put(it.toJson()) }
        root.put("profiles", arr)
        root.put("version", 2)
        return root.toString(2)
    }

    fun importJsonText(data: String) {
        require(data.length < 2_000_000) { "File terlalu besar" }
        val arr = JSONObject(data).getJSONArray("profiles")
        require(arr.length() <= 150) { "Terlalu banyak profil" }
        val parsed = LinkedHashMap<String, Profile>()
        for (i in 0 until arr.length()) {
            val item = Profile.fromJson(arr.getJSONObject(i))
            require(item.keys.size <= 300 && item.joys.size <= 20) { "Profil terlalu besar" }
            require(item.pkg.length < 256) { "Package tidak valid" }
            item.keys.forEach { key ->
                key.x = key.x.coerceIn(0f, 1f)
                key.y = key.y.coerceIn(0f, 1f)
                key.dx = key.dx.coerceIn(-1f, 1f)
                key.dy = key.dy.coerceIn(-1f, 1f)
                key.dur = key.dur.coerceIn(50, 3000)
            }
            parsed[item.pkg] = item
        }
        profiles.clear()
        profiles.putAll(parsed)
        save()
    }

    fun save() {
        try {
            val temp = File(file.parentFile, "profiles.tmp")
            temp.writeText(toJsonText())
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (_: Exception) {
        }
    }

    /** Profil untuk package; jatuh ke profil default "*" bila tidak ada. */
    fun profileFor(pkg: String): Profile? = profiles[pkg] ?: profiles["*"]

    fun ensureProfile(pkg: String, name: String): Profile {
        return profiles.getOrPut(pkg) {
            Profile(pkg, name).also { save() }
        }
    }

    fun addMmorpgPreset(pkg: String) {
        val p = profiles[pkg] ?: return
        if (p.joys.none { it.up == android.view.KeyEvent.KEYCODE_W }) {
            p.joys.add(JoyMap(x = 0.19f, y = 0.75f,
                up = android.view.KeyEvent.KEYCODE_W, down = android.view.KeyEvent.KEYCODE_S,
                left = android.view.KeyEvent.KEYCODE_A, right = android.view.KeyEvent.KEYCODE_D))
        }
        val keys = listOf(
            android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_E,
            android.view.KeyEvent.KEYCODE_R, android.view.KeyEvent.KEYCODE_F,
            android.view.KeyEvent.KEYCODE_SPACE, android.view.KeyEvent.KEYCODE_1,
            android.view.KeyEvent.KEYCODE_2, android.view.KeyEvent.KEYCODE_3,
            android.view.KeyEvent.KEYCODE_4
        )
        keys.forEachIndexed { i, kc ->
            if (p.keys.none { it.keyCode == kc }) {
                p.keys.add(KeyMap(keyCode = kc, x = (0.62f + (i % 5) * 0.075f).coerceAtMost(0.95f),
                    y = 0.69f + (i / 5) * 0.15f))
            }
        }
        if (p.camera == null) p.camera = CameraMap()
        save()
    }

    fun deleteProfile(pkg: String) {
        if (pkg == "*") {
            profiles["*"] = Profile("*", "Default")
        } else {
            profiles.remove(pkg)
        }
        save()
    }

    fun appLabel(pkg: String): String {
        return try {
            val pm = appCtx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }
}
