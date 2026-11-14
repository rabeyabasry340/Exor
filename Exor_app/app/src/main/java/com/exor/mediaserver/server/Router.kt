package com.exor.mediaserver.server

import android.os.Build
import com.exor.mediaserver.upnp.Descriptions
import com.exor.mediaserver.upnp.EventHub
import com.exor.mediaserver.upnp.ServiceHandlers
import com.exor.mediaserver.upnp.Soap
import com.exor.mediaserver.upnp.SoapError
import com.exor.mediaserver.upnp.Upnp

class Router(
    private val udn: String,
    private val friendlyName: () -> String,
    private val handlers: ServiceHandlers,
    private val events: EventHub,
    private val streamer: MediaStreamer,
) {
    private val xml = "text/xml; charset=\"utf-8\""

    fun route(req: HttpRequest, r: HttpResponder) {
        val path = req.path
        when {
            path == "/" || path == "/description.xml" ->
                r.send(200, xml, Descriptions.device(udn, friendlyName(), "${Build.MANUFACTURER} ${Build.MODEL}").toByteArray())
            path == "/cd/scpd.xml" -> r.send(200, xml, Descriptions.contentDirectoryScpd.toByteArray())
            path == "/cm/scpd.xml" -> r.send(200, xml, Descriptions.connectionManagerScpd.toByteArray())

            path == "/cd/control" && req.method == "POST" -> soap(req, r, Upnp.CD_TYPE, handlers::contentDirectory)
            path == "/cm/control" && req.method == "POST" -> soap(req, r, Upnp.CM_TYPE, handlers::connectionManager)

            path == "/cd/event" -> event(req, r, "cd")
            path == "/cm/event" -> event(req, r, "cm")

            path.startsWith("/media/") && (req.method == "GET" || req.method == "HEAD") -> {
                val p = path.removePrefix("/media/").split('/')
                if (p.size == 2) streamer.serveMedia(req, r, p[0], p[1]) else r.text(404, "Not found")
            }
            path.startsWith("/thumb/") -> {
                val p = path.removePrefix("/thumb/").split('/')
                if (p.size == 2) streamer.serveThumb(r, p[0], p[1]) else r.text(404, "Not found")
            }
            path.startsWith("/art/") -> streamer.serveAlbumArt(r, path.removePrefix("/art/"))
            path.startsWith("/icon/") -> {
                val size = path.removePrefix("/icon/").removeSuffix(".png").toIntOrNull()
                if (size == 48 || size == 120) r.send(200, "image/png", IconRenderer.png(size)) else r.text(404, "Not found")
            }
            else -> r.text(404, "Not found")
        }
    }

    private fun soap(req: HttpRequest, r: HttpResponder, type: String, handle: (com.exor.mediaserver.upnp.SoapCall) -> String) {
        val call = Soap.parse(req.body)
        if (call == null) {
            r.send(500, xml, Soap.fault(401, "Invalid Action").toByteArray())
            return
        }
        try {
            r.send(200, xml, handle(call).toByteArray(), mapOf("EXT" to ""))
        } catch (e: SoapError) {
            r.send(500, xml, Soap.fault(e.code, e.description).toByteArray())
        }
    }

    private fun event(req: HttpRequest, r: HttpResponder, service: String) {
        when (req.method) {
            "SUBSCRIBE" -> {
                val sid = req.headers["sid"]
                if (sid != null) {
                    val t = events.renew(sid, req.headers["timeout"])
                    if (t == null) r.send(412, null, ByteArray(0))
                    else r.send(200, null, ByteArray(0), mapOf("SID" to sid, "TIMEOUT" to "Second-$t"))
                } else {
                    val res = events.subscribe(service, req.headers["callback"], req.headers["timeout"])
                    if (res == null) r.send(412, null, ByteArray(0))
                    else r.send(200, null, ByteArray(0), mapOf("SID" to res.first, "TIMEOUT" to "Second-${res.second}"))
                }
            }
            "UNSUBSCRIBE" -> {
                val ok = req.headers["sid"]?.let { events.unsubscribe(it) } == true
                r.send(if (ok) 200 else 412, null, ByteArray(0))
            }
            else -> r.send(405, null, ByteArray(0))
        }
    }
}
