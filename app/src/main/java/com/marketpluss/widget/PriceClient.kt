package com.marketpluss.widget

import com.google.gson.Gson
import com.google.gson.JsonElement
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
 * منابع قیمت:
 * - دلار آمریکا، طلای ۱۸ عیار، سکه امامی: TGJU
 * - بیت‌کوین، اتریوم، انس طلا، انس نقره، انس مس، S&P 500، Nasdaq: یاهو فایننس (Yahoo Finance Chart API)
 * - طلای بدون حباب: انس طلا × دلار آمریکا ÷ 41.45
 * - شاخص بورس / دلار، سهم موج، صندوق گنج: TSETMC
 */
object PriceClient {
    private val gson = Gson()

    private const val TGJU = "https://call1.tgju.org/ajax.json"
    private const val YAHOO_CHART = "https://query1.finance.yahoo.com/v8/finance/chart/"
    private const val TSETMC_INDEX = "https://cdn.tsetmc.com/api/Index/GetIndexB1LastAll/SelectedIndexes/1"
    // https://tsetmc.com/instInfo/67141987086032267 (سهم موج)
    private const val TSETMC_MOWJ = "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/67141987086032267"
    // https://tsetmc.com/instInfo/58514988269776425 (صندوق گنج)
    private const val TSETMC_GANJ = "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/58514988269776425"

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

        // دلار آمریکا: TGJU (قیمت خام به ریال است؛ ÷10 برای تبدیل به تومان)
        val dollarToman = price("price_dollar_rl") / 10.0
        val dollarChg = change("price_dollar_rl")

        // طلای ۱۸ عیار و سکه امامی: TGJU
        val gold18Toman = price("geram18") / 10.0
        val gold18Chg = change("geram18")
        val sekeeToman = price("sekee") / 10.0
        val sekeeChg = change("sekee")

        // بیت‌کوین، اتریوم، انس طلا، انس نقره، انس مس، S&P 500، Nasdaq: یاهو فایننس
        val (btc, btcChg) = fetchYahooQuote("BTC-USD")
        val (eth, ethChg) = fetchYahooQuote("ETH-USD")
        val (ons, onsChg) = fetchYahooQuote("PAXG-USD")
        val (silverOns, silverChg) = fetchYahooQuote("SI=F")
        val (copperOns, copperChg) = fetchYahooQuote("HG=F")
        val (snp500, snp500Chg) = fetchYahooQuote("^GSPC")
        val (nasdaq, nasdaqChg) = fetchYahooQuote("^IXIC")

        // طلای بدون حباب = انس طلا × دلار آمریکا ÷ 41.45
        val fairGold18 = if (ons > 0 && dollarToman > 0) ons * dollarToman / 41.45 else 0.0
        // درصد تغییرش ترکیب درصد تغییر انس طلا و درصد تغییر دلار است
        val fairGold18Chg = if (ons > 0 && dollarToman > 0) {
            ((1.0 + onsChg / 100.0) * (1.0 + dollarChg / 100.0) - 1.0) * 100.0
        } else 0.0

        // شاخص بورس / دلار: TSETMC ÷ قیمت دلار
        val (bourse, bourseChg) = fetchTsetmcIndex()
        val indexPerDollar = if (dollarToman > 0) bourse / dollarToman else 0.0

        // سهم موج و صندوق گنج: TSETMC
        val mowj = fetchTsetmcClosingPrice(TSETMC_MOWJ)
        val ganj = fetchTsetmcClosingPrice(TSETMC_GANJ)

