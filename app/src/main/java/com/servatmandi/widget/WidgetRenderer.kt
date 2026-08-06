package com.servatmandi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.google.gson.Gson

object WidgetRenderer {
    private val gson = Gson()
    private val POS = Color.parseColor("#34D399")
    private val NEG = Color.parseColor("#F87171")
    private val MUTED = Color.parseColor("#94A3B8")

    private val nameIds = intArrayOf(
        R.id.tv_name_0, R.id.tv_name_1, R.id.tv_name_2, R.id.tv_name_3, R.id.tv_name_4,
        R.id.tv_name_5, R.id.tv_name_6, R.id.tv_name_7, R.id.tv_name_8, R.id.tv_name_9
    )
    private val valueIds = intArrayOf(
        R.id.tv_value_0, R.id.tv_value_1, R.id.tv_value_2, R.id.tv_value_3, R.id.tv_value_4,
        R.id.tv_value_5, R.id.tv_value_6, R.id.tv_value_7, R.id.tv_value_8, R.id.tv_value_9
    )
    private val unitIds = intArrayOf(
        R.id.tv_unit_0, R.id.tv_unit_1, R.id.tv_unit_2, R.id.tv_unit_3, R.id.tv_unit_4,
        R.id.tv_unit_5, R.id.tv_unit_6, R.id.tv_unit_7, R.id.tv_unit_8, R.id.tv_unit_9
    )
    private val changeIds = intArrayOf(
        R.id.tv_change_0, R.id.tv_change_1, R.id.tv_change_2, R.id.tv_change_3, R.id.tv_change_4,
        R.id.tv_change_5, R.id.tv_change_6, R.id.tv_change_7, R.id.tv_change_8, R.id.tv_change_9
    )

    fun allIds(ctx: Context): IntArray {
        val mgr = AppWidgetManager.getInstance(ctx)
        return mgr.getAppWidgetIds(ComponentName(ctx, MarketWidgetProvider::class.java))
    }

    fun fetchAndApply(ctx: Context): Boolean {
        return try {
            val snap = PriceClient.fetchSnapshot()
            Prefs.saveCache(ctx, gson.toJson(snap))
            apply(ctx, snap, offline = false)
            true
        } catch (e: Exception) {
            val cached = Prefs.getCache(ctx)
            if (cached != null) {
                val snap = gson.fromJson(cached, MarketSnapshot::class.java)
                apply(ctx, snap, offline = true)
            } else {
                showError(ctx, e.message ?: "خطا")
            }
            false
        }
    }

    fun applyCache(ctx: Context) {
        val cached = Prefs.getCache(ctx) ?: return
        try {
            val snap = gson.fromJson(cached, MarketSnapshot::class.java)
            apply(ctx, snap, offline = true)
        } catch (_: Exception) {
        }
    }

    private fun apply(ctx: Context, snap: MarketSnapshot, offline: Boolean) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        for (id in allIds(app)) {
            val views = baseViews(app, id)
            val stamp = if (offline) "آفلاین · ${snap.updatedAt}" else snap.updatedAt
            views.setTextViewText(R.id.tv_updated, stamp)
            snap.items.take(10).forEachIndexed { i, item ->
                views.setTextViewText(nameIds[i], item.name)
                views.setTextViewText(valueIds[i], NumberUtils.format(item.value, item.formatDecimals))
                views.setTextViewText(unitIds[i], item.unit)
                val chg = NumberUtils.formatChange(item.changePercent)
                val arrow = when {
                    item.changePercent > 0 -> " ↑"
                    item.changePercent < 0 -> " ↓"
                    else -> ""
                }
                views.setTextViewText(changeIds[i], chg + arrow)
                val color = when {
                    item.changePercent > 0 -> POS
                    item.changePercent < 0 -> NEG
                    else -> MUTED
                }
                views.setTextColor(changeIds[i], color)
            }
            mgr.updateAppWidget(id, views)
        }
    }

    private fun showError(ctx: Context, msg: String) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        for (id in allIds(app)) {
            val views = baseViews(app, id)
            views.setTextViewText(R.id.tv_updated, msg.take(30))
            mgr.updateAppWidget(id, views)
        }
    }

    private fun baseViews(ctx: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(ctx.packageName, R.layout.widget_layout)
        val refresh = Intent(ctx, SilentRefreshActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        views.setOnClickPendingIntent(
            R.id.btn_refresh,
            PendingIntent.getActivity(
                ctx, 100 + widgetId, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                ctx, 200 + widgetId,
                Intent(ctx, ConfigActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }
}
