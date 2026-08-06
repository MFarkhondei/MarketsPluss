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

    fun fetchSnapshot(): MarketSnapshot {
        val body = httpGet(TGJU)
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

        val btc = price("crypto-bitcoin")
        val btcChg = change("crypto-bitcoin")
        val eth = price("crypto-ethereum")
        val ethChg = change("crypto-ethereum")

        val bourse = price("bourse")
        val bourseChg = change("bourse")

        val indexPerDollar = if (dollarToman > 0) bourse / dollarToman else 0.0

        val fairGold18 = if (ons > 0 && dollarToman > 0) {
            ons * dollarToman * (18.0 / 24.0) / 31.1034768
        } else 0.0

        val moj = price("ime_fund_nahal").takeIf { it > 0 } ?: 0.0
        val mojChg = change("ime_fund_nahal")

        val items = listOf(
            MarketItem("دلار آمریکا", dollarToman, dollarChg, "تومان"),
            MarketItem("انس طلا", ons, onsChg, "دلار", 2),
            MarketItem("طلا ۱۸ عیار", gold18Toman, gold18Chg, "تومان"),
            MarketItem("طلای ۱۸ عیار (بدون حباب)", fairGold18, 0.0, "تومان"),
            MarketItem("سکه امامی", sekeeToman, sekeeChg, "تومان"),
            MarketItem("صندوق عیار", ayar, ayarChg, "تومان"),
            MarketItem("بیت‌کوین", btc, btcChg, "دلار", 2),
            MarketItem("اتریوم", eth, ethChg, "دلار", 2),
            MarketItem("شاخص بورس / دلار", indexPerDollar, bourseChg, "", 2),
            MarketItem("سهم موج", moj, mojChg, "تومان")
        )

        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Tehran")
        return MarketSnapshot(items, sdf.format(Date()))
    }

    private fun httpGet(urlStr: String): String {
        var current = urlStr
        var redirects = 0
        while (redirects < 5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 25000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MarketPluss/1.5")
                setRequestProperty("Accept", "application/json")
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
