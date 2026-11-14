package com.exor.mediaserver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVER, getString(R.string.channel_server), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
    }

    companion object {
        const val CHANNEL_SERVER = "exor_server"
    }
}
