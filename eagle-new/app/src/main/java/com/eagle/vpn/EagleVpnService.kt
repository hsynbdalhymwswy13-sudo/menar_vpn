package com.eagle.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import java.io.File

class EagleVpnService : VpnService(), CommandServerHandler {

    private var commandServer: CommandServer? = null
    private var platform: EaglePlatform? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopEngine()
            else -> {
                try {
                    createNotificationChannel()
                    startForeground(NOTIFICATION_ID, buildNotification())
                    startEngine()
                    sendStatus("CONNECTED")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Unable to start libbox", e)
                    sendStatus("ERROR", e.message ?: "Unknown service error")
                    stopEngine()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "EAGLE VPN connection",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows the current EAGLE VPN service status"
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (android.os.Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("EAGLE VPN")
                .setContentText("VPN service is running")
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("EAGLE VPN")
                .setContentText("VPN service is running")
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        }
    }

    private fun sendStatus(status: String, message: String = "") {
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra("status", status)
                .putExtra("message", message)
        )
    }

    @Synchronized
    private fun startEngine() {
        if (commandServer != null) return

        val configFile = File(filesDir, "config.json")
        if (!configFile.exists()) {
            configFile.writeText(
                """{"log":{"level":"info"},"inbounds":[{"type":"tun","tag":"eagle-tun","interface_name":"eagle0","address":["172.19.0.1/30"],"mtu":1500,"auto_route":true,"strict_route":true,"stack":"system"}],"outbounds":[{"type":"direct","tag":"direct"}],"route":{"auto_detect_interface":true}}"""
            )
        }

        val setup = SetupOptions().apply {
            basePath = filesDir.absolutePath
            workingPath = getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
            tempPath = cacheDir.absolutePath
            fixAndroidStack = true
            logMaxLines = 3000
            debug = false
            crashReportSource = "EAGLE VPN"
            appVersion = "1"
            appMarketingVersion = "1.0"
        }

        if (!libboxReady) {
            Libbox.setup(setup)
            libboxReady = true
        } else {
            Libbox.reloadSetupOptions(setup)
        }

        val platformInterface = EaglePlatform(this)
        val server = Libbox.newCommandServer(this, platformInterface)
        platform = platformInterface
        commandServer = server

        try {
            server.start()
            server.startOrReloadService(configFile.readText(), OverrideOptions())
        } catch (e: Exception) {
            commandServer = null
            platform = null
            try {
                server.close()
            } catch (_: Exception) {
            }
            throw e
        }

        android.util.Log.i(TAG, "libbox service started")
    }

    @Synchronized
    private fun stopEngine() {
        val server = commandServer
        commandServer = null
        platform = null

        if (server != null) {
            try {
                server.closeService()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Error closing libbox service", e)
            }
            try {
                server.close()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Error closing command server", e)
            }
        }
        sendStatus("READY")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun connectSSHAgent(): Int = 0

    override fun getSystemProxyStatus(): SystemProxyStatus =
        SystemProxyStatus().apply {
            available = false
            enabled = false
        }

    override fun serviceReload() {
        val server = commandServer ?: return
        val configFile = File(filesDir, "config.json")
        if (configFile.exists()) {
            server.startOrReloadService(configFile.readText(), OverrideOptions())
        }
    }

    override fun serviceStop() {
        stopEngine()
    }

    override fun setSystemProxyEnabled(isEnabled: Boolean) {
        // Android system-wide proxy is not implemented yet.
    }

    override fun triggerNativeCrash() {
        // Intentionally disabled.
    }

    override fun writeDebugMessage(message: String) {
        android.util.Log.d(TAG, message)
    }

    override fun onDestroy() {
        stopEngine()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    companion object {
        private const val TAG = "EagleVpnService"
        const val ACTION_START = "com.eagle.vpn.START"
        const val ACTION_STOP = "com.eagle.vpn.STOP"
        const val ACTION_STATUS = "com.eagle.vpn.STATUS"
        private const val CHANNEL_ID = "eagle_vpn_status"
        private const val NOTIFICATION_ID = 701

        @Volatile
        private var libboxReady = false
    }
}
