package com.exor.mediaserver.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.exor.mediaserver.App
import com.exor.mediaserver.MainActivity
import com.exor.mediaserver.R
import com.exor.mediaserver.core.LocalNet
import com.exor.mediaserver.core.NetUtil
import com.exor.mediaserver.core.Prefs
import com.exor.mediaserver.core.ServerState
import com.exor.mediaserver.core.ServerStatus
import com.exor.mediaserver.media.MediaStoreRepo
import com.exor.mediaserver.upnp.ContentDirectory
import com.exor.mediaserver.upnp.EventHub
import com.exor.mediaserver.upnp.ServiceHandlers
import com.exor.mediaserver.upnp.SsdpServer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service hosting SSDP + HTTP. Design goals: idle = zero work.
 *  - sockets block on I/O, no polling loops
 *  - Wi-Fi/CPU locks are held ONLY while a stream is actually running
 *  - MediaStore is observed (not scanned); a change only bumps an id and pings subscribers
 */
class ExorServerService : Service() {

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "exor-service") }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private var http: HttpServer? = null
    private var ssdp: SsdpServer? = null
    private var events: EventHub? = null
    private var streamer: MediaStreamer? = null
    private var directory: ContentDirectory? = null
    private var net: LocalNet? = null
    private var port = 0

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var cpuLock: PowerManager.WakeLock? = null
    private val streams = AtomicInteger(0)

    private var connectivity: ConnectivityManager? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var observer: ContentObserver? = null
    private val changeRunnable = Runnable { onLibraryChanged() }
    private val rebindRunnable = Runnable { worker.execute { rebindIfNeeded() } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(buildNotification("Starting…"))
        worker.execute { startServer() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        worker.execute { stopServer() }
        worker.shutdown()
        ServerState.status.value = ServerStatus()
        super.onDestroy()
    }

    // ---------- lifecycle of the actual server ----------

    private fun startServer() {
        if (http != null) return
        val found = NetUtil.findLocal()
        if (found == null) {
            publish(false, "No Wi-Fi / LAN connection. Waiting for network…")
            watchNetwork()
            return
        }
        try {
            net = found
            val repo = MediaStoreRepo(this)
            val dir = ContentDirectory(repo, prefs) { "http://${net?.ip?.hostAddress}:$port" }
            val ev = EventHub { dir.updateId }
            val str = MediaStreamer(this, repo, ::onStreamStart, ::onStreamEnd)
            val handlers = ServiceHandlers(dir)
            val router = Router(prefs.udn, { prefs.serverName }, handlers, ev, str)

            val server = HttpServer(prefs.port, router::route)
            port = server.start()
            http = server

            acquireMulticastLock()
            val s = SsdpServer(prefs.udn, found) { "http://${found.ip.hostAddress}:$port/description.xml" }
            s.start()

            directory = dir; events = ev; streamer = str; ssdp = s
            registerObserver()
            watchNetwork()
            publish(true, null)
        } catch (e: Exception) {
            stopServer()
            publish(false, "Could not start: ${e.message}")
        }
    }

    private fun stopServer(unwatch: Boolean = true) {
        unregisterObserver()
        if (unwatch) unwatchNetwork()
        try { ssdp?.stop() } catch (_: Exception) {}
        try { http?.stop() } catch (_: Exception) {}
        try { events?.shutdown() } catch (_: Exception) {}
        streamer?.clearCache()
        ssdp = null; http = null; events = null; streamer = null; directory = null; net = null
        releaseStreamLocks()
        streams.set(0)
        try { if (multicastLock?.isHeld == true) multicastLock?.release() } catch (_: Exception) {}
        multicastLock = null
    }

    private fun rebindIfNeeded() {
        val now = NetUtil.findLocal()
        val old = net
        if (http != null && now?.ip?.hostAddress == old?.ip?.hostAddress) return
        if (http == null && now == null) return // still no LAN: nothing to do, wait for the next callback
        stopServer(unwatch = false)
        startServer()
    }

    // ---------- change detection (event based, no timers) ----------

    private fun watchNetwork() {
        if (netCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = scheduleRebind()
            override fun onLost(network: Network) = scheduleRebind()
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            connectivity = cm
            netCallback = cb
        } catch (_: Exception) {
        }
    }

    private fun unwatchNetwork() {
        try { netCallback?.let { connectivity?.unregisterNetworkCallback(it) } } catch (_: Exception) {}
        netCallback = null
    }

    private fun scheduleRebind() {
        main.removeCallbacks(rebindRunnable)
        main.postDelayed(rebindRunnable, 1500)
    }

    private fun registerObserver() {
        if (observer != null) return
        val o = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                main.removeCallbacks(changeRunnable)
                main.postDelayed(changeRunnable, 3000) // debounce bursts of media scans
            }
        }
        val cr: ContentResolver = contentResolver
        listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        ).forEach { cr.registerContentObserver(it, true, o) }
        observer = o
    }

    private fun unregisterObserver() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun onLibraryChanged() {
        directory?.bump()
        streamer?.clearCache()
        worker.execute { events?.libraryChanged() }
    }

    // ---------- streams & locks ----------

    private fun onStreamStart() {
        if (streams.incrementAndGet() == 1) acquireStreamLocks()
        publish(true, null)
    }

    private fun onStreamEnd() {
        if (streams.decrementAndGet() <= 0) {
            streams.set(0)
            releaseStreamLocks()
        }
        publish(true, null)
    }

    private fun acquireMulticastLock() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("exor-ssdp").apply { setReferenceCounted(false); acquire() }
    }

    @Suppress("DEPRECATION")
    private fun acquireStreamLocks() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "exor-stream").apply {
                setReferenceCounted(false); acquire()
            }
            val pm = getSystemService(PowerManager::class.java)
            // 6 h safety timeout so a crashed stream can never pin the CPU forever.
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "exor:stream").apply {
                setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseStreamLocks() {
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
        try { if (cpuLock?.isHeld == true) cpuLock?.release() } catch (_: Exception) {}
        wifiLock = null; cpuLock = null
    }

    // ---------- status / notification ----------

    private fun publish(running: Boolean, message: String?) {
        val st = ServerStatus(
            running = running, name = prefs.serverName, ip = net?.ip?.hostAddress, port = port,
            activeStreams = streams.get(), message = message,
        )
        ServerState.status.value = st
        val text = when {
            !running -> message ?: "Stopped"
            st.activeStreams > 0 -> "${st.url} · ${st.activeStreams} streaming"
            else -> "${st.url} · idle"
        }
        try {
            getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ExorServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, App.CHANNEL_SERVER)
            .setSmallIcon(R.drawable.ic_stat_exor)
            .setContentTitle("Exor Media Server")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val ACTION_STOP = "com.exor.mediaserver.STOP"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ExorServerService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ExorServerService::class.java))
        }
    }
}
