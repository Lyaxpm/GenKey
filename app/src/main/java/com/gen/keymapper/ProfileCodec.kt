package com.gen.keymapper

import org.json.JSONArray
import org.json.JSONObject

internal object ProfileCodec {
    fun decode(text: String): LinkedHashMap<String, Profile> {
        require(text.length <= 2_000_000) { "Backup maksimal 2 MB" }
        val root = JSONObject(text)
        require(root.optInt("version", 2) in 1..3) { "Versi backup belum didukung" }
        val array = root.getJSONArray("profiles")
        require(array.length() <= 150) { "Maksimal 150 profil" }
        val result = linkedMapOf<String, Profile>()
        for (i in 0 until array.length()) {
            val profile = Profile.fromJson(array.getJSONObject(i))
            require(profile.pkg !in result) { "Package profil duplikat" }
            result[profile.pkg] = profile
        }
        return result
    }
    fun encode(profiles: Collection<Profile>): String {
        val array = JSONArray()
        profiles.forEach { it.validate(); array.put(it.toJson()) }
        return JSONObject().put("version", 3).put("profiles", array).toString(2)
    }
}
