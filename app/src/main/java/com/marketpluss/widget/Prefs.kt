package com.marketpluss.widget

import android.content.Context

object Prefs {
    private const val NAME = "marketpluss_widget_prefs"
    private const val KEY_INTERVAL = "interval_min"
    private const val KEY_CACHE = "cache_json"
    private const val KEY_CACHE_AT = "cache_at"
    private const val KEY_FONT_SP = "font_size_sp"

    val INTERVAL_OPTIONS = intArrayOf(15, 30, 60, 120, 0)

    /** اندازه فونت لیست (sp) */
    val FONT_OPTIONS = floatArrayOf(12f, 13.5f, 15f, 16.5f)
    const val FONT_DEFAULT = 13.5f

    private fun p(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getIntervalMin(ctx: Context): Int = p(ctx).getInt(KEY_INTERVAL, 30)

    fun setIntervalMin(ctx: Context, min: Int) {
        p(ctx).edit().putInt(KEY_INTERVAL, min).apply()
    }

    fun getFontSp(ctx: Context): Float =
        p(ctx).getFloat(KEY_FONT_SP, FONT_DEFAULT)

    fun setFontSp(ctx: Context, sp: Float) {
        p(ctx).edit().putFloat(KEY_FONT_SP, sp).apply()
    }

    fun fontOptionIndex(ctx: Context): Int {
        val cur = getFontSp(ctx)
        val idx = FONT_OPTIONS.indexOfFirst { kotlin.math.abs(it - cur) < 0.01f }
        return if (idx >= 0) idx else 1
    }

    fun saveCache(ctx: Context, json: String) {
        p(ctx).edit()
            .putString(KEY_CACHE, json)
            .putLong(KEY_CACHE_AT, System.currentTimeMillis())
            .apply()
    }

    fun getCache(ctx: Context): String? = p(ctx).getString(KEY_CACHE, null)
}
