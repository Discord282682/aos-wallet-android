package com.austland.aos.wallet

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MINING,
                    "Mining rewards",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notifies you when a 24h mining claim is confirmed on-chain." }
            )
        }
    }

    companion object {
        const val CHANNEL_MINING = "aos_mining_channel"
    }
}
