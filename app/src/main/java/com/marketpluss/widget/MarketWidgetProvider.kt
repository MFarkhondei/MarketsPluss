package com.marketpluss.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class MarketWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRenderer.applyCache(context)
        UpdateScheduler.schedule(context)
        WidgetUpdateService.start(context)
    }

    override fun onEnabled(context: Context) {
        UpdateScheduler.schedule(context)
        WidgetUpdateService.start(context)
    }

    override fun onDisabled(context: Context) {
        UpdateScheduler.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_REFRESH) {
            WidgetUpdateService.start(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.marketpluss.widget.ACTION_REFRESH"
    }
}
