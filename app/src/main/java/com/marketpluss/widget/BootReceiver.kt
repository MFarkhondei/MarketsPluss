package com.marketpluss.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED ||
            a == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            UpdateScheduler.schedule(context)
            MarketWidgetProvider.runRefresh(context.applicationContext)
        }
    }
}
