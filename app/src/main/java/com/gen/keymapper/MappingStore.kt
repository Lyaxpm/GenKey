package com.gen.keymapper

import android.content.Context
import org.json.JSONObject
import java.io.File
import android.util.AtomicFile

/** Penyimpanan profil mapping (JSON) + pengaturan. Tanpa internet, tanpa akun. */
class MappingStore(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val prefs = appCtx.getSharedPreferences("genkey", Context.MODE_PRIVATE)
    private val file = File(appCtx.filesDir, "profiles.json")

    var lastError: String = ""; private set
    var bubbleX: Int
        get() = prefs.getInt("bubbleX", 24)
        set(v) = prefs.edit().putInt("bubbleX", v).apply()
    var bubbleY: Int
        get() = prefs.getInt("bubbleY", 220)
        set(v) = prefs.edit().putInt("bubbleY", v).apply()
    var gamepadEnabled: Boolean
        get() = prefs.getBoolean("gamepadEnabled", false)
        set(v) = prefs.edit().putBoolean("gamepadEnabled", v).apply()
    var deadZone: Float
        get() = prefs.getFloat("deadZone", .18f)
        set(v) = prefs.edit().putFloat("deadZone", v.coerceIn(.05f, .5f)).apply()
    var invertY: Boolean
        get() = prefs.getBoolean("invertY", false)
        set(v) = prefs.edit().putBoolean("invertY", v).apply()

    val profiles = LinkedHashMap<String, Profile>()

    var activePackage: String
        get() = prefs.getString("activePackage", "*") ?: "*"
        set(value) = prefs.edit().putString("activePackage", value).apply()

    var showChips: Boolean
        get() = prefs.getBoolean("showChips", true)
        set(v) = prefs.edit().putBoolean("showChips", v).apply()

    var mappingEnabled: Boolean
        get() = prefs.getBoolean("mappingEnabled", true)
        set(v) = prefs.edit().putBoolean("mappingEnabled", v).apply()

    var tapMs: Long
        get() = prefs.getLong("tapMs", 60)
        set(v) = prefs.edit().putLong("tapMs", v.coerceIn(20, 500)).apply()

    var mouseSensitivity: Float
        get() = prefs.getFloat("mouseSensitivity", 1.75f)
        set(v) = prefs.edit().putFloat("mouseSensitivity", v.coerceIn(0.5f, 6.0f)).apply()

    var joyRadiusDp: Int
        get() = prefs.getInt("joyRadiusDp", 110)
        set(v) = prefs.edit().putInt("joyRadiusDp", v.coerceIn(60, 220)).apply()

    fun load() {
        try {
            val atomic = AtomicFile(file)
            if (!file.exists() && !File(file.path + ".bak").exists()) return
            val parsed = ProfileCodec.decode(atomic.openRead().bufferedReader().use { it.readText() })
            profiles.clear(); profiles.putAll(parsed); lastError = ""
        } catch (e: Exception) {
            lastError = "Profil gagal dibaca: ${e.message}"
        }
    }
    fun toJsonText() = ProfileCodec.encode(profiles.values)
    fun importJsonText(data: String) {
        val parsed = ProfileCodec.decode(data)
        // Write before replacing the in-memory state; invalid/failed imports never destroy old data.
        write(ProfileCodec.encode(parsed.values))
        profiles.clear(); profiles.putAll(parsed)
        if (activePackage !in profiles) activePackage = profiles.keys.firstOrNull() ?: "*"
    }
    private fun write(data: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(data.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun save(): Boolean {
        return try { write(toJsonText()); lastError = ""; true }
        catch (e: Exception) { lastError = "Profil gagal disimpan: ${e.message}"; false }
    }

    /** Profil untuk package; jatuh ke profil default "*" bila tidak ada. */
    fun profileFor(pkg: String): Profile? = profiles[pkg] ?: profiles["*"]

    fun ensureProfile(pkg: String, name: String): Profile {
        profiles[pkg]?.let { return it }
        val created = Profile(pkg, name)
        profiles[pkg] = created
        save()
        return created
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

    fun addFpsPreset(pkg: String) {
        val p = profiles[pkg] ?: return
        if (p.joys.isEmpty()) p.joys.add(JoyMap(x = .18f, y = .74f))
        val controls = listOf(
            KeyMap(keyCode = 188, action = "hold", x = .88f, y = .42f),
            KeyMap(keyCode = 189, action = "toggle", x = .83f, y = .60f),
            KeyMap(keyCode = 46, x = .90f, y = .79f),
            KeyMap(keyCode = 62, x = .91f, y = .65f),
            KeyMap(keyCode = 31, x = .74f, y = .83f),
            KeyMap(keyCode = 34, x = .60f, y = .60f))
        controls.filter { candidate -> p.keys.none { it.keyCode == candidate.keyCode } }.forEach { p.keys.add(it) }
        if (p.camera == null) p.camera = CameraMap(.68f, .45f)
        save()
    }

    fun deleteProfile(pkg: String) {
        if (pkg == "*") {
            profiles["*"] = Profile("*", "Default")
        } else {
            profiles.remove(pkg)
        }
        if (activePackage !in profiles) activePackage = profiles.keys.firstOrNull() ?: "*"
        save()
    }

    fun appLabel(pkg: String): String {
        profiles[pkg]?.let { return it.name }
        if (pkg == "*") return "Profil umum"
        return try {
            val pm = appCtx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }
}
