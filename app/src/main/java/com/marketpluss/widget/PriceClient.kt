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
 * قیمت‌ها از منابع باز بازار ایران خوانده می‌شود (مشابه دیده‌بان‌های ثروتمندی).
 * دلار، طلا، سکه و ارز دیجیتال از BrsApi.ir خوانده می‌شود و در صورت خطا،
 * به‌عنوان پشتیبان از TGJU استفاده می‌شود.
 */
object PriceClient {
    private val gson = Gson()
    private const val TGJU = "https://call1.tgju.org/ajax.json"
    private const val TAVAN_URL = "https://www.shakhesban.com/markets/fund/%D8%AA%D9%88%D8%A7%D9%86"

    // کلید رایگان اختصاصی خودتان را از این آدرس بگیرید و جایگزین کنید (سقف روزانه مشترک کلید نمونه محدود است):
    // https://brsapi.ir/free-api-gold-currency-webservice/
    private const val BRS_API_KEY = "FreeSV0E1LSgB9RDjuf0QorSLViX8pPG"
    private const val BRS_URL = "https://Api.BrsApi.ir/Market/Gold_Currency.php?key=$BRS_API_KEY"
    private const val BRS_COMMODITY_URL = "https://BrsApi.ir/Api/Market/Commodity.php?key=$BRS_API_KEY"

