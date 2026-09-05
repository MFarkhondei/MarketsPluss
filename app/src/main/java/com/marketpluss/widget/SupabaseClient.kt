package com.marketpluss.widget

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ذخیره‌ی روزانه‌ی قیمت‌ها در Supabase. رکورد امروز در هر بروزرسانی upsert
 * می‌شود تا پس از پایان روز، آخرین قیمت دریافت‌شده به‌عنوان قیمت پایانی بماند.
 *
 * پیش‌نیاز: این جدول را یک بار در SQL editor پروژه‌ی سوپابیس بسازید:
 *
 *   create table market_daily_prices (
 *     id bigserial primary key,
 *     snapshot_date date not null,
 *     name text not null,
 *     value numeric,
 *     change_percent numeric,
 *     unit text,
 *     created_at timestamptz not null default now(),
 *     unique (snapshot_date, name)
 *   );
 *
 * سپس SUPABASE_URL و SUPABASE_ANON_KEY را از Project Settings → API پر کنید.
 * تا وقتی پر نشده باشند، این کلاس کاری انجام نمی‌دهد (خطایی هم تولید نمی‌کند).
 */
data class DailyPrice(val date: String, val value: Double)

object SupabaseClient {
    private const val SUPABASE_URL = "https://qlpdrrbeyvejhjrrnihp.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_gOE9-0Z2BJ_EfhcYeS6mMA_nt1pB2Ed"
    private const val TABLE = "market_daily_prices"

    private val gson = Gson()

    private const val TAG = "SupabaseClient"
    private const val RSI_PERIOD = 14
    private const val HISTORY_LIMIT = 5000

    private fun isConfigured(): Boolean =
        !SUPABASE_URL.contains("YOUR-PROJECT") && !SUPABASE_ANON_KEY.contains("YOUR-ANON-KEY")

    /**
     * شاخص‌های روزانه را بر پایه تاریخچه Supabase محاسبه می‌کند:
     * - درصد تغییر نسبت به آخرین قیمت ثبت‌شده در آخرین روز قبل از امروز
     * - RSI استاندارد Wilder با دوره ۱۴ روزه
     */
    fun attachDailyMetrics(snap: MarketSnapshot, previous: MarketSnapshot? = null): MarketSnapshot {
        if (!isConfigured()) return withPreviousMetrics(snap, previous)

        return try {
            val history = fetchDailyHistory()
            val today = todayTehran()
            val previousItems = previous?.items?.associateBy { it.name }.orEmpty()
            val items = snap.items.map { item ->
                val dailyValues = history[item.name].orEmpty().toMutableMap()
                val previousClose = dailyValues.entries
                    .filter { it.key < today && it.value > 0.0 }
                    .maxByOrNull { it.key }
                    ?.value
                if (item.value > 0.0) dailyValues[today] = item.value
                val closes = dailyValues.toSortedMap().values.filter { it > 0.0 }
                item.copy(
                    changePercent = calculateDailyChange(item.value, previousClose)
                        ?: previousItems[item.name]?.changePercent
                        ?: 0.0,
                    rsi14 = calculateRsi14(closes) ?: previousItems[item.name]?.rsi14
                )
            }
            snap.copy(items = items)
        } catch (e: Exception) {
            Log.e(TAG, "خطا در دریافت تاریخچه برای شاخص‌های روزانه", e)
            withPreviousMetrics(snap, previous)
        }
    }

    internal fun calculateDailyChange(current: Double, previousClose: Double?): Double? {
        if (current <= 0.0 || previousClose == null || previousClose <= 0.0) return null
        return ((current - previousClose) / previousClose) * 100.0
    }

    private fun fetchDailyHistory(): Map<String, Map<String, Double>> {
        val url = URL(
            "$SUPABASE_URL/rest/v1/$TABLE" +
                "?select=snapshot_date,name,value&order=snapshot_date.desc&limit=$HISTORY_LIMIT"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader -> reader.readText() }
            }.orEmpty()
            if (code !in 200..299) throw Exception("HTTP $code: $body")

