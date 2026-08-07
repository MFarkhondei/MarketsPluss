package com.marketpluss.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * دو آلارم موازی:
 * 1) setExactAndAllowWhileIdle / setAndAllowWhileIdle برای دقت بیشتر
 * 2) setInexactRepeating به‌عنوان پشتیبان در Doze
 * بعد از هر آپدیت scheduleNext دوباره از «الان» حساب می‌کند.
 */
object UpdateScheduler {
    private const val REQ_EXACT = 4401
    private const val REQ_INEXACT = 4402

    fun schedule(ctx: Context) {
        val minutes = Prefs.getIntervalMin(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactPi = pending(ctx, REQ_EXACT)
        val inexactPi = pending(ctx, REQ_INEXACT)
        am.cancel(exactPi)
        am.cancel(inexactPi)
        if (minutes <= 0) return

        val intervalMs = minutes.coerceAtLeast(15) * 60_000L
        val trigger = SystemClock.elapsedRealtime() + intervalMs

        // آلارم دقیق‌تر
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, exactPi
                        )
                    } else {
                        am.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, exactPi
                        )
                    }
                } else {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, exactPi
                    )
                }
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, exactPi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, exactPi
                )
            } catch (_: Exception) {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, exactPi)
            }
        } catch (_: Exception) {
            try {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, exactPi)
            } catch (_: Exception) {
            }
        }

        // پشتیبان تکراری
        try {
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                trigger,
                intervalMs.coerceAtLeast(AlarmManager.INTERVAL_FIFTEEN_MINUTES.toLong()),
                inexactPi
            )
        } catch (_: Exception) {
        }
    }

    fun scheduleNext(ctx: Context) {
        schedule(ctx)
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, REQ_EXACT))
        am.cancel(pending(ctx, REQ_INEXACT))
    }

    private fun pending(ctx: Context, req: Int): PendingIntent {
        val i = Intent(ctx, MarketWidgetProvider::class.java).apply {
            action = MarketWidgetProvider.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            ctx, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
