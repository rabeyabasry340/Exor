package com.exor.mediaserver.core

import android.content.Context
import android.os.Build
import java.util.UUID

/** Tiny SharedPreferences wrapper. Everything is read lazily, nothing polls. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("exor", Context.MODE_PRIVATE)

    /** Stable device identity so the TV keeps recognising this server between runs. */
    val udn: String
        get() = sp.getString("udn", null)
            ?: "uuid:${UUID.randomUUID()}".also { sp.edit().putString("udn", it).apply() }

    var serverName: String
        get() = sp.getString("name", null) ?: "Exor Media Server (${Build.MODEL})"
        set(v) = sp.edit().putString("name", v.trim().ifEmpty { "Exor Media Server" }).apply()

    var port: Int
        get() = sp.getInt("port", 49500)
        set(v) = sp.edit().putInt("port", v.coerceIn(1024, 65535)).apply()

    var autoStartServer: Boolean
        get() = sp.getBoolean("autostart", false)
        set(v) = sp.edit().putBoolean("autostart", v).apply()

    var shareVideos: Boolean
        get() = sp.getBoolean("share_v", true)
        set(v) = sp.edit().putBoolean("share_v", v).apply()

    var shareMusic: Boolean
        get() = sp.getBoolean("share_m", true)
        set(v) = sp.edit().putBoolean("share_m", v).apply()

    var sharePhotos: Boolean
        get() = sp.getBoolean("share_p", true)
        set(v) = sp.edit().putBoolean("share_p", v).apply()

    var webPassword: String
        get() = sp.getString("web_pass", "Frozen") ?: "Frozen"
        set(v) = sp.edit().putString("web_pass", v.ifBlank { "Frozen" }).apply()

    var webPort: Int
        get() = sp.getInt("web_port", 8080)
        set(v) = sp.edit().putInt("web_port", v.coerceIn(1024, 65535)).apply()

    var webHostPath: String?
        get() = sp.getString("web_host_path", null)
        set(v) = sp.edit().putString("web_host_path", v).apply()
}
