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
    private val PRIMARY = Color.parseColor("#F8FAFC")
    private val SECONDARY = Color.parseColor("#94A3B8")
    private val GOLD = Color.parseColor("#F5C542")

    private const val ROW_COUNT = 11

    private val rowIds = intArrayOf(
        R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4,
        R.id.row_5, R.id.row_6, R.id.row_7, R.id.row_8, R.id.row_9,
        R.id.row_10
    )
    private val nameIds = intArrayOf(
        R.id.iv_name_0, R.id.iv_name_1, R.id.iv_name_2, R.id.iv_name_3, R.id.iv_name_4,
        R.id.iv_name_5, R.id.iv_name_6, R.id.iv_name_7, R.id.iv_name_8, R.id.iv_name_9,
        R.id.iv_name_10
    )
    private val valueIds = intArrayOf(
        R.id.iv_value_0, R.id.iv_value_1, R.id.iv_value_2, R.id.iv_value_3, R.id.iv_value_4,
        R.id.iv_value_5, R.id.iv_value_6, R.id.iv_value_7, R.id.iv_value_8, R.id.iv_value_9,
        R.id.iv_value_10
    )
    private val unitIds = intArrayOf(
        R.id.iv_unit_0, R.id.iv_unit_1, R.id.iv_unit_2, R.id.iv_unit_3, R.id.iv_unit_4,
        R.id.iv_unit_5, R.id.iv_unit_6, R.id.iv_unit_7, R.id.iv_unit_8, R.id.iv_unit_9,
        R.id.iv_unit_10
    )
    private val changeIds = intArrayOf(
        R.id.iv_change_0, R.id.iv_change_1, R.id.iv_change_2, R.id.iv_change_3, R.id.iv_change_4,
        R.id.iv_change_5, R.id.iv_change_6, R.id.iv_change_7, R.id.iv_change_8, R.id.iv_change_9,
        R.id.iv_change_10
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

    private data class RowBitmaps(
        val name: android.graphics.Bitmap,
        val value: android.graphics.Bitmap,
        val unit: android.graphics.Bitmap,
        val change: android.graphics.Bitmap
    )

    private fun apply(ctx: Context, snap: MarketSnapshot, offline: Boolean) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)

        val titleBmp = BitmapTextRenderer.render(app, app.getString(R.string.widget_name), 15f, GOLD, bold = true, maxWidthDp = 150f)
        val colNameBmp = BitmapTextRenderer.render(app, app.getString(R.string.col_name), 11f, GOLD, bold = true, maxWidthDp = 110f)
        val colValueBmp = BitmapTextRenderer.render(app, app.getString(R.string.col_value), 11f, SECONDARY, bold = true, maxWidthDp = 90f, alignStart = false)
        val colChangeBmp = BitmapTextRenderer.render(app, app.getString(R.string.col_change), 11f, SECONDARY, bold = true, maxWidthDp = 80f, alignStart = false)
        val stamp = if (offline) "آفلاین · ${snap.updatedAt}" else snap.updatedAt
        val updatedBmp = BitmapTextRenderer.render(app, stamp, 10f, MUTED, maxWidthDp = 120f)

        val rows = snap.items.take(ROW_COUNT).map { item ->
            val nameBmp = BitmapTextRenderer.render(
                app, item.name, 12f, PRIMARY, bold = true, maxWidthDp = 120f, maxLines = 2
            )
            val valueBmp = BitmapTextRenderer.render(
                app, NumberUtils.format(item.value, item.formatDecimals),
                12f, PRIMARY, bold = true, maxWidthDp = 100f, alignStart = false
            )
            val unitBmp = BitmapTextRenderer.render(
                app, item.unit.ifBlank { " " }, 9f, MUTED, maxWidthDp = 100f, alignStart = false
            )
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
            val changeBmp = BitmapTextRenderer.render(
                app, NumberUtils.formatChange(item.changePercent) + arrow,
                11f, changeColor, bold = true, maxWidthDp = 85f, alignStart = false
            )
            RowBitmaps(nameBmp, valueBmp, unitBmp, changeBmp)
        }

        for (id in allIds(app)) {
            val views = baseViews(app, id)
            views.setImageViewBitmap(R.id.iv_app_title, titleBmp)
            views.setImageViewBitmap(R.id.iv_updated, updatedBmp)
            views.setImageViewBitmap(R.id.iv_col_name, colNameBmp)
            views.setImageViewBitmap(R.id.iv_col_value, colValueBmp)
            views.setImageViewBitmap(R.id.iv_col_change, colChangeBmp)

            rows.forEachIndexed { i, row ->
                views.setViewVisibility(rowIds[i], View.VISIBLE)
                views.setImageViewBitmap(nameIds[i], row.name)
                views.setImageViewBitmap(valueIds[i], row.value)
                views.setImageViewBitmap(unitIds[i], row.unit)
                views.setImageViewBitmap(changeIds[i], row.change)
                views.setViewVisibility(nameIds[i], View.VISIBLE)
                views.setViewVisibility(valueIds[i], View.VISIBLE)
                views.setViewVisibility(unitIds[i], View.VISIBLE)
                views.setViewVisibility(changeIds[i], View.VISIBLE)
            }
            for (i in rows.size until ROW_COUNT) {
                views.setViewVisibility(rowIds[i], View.GONE)
                views.setViewVisibility(nameIds[i], View.GONE)
                views.setViewVisibility(valueIds[i], View.GONE)
                views.setViewVisibility(unitIds[i], View.GONE)
                views.setViewVisibility(changeIds[i], View.GONE)
            }

            mgr.updateAppWidget(id, views)
        }
    }

    private fun showError(ctx: Context, msg: String) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val titleBmp = BitmapTextRenderer.render(app, app.getString(R.string.widget_name), 15f, GOLD, bold = true, maxWidthDp = 150f)
        val errBmp = BitmapTextRenderer.render(app, msg.take(40), 11f, NEG, maxWidthDp = 140f)
        for (id in allIds(app)) {
            val views = baseViews(app, id)
            views.setImageViewBitmap(R.id.iv_app_title, titleBmp)
            views.setImageViewBitmap(R.id.iv_updated, errBmp)
            mgr.updateAppWidget(id, views)
        }
    }

    private fun baseViews(ctx: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(ctx.packageName, R.layout.widget_layout)
        val refresh = Intent(ctx, SilentRefreshActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        val refreshPendingIntent = PendingIntent.getActivity(
            ctx, 100 + widgetId, refresh,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_root, refreshPendingIntent)
        return views
    }
}
