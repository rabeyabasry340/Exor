package com.exor.mediaserver.overlay

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.exor.mediaserver.App
import com.exor.mediaserver.MainActivity
import com.exor.mediaserver.R
import com.exor.mediaserver.core.ServerState
import com.exor.mediaserver.server.ExorServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Tiny draggable status window drawn over other apps. Plain Views: no Compose overhead. */
class FloatingWindowService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wm: WindowManager? = null
    private var root: View? = null
    private lateinit var status: TextView
    private lateinit var toggle: TextView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (root == null) show()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        root?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        root = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun show() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#F00B1030"))
                setStroke(dp(1), Color.parseColor("#8838E1FF"))
            }
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "Exor"
            setTextColor(Color.parseColor("#FF38E1FF"))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 15f
        }
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(18), 0, 0, 0)
            setOnClickListener { stopSelf() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(close)

        status = TextView(this).apply { setTextColor(Color.parseColor("#FFC9D0E8")); textSize = 12f; setPadding(0, dp(4), 0, dp(8)) }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toggle = pill("Start") {
            if (ServerState.status.value.running) ExorServerService.stop(this) else ExorServerService.start(this)
        }
        val open = pill("Open") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        row.addView(toggle)
        row.addView(open, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })

        panel.addView(header)
        panel.addView(status)
        panel.addView(row)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(24); y = dp(120) }

        // Drag by the header.
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startX = lp.x; startY = lp.y; touchX = e.rawX; touchY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).toInt()
                    lp.y = startY + (e.rawY - touchY).toInt()
                    try { wm?.updateViewLayout(panel, lp) } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        wm?.addView(panel, lp)
        root = panel

        scope.launch {
            ServerState.status.collect { st ->
                status.text = when {
                    !st.running -> st.message ?: "Server stopped"
                    st.activeStreams > 0 -> "${st.url}\n${st.activeStreams} streaming"
                    else -> "${st.url}\nidle"
                }
                toggle.text = if (st.running) "Stop" else "Start"
            }
        }
    }

    private fun pill(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 13f
        setPadding(dp(14), dp(6), dp(14), dp(6))
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#FF5B3FD6")) }
        setOnClickListener { onClick() }
    }

    private fun startInForeground() {
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val close = PendingIntent.getService(
            this, 3, Intent(this, FloatingWindowService::class.java).setAction(ACTION_CLOSE), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, App.CHANNEL_SERVER)
            .setSmallIcon(R.drawable.ic_stat_exor)
            .setContentTitle("Exor floating window")
            .setContentText("Tap to open the app")
            .setContentIntent(open)
            .setOngoing(true).setSilent(true)
            .addAction(0, "Close", close)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1002, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1002, n)
    }

    companion object {
        private const val ACTION_CLOSE = "com.exor.mediaserver.FLOAT_CLOSE"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, FloatingWindowService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, FloatingWindowService::class.java))
        }
    }
}
