package com.eagle.vpn

import android.content.Context
import java.util.UUID
import java.net.InetSocketAddress
import java.net.Socket

class EagleProfileManager(context: Context) {

    private val store = EagleProfileStore(context)

    fun profiles(): List<EagleProfile> = store.load()

    fun addConfig(config: String, name: String = "EAGLE Server"): EagleProfile {
        val profile = EagleProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            address = extractAddress(config),
            config = config
        )
        store.save(profile)
        store.activate(profile)
        return profile
    }

    fun savePing(profile: EagleProfile, ping: Long) {
        store.save(profile.copy(ping = ping))
    }

    fun activate(profile: EagleProfile) {
        store.activate(profile)
    }

    fun delete(id: String) {
        store.delete(id)
    }

    fun toggleFavorite(profile: EagleProfile) {
        store.save(profile.copy(isFavorite = !profile.isFavorite))
    }

    fun ping(profile: EagleProfile, timeoutMs: Int = 2500): Long {
        val parts = profile.address.substringAfterLast("://").split(":")
        if (parts.size != 2) return -1L
        val host = parts[0].trim()
        val port = parts[1].toIntOrNull() ?: return -1L
        return try {
            Socket().use { socket ->
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                (System.nanoTime() - start) / 1_000_000
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun extractAddress(config: String): String {
        val match = Regex("[a-zA-Z0-9.-]+:[0-9]{2,5}").find(config)
        return match?.value ?: "EAGLE Server"
    }
}
