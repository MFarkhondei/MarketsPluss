package com.marketpluss.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * زمان‌بندی به‌روزرسانی ویجت.
 * از Broadcast → AppWidgetProvider استفاده می‌کند (نه getService مستقیم)
 * تا روی اندروید ۸+ و Doze قابل اطمینان‌تر باشد.
 * بعد از هر به‌روزرسانی موفق، [scheduleNext] دوباره زمان بعدی را ست می‌کند.
 */
object UpdateScheduler {
    private const val REQ = 4401

    fun schedule(ctx: Context) {
        val minutes = Prefs.getIntervalMin(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx)
        am.cancel(pi)
        if (minutes <= 0) return
        val trigger = SystemClock.elapsedRealtime() + minutes * 60_000L
        setAlarm(am, trigger, pi)
    }

    /** بعد از هر آپدیت، آلارم بعدی را از همین لحظه محاسبه کن */
    fun scheduleNext(ctx: Context) {
        schedule(ctx)
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }

    private fun setAlarm(am: AlarmManager, triggerElapsed: Long, pi: PendingIntent) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    am.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerElapsed,
                        pi
                    )
                }
                else -> {
                    am.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerElapsed,
                        pi
                    )
                }
            }
        } catch (_: SecurityException) {
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsed,
                AlarmManager.INTERVAL_HALF_HOUR,
                pi
            )
        }
    }

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, MarketWidgetProvider::class.java).apply {
            action = MarketWidgetProvider.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            ctx, REQ, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
