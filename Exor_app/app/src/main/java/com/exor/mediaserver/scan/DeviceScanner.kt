package com.exor.mediaserver.scan

import android.util.Xml
import com.exor.mediaserver.core.NetUtil
import com.exor.mediaserver.upnp.Upnp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL

/**
 * One-shot scan for UPnP MediaRenderers (Bravia TVs, AV receivers...). Runs only when the
 * user taps refresh: ~4 seconds of listening, then the socket is closed. No background polling.
 */
class DeviceScanner {

    suspend fun scan(listenMs: Long = 4000): List<RendererDevice> = withContext(Dispatchers.IO) {
        val local = NetUtil.findLocal() ?: return@withContext emptyList()
        val locations = LinkedHashMap<String, String>() // USN-ish key -> LOCATION

        val s = MulticastSocket(null)
        try {
            s.reuseAddress = true
            s.bind(InetSocketAddress(local.ip, 0))
            s.networkInterface = local.nif
            s.timeToLive = 4
            val msg = "M-SEARCH * HTTP/1.1\r\nHOST: ${Upnp.SSDP_ADDR}:${Upnp.SSDP_PORT}\r\n" +
                "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ${Upnp.RENDERER_TYPE}\r\n" +
                "USER-AGENT: ${Upnp.serverHeader}\r\n\r\n"
            val group = InetAddress.getByName(Upnp.SSDP_ADDR)
            repeat(2) {
                s.send(DatagramPacket(msg.toByteArray(), msg.length, group, Upnp.SSDP_PORT))
            }
            val end = System.currentTimeMillis() + listenMs
            val buf = ByteArray(2048)
            while (true) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                s.soTimeout = left.toInt()
                val p = DatagramPacket(buf, buf.size)
                try {
                    s.receive(p)
                } catch (e: SocketTimeoutException) {
                    break
                }
                val text = String(p.data, 0, p.length, Charsets.UTF_8)
                var loc: String? = null
                var usn = ""
                text.split("\r\n").forEach { line ->
                    val i = line.indexOf(':')
                    if (i > 0) {
                        when (line.substring(0, i).trim().uppercase()) {
                            "LOCATION" -> loc = line.substring(i + 1).trim()
                            "USN" -> usn = line.substring(i + 1).trim()
                        }
                    }
                }
                loc?.let { locations.putIfAbsent(usn.substringBefore("::").ifEmpty { it }, it) }
            }
        } catch (_: IOException) {
        } finally {
            try { s.close() } catch (_: Exception) {}
        }

        locations.values.distinct().mapNotNull { describe(it) }
            .distinctBy { it.udn }
            .sortedBy { it.name.lowercase() }
    }

    private fun describe(location: String): RendererDevice? {
        return try {
            val conn = URL(location).openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            parse(location, bytes)
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(location: String, xml: ByteArray): RendererDevice? {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(xml), null)

        var urlBase: String? = null
        var udn = ""; var name = ""; var maker = ""; var model = ""
        var deviceType = ""
        var avt: String? = null; var rc: String? = null

        var curService: String? = null
        var curControl: String? = null
        var depthDevice = 0
        var tag = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name.substringAfter(':')
                    if (tag == "device") depthDevice++
                    if (tag == "service") { curService = null; curControl = null }
                }
                XmlPullParser.TEXT -> {
                    val t = p.text.trim()
                    if (t.isNotEmpty()) when (tag) {
                        "URLBase" -> urlBase = t
                        // Only the root device's own fields (first occurrence wins)
                        "UDN" -> if (udn.isEmpty()) udn = t
                        "friendlyName" -> if (name.isEmpty()) name = t
                        "manufacturer" -> if (maker.isEmpty()) maker = t
                        "modelName" -> if (model.isEmpty()) model = t
                        "deviceType" -> if (deviceType.isEmpty()) deviceType = t
                        "serviceType" -> curService = t
                        "controlURL" -> curControl = t
                    }
                }
                XmlPullParser.END_TAG -> {
                    val end = p.name.substringAfter(':')
                    if (end == "service") {
                        val st = curService; val cu = curControl
                        if (st != null && cu != null) {
                            if (st.startsWith(Upnp.AVT_PREFIX) && avt == null) avt = resolve(urlBase ?: location, cu)
                            if (st.contains(":service:RenderingControl:") && rc == null) rc = resolve(urlBase ?: location, cu)
                        }
                    }
                    if (end == "device") depthDevice--
                    tag = ""
                }
            }
            ev = p.next()
        }
        if (udn.isEmpty() || !deviceType.contains("MediaRenderer")) return null
        val ip = URL(location).host
        return RendererDevice(udn, name.ifEmpty { ip }, maker, model, ip, location, avt, rc)
    }

    private fun resolve(base: String, path: String): String =
        try { URL(URL(base), path).toString() } catch (e: Exception) { path }
}
