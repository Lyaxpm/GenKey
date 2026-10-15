package com.gen.keymapper

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Penyimpanan profil (JSON) dan pengaturan (SharedPreferences). Tanpa internet, tanpa akun. */
class MappingStore(ctx: Context) : EngineSettings {
    private val appCtx = ctx.applicationContext
    private val prefs = appCtx.getSharedPreferences("genkey3", Context.MODE_PRIVATE)
    private val file = File(appCtx.filesDir, "profiles_v3.json")
    private val legacyFile = File(appCtx.filesDir, "profiles.json")

    val profiles = LinkedHashMap<String, Profile>()

    // ---------- pengaturan ----------

    override var tapMs: Int
        get() = prefs.getInt("tapMs", 45)
        set(v) = prefs.edit().putInt("tapMs", v.coerceIn(20, 300)).apply()

    override var cursorSpeed: Float
        get() = prefs.getFloat("cursorSpeed", 1.2f)
        set(v) = prefs.edit().putFloat("cursorSpeed", v.coerceIn(0.3f, 4f)).apply()

    override var camSens: Float
        get() = prefs.getFloat("camSens", 1.0f)
        set(v) = prefs.edit().putFloat("camSens", v.coerceIn(0.2f, 5f)).apply()

    var showHints: Boolean
        get() = prefs.getBoolean("showHints", true)
        set(v) = prefs.edit().putBoolean("showHints", v).apply()

    var hintOpacity: Float
        get() = prefs.getFloat("hintOpacity", 0.6f)
        set(v) = prefs.edit().putFloat("hintOpacity", v.coerceIn(0.1f, 1f)).apply()

    override var aimKey: Int
        get() = prefs.getInt("aimKey", KeyEvent.KEYCODE_CTRL_LEFT)
        set(v) = prefs.edit().putInt("aimKey", v).apply()

    override var cursorHoldKey: Int
        get() = prefs.getInt("cursorHoldKey", KeyEvent.KEYCODE_ESCAPE)
        set(v) = prefs.edit().putInt("cursorHoldKey", v).apply()

    override var freeCursor: Boolean
        get() = prefs.getBoolean("freeCursor", true)
        set(v) = prefs.edit().putBoolean("freeCursor", v).apply()

    override var mouseOnlyInCursor: Boolean
        get() = prefs.getBoolean("mouseOnlyInCursor", true)
        set(v) = prefs.edit().putBoolean("mouseOnlyInCursor", v).apply()

    var pauseKey: Int
        get() = prefs.getInt("pauseKey", KeyEvent.KEYCODE_F8)
        set(v) = prefs.edit().putInt("pauseKey", v).apply()

    var bubbleX: Int
        get() = prefs.getInt("bubbleX", 24)
        set(v) = prefs.edit().putInt("bubbleX", v).apply()

    var bubbleY: Int
        get() = prefs.getInt("bubbleY", 220)
        set(v) = prefs.edit().putInt("bubbleY", v).apply()

    var lastPkg: String
        get() = prefs.getString("lastPkg", "") ?: ""
        set(v) = prefs.edit().putString("lastPkg", v).apply()

    // ---------- profil ----------

    fun load() {
        profiles.clear()
        try {
            val src = when {
                file.exists() -> file
                legacyFile.exists() -> legacyFile
                else -> return
            }
            val arr = JSONObject(src.readText()).optJSONArray("profiles") ?: return
            for (i in 0 until arr.length()) {
                val p = Profile.fromJson(arr.getJSONObject(i))
                profiles[p.pkg] = p
            }
        } catch (_: Exception) {
        }
    }

