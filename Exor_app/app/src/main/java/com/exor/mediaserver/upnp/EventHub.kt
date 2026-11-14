package com.exor.mediaserver.upnp

import com.exor.mediaserver.media.Dlna
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Minimal GENA eventing. Control points (the TV) subscribe, get one initial NOTIFY, and a
 * new one only when the library really changes. No periodic traffic.
 */
class EventHub(private val updateId: () -> Int) {

    private class Sub(val sid: String, val service: String, val callbacks: List<String>, var expires: Long) {
        var seq = 0L
    }

    private val subs = ConcurrentHashMap<String, Sub>()
    private val worker = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "exor-gena").apply { isDaemon = true }
    }

    fun shutdown() {
        worker.shutdownNow()
        subs.clear()
    }

    /** @return SID and timeout seconds, or null if the request is invalid. */
    fun subscribe(service: String, callbackHeader: String?, timeoutHeader: String?): Pair<String, Int>? {
        val urls = Regex("<([^>]+)>").findAll(callbackHeader ?: return null).map { it.groupValues[1] }
            .filter { it.startsWith("http://") }.toList()
        if (urls.isEmpty()) return null
        val timeout = parseTimeout(timeoutHeader)
        val sub = Sub("uuid:${UUID.randomUUID()}", service, urls, now() + timeout * 1000L)
        subs[sub.sid] = sub
        worker.schedule({ send(sub) }, 400, TimeUnit.MILLISECONDS)
        return sub.sid to timeout
    }

    fun renew(sid: String, timeoutHeader: String?): Int? {
        val sub = subs[sid] ?: return null
        val timeout = parseTimeout(timeoutHeader)
        sub.expires = now() + timeout * 1000L
        return timeout
    }

    fun unsubscribe(sid: String): Boolean = subs.remove(sid) != null

    /** Called (debounced by the caller) when the library changed. */
    fun libraryChanged() {
        val t = now()
        subs.values.removeIf { it.expires < t }
        subs.values.filter { it.service == "cd" }.forEach { s -> worker.execute { send(s) } }
    }

    private fun parseTimeout(h: String?): Int =
        (h?.substringAfter("Second-", "")?.toIntOrNull() ?: 1800).coerceIn(60, 1800)

    private fun now() = System.currentTimeMillis()

    private fun body(sub: Sub): String {
        val props = if (sub.service == "cd") {
            "<e:property><SystemUpdateID>${updateId()}</SystemUpdateID></e:property>"
        } else {
            "<e:property><SourceProtocolInfo>${Upnp.esc(Dlna.sourceProtocolInfo)}</SourceProtocolInfo></e:property>" +
                "<e:property><SinkProtocolInfo></SinkProtocolInfo></e:property>" +
                "<e:property><CurrentConnectionIDs>0</CurrentConnectionIDs></e:property>"
        }
        return """<?xml version="1.0" encoding="utf-8"?><e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">$props</e:propertyset>"""
    }

    private fun send(sub: Sub) {
        val payload = body(sub).toByteArray()
        val seq = sub.seq++
        for (cb in sub.callbacks) {
            try {
                val u = URL(cb)
                val port = if (u.port > 0) u.port else 80
                Socket().use { s ->
                    s.connect(InetSocketAddress(u.host, port), 3000)
                    s.soTimeout = 3000
                    val path = if (u.file.isNullOrEmpty()) "/" else u.file
                    val head = "NOTIFY $path HTTP/1.1\r\nHOST: ${u.host}:$port\r\n" +
                        "CONTENT-TYPE: text/xml; charset=\"utf-8\"\r\nNT: upnp:event\r\nNTS: upnp:propchange\r\n" +
                        "SID: ${sub.sid}\r\nSEQ: $seq\r\nCONTENT-LENGTH: ${payload.size}\r\nConnection: close\r\n\r\n"
                    s.getOutputStream().apply { write(head.toByteArray()); write(payload); flush() }
                    s.getInputStream().read(ByteArray(256)) // drain the status line, ignore content
                }
                return
            } catch (_: Exception) {
                // try next callback URL
            }
        }
        // Unreachable subscriber: drop it, it will resubscribe if it still cares.
        if (sub.seq > 1) subs.remove(sub.sid)
    }
}
