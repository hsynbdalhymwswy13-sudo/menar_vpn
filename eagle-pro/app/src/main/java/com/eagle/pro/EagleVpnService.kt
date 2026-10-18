package com.eagle.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import java.io.File

class EagleVpnService : VpnService(), CommandServerHandler {

    private var commandServer: CommandServer? = null
    private var platform: EaglePlatform? = null
    @Volatile private var statusClient: CommandClient? = null

    private val statusHandler = object : CommandClientHandler {
        override fun connected() {
            sendStatus(
                "ENGINE_STARTED",
                "libbox status monitor connected; remote connectivity is not verified"
            )
        }

        override fun disconnected(message: String?) {
            if (statusClient != null && commandServer != null) {
                sendStatus(
                    "ENGINE_STARTED",
                    "Status monitor disconnected; VPN engine state is not confirmed"
                )
            }
        }

        override fun writeStatus(message: StatusMessage) {
            sendBroadcast(
                Intent(ACTION_STATUS)
                    .setPackage(packageName)
                    .putExtra("status", "ENGINE_STARTED")
                    .putExtra("message", "libbox status received; remote connectivity is not verified")
                    .putExtra("trafficAvailable", message.trafficAvailable)
                    .putExtra("uplink", message.uplink)
                    .putExtra("downlink", message.downlink)
                    .putExtra("uplinkTotal", message.uplinkTotal)
                    .putExtra("downlinkTotal", message.downlinkTotal)
                    .putExtra("connectionsIn", message.connectionsIn)
                    .putExtra("connectionsOut", message.connectionsOut)
                    .putExtra("memory", message.memory)
            )
        }

        override fun clearLogs() {}
        override fun initializeClashMode(modeList: StringIterator, currentMode: String) {}
        override fun setDefaultLogLevel(level: Int) {}
        override fun updateClashMode(newMode: String) {}
        override fun writeConnectionEvents(events: ConnectionEvents) {}
        override fun writeGroups(message: OutboundGroupIterator) {}
        override fun writeLogs(messageList: LogIterator) {}
        override fun writeOutbounds(message: OutboundGroupItemIterator) {}
    }

    private fun startStatusClient() {
        if (statusClient != null) return

        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandStatus)
            statusInterval = 1_000_000_000L
        }
        val client = Libbox.newCommandClient(statusHandler, options)
        statusClient = client

        Thread({
            try {
                client.connect()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "libbox status client failed", e)
                if (statusClient === client) {
                    sendStatus(
                        "STATUS_UNAVAILABLE",
                        "Status monitor unavailable: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }, "EagleLibboxStatus").start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        traceEngine("onStartCommand action=${intent?.action} startId=$startId")
        when (intent?.action) {
            ACTION_STOP -> stopEngine()
            else -> {
                try {
                    createNotificationChannel()
                    traceEngine("creating foreground notification")
                    startForeground(NOTIFICATION_ID, buildNotification())
                    traceEngine("foreground notification started")
                    startEngine()
                    traceEngine("startEngine returned successfully")
                    sendStatus("ENGINE_STARTED", "VPN engine started; remote connectivity is not verified")
                } catch (e: Exception) {
                    traceEngine("Unable to start libbox", e)
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

    private fun traceEngine(message: String, error: Throwable? = null) {
        try {
            val file = File(filesDir, "eagle-engine.log")
            val detail = buildString {
                append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()))
                append("  ")
                append(message)
                if (error != null) {
                    append("\\n")
                    append(android.util.Log.getStackTraceString(error))
                }
                append("\\n")
            }
            file.appendText(detail)
        } catch (_: Exception) {
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
        traceEngine("startEngine entered")
        if (commandServer != null) {
            traceEngine("command server already exists")
            return
        }

        val configFile = File(filesDir, "config.json")
        val importedFlag = File(filesDir, "profile-imported.flag")
        if (!configFile.isFile || !importedFlag.isFile) {
            throw IllegalStateException("Import a valid sing-box JSON profile before connecting")
        }
        traceEngine("validating config")
        Libbox.checkConfig(configFile.readText())
        traceEngine("config validated")

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

        traceEngine("setting up libbox")
        if (!libboxReady) {
            Libbox.setup(setup)
            libboxReady = true
        } else {
            Libbox.reloadSetupOptions(setup)
        }
        traceEngine("libbox setup complete")

        val platformInterface = EaglePlatform(this)
        val server = Libbox.newCommandServer(this, platformInterface)
        platform = platformInterface
        commandServer = server

        try {
            traceEngine("starting command server")
            server.start()
            traceEngine("command server started; starting sing-box service")
            server.startOrReloadService(configFile.readText(), OverrideOptions())
            traceEngine("sing-box service started; starting status client")
            startStatusClient()
            traceEngine("status client launch requested")
        } catch (e: Exception) {
            traceEngine("libbox server startup failed", e)
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
        val client = statusClient
        statusClient = null
        if (client != null) {
            try {
                client.disconnect()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Error closing libbox status client", e)
            }
        }

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
        const val ACTION_START = "com.eagle.pro.START"
        const val ACTION_STOP = "com.eagle.pro.STOP"
        const val ACTION_STATUS = "com.eagle.pro.STATUS"
        private const val CHANNEL_ID = "eagle_vpn_status"
        private const val NOTIFICATION_ID = 701

        @Volatile
        private var libboxReady = false
    }
}
