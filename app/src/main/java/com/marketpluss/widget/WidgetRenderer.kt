package com.marketpluss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.google.gson.Gson

object WidgetRenderer {
    private val gson = Gson()
    private val POS = Color.parseColor("#34D399")
    private val NEG = Color.parseColor("#F87171")
    private val MUTED = Color.parseColor("#94A3B8")
    private val GOLD = Color.parseColor("#F5C542")

    private const val ROW_COUNT = 10

    private val rowIds = intArrayOf(
        R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4,
        R.id.row_5, R.id.row_6, R.id.row_7, R.id.row_8, R.id.row_9
    )
    private val rowImageIds = intArrayOf(
        R.id.iv_row_0, R.id.iv_row_1, R.id.iv_row_2, R.id.iv_row_3, R.id.iv_row_4,
        R.id.iv_row_5, R.id.iv_row_6, R.id.iv_row_7, R.id.iv_row_8, R.id.iv_row_9
    )
    private val dividerIds = intArrayOf(
        R.id.div_0, R.id.div_1, R.id.div_2, R.id.div_3, R.id.div_4,
        R.id.div_5, R.id.div_6, R.id.div_7, R.id.div_8, R.id.div_9
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

    private fun widgetWidthDp(ctx: Context): Float {
        val dm = ctx.resources.displayMetrics
        val screenDp = dm.widthPixels / dm.density
        return (screenDp * 0.92f).coerceIn(260f, 400f)
    }

    /** فیلتر حباب از کش قدیمی + حداکثر ROW_COUNT ردیف */
    private fun filteredItems(snap: MarketSnapshot): List<MarketItem> {
        return snap.items
            .filterNot { it.name.contains("حباب") }
            .take(ROW_COUNT)
    }

    private fun apply(ctx: Context, snap: MarketSnapshot, offline: Boolean) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val widthDp = widgetWidthDp(app)

        // عنوان کامل بدون truncate، سمت چپ
        val titleBmp = WidgetText.render(
            app, app.getString(R.string.widget_name), 16f, GOLD,
            bold = true, maxWidthDp = 220f, ellipsize = false
        )
        val stamp = if (offline) "آفلاین · ${snap.updatedAt}" else snap.updatedAt
        val updatedBmp = WidgetText.render(app, stamp, 10f, MUTED, maxWidthDp = 100f)

        val headerBmp = WidgetText.renderHeader(
            app,
            app.getString(R.string.col_name),
            app.getString(R.string.col_value),
            app.getString(R.string.col_change),
            widthDp = widthDp
        )

        val items = filteredItems(snap)
        val rowBmps = items.map { item ->
            val arrow = when {
                item.changePercent > 0 -> " ↑"
                item.changePercent < 0 -> " ↓"
                else -> ""
            }
            val changeColor = when {
                item.changePercent > 0 -> POS
                item.changePercent < 0 -> NEG
                else -> MUTED
            }
            WidgetText.renderRow(
                app,
                name = item.name,
                value = NumberUtils.format(item.value, item.formatDecimals),
                unit = item.unit,
                change = NumberUtils.formatChange(item.changePercent) + arrow,
                changeColor = changeColor,
                widthDp = widthDp
            )
        }

        for (id in allIds(app)) {
            val views = baseViews(app, id)
            views.setImageViewBitmap(R.id.iv_app_title, titleBmp)
            views.setImageViewBitmap(R.id.iv_updated, updatedBmp)
            views.setImageViewBitmap(R.id.iv_header, headerBmp)

            rowBmps.forEachIndexed { i, bmp ->
                views.setViewVisibility(rowIds[i], View.VISIBLE)
                views.setViewVisibility(dividerIds[i], View.VISIBLE)
                views.setImageViewBitmap(rowImageIds[i], bmp)
            }
            for (i in rowBmps.size until ROW_COUNT) {
                views.setViewVisibility(rowIds[i], View.GONE)
                views.setViewVisibility(dividerIds[i], View.GONE)
            }

            mgr.updateAppWidget(id, views)
        }
    }

    private fun showError(ctx: Context, msg: String) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val titleBmp = WidgetText.render(
            app, app.getString(R.string.widget_name), 16f, GOLD,
            bold = true, maxWidthDp = 220f, ellipsize = false
        )
        val errBmp = WidgetText.render(app, msg.take(40), 11f, NEG, maxWidthDp = 160f)
        for (id in allIds(app)) {
            val views = baseViews(app, id)
            views.setImageViewBitmap(R.id.iv_app_title, titleBmp)
            views.setImageViewBitmap(R.id.iv_updated, errBmp)
            mgr.updateAppWidget(id, views)
        }
    }

    private fun baseViews(ctx: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(ctx.packageName, R.layout.widget_layout)
        // به‌روزرسانی بی‌صدا از طریق Broadcast → Service (بدون Activity و بدون کادر سیاه)
        val refresh = Intent(ctx, MarketWidgetProvider::class.java).apply {
            action = MarketWidgetProvider.ACTION_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 100 + widgetId, refresh,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, pi)
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        return views
    }
}
