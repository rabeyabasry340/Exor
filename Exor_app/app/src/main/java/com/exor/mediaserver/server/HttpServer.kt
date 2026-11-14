package com.exor.mediaserver.server

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class HttpRequest(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, String>, // keys lower-case
    val body: ByteArray,
)

class HttpResponder(private val out: OutputStream, val isHead: Boolean) {
    val stream: OutputStream get() = out

    fun send(status: Int, contentType: String?, body: ByteArray, extra: Map<String, String> = emptyMap()) {
        header(status, contentType, body.size.toLong(), extra)
        if (!isHead) out.write(body)
        out.flush()
    }

    fun text(status: Int, msg: String) = send(status, "text/plain; charset=utf-8", msg.toByteArray())

    /** Writes the status line + headers only. Caller streams the body (unless HEAD). */
    fun header(status: Int, contentType: String?, length: Long, extra: Map<String, String> = emptyMap()) {
        val sb = StringBuilder(256)
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
        sb.append("Server: ").append(com.exor.mediaserver.upnp.Upnp.serverHeader).append("\r\n")
        sb.append("Date: ").append(httpDate()).append("\r\n")
        if (contentType != null) sb.append("Content-Type: ").append(contentType).append("\r\n")
        sb.append("Content-Length: ").append(length).append("\r\n")
        sb.append("Connection: close\r\n")
        for ((k, v) in extra) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun reason(s: Int) = when (s) {
        200 -> "OK"; 206 -> "Partial Content"; 400 -> "Bad Request"; 404 -> "Not Found"
        405 -> "Method Not Allowed"; 412 -> "Precondition Failed"; 416 -> "Requested Range Not Satisfiable"
        500 -> "Internal Server Error"; else -> "Status"
    }

    private fun httpDate(): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }.format(Date())
}

/**
 * Small blocking HTTP/1.1 server. Bounded to a handful of threads (the TV opens 1-3
 * connections at a time), idle threads die after 20 s, one request per connection.
 */
class HttpServer(
    private val preferredPort: Int,
    private val router: (HttpRequest, HttpResponder) -> Unit,
) {
    @Volatile private var server: ServerSocket? = null
    private var acceptor: Thread? = null

    private val pool = ThreadPoolExecutor(
        6, 6, 20, TimeUnit.SECONDS, LinkedBlockingQueue(),
    ) { r -> Thread(r, "exor-http").apply { isDaemon = true } }.apply { allowCoreThreadTimeOut(true) }

    /** @return the port actually bound */
    fun start(): Int {
        val ss = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(preferredPort)) }
        } catch (e: BindException) {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(0)) }
        }
        server = ss
        acceptor = Thread({
            while (!ss.isClosed) {
                val s = try { ss.accept() } catch (e: IOException) { break }
                try { pool.execute { serve(s) } } catch (_: Exception) { try { s.close() } catch (_: Exception) {} }
            }
        }, "exor-http-accept").apply { isDaemon = true; start() }
        return ss.localPort
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
        pool.shutdownNow()
    }

    private fun serve(sock: Socket) {
        sock.use {
            try {
                sock.soTimeout = 20_000
                sock.tcpNoDelay = true
                val input = BufferedInputStream(sock.getInputStream(), 8192)
                val req = parse(input) ?: return
                sock.soTimeout = 0
                val out = sock.getOutputStream()
                val resp = HttpResponder(out, req.method == "HEAD")
                try {
                    router(req, resp)
                } catch (e: IOException) {
                    // client went away mid-stream: normal for seeking TVs
                } catch (e: Exception) {
                    try { resp.text(500, "Internal error") } catch (_: Exception) {}
                }
                try { out.flush() } catch (_: Exception) {}
            } catch (_: Exception) {
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val bo = ByteArrayOutputStream(128)
        while (true) {
            val b = input.read()
            if (b < 0) return if (bo.size() == 0) null else bo.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) bo.write(b)
            if (bo.size() > 8192) return null
        }
        return bo.toString("ISO-8859-1")
    }

    private fun parse(input: InputStream): HttpRequest? {
        val line = readLine(input) ?: return null
        val parts = line.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        var count = 0
        while (true) {
            val h = readLine(input) ?: return null
            if (h.isEmpty()) break
            if (++count > 64) return null
            val i = h.indexOf(':')
            if (i > 0) headers[h.substring(0, i).trim().lowercase(Locale.ROOT)] = h.substring(i + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len > 1_000_000) return null
        val body = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = input.read(body, read, len - read)
            if (n < 0) return null
            read += n
        }
        val target = parts[1]
        val q = target.indexOf('?')
        val path = java.net.URLDecoder.decode(if (q >= 0) target.substring(0, q) else target, "UTF-8")
        return HttpRequest(parts[0].uppercase(Locale.ROOT), path, if (q >= 0) target.substring(q + 1) else "", headers, body)
    }
}
