package com.marketpluss.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetUpdateService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "بروزرسانی بازار", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            val n = Notification.Builder(this, CHANNEL)
                .setContentTitle("بازار")
                .setContentText("در حال بروزرسانی…")
                .setSmallIcon(R.drawable.ic_refresh)
                .build()
            startForeground(1, n)
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.fetchAndApply(applicationContext)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "market_update"
        fun start(ctx: Context) {
            val i = Intent(ctx, WidgetUpdateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }
    }
}
