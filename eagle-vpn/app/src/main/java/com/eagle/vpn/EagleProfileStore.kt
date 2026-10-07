package com.eagle.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class EagleProfileStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("eagle_profiles", Context.MODE_PRIVATE)

    fun load(): List<EagleProfile> {
        val raw = prefs.getString("profiles", "[]") ?: "[]"
        val array = JSONArray(raw)

        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    EagleProfile(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        address = o.optString("address"),
                        config = o.optString("config"),
                        ping = o.optLong("ping"),
                        isFavorite = o.optBoolean("favorite")
                    )
                )
            }
        }
    }

    fun save(profile: EagleProfile) {
        val profiles = load().filterNot { it.id == profile.id } + profile
        val array = JSONArray()

        profiles.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("address", it.address)
                    put("config", it.config)
                    put("ping", it.ping)
                    put("favorite", it.isFavorite)
                }
            )
        }

        prefs.edit().putString("profiles", array.toString()).apply()
    }

    fun activate(profile: EagleProfile) {
        context.getSharedPreferences("eagle", Context.MODE_PRIVATE)
            .edit()
            .putString("config", profile.config)
            .putString("active_profile_id", profile.id)
            .apply()
    }

    fun delete(id: String) {
        val profiles = load().filterNot { it.id == id }
        val array = JSONArray()

        profiles.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("address", it.address)
                    put("config", it.config)
                    put("ping", it.ping)
                    put("favorite", it.isFavorite)
                }
            )
        }

        prefs.edit().putString("profiles", array.toString()).apply()
    }
}
