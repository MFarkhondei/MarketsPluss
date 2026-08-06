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

    // رنگ‌ها دقیقاً مثل TradePluss
    private val GOLD = Color.parseColor("#F5C542")
    private val WHITE = Color.parseColor("#F8FAFC")
    private val MUTED = Color.parseColor("#A8B2C1")
    private val SECONDARY = Color.parseColor("#94A3B8")
    private val POS = Color.parseColor("#34D399")
    private val NEG = Color.parseColor("#F87171")

    /** اندازه یکسان هدر جدول + نام/مقدار/درصد — مثل TradePluss */
    private const val TABLE_SP = 13f
    private const val ROW_COUNT = 10

    private data class RowSlot(
        val row: Int,
        val name: Int,
        val value: Int,
        val unit: Int,
        val pct: Int,
        val badge: Int,
        val div: Int
    )

    private val slots = arrayOf(
        RowSlot(R.id.row_0, R.id.iv_name_0, R.id.iv_value_0, R.id.iv_unit_0, R.id.iv_pct_0, R.id.badge_0, R.id.div_0),
        RowSlot(R.id.row_1, R.id.iv_name_1, R.id.iv_value_1, R.id.iv_unit_1, R.id.iv_pct_1, R.id.badge_1, R.id.div_1),
        RowSlot(R.id.row_2, R.id.iv_name_2, R.id.iv_value_2, R.id.iv_unit_2, R.id.iv_pct_2, R.id.badge_2, R.id.div_2),
        RowSlot(R.id.row_3, R.id.iv_name_3, R.id.iv_value_3, R.id.iv_unit_3, R.id.iv_pct_3, R.id.badge_3, R.id.div_3),
        RowSlot(R.id.row_4, R.id.iv_name_4, R.id.iv_value_4, R.id.iv_unit_4, R.id.iv_pct_4, R.id.badge_4, R.id.div_4),
        RowSlot(R.id.row_5, R.id.iv_name_5, R.id.iv_value_5, R.id.iv_unit_5, R.id.iv_pct_5, R.id.badge_5, R.id.div_5),
        RowSlot(R.id.row_6, R.id.iv_name_6, R.id.iv_value_6, R.id.iv_unit_6, R.id.iv_pct_6, R.id.badge_6, R.id.div_6),
        RowSlot(R.id.row_7, R.id.iv_name_7, R.id.iv_value_7, R.id.iv_unit_7, R.id.iv_pct_7, R.id.badge_7, R.id.div_7),
        RowSlot(R.id.row_8, R.id.iv_name_8, R.id.iv_value_8, R.id.iv_unit_8, R.id.iv_pct_8, R.id.badge_8, R.id.div_8),
        RowSlot(R.id.row_9, R.id.iv_name_9, R.id.iv_value_9, R.id.iv_unit_9, R.id.iv_pct_9, R.id.badge_9, R.id.div_9)
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

    private fun filteredItems(snap: MarketSnapshot): List<MarketItem> {
        return snap.items
            .filterNot { it.name.contains("حباب") }
            .take(ROW_COUNT)
    }

    private fun apply(ctx: Context, snap: MarketSnapshot, offline: Boolean) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val items = filteredItems(snap)

        for (id in allIds(app)) {
            val views = baseViews(app, id)

            val stamp = if (offline) "آفلاین · ${snap.updatedAt}" else snap.updatedAt
            FontHelper.setTextBitmap(views, app, R.id.iv_updated_at, stamp, 11f, MUTED)

            items.forEachIndexed { i, item ->
                val slot = slots[i]
                views.setViewVisibility(slot.row, View.VISIBLE)
                views.setViewVisibility(slot.div, View.VISIBLE)
                bindRow(app, views, slot, item)
            }
            for (i in items.size until ROW_COUNT) {
                views.setViewVisibility(slots[i].row, View.GONE)
                views.setViewVisibility(slots[i].div, View.GONE)
            }

            mgr.updateAppWidget(id, views)
        }
    }

    private fun bindRow(ctx: Context, views: RemoteViews, slot: RowSlot, item: MarketItem) {
        val positive = item.changePercent > 0
        val neutral = item.changePercent == 0.0
        val pctColor = when {
            positive -> POS
            neutral -> MUTED
            else -> NEG
        }
        val arrow = when {
            positive -> " ↑"
            item.changePercent < 0 -> " ↓"
            else -> ""
        }

        FontHelper.setTextBitmap(
            views, ctx, slot.name, item.name, TABLE_SP, WHITE,
            bold = true, maxWidthDp = 140f
        )
        FontHelper.setTextBitmap(
            views, ctx, slot.value,
            NumberUtils.format(item.value, item.formatDecimals),
            TABLE_SP, WHITE, bold = true, align = FontHelper.Align.CENTER
        )
        FontHelper.setTextBitmap(
            views, ctx, slot.unit,
            item.unit.ifBlank { " " },
            11f, MUTED, align = FontHelper.Align.CENTER
        )
        FontHelper.setTextBitmap(
            views, ctx, slot.pct,
            NumberUtils.formatChange(item.changePercent) + arrow,
            TABLE_SP, pctColor, bold = true, align = FontHelper.Align.CENTER
        )

        val badgeRes = when {
            positive -> R.drawable.bg_badge_pos
            neutral -> R.drawable.bg_badge_pos
            else -> R.drawable.bg_badge_neg
        }
        views.setInt(slot.badge, "setBackgroundResource", badgeRes)
    }

    private fun showError(ctx: Context, msg: String) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        for (id in allIds(app)) {
            val views = baseViews(app, id)
            FontHelper.setTextBitmap(views, app, R.id.iv_updated_at, msg.take(40), 11f, NEG)
            mgr.updateAppWidget(id, views)
        }
    }

    private fun baseViews(ctx: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(ctx.packageName, R.layout.widget_layout)

        // عنوان ۱۸sp طلایی bold — سمت چپ — مثل TradePluss
        FontHelper.setTextBitmap(
            views, ctx, R.id.iv_title,
            ctx.getString(R.string.widget_name),
            18f, GOLD, bold = true
        )

        // هدر جدول ۱۳sp muted bold — مثل TradePluss
        FontHelper.setTextBitmap(
            views, ctx, R.id.iv_header_name,
            ctx.getString(R.string.col_name),
            TABLE_SP, MUTED, bold = true
        )
        FontHelper.setTextBitmap(
            views, ctx, R.id.iv_header_value,
            ctx.getString(R.string.col_value),
            TABLE_SP, MUTED, bold = true, align = FontHelper.Align.CENTER
        )
        FontHelper.setTextBitmap(
            views, ctx, R.id.iv_header_pct,
            ctx.getString(R.string.col_change),
            TABLE_SP, MUTED, bold = true, align = FontHelper.Align.CENTER
        )

        // رفرش بی‌صدا: Broadcast → Service
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
