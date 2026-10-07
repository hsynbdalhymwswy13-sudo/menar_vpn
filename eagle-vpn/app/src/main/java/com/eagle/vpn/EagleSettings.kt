package com.eagle.vpn

import android.content.Context

class EagleSettings(context: Context) {
    private val prefs = context.getSharedPreferences("eagle_settings", Context.MODE_PRIVATE)

    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", false)
        set(value) = prefs.edit().putBoolean("auto_connect", value).apply()

    var autoReconnect: Boolean
        get() = prefs.getBoolean("auto_reconnect", true)
        set(value) = prefs.edit().putBoolean("auto_reconnect", value).apply()
}
