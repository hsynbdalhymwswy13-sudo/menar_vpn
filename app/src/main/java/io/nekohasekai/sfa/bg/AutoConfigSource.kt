package io.nekohasekai.sfa.bg

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import java.io.File
import java.net.URI
import java.util.Date

object AutoConfigSource {
    suspend fun fetchAndActivate(): String {
        val url = Settings.autoConfigSourceUrl.trim()
        require(url.isNotEmpty()) { "ابتدا نشانی منبع کانفیگ را وارد کنید." }

        val uri = URI(url)
        require(
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                !uri.host.isNullOrBlank()
        ) { "نشانی منبع باید یک URL معتبر HTTP یا HTTPS باشد." }

        val content = HTTPClient().use { it.getString(url) }
        Libbox.checkConfig(content)

        val existing = ProfileManager.list().firstOrNull {
            it.typed.type == TypedProfile.Type.Remote &&
                it.typed.remoteURL.trim() == url
        }

        val profile = existing ?: Profile(
            name = "MENAR Auto Config",
            typed = TypedProfile().apply {
                type = TypedProfile.Type.Remote
                remoteURL = url
                autoUpdate = true
                autoUpdateInterval = 60
                lastUpdated = Date()
            },
        ).apply {
            userOrder = ProfileManager.nextOrder()
        }

        val configDirectory = File(Application.application.filesDir, "configs")
            .also { it.mkdirs() }

        val configFile = if (existing != null) {
            File(existing.typed.path)
        } else {
            File(configDirectory, "${ProfileManager.nextFileID()}.json")
        }

        configFile.writeText(content)

        profile.typed.path = configFile.path
        profile.typed.type = TypedProfile.Type.Remote
        profile.typed.remoteURL = url
        profile.typed.autoUpdate = true
        profile.typed.autoUpdateInterval = 60
        profile.typed.lastUpdated = Date()

        if (existing != null) {
            ProfileManager.update(profile)
            Settings.selectedProfile = profile.id
        } else {
            ProfileManager.create(profile, andSelect = true)
        }

        UpdateProfileWork.reconfigureUpdater()
        return profile.name
    }
}
