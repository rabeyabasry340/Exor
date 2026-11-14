package com.exor.mediaserver.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.exor.mediaserver.App
import com.exor.mediaserver.MainActivity
import com.exor.mediaserver.R
import com.exor.mediaserver.core.NetUtil
import com.exor.mediaserver.core.Prefs
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight HTTP host for a single .html file (or directory index).
 * Shows a login page; password from Prefs (default "Frozen").
 * Foreground service so Android does not kill it.
 */
class WebHostService : Service() {

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private var htmlFile: File? = null
    private var port = 8080
    private var password = "Frozen"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val path = intent?.getStringExtra(EXTRA_PATH) ?: return START_NOT_STICKY
        val f = File(path)
        if (!f.exists() || !f.canRead()) {
            stopSelf()
            return START_NOT_STICKY
        }
        htmlFile = f
        val prefs = Prefs(this)
        port = prefs.webPort
        password = prefs.webPassword

        startForegroundCompat()
        if (running.compareAndSet(false, true)) {
            pool.execute { serveLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        pool.shutdownNow()
        super.onDestroy()
    }

    private fun serveLoop() {
        try {
            serverSocket = ServerSocket(port)
            while (running.get()) {
                val client = try { serverSocket!!.accept() } catch (_: Exception) { break }
                pool.execute { handle(client) }
            }
        } catch (_: Exception) {
            // port in use or closed
        } finally {
            running.set(false)
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = input.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }

            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            val out = socket.getOutputStream()
            when {
                path == "/login" && method == "POST" -> {
                    // very simple body read (Content-Length limited)
                    val len = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = CharArray(len.coerceAtMost(4096))
                    if (len > 0) input.read(body, 0, body.size)
                    val bodyStr = String(body)
                    val pass = bodyStr.substringAfter("password=", "").substringBefore("&")
                    if (pass == password) {
                        writeResponse(out, 302, "text/html", "", mapOf("Location" to "/", "Set-Cookie" to "exor_auth=1; Path=/; HttpOnly"))
                    } else {
                        writeResponse(out, 200, "text/html", loginPage("Wrong password"))
                    }
                }
                path == "/" || path == "/index.html" -> {
                    val cookie = headers["cookie"] ?: ""
                    if (cookie.contains("exor_auth=1")) {
                        val bytes = htmlFile!!.readBytes()
                        writeRaw(out, 200, "text/html; charset=utf-8", bytes)
                    } else {
                        writeResponse(out, 200, "text/html", loginPage(null))
                    }
                }
                else -> writeResponse(out, 404, "text/plain", "Not found")
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun loginPage(error: String?): String {
        val err = if (error != null) "<p style='color:#ff6b6b'>$error</p>" else ""
        return """
            <!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Exor Login</title>
            <style>
              body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
                   background:linear-gradient(160deg,#0a0e27,#1a1040);font-family:system-ui,sans-serif;color:#fff}
              .card{background:rgba(255,255,255,.08);border:1px solid rgba(0,212,255,.4);border-radius:20px;
                    padding:32px;width:min(360px,90vw);backdrop-filter:blur(12px)}
              h1{margin:0 0 8px;font-size:1.5rem} p{opacity:.7;margin:0 0 20px}
              input{width:100%;padding:12px 14px;border-radius:12px;border:1px solid #444;background:#111;color:#fff;font-size:16px;box-sizing:border-box}
              button{margin-top:14px;width:100%;padding:12px;border:0;border-radius:12px;background:linear-gradient(90deg,#00d4ff,#7b2cbf);color:#fff;font-weight:700;font-size:16px}
            </style></head><body>
            <div class="card"><h1>Exor Media Server</h1><p>Enter password to continue</p>$err
            <form method="POST" action="/login"><input type="password" name="password" placeholder="Password" autofocus required>
            <button type="submit">Unlock</button></form></div></body></html>
        """.trimIndent()
    }

    private fun writeResponse(out: OutputStream, code: Int, type: String, body: String, extra: Map<String, String> = emptyMap()) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writeRaw(out, code, type, bytes, extra)
    }

    private fun writeRaw(out: OutputStream, code: Int, type: String, body: ByteArray, extra: Map<String, String> = emptyMap()) {
        val status = when (code) {
            200 -> "200 OK"
            302 -> "302 Found"
            404 -> "404 Not Found"
            else -> "$code"
        }
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $status\r\n")
        sb.append("Content-Type: $type\r\n")
        sb.append("Content-Length: ${body.size}\r\n")
        sb.append("Connection: close\r\n")
        extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun startForegroundCompat() {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, App.CHANNEL_SERVER)
            .setSmallIcon(R.drawable.ic_stat_exor)
            .setContentTitle("Server running")
            .setContentText("Web host on port $port")
            .setContentIntent(pi)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val ACTION_STOP = "com.exor.mediaserver.WEB_STOP"
        private const val EXTRA_PATH = "path"
        private const val NOTIF_ID = 42

        fun start(ctx: Context, absolutePath: String) {
            val i = Intent(ctx, WebHostService::class.java).putExtra(EXTRA_PATH, absolutePath)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, WebHostService::class.java).setAction(ACTION_STOP))
        }
    }
}
