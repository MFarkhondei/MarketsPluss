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

    private val GOLD = Color.parseColor("#F5C542")
    private val WHITE = Color.parseColor("#F8FAFC")
    private val MUTED = Color.parseColor("#A8B2C1")
    private val POS = Color.parseColor("#34D399")
    private val NEG = Color.parseColor("#F87171")

    private const val ROW_COUNT = 14

    private data class FontSizes(val title: Float, val table: Float, val stamp: Float)

    private fun fontSizes(ctx: Context): FontSizes {
        val table = Prefs.getFontSp(ctx)
        return FontSizes(
            title = (table + 2.5f).coerceIn(14f, 20f),
            table = table,
            stamp = (table - 2.5f).coerceAtLeast(10f)
        )
    }

    // ---------- نمایش لیستی ----------

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
        RowSlot(R.id.row_9, R.id.iv_name_9, R.id.iv_value_9, R.id.iv_unit_9, R.id.iv_pct_9, R.id.badge_9, R.id.div_9),
        RowSlot(R.id.row_10, R.id.iv_name_10, R.id.iv_value_10, R.id.iv_unit_10, R.id.iv_pct_10, R.id.badge_10, R.id.div_10),
        RowSlot(R.id.row_11, R.id.iv_name_11, R.id.iv_value_11, R.id.iv_unit_11, R.id.iv_pct_11, R.id.badge_11, R.id.div_11),
        RowSlot(R.id.row_12, R.id.iv_name_12, R.id.iv_value_12, R.id.iv_unit_12, R.id.iv_pct_12, R.id.badge_12, R.id.div_12),
        RowSlot(R.id.row_13, R.id.iv_name_13, R.id.iv_value_13, R.id.iv_unit_13, R.id.iv_pct_13, R.id.badge_13, R.id.div_13)
    )

    // ---------- نمایش باکسی ----------

    private data class BoxSlot(
        val card: Int,
        val pctLabel: Int,
        val pct: Int,
        val badge: Int,
        val name: Int,
        val value: Int,
        val unit: Int
    )

    private val boxSlots = arrayOf(
        BoxSlot(R.id.card_0, R.id.iv_pctlabel_0, R.id.iv_pct_0, R.id.badge_0, R.id.iv_name_0, R.id.iv_value_0, R.id.iv_unit_0),
        BoxSlot(R.id.card_1, R.id.iv_pctlabel_1, R.id.iv_pct_1, R.id.badge_1, R.id.iv_name_1, R.id.iv_value_1, R.id.iv_unit_1),
        BoxSlot(R.id.card_2, R.id.iv_pctlabel_2, R.id.iv_pct_2, R.id.badge_2, R.id.iv_name_2, R.id.iv_value_2, R.id.iv_unit_2),
        BoxSlot(R.id.card_3, R.id.iv_pctlabel_3, R.id.iv_pct_3, R.id.badge_3, R.id.iv_name_3, R.id.iv_value_3, R.id.iv_unit_3),
        BoxSlot(R.id.card_4, R.id.iv_pctlabel_4, R.id.iv_pct_4, R.id.badge_4, R.id.iv_name_4, R.id.iv_value_4, R.id.iv_unit_4),
        BoxSlot(R.id.card_5, R.id.iv_pctlabel_5, R.id.iv_pct_5, R.id.badge_5, R.id.iv_name_5, R.id.iv_value_5, R.id.iv_unit_5),
        BoxSlot(R.id.card_6, R.id.iv_pctlabel_6, R.id.iv_pct_6, R.id.badge_6, R.id.iv_name_6, R.id.iv_value_6, R.id.iv_unit_6),
        BoxSlot(R.id.card_7, R.id.iv_pctlabel_7, R.id.iv_pct_7, R.id.badge_7, R.id.iv_name_7, R.id.iv_value_7, R.id.iv_unit_7),
        BoxSlot(R.id.card_8, R.id.iv_pctlabel_8, R.id.iv_pct_8, R.id.badge_8, R.id.iv_name_8, R.id.iv_value_8, R.id.iv_unit_8),
        BoxSlot(R.id.card_9, R.id.iv_pctlabel_9, R.id.iv_pct_9, R.id.badge_9, R.id.iv_name_9, R.id.iv_value_9, R.id.iv_unit_9),
        BoxSlot(R.id.card_10, R.id.iv_pctlabel_10, R.id.iv_pct_10, R.id.badge_10, R.id.iv_name_10, R.id.iv_value_10, R.id.iv_unit_10),
        BoxSlot(R.id.card_11, R.id.iv_pctlabel_11, R.id.iv_pct_11, R.id.badge_11, R.id.iv_name_11, R.id.iv_value_11, R.id.iv_unit_11),
        BoxSlot(R.id.card_12, R.id.iv_pctlabel_12, R.id.iv_pct_12, R.id.badge_12, R.id.iv_name_12, R.id.iv_value_12, R.id.iv_unit_12),
        BoxSlot(R.id.card_13, R.id.iv_pctlabel_13, R.id.iv_pct_13, R.id.badge_13, R.id.iv_name_13, R.id.iv_value_13, R.id.iv_unit_13)
    )

    fun allIds(ctx: Context): IntArray {
        val mgr = AppWidgetManager.getInstance(ctx)
        return mgr.getAppWidgetIds(ComponentName(ctx, MarketWidgetProvider::class.java))
    }

    fun fetchAndApply(ctx: Context): Boolean {
        return try {
            val previous = Prefs.getCache(ctx)?.let {
                try { gson.fromJson(it, MarketSnapshot::class.java) } catch (_: Exception) { null }
            }
            val snap = PriceClient.fetchSnapshot(previous)
            Prefs.saveCache(ctx, gson.toJson(snap))
            apply(ctx, snap, offline = false)
            SupabaseClient.saveDailyIfNeeded(ctx, snap)
            true
        } catch (e: Exception) {
            val cached = Prefs.getCache(ctx)
            if (cached != null) {
                try {
                    val snap = gson.fromJson(cached, MarketSnapshot::class.java)
                    apply(ctx, snap, offline = true)
                } catch (_: Exception) {
                    showError(ctx, e.message ?: "خطا")
                }
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
        return snap.items.take(ROW_COUNT)
    }

    private fun apply(ctx: Context, snap: MarketSnapshot, offline: Boolean) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val items = filteredItems(snap)
        val isBox = Prefs.getViewMode(app) == Prefs.VIEW_MODE_BOX

        val fs = fontSizes(app)
        for (id in allIds(app)) {
            val views = baseViews(app, id, fs, isBox)

            val stamp = if (offline) "آفلاین · ${snap.updatedAt}" else snap.updatedAt
            FontHelper.setTextBitmap(views, app, R.id.iv_updated_at, stamp, fs.stamp, MUTED)

            if (isBox) {
                items.forEachIndexed { i, item ->
                    val slot = boxSlots[i]
                    views.setViewVisibility(slot.card, View.VISIBLE)
                    bindBoxRow(app, views, slot, item, fs)
                }
                for (i in items.size until ROW_COUNT) {
                    views.setViewVisibility(boxSlots[i].card, View.GONE)
                }
            } else {
                items.forEachIndexed { i, item ->
                    val slot = slots[i]
                    views.setViewVisibility(slot.row, View.VISIBLE)
                    views.setViewVisibility(slot.div, View.VISIBLE)
                    bindRow(app, views, slot, item, fs)
                }
                for (i in items.size until ROW_COUNT) {
                    views.setViewVisibility(slots[i].row, View.GONE)
                    views.setViewVisibility(slots[i].div, View.GONE)
                }
            }

            mgr.updateAppWidget(id, views)
        }
    }

    private fun bindRow(ctx: Context, views: RemoteViews, slot: RowSlot, item: MarketItem, fs: FontSizes) {
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
            views, ctx, slot.name, item.name, fs.table, WHITE,
            bold = true, maxWidthDp = 140f
        )
        val valueText = NumberUtils.format(item.value, item.formatDecimals)
        FontHelper.setTextBitmap(
            views, ctx, slot.value,
            valueText,
            fs.table, WHITE, bold = true, align = FontHelper.Align.CENTER,
            maxWidthDp = 130f
        )
        if (item.unit.isBlank()) {
            views.setViewVisibility(slot.unit, View.GONE)
        } else {
            views.setViewVisibility(slot.unit, View.VISIBLE)
            FontHelper.setTextBitmap(
                views, ctx, slot.unit, item.unit, fs.stamp, MUTED,
                align = FontHelper.Align.CENTER, maxWidthDp = 130f
            )
        }
        FontHelper.setTextBitmap(
            views, ctx, slot.pct,
            NumberUtils.formatChange(item.changePercent) + arrow,
            fs.table, pctColor, bold = true, align = FontHelper.Align.CENTER,
            maxWidthDp = 80f
        )

        val badgeRes = when {
            positive -> R.drawable.bg_badge_pos
            neutral -> R.drawable.bg_badge_neutral
            else -> R.drawable.bg_badge_neg
        }
        views.setInt(slot.badge, "setBackgroundResource", badgeRes)
    }

    private fun bindBoxRow(ctx: Context, views: RemoteViews, slot: BoxSlot, item: MarketItem, fs: FontSizes) {
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

        val nameSize = fs.stamp
        val valueSize = (fs.table + 1.5f).coerceAtMost(18f)
        val unitSize = (fs.stamp - 1.5f).coerceAtLeast(9f)
        val pctSize = (unitSize + 2f)

        views.setViewVisibility(slot.pctLabel, View.GONE)

        FontHelper.setTextBitmap(
            views, ctx, slot.name, item.name, nameSize, WHITE,
            bold = true, maxWidthDp = 100f
        )
        val valueText = NumberUtils.format(item.value, item.formatDecimals)
        FontHelper.setTextBitmap(
            views, ctx, slot.value,
            valueText,
            valueSize, WHITE, bold = true, maxWidthDp = 100f
        )
        if (item.unit.isBlank()) {
            views.setViewVisibility(slot.unit, View.GONE)
        } else {
            views.setViewVisibility(slot.unit, View.VISIBLE)
            FontHelper.setTextBitmap(
                views, ctx, slot.unit, item.unit, unitSize, MUTED, maxWidthDp = 100f
            )
        }
        FontHelper.setTextBitmap(
            views, ctx, slot.pct,
            NumberUtils.formatChange(item.changePercent) + arrow,
            pctSize, pctColor, bold = true
        )

        val badgeRes = when {
            positive -> R.drawable.bg_badge_pos
            neutral -> R.drawable.bg_badge_neutral
            else -> R.drawable.bg_badge_neg
        }
        views.setInt(slot.badge, "setBackgroundResource", badgeRes)
    }

    private fun showError(ctx: Context, msg: String) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val fs = fontSizes(app)
        val isBox = Prefs.getViewMode(app) == Prefs.VIEW_MODE_BOX
        for (id in allIds(app)) {
            val views = baseViews(app, id, fs, isBox)
            FontHelper.setTextBitmap(views, app, R.id.iv_updated_at, msg.take(40), fs.stamp, NEG)
            mgr.updateAppWidget(id, views)
        }
    }

    private fun baseViews(ctx: Context, widgetId: Int, fs: FontSizes, isBox: Boolean): RemoteViews {
        val layoutRes = if (isBox) R.layout.widget_layout_box else R.layout.widget_layout
        val views = RemoteViews(ctx.packageName, layoutRes)

        FontHelper.setTextBitmap(
            views, ctx, R.id.iv_title,
            ctx.getString(R.string.widget_name),
            fs.title, GOLD, bold = true
        )
        FontHelper.setTextBitmap(
            views, ctx, R.id.iv_subtitle,
            ctx.getString(R.string.live_badge),
            fs.stamp, MUTED
        )

        val refresh = Intent(ctx, MarketWidgetProvider::class.java).apply {
            action = MarketWidgetProvider.ACTION_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 100 + widgetId, refresh,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // کلیک روی هر نقطه ویجت → بروزرسانی
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        views.setOnClickPendingIntent(R.id.btn_refresh, pi)
        views.setOnClickPendingIntent(R.id.iv_title, pi)
        views.setOnClickPendingIntent(R.id.iv_subtitle, pi)
        views.setOnClickPendingIntent(R.id.iv_updated_at, pi)

        if (!isBox) {
            FontHelper.setTextBitmap(
                views, ctx, R.id.iv_header_name,
                ctx.getString(R.string.col_name),
                fs.table, MUTED, bold = true
            )
            FontHelper.setTextBitmap(
                views, ctx, R.id.iv_header_value,
                ctx.getString(R.string.col_value),
                fs.table, MUTED, bold = true, align = FontHelper.Align.CENTER,
                maxWidthDp = 130f
            )
            FontHelper.setTextBitmap(
                views, ctx, R.id.iv_header_pct,
                ctx.getString(R.string.col_change),
                fs.table, MUTED, bold = true, align = FontHelper.Align.CENTER,
                maxWidthDp = 80f
            )
            views.setOnClickPendingIntent(R.id.iv_header_name, pi)
            views.setOnClickPendingIntent(R.id.iv_header_value, pi)
            views.setOnClickPendingIntent(R.id.iv_header_pct, pi)
            for (slot in slots) {
                views.setOnClickPendingIntent(slot.row, pi)
                views.setOnClickPendingIntent(slot.name, pi)
                views.setOnClickPendingIntent(slot.value, pi)
                views.setOnClickPendingIntent(slot.pct, pi)
                views.setOnClickPendingIntent(slot.badge, pi)
            }
        }

        if (isBox) {
            for (slot in boxSlots) {
                views.setOnClickPendingIntent(slot.card, pi)
            }
        }

        return views
    }
}
