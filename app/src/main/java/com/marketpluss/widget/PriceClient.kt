package com.marketpluss.widget

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * قیمت‌ها از منابع باز بازار ایران خوانده می‌شود (مشابه دیده‌بان‌های ثروتمندی).
 */
object PriceClient {
    private val gson = Gson()
    private const val TGJU = "https://call1.tgju.org/ajax.json"
    private const val TAVAN_URL = "https://www.shakhesban.com/markets/fund/%D8%AA%D9%88%D8%A7%D9%86"

    fun fetchSnapshot(): MarketSnapshot {
        val body = httpGet(TGJU, jsonAccept = true)
        val root = gson.fromJson(body, JsonObject::class.java)
        val current = root.getAsJsonObject("current")
            ?: throw Exception("داده قیمت خالی است")

        fun node(key: String): JsonObject? =
            if (current.has(key) && current.get(key).isJsonObject) current.getAsJsonObject(key) else null

        fun price(key: String): Double =
            NumberUtils.parseNumber(node(key)?.get("p")?.asString)

        fun change(key: String): Double =
            node(key)?.get("dp")?.asDouble ?: 0.0

        val dollarToman = price("price_dollar_rl") / 10.0
        val dollarChg = change("price_dollar_rl")

        val gold18Toman = price("geram18") / 10.0
        val gold18Chg = change("geram18")

        val ons = price("ons")
        val onsChg = change("ons")

        val sekeeToman = price("sekee") / 10.0
        val sekeeChg = change("sekee")

        val ayar = price("ime_fund_ayar")
        val ayarChg = change("ime_fund_ayar")

        val silverOns = price("silver")
        val silverChg = change("silver")

        // https://www.tgju.org/profile/basemetal-copper
        val copperOns = price("basemetal-copper")
        val copperChg = change("basemetal-copper")

        val btc = price("crypto-bitcoin")
        val btcChg = change("crypto-bitcoin")
        val eth = price("crypto-ethereum")
        val ethChg = change("crypto-ethereum")

        // https://www.tgju.org/profile/s_p_500_us
        val snp500 = price("s_p_500_us")
        val snp500Chg = change("s_p_500_us")

        // https://www.tgju.org/profile/nasdaq_us
        val nasdaq = price("nasdaq_us")
        val nasdaqChg = change("nasdaq_us")

        val bourse = price("bourse")
        val bourseChg = change("bourse")

        val indexPerDollar = if (dollarToman > 0) bourse / dollarToman else 0.0

        val fairGold18 = if (ons > 0 && dollarToman > 0) {
            ons * dollarToman * (18.0 / 24.0) / 31.1034768
        } else 0.0

        // https://www.shakhesban.com/markets/fund/توان
        val tavan = fetchShakhesbanFund(TAVAN_URL)

        val items = listOf(
            MarketItem("دلار آمریکا", dollarToman, dollarChg, "تومان"),
            MarketItem("انس طلا", ons, onsChg, "دلار", 2),
            MarketItem("طلا ۱۸ عیار", gold18Toman, gold18Chg, "تومان"),
            MarketItem("طلای بدون حباب", fairGold18, 0.0, "تومان"),
            MarketItem("سکه امامی", sekeeToman, sekeeChg, "تومان"),
            MarketItem("صندوق عیار", ayar, ayarChg, "تومان"),
            MarketItem("بیت‌کوین", btc, btcChg, "دلار", 2),
            MarketItem("اتریوم", eth, ethChg, "دلار", 2),
            MarketItem("انس نقره", silverOns, silverChg, "دلار", 2),
            MarketItem("انس مس", copperOns, copperChg, "دلار", 2),
            MarketItem("S&P 500", snp500, snp500Chg, "", 2),
            MarketItem("Nasdaq", nasdaq, nasdaqChg, "", 2),
            MarketItem("شاخص بورس / دلار", indexPerDollar, bourseChg, "", 2),
            MarketItem("سهم توان", tavan.first, tavan.second, "تومان")
        )

        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Tehran")
        return MarketSnapshot(items, sdf.format(Date()))
    }

    /**
     * قیمت صندوق «توان» از شاخص‌بان خوانده می‌شود (این صفحه API عمومی ندارد،
     * بنابراین قیمت و درصد تغییر با استخراج از متن HTML صفحه به‌دست می‌آید).
     * اگر ساختار صفحه در آینده تغییر کند، این تابع مقدار صفر برمی‌گرداند.
     */
    private fun fetchShakhesbanFund(url: String): Pair<Double, Double> {
        return try {
            val html = httpGet(url, jsonAccept = false)
            val text = html.replace(Regex("<[^>]+>"), " ")
            val m = Regex("آخرین\\s*قیمت[^0-9\\-]*([0-9,]+)[^0-9\\-]*(-?[0-9]+(?:\\.[0-9]+)?)")
                .find(text)
            if (m != null) {
                val price = NumberUtils.parseNumber(m.groupValues[1])
                val chg = m.groupValues[2].toDoubleOrNull() ?: 0.0
                price to chg
            } else {
                0.0 to 0.0
            }
        } catch (_: Exception) {
            0.0 to 0.0
        }
    }

    private fun httpGet(urlStr: String, jsonAccept: Boolean = true): String {
        var current = urlStr
        var redirects = 0
        while (redirects < 5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 25000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MarketPluss/1.5")
                setRequestProperty("Accept", if (jsonAccept) "application/json" else "text/html")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                        ?: throw Exception("ریدایرکت نامعتبر")
                    current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                    redirects++
                    continue
                }
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    ?: throw Exception("HTTP $code")
                val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) throw Exception("HTTP $code")
                if (body.isBlank()) throw Exception("پاسخ خالی")
                return body
            } finally {
                conn.disconnect()
            }
        }
        throw Exception("ریدایرکت بیش از حد")
    }
}
