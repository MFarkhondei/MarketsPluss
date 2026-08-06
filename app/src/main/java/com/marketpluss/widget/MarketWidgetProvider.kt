package com.marketpluss.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class MarketWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRenderer.applyCache(context)
        UpdateScheduler.schedule(context)
        // کار شبکه در پس‌زمینه — بدون وابستگی به ForegroundService
        runRefresh(context.applicationContext)
    }

    override fun onEnabled(context: Context) {
        UpdateScheduler.schedule(context)
        runRefresh(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        UpdateScheduler.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        // ACTION_REFRESH را قبل از super هندل می‌کنیم تا goAsync در دسترس باشد
        if (action == ACTION_REFRESH) {
            val pending = goAsync()
            executor.execute {
                try {
                    WidgetRenderer.fetchAndApply(context.applicationContext)
                } catch (_: Exception) {
                    try {
                        WidgetRenderer.applyCache(context.applicationContext)
                    } catch (_: Exception) {
                    }
                } finally {
                    UpdateScheduler.scheduleNext(context.applicationContext)
                    try {
                        pending.finish()
                    } catch (_: Exception) {
                    }
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH = "com.marketpluss.widget.ACTION_REFRESH"

        private val executor = Executors.newSingleThreadExecutor()

        /** به‌روزرسانی در thread جدا — از Activity و onUpdate قابل فراخوانی */
        fun runRefresh(ctx: Context) {
            executor.execute {
                try {
                    WidgetRenderer.fetchAndApply(ctx.applicationContext)
                } catch (_: Exception) {
                    try {
                        WidgetRenderer.applyCache(ctx.applicationContext)
                    } catch (_: Exception) {
                    }
                } finally {
                    UpdateScheduler.scheduleNext(ctx.applicationContext)
                }
            }
        }
    }
}