    fun save() {
        try {
            val tmp = File(file.parentFile, "profiles_v3.tmp")
            tmp.writeText(toJsonText())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun toJsonText(): String {
        val arr = JSONArray()
        profiles.values.forEach { arr.put(it.toJson()) }
        return JSONObject().put("version", 3).put("profiles", arr).toString(2)
    }

    /** Impor profil; mengganti profil dengan package sama. Mengembalikan jumlah profil yang masuk. */
    fun importJsonText(data: String): Int {
        require(data.length < 4_000_000) { "File terlalu besar" }
        val arr = JSONObject(data).getJSONArray("profiles")
        require(arr.length() <= 200) { "Terlalu banyak profil" }
        val parsed = ArrayList<Profile>()
        for (i in 0 until arr.length()) {
            val p = Profile.fromJson(arr.getJSONObject(i))
            require(p.pkg.length in 1..255) { "Package tidak valid" }
            require(p.bindings.size <= 400) { "Profil terlalu besar" }
            parsed.add(p)
        }
        parsed.forEach { profiles[it.pkg] = it }
        save()
        return parsed.size
    }

    fun profileFor(pkg: String): Profile? = profiles[pkg]

    fun ensureProfile(pkg: String, name: String): Profile {
        val existing = profiles[pkg]
        if (existing != null) return existing
        val p = Profile(pkg, name)
        profiles[pkg] = p
        save()
        return p
    }

    fun deleteProfile(pkg: String) {
        profiles.remove(pkg)
        save()
    }

    fun appLabel(pkg: String): String = try {
        val pm = appCtx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    // ---------- preset ----------

    fun applyPreset(pkg: String, preset: String) {
        val p = profiles[pkg] ?: return
        p.bindings = when (preset) {
            PRESET_FPS -> fpsPreset()
            else -> mmorpgPreset()
        }
        p.startAim = preset == PRESET_FPS || freeCursor
        save()
    }

    private fun key(code: Int, x: Float, y: Float, act: String = Act.TAP, scope: Int = Scope.ANY) =
        Binding(type = Bt.KEY, key = code, x = x, y = y, action = act, scope = scope)

    private fun mmorpgPreset(): MutableList<Binding> {
        val l = mutableListOf<Binding>()
        l.add(
            Binding(
                type = Bt.JOY, x = 0.17f, y = 0.72f, radius = 0.15f,
                up = KeyEvent.KEYCODE_W, down = KeyEvent.KEYCODE_S,
                left = KeyEvent.KEYCODE_A, right = KeyEvent.KEYCODE_D
            )
        )
        // Kamera: tahan klik kanan lalu gerakkan mouse.
        l.add(Binding(type = Bt.CAM, x = 0.72f, y = 0.42f, radius = 0.26f, camHold = KeyNames.MOUSE_RIGHT))
        l.add(key(KeyEvent.KEYCODE_SPACE, 0.90f, 0.80f))
        l.add(key(KeyEvent.KEYCODE_Q, 0.70f, 0.88f))
        l.add(key(KeyEvent.KEYCODE_E, 0.78f, 0.78f))
        l.add(key(KeyEvent.KEYCODE_R, 0.86f, 0.66f))
        l.add(key(KeyEvent.KEYCODE_F, 0.80f, 0.58f))
        l.add(key(KeyEvent.KEYCODE_1, 0.40f, 0.92f))
        l.add(key(KeyEvent.KEYCODE_2, 0.46f, 0.92f))
        l.add(key(KeyEvent.KEYCODE_3, 0.52f, 0.92f))
        l.add(key(KeyEvent.KEYCODE_4, 0.58f, 0.92f))
        l.add(key(KeyEvent.KEYCODE_TAB, 0.93f, 0.12f))
        return l
    }

    private fun fpsPreset(): MutableList<Binding> {
        val l = mutableListOf<Binding>()
        l.add(
            Binding(
                type = Bt.JOY, x = 0.16f, y = 0.70f, radius = 0.15f,
                up = KeyEvent.KEYCODE_W, down = KeyEvent.KEYCODE_S,
                left = KeyEvent.KEYCODE_A, right = KeyEvent.KEYCODE_D
            )
        )
        // Kamera aktif saat Mode Bidik (default: tombol tengah mouse untuk berpindah).
        l.add(Binding(type = Bt.CAM, x = 0.72f, y = 0.45f, radius = 0.28f))
        l.add(key(KeyNames.MOUSE_LEFT, 0.87f, 0.62f, Act.HOLD, Scope.AIM))     // tembak
        l.add(key(KeyNames.MOUSE_RIGHT, 0.93f, 0.44f, Act.TOGGLE, Scope.AIM))  // bidik/scope
        l.add(key(KeyEvent.KEYCODE_SPACE, 0.93f, 0.76f))                       // lompat
        l.add(key(KeyEvent.KEYCODE_C, 0.88f, 0.88f))                           // jongkok
        l.add(key(KeyEvent.KEYCODE_Z, 0.80f, 0.90f))                           // tiarap
        l.add(key(KeyEvent.KEYCODE_R, 0.76f, 0.80f))                           // reload
        l.add(key(KeyEvent.KEYCODE_F, 0.70f, 0.60f))                           // ambil
        l.add(key(KeyEvent.KEYCODE_SHIFT_LEFT, 0.22f, 0.54f, Act.TOGGLE))      // lari
        l.add(key(KeyEvent.KEYCODE_Q, 0.46f, 0.14f))                           // condong kiri
        l.add(key(KeyEvent.KEYCODE_E, 0.56f, 0.14f))                           // condong kanan
        l.add(key(KeyEvent.KEYCODE_1, 0.40f, 0.92f))
        l.add(key(KeyEvent.KEYCODE_2, 0.47f, 0.92f))
        l.add(key(KeyEvent.KEYCODE_3, 0.54f, 0.92f))
        l.add(key(KeyEvent.KEYCODE_G, 0.62f, 0.92f))
        l.add(key(KeyEvent.KEYCODE_M, 0.10f, 0.14f))                           // peta
        return l
    }

    companion object {
        const val PRESET_MMORPG = "mmorpg"
        const val PRESET_FPS = "fps"
    }
}
