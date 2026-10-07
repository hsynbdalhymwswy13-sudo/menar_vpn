package com.eagle.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import android.os.Build
import android.os.IBinder
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.Notification as BoxNotification
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import android.os.ParcelFileDescriptor

class EagleVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.eagle.vpn.CONNECT"
        const val ACTION_STOP = "com.eagle.vpn.STOP"
        private const val CHANNEL_ID = "eagle_vpn"
        private const val NOTIFICATION_ID = 7001

        @Volatile
        var vpnInterface: ParcelFileDescriptor? = null
    }

    private var commandServer: CommandServer? = null
    private var trafficClient: CommandClient? = null
    private var setupDone = false

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
            }
            ACTION_CONNECT -> {
                val config = intent.getStringExtra("config").orEmpty()
                if (config.isNotBlank()) {
                    startVpn(config)
                } else {
                    EagleState.update { it.copy(connecting = false, error = "Configuration is empty") }
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(config: String) {
        try {
            EagleState.update { it.copy(connecting = true, error = null) }
            startForeground(NOTIFICATION_ID, buildNotification())

            setupLibbox()
            if (commandServer == null) {
                commandServer = CommandServer(EagleCommandHandler(), EaglePlatform(this))
                commandServer!!.start()
            }

            if (trafficClient == null) {
                val options = CommandClientOptions().apply {
                    addCommand(Libbox.CommandStatus)
                    statusInterval = 1_000_000_000L
                }
                trafficClient = CommandClient(TrafficCommandHandler(), options)
                trafficClient!!.connect()
            }

            Libbox.checkConfig(config)
            commandServer!!.startOrReloadService(config, OverrideOptions())

            EagleState.update {
                it.copy(
                    connected = true,
                    connecting = false,
                    configReady = true,
                    startedAt = System.currentTimeMillis(),
                    error = null
                )
            }
        } catch (e: Exception) {
            EagleState.update { it.copy(connected = false, connecting = false, error = e.message ?: "Connection failed") }
            stopVpn()
        }
    }

    private fun setupLibbox() {
        if (setupDone) return
        val base = filesDir
        val work = java.io.File(base, "work").apply { mkdirs() }
        val temp = cacheDir
        Libbox.setup(SetupOptions().apply {
            basePath = base.path
            workingPath = work.path
            tempPath = temp.path
            fixAndroidStack = true
            commandServerListenPort = 9090
            logMaxLines = 3000
            debug = false
            appVersion = BuildConfig.VERSION_CODE.toString()
            appMarketingVersion = BuildConfig.VERSION_NAME
        })
        Libbox.promoteOOMDraft()
        setupDone = true
    }

    fun establishTun(
        sessionName: String,
        mtu: Int,
        addresses: List<Pair<String, Int>>,
        routes: List<Pair<String, Int>>,
        dnsServers: List<String>
    ): Int {
        vpnInterface?.close()
        val builder = Builder().setSession(sessionName).setMtu(mtu)
        addresses.forEach { builder.addAddress(it.first, it.second) }
        routes.forEach { builder.addRoute(it.first, it.second) }
        dnsServers.forEach { builder.addDnsServer(it) }
        vpnInterface = builder.establish()
            ?: error("Failed to establish VPN interface")
        return vpnInterface!!.fd
    }

    private fun stopVpn() {
        try {
            trafficClient?.disconnect()
        } catch (_: Exception) {
        }
        trafficClient = null
        try {
            commandServer?.closeService()
        } catch (_: Exception) {
        }
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        EagleState.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private class TrafficCommandHandler : CommandClientHandler {
        override fun clearLogs() {}
        override fun connected() {}
        override fun disconnected(message: String) {}
        override fun initializeClashMode(modes: StringIterator, currentMode: String) {}
        override fun setDefaultLogLevel(level: Int) {}
        override fun updateClashMode(mode: String) {}
        override fun writeConnectionEvents(events: io.nekohasekai.libbox.ConnectionEvents) {}
        override fun writeGroups(groups: io.nekohasekai.libbox.OutboundGroupIterator) {}
        override fun writeLogs(logs: io.nekohasekai.libbox.LogIterator) {}
        override fun writeOutbounds(outbounds: io.nekohasekai.libbox.OutboundGroupItemIterator) {}
        override fun writeStatus(status: StatusMessage) {
            EagleState.updateStatus(status)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "EAGLE VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("EAGLE VPN")
                .setContentText("VPN connection active")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("EAGLE VPN")
                .setContentText("VPN connection active")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private class EagleCommandHandler : CommandServerHandler {
        override fun connectSSHAgent(): Int = 0
        override fun getSystemProxyStatus(): SystemProxyStatus =
            SystemProxyStatus().apply {
                available = false
                enabled = false
            }
        override fun serviceReload() {}
        override fun serviceStop() {}
        override fun setSystemProxyEnabled(isEnabled: Boolean) {}
        override fun triggerNativeCrash() {}
        override fun writeDebugMessage(message: String) {}
    }
}
