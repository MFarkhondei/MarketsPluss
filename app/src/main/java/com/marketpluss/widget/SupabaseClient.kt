package com.marketpluss.widget

import android.content.Context
import android.util.Log
import com.google.gson.Gson
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
 * ذخیره‌ی روزانه‌ی قیمت‌ها در Supabase — فقط یک بار در روز (به وقت تهران)،
 * حتی اگر ویجت چند بار در روز آپدیت شود.
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
object SupabaseClient {
    private const val SUPABASE_URL = "https://qlpdrrbeyvejhjrrnihp.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_gOE9-0Z2BJ_EfhcYeS6mMA_nt1pB2Ed"
    private const val TABLE = "market_daily_prices"

    private val gson = Gson()

    private const val TAG = "SupabaseClient"

    private fun isConfigured(): Boolean =
        !SUPABASE_URL.contains("YOUR-PROJECT") && !SUPABASE_ANON_KEY.contains("YOUR-ANON-KEY")

    /** اگر امروز (به وقت تهران) هنوز ذخیره نشده، اسنپ‌شات فعلی را در سوپابیس ثبت می‌کند. */
    fun saveDailyIfNeeded(ctx: Context, snap: MarketSnapshot) {
        if (!isConfigured()) {
            Log.w(TAG, "رد شد: SUPABASE_URL / SUPABASE_ANON_KEY هنوز پر نشده‌اند")
            return
        }
        val today = todayTehran()
        if (Prefs.getLastDailySaveDate(ctx) == today) {
            Log.d(TAG, "رد شد: امروز ($today) قبلاً ذخیره شده")
            return
        }

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
                    Prefs.setLastDailySaveDate(ctx, today)
                    Log.d(TAG, "ذخیره شد: $today (${rows.size} ردیف)")
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

    private fun todayTehran(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Tehran")
        return sdf.format(Date())
    }
}