        val items = listOf(
            MarketItem("دلار آمریکا", dollarToman, dollarChg, "تومان"),
            MarketItem("انس طلا", ons, onsChg, "دلار", 0),
            MarketItem("طلا ۱۸ عیار", gold18Toman, gold18Chg, "تومان"),
            MarketItem("طلای بدون حباب", fairGold18, fairGold18Chg, "تومان"),
            MarketItem("سکه امامی", sekeeToman, sekeeChg, "تومان"),
            MarketItem("صندوق گنج", ganj.first, ganj.second, "تومان"),
            MarketItem("بیت‌کوین", btc, btcChg, "دلار", 0),
            MarketItem("اتریوم", eth, ethChg, "دلار", 0),
            MarketItem("انس نقره", silverOns, silverChg, "دلار", 2),
            MarketItem("انس مس", copperOns, copperChg, "دلار", 2),
            MarketItem("S&P 500", snp500, snp500Chg, "دلار", 0),
            MarketItem("Nasdaq", nasdaq, nasdaqChg, "دلار", 0),
            MarketItem("شاخص بورس / دلار", indexPerDollar, bourseChg, "نسبت", 2),
            MarketItem("سهم موج", mowj.first, mowj.second, "تومان")
        )

        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Tehran")
        return MarketSnapshot(items, sdf.format(Date()))
    }

    // ---------- یاهو فایننس (بیت‌کوین، اتریوم، انس طلا/نقره/مس، S&P 500، Nasdaq) ----------

    private fun fetchYahooQuote(symbol: String): Pair<Double, Double> {
        return try {
            val url = YAHOO_CHART + symbol.replace("^", "%5E")
            val body = httpGet(url, jsonAccept = true)
            val root = gson.fromJson(body, JsonObject::class.java)
            val result = root.getAsJsonObject("chart")
                ?.getAsJsonArray("result")
                ?.firstOrNull { it.isJsonObject }?.asJsonObject ?: return 0.0 to 0.0
            val meta = result.getAsJsonObject("meta") ?: return 0.0 to 0.0
            val last = meta.get("regularMarketPrice")?.takeIf { !it.isJsonNull }?.asDouble
                ?: return 0.0 to 0.0
            val prevClose = meta.get("previousClose")?.takeIf { !it.isJsonNull }?.asDouble
                ?: meta.get("chartPreviousClose")?.takeIf { !it.isJsonNull }?.asDouble
                ?: last
            val chg = if (prevClose > 0) (last - prevClose) / prevClose * 100.0 else 0.0
            last to chg
        } catch (_: Exception) {
            0.0 to 0.0
        }
    }

    // ---------- TSETMC (شاخص بورس، سهم موج، صندوق گنج) ----------

    // insCode شناخته‌شده‌ی «شاخص کل» بورس تهران در سرویس GetIndexB1LastAll
    private const val TSETMC_TOTAL_INDEX_INS_CODE = "32097828799138957"
    private const val TSETMC_TOTAL_INDEX_NAME = "شاخص كل"

    /**
     * پاسخ GetIndexB1LastAll آرایه‌ای به نام indexB1 است که هر آیتم یک شاخص را نشان می‌دهد
     * (شاخص کل، شاخص کل هم‌وزن، شاخص قیمت، ...). آیتم «شاخص کل» با insCode ثابت شناسایی
     * می‌شود؛ در صورت نبودنش (مثلاً تغییر insCode)، بر اساس نام lVal30 هم جست‌وجو می‌شود.
     * xDrNivJIdx004 = مقدار لحظه‌ای شاخص، xVarIdxJRfV = درصد تغییر نسبت به روز قبل.
     */
    private fun fetchTsetmcIndex(): Pair<Double, Double> {
        return try {
            val body = httpGet(TSETMC_INDEX, jsonAccept = true)
            val root = gson.fromJson(body, JsonObject::class.java)
            val list = root.getAsJsonArray("indexB1") ?: return 0.0 to 0.0

            val total = list.firstOrNull {
                it.isJsonObject && it.asJsonObject.get("insCode")?.asString == TSETMC_TOTAL_INDEX_INS_CODE
            }?.asJsonObject ?: list.firstOrNull {
                it.isJsonObject && it.asJsonObject.get("lVal30")?.asString?.trim() == TSETMC_TOTAL_INDEX_NAME
            }?.asJsonObject ?: return 0.0 to 0.0

            val value = total.get("xDrNivJIdx004")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
            val changePercent = total.get("xVarIdxJRfV")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
            value to changePercent
        } catch (_: Exception) {
            0.0 to 0.0
        }
    }

    /** GetClosingPriceInfo: pClosing = قیمت پایانی، priceYesterday = قیمت پایانی روز قبل. */
    private fun fetchTsetmcClosingPrice(url: String): Pair<Double, Double> {
        return try {
            val body = httpGet(url, jsonAccept = true)
            val root = gson.fromJson(body, JsonElement::class.java)
            val pClosing = findFirstDouble(root, "pClosing") ?: return 0.0 to 0.0
            val priceYesterday = findFirstDouble(root, "priceYesterday")
            val chg = if (priceYesterday != null && priceYesterday > 0) {
                (pClosing - priceYesterday) / priceYesterday * 100.0
            } else 0.0
            pClosing to chg
        } catch (_: Exception) {
            0.0 to 0.0
        }
    }

    private fun findFirstDouble(el: JsonElement, vararg keys: String): Double? {
        if (el.isJsonObject) {
            val obj = el.asJsonObject
            for (k in keys) {
                val v = obj.get(k) ?: continue
                if (v.isJsonNull || !v.isJsonPrimitive) continue
                val prim = v.asJsonPrimitive
                if (prim.isNumber) return prim.asDouble
                if (prim.isString) {
                    val parsed = NumberUtils.parseNumber(prim.asString)
                    if (parsed != 0.0) return parsed
                }
            }
            for ((_, v) in obj.entrySet()) {
                findFirstDouble(v, *keys)?.let { return it }
            }
        } else if (el.isJsonArray) {
            for (item in el.asJsonArray) {
                findFirstDouble(item, *keys)?.let { return it }
            }
        }
        return null
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
                val bodyText = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) throw Exception("HTTP $code")
                if (bodyText.isBlank()) throw Exception("پاسخ خالی")
                return bodyText
            } finally {
                conn.disconnect()
            }
        }
        throw Exception("ریدایرکت بیش از حد")
    }
}
