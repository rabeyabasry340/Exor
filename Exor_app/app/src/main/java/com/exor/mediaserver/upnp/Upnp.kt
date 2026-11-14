package com.exor.mediaserver.upnp

import android.os.Build

object Upnp {
    const val DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaServer:1"
    const val CD_TYPE = "urn:schemas-upnp-org:service:ContentDirectory:1"
    const val CM_TYPE = "urn:schemas-upnp-org:service:ConnectionManager:1"
    const val RENDERER_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val AVT_PREFIX = "urn:schemas-upnp-org:service:AVTransport:"

    const val SSDP_ADDR = "239.255.255.250"
    const val SSDP_PORT = 1900

    val serverHeader: String get() = "Android/${Build.VERSION.RELEASE} UPnP/1.0 DLNADOC/1.50 ExorMediaServer/0.1"

    fun esc(s: String?): String {
        if (s == null) return ""
        val sb = StringBuilder(s.length + 16)
        for (ch in s) when (ch) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&apos;")
            else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\t') sb.append(ch)
        }
        return sb.toString()
    }
}