            val rows = gson.fromJson(body, JsonArray::class.java)
            val result = mutableMapOf<String, MutableMap<String, Double>>()
            for (element in rows) {
                if (!element.isJsonObject) continue
                val row = element.asJsonObject
                val name = row.get("name")?.takeIf { !it.isJsonNull }?.asString ?: continue
                val date = row.get("snapshot_date")?.takeIf { !it.isJsonNull }?.asString ?: continue
                val value = row.get("value")?.takeIf { !it.isJsonNull }?.asDouble ?: continue
                if (value > 0.0) result.getOrPut(name) { mutableMapOf() }[date] = value
            }
            return result
        } finally {
            conn.disconnect()
        }
    }

    fun fetchDailyPrices(name: String): List<DailyPrice> =
        fetchDailyHistory()[name].orEmpty()
            .toSortedMap()
            .map { (date, value) -> DailyPrice(date, value) }

    internal fun calculateRsi14(closes: List<Double>): Double? {
        if (closes.size < RSI_PERIOD + 1) return null
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..RSI_PERIOD) {
            val change = closes[i] - closes[i - 1]
            if (change > 0.0) gainSum += change else lossSum -= change
        }

        // RSI استاندارد Wilder: میانگین اولیه از ۱۴ تغییر و سپس هموارسازی بازگشتی
        var averageGain = gainSum / RSI_PERIOD
        var averageLoss = lossSum / RSI_PERIOD
        for (i in (RSI_PERIOD + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            val gain = if (change > 0.0) change else 0.0
            val loss = if (change < 0.0) -change else 0.0
            averageGain = ((averageGain * (RSI_PERIOD - 1)) + gain) / RSI_PERIOD
            averageLoss = ((averageLoss * (RSI_PERIOD - 1)) + loss) / RSI_PERIOD
        }

        if (averageGain == 0.0 && averageLoss == 0.0) return 50.0
        if (averageLoss == 0.0) return 100.0
        if (averageGain == 0.0) return 0.0
        val relativeStrength = averageGain / averageLoss
        return 100.0 - (100.0 / (1.0 + relativeStrength))
    }

    private fun withPreviousMetrics(snap: MarketSnapshot, previous: MarketSnapshot?): MarketSnapshot {
        val previousItems = previous?.items?.associateBy { it.name }.orEmpty()
        return snap.copy(items = snap.items.map { item ->
            val cached = previousItems[item.name]
            item.copy(
                changePercent = cached?.changePercent ?: item.changePercent,
                rsi14 = item.rsi14 ?: cached?.rsi14
            )
        })
    }

    /** رکورد امروز را با آخرین قیمت‌های دریافت‌شده بروزرسانی می‌کند. */
    fun saveDailySnapshot(snap: MarketSnapshot) {
        if (!isConfigured()) {
            Log.w(TAG, "رد شد: SUPABASE_URL / SUPABASE_ANON_KEY هنوز پر نشده‌اند")
            return
        }
        val today = todayTehran()

        try {
            val rows = snap.items.map { item ->
                mapOf(
                    "snapshot_date" to today,
                    "name" to item.name,
                    "value" to item.value,
                    "change_percent" to item.changePercent,
                    "unit" to item.unit
                )
            }
            val body = gson.toJson(rows)
            val url = URL("$SUPABASE_URL/rest/v1/$TABLE?on_conflict=snapshot_date,name")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
            }
            try {
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
                val code = conn.responseCode
                if (code in 200..299) {
                    Log.d(TAG, "آخرین قیمت روز بروزرسانی شد: $today (${rows.size} ردیف)")
                } else {
                    val err = (conn.errorStream ?: conn.inputStream)?.let {
                        BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { r -> r.readText() }
                    }
                    Log.e(TAG, "ذخیره ناموفق — HTTP $code: $err")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطای اتصال به سوپابیس", e)
        }
    }

    internal fun todayTehran(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Tehran")
        return sdf.format(Date())
    }
}
