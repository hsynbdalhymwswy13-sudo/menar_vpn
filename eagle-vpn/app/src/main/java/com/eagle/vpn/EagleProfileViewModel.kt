package com.eagle.vpn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EagleProfileViewModel(context: Context) {

    private val manager = EagleProfileManager(context)

    var profiles by mutableStateOf(manager.profiles())
        private set

    var activeId by mutableStateOf(
        context.getSharedPreferences("eagle", Context.MODE_PRIVATE)
            .getString("active_profile_id", null)
    )
        private set

    fun add(config: String, name: String = "EAGLE Server") {
        manager.addConfig(config, name)
        refresh()
    }

    fun select(profile: EagleProfile) {
        activeId = profile.id
        manager.activate(profile)
        EagleState.update {
            it.copy(
                configReady = true,
                configName = profile.name,
                error = null
            )
        }
    }

    fun ping(profile: EagleProfile) {
        CoroutineScope(Dispatchers.IO).launch {
            val value = manager.ping(profile)
            manager.savePing(profile, value)
            withContext(Dispatchers.Main) { profiles = manager.profiles() }
        }
    }

    fun pingAll() {
        CoroutineScope(Dispatchers.IO).launch {
            profiles.forEach { profile ->
                val value = manager.ping(profile)
                manager.savePing(profile, value)
            }
            withContext(Dispatchers.Main) { profiles = manager.profiles() }
        }
    }

    fun favorite(profile: EagleProfile) {
        manager.toggleFavorite(profile)
        refresh()
    }

    fun delete(profile: EagleProfile) {
        manager.delete(profile.id)
        if (activeId == profile.id) {
            activeId = null
        }
        refresh()
    }

    fun refresh() {
        profiles = manager.profiles()
    }
}
