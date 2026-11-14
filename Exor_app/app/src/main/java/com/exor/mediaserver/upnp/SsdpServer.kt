package com.exor.mediaserver.upnp

import com.exor.mediaserver.core.LocalNet
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * SSDP: answers M-SEARCH and announces presence. Fully event driven; the only timer
 * is one tiny re-announce every 15 minutes (max-age is 30 minutes).
 */
class SsdpServer(
    private val udn: String,
    private val local: LocalNet,
    private val location: () -> String,
) {
    @Volatile private var socket: MulticastSocket? = null
    @Volatile private var closed = false
    private var rx: Thread? = null

    private val group: InetAddress = InetAddress.getByName(Upnp.SSDP_ADDR)
    private val timer = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "exor-ssdp-timer").apply { isDaemon = true }
    }

    // NT -> USN
    private val targets: List<Pair<String, String>> = listOf(
        "upnp:rootdevice" to "$udn::upnp:rootdevice",
        udn to udn,
        Upnp.DEVICE_TYPE to "$udn::${Upnp.DEVICE_TYPE}",
        Upnp.CD_TYPE to "$udn::${Upnp.CD_TYPE}",
        Upnp.CM_TYPE to "$udn::${Upnp.CM_TYPE}",
    )

    fun start() {
        val s = MulticastSocket(null)
        s.reuseAddress = true
        s.bind(InetSocketAddress(Upnp.SSDP_PORT))
        s.networkInterface = local.nif
        s.timeToLive = 4
        s.joinGroup(InetSocketAddress(group, Upnp.SSDP_PORT), local.nif)
        socket = s
        closed = false

        rx = Thread({ receiveLoop(s) }, "exor-ssdp-rx").apply { isDaemon = true; start() }

        timer.schedule({ announce("ssdp:alive") }, 200, TimeUnit.MILLISECONDS)
        timer.schedule({ announce("ssdp:alive") }, 900, TimeUnit.MILLISECONDS)
        timer.scheduleWithFixedDelay({ announce("ssdp:alive") }, 15, 15, TimeUnit.MINUTES)
    }

    fun stop() {
        closed = true
        try {
            announce("ssdp:byebye")
        } catch (_: Exception) {
        }
        timer.shutdownNow()
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    private fun receiveLoop(s: MulticastSocket) {
        val buf = ByteArray(2048)
        while (!closed) {
            val p = DatagramPacket(buf, buf.size)
            try {
                s.receive(p)
            } catch (e: IOException) {
                break
            }
            val text = String(p.data, 0, p.length, Charsets.UTF_8)
            if (text.startsWith("M-SEARCH", ignoreCase = true)) {
                try {
                    handleSearch(text, p.socketAddress)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun headers(text: String): Map<String, String> {
        val m = HashMap<String, String>()
        text.split("\r\n").drop(1).forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) m[line.substring(0, i).trim().uppercase(Locale.ROOT)] = line.substring(i + 1).trim()
        }
        return m
    }

    private fun handleSearch(text: String, from: SocketAddress) {
        val h = headers(text)
        if (h["MAN"]?.trim('"') != "ssdp:discover") return
        val st = h["ST"] ?: return
        val mx = (h["MX"]?.toIntOrNull() ?: 1).coerceIn(1, 5)
        val matches = if (st == "ssdp:all") targets else targets.filter { it.first == st }
        for ((nt, usn) in matches) {
            val delay = Random.nextLong(0, minOf(mx * 300L, 900L))
            timer.schedule({
                val msg = "HTTP/1.1 200 OK\r\n" +
                    "CACHE-CONTROL: max-age=1800\r\n" +
                    "DATE: ${httpDate()}\r\n" +
                    "EXT:\r\n" +
                    "LOCATION: ${location()}\r\n" +
                    "SERVER: ${Upnp.serverHeader}\r\n" +
                    "ST: $nt\r\n" +
                    "USN: $usn\r\n\r\n"
                send(DatagramPacket(msg.toByteArray(), msg.length, from))
            }, delay, TimeUnit.MILLISECONDS)
        }
    }

    private fun announce(nts: String) {
        for ((nt, usn) in targets) {
            val msg = "NOTIFY * HTTP/1.1\r\n" +
                "HOST: ${Upnp.SSDP_ADDR}:${Upnp.SSDP_PORT}\r\n" +
                (if (nts == "ssdp:alive") "CACHE-CONTROL: max-age=1800\r\nLOCATION: ${location()}\r\nSERVER: ${Upnp.serverHeader}\r\n" else "") +
                "NT: $nt\r\nNTS: $nts\r\nUSN: $usn\r\n\r\n"
            send(DatagramPacket(msg.toByteArray(), msg.length, group, Upnp.SSDP_PORT))
        }
    }

    private fun send(p: DatagramPacket) {
        val s = socket ?: return
        try {
            synchronized(s) { s.send(p) }
        } catch (_: IOException) {
        }
    }

    private fun httpDate(): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }.format(Date())
}