    fun fetchSnapshot(): MarketSnapshot {
        val brs = fetchBrsRates()
        val brsCommodity = fetchBrsCommodityRates()

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

        // دلار، طلا، سکه و ارز دیجیتال: اول از BrsApi، در صورت نبود از TGJU
        val dollarToman = brs.rate("USD", "دلار")?.first ?: (price("price_dollar_rl") / 10.0)
        val dollarChg = brs.rate("USD", "دلار")?.second ?: change("price_dollar_rl")

        val gold18Toman = brs.rate("IR_GOLD_18K", "طلای 18 عیار", "طلا 18 عیار", "طلای ۱۸ عیار")?.first
            ?: (price("geram18") / 10.0)
        val gold18Chg = brs.rate("IR_GOLD_18K", "طلای 18 عیار", "طلا 18 عیار", "طلای ۱۸ عیار")?.second
            ?: change("geram18")

        val ons = brs.rate("XAUUSD", "انس طلا", "اونس طلا")?.first
            ?: brsCommodity.rate("XAUUSD", "انس طلا", "اونس طلا")?.first ?: price("ons")
        val onsChg = brs.rate("XAUUSD", "انس طلا", "اونس طلا")?.second
            ?: brsCommodity.rate("XAUUSD", "انس طلا", "اونس طلا")?.second ?: change("ons")

        val sekeeToman = brs.rate("IR_COIN_EMAMI", "سکه امامی", "سکه تمام امامی")?.first
            ?: (price("sekee") / 10.0)
        val sekeeChg = brs.rate("IR_COIN_EMAMI", "سکه امامی", "سکه تمام امامی")?.second
            ?: change("sekee")

        val ayar = price("ime_fund_ayar")
        val ayarChg = change("ime_fund_ayar")

        val silverOns = brsCommodity.rate("XAGUSD", "انس نقره", "اونس نقره")?.first ?: price("silver")
        val silverChg = brsCommodity.rate("XAGUSD", "انس نقره", "اونس نقره")?.second ?: change("silver")

        // https://www.tgju.org/profile/basemetal-copper
        val copperOns = brsCommodity.rate("Cu", "مس")?.first ?: price("basemetal-copper")
        val copperChg = brsCommodity.rate("Cu", "مس")?.second ?: change("basemetal-copper")

        val btc = brs.rate("BTC", "بیت کوین", "بیت‌کوین")?.first ?: price("crypto-bitcoin")
        val btcChg = brs.rate("BTC", "بیت کوین", "بیت‌کوین")?.second ?: change("crypto-bitcoin")
        val eth = brs.rate("ETH", "اتریوم")?.first ?: price("crypto-ethereum")
        val ethChg = brs.rate("ETH", "اتریوم")?.second ?: change("crypto-ethereum")

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

    private data class BrsEntry(val symbol: String, val nameFa: String, val price: Double, val changePercent: Double)

    private class BrsRates(private val entries: List<BrsEntry>) {
        /** جست‌وجوی نرخ با نماد (دقیق) یا بخشی از نام فارسی. اولین کلید، نماد در نظر گرفته می‌شود. */
        fun rate(vararg keys: String): Pair<Double, Double>? {
            if (entries.isEmpty()) return null
            val symbolKey = keys.firstOrNull()?.uppercase(Locale.US)
            entries.firstOrNull { it.symbol.uppercase(Locale.US) == symbolKey }?.let {
                return it.price to it.changePercent
            }
            for (key in keys) {
                entries.firstOrNull { it.nameFa.contains(key) }?.let {
                    return it.price to it.changePercent
                }
            }
            return null
        }
    }

    /**
     * دریافت و پارس نرخ‌های BrsApi.ir (Market/Gold_Currency.php) — آرایه‌های gold/currency/cryptocurrency
     * با فیلدهای symbol, name, price, change_percent, unit. قیمت‌های تومانی (gold, currency) در پاسخ
     * از قبل به تومان هستند (نه ریال)، پس دیگر نیازی به تقسیم بر ۱۰ نیست. در صورت هر خطایی
     * (کلید نامعتبر، قطعی سرویس و ...) نتیجه‌ی خالی برمی‌گردد و بقیه‌ی برنامه بدون وقفه از
     * منبع پشتیبان TGJU استفاده می‌کند. پیمایش JSON عمومی نگه داشته شده تا در برابر افزوده‌شدن
     * فیلدهای جدید مقاوم بماند.
     */
    private fun fetchBrsRates(): BrsRates {
        if (BRS_API_KEY.isBlank()) return BrsRates(emptyList())
        return try {
            val body = httpGet(BRS_URL, jsonAccept = true)
            val root = gson.fromJson(body, JsonElement::class.java)
            val entries = mutableListOf<BrsEntry>()
            collectBrsEntries(root, entries)
            BrsRates(entries)
        } catch (_: Exception) {
            BrsRates(emptyList())
        }
    }

    /**
     * دریافت نرخ فلزات گران‌بها/اساسی و انرژی از BrsApi.ir (Market/Commodity.php) —
     * آرایه‌های metal_precious/metal_base/energy با همان فیلدهای symbol/name/price/change_percent.
     * قیمت‌ها به دلار هستند (unit: "دلار")، دقیقاً مطابق مقادیر فعلی TGJU برای این آیتم‌ها.
     */
    private fun fetchBrsCommodityRates(): BrsRates {
        if (BRS_API_KEY.isBlank()) return BrsRates(emptyList())
        return try {
            val body = httpGet(BRS_COMMODITY_URL, jsonAccept = true)
            val root = gson.fromJson(body, JsonElement::class.java)
            val entries = mutableListOf<BrsEntry>()
            collectBrsEntries(root, entries)
            BrsRates(entries)
        } catch (_: Exception) {
            BrsRates(emptyList())
        }
    }

    private fun collectBrsEntries(el: JsonElement, out: MutableList<BrsEntry>) {
        when {
            el.isJsonArray -> el.asJsonArray.forEach { collectBrsEntries(it, out) }
            el.isJsonObject -> {
                val obj = el.asJsonObject
                val symbol = firstString(obj, "symbol", "name_en", "l18", "en_name")
                val nameFa = firstString(obj, "name", "name_fa", "title", "l30") ?: ""
                val priceStr = firstString(obj, "price", "p", "value", "rate", "sell")
                if (symbol != null && priceStr != null) {
                    val price = NumberUtils.parseNumber(priceStr)
                    val changeStr = firstString(obj, "change_percent", "percent", "dp", "change", "change_percent_value")
                    val change = changeStr?.replace("%", "")?.let { NumberUtils.parseNumber(it) } ?: 0.0
                    if (price > 0) out.add(BrsEntry(symbol, nameFa, price, change))
                }
                obj.entrySet().forEach { (_, v) -> collectBrsEntries(v, out) }
            }
        }
    }

    private fun firstString(obj: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val el = obj.get(k) ?: continue
            if (!el.isJsonNull && el.isJsonPrimitive) return el.asString
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
