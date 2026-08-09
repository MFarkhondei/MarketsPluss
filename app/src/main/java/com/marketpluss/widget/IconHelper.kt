package com.marketpluss.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * آیکن هر ردیف در نمایش «باکسی» را با Canvas می‌کشد (بدون وابستگی به گلیف‌های فونت
 * یا مسیرهای وکتور دستی)، تا شکل هر آیتم (طلا، نقره، بیت‌کوین و ...) قابل تشخیص باشد.
 */
object IconHelper {

    enum class IconType {
        DOLLAR, GOLD_BAR, GOLD_18K, GOLD_NUGGET, COIN, SAFE,
        SILVER_BAR, BITCOIN, ETHEREUM, CHART, WAVE, GENERIC
    }

    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun iconFor(name: String): IconType = when (name) {
        "دلار آمریکا" -> IconType.DOLLAR
        "انس طلا" -> IconType.GOLD_BAR
        "طلا ۱۸ عیار" -> IconType.GOLD_18K
        "طلای بدون حباب" -> IconType.GOLD_NUGGET
        "سکه امامی" -> IconType.COIN
        "صندوق گنج" -> IconType.SAFE
        "انس نقره" -> IconType.SILVER_BAR
        "بیت‌کوین" -> IconType.BITCOIN
        "اتریوم" -> IconType.ETHEREUM
        "شاخص بورس / دلار" -> IconType.CHART
        "سهم موج" -> IconType.WAVE
        else -> IconType.GENERIC
    }

    fun render(context: Context, type: IconType, sizeDp: Float = 48f): Bitmap {
        val density = context.resources.displayMetrics.density
        val key = "$type-$sizeDp-$density"
        cache[key]?.let { return it }

        val size = max(1, (sizeDp * density).toInt())
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()

        val (holderFill, holderStroke) = holderColors(type)
        drawHolder(c, s, holderFill, holderStroke)

        val pad = s * 0.24f
        val inner = RectF(pad, pad, s - pad, s - pad)

        when (type) {
            IconType.DOLLAR -> drawDollar(c, s, inner)
            IconType.GOLD_BAR -> drawBars(c, s, Color.parseColor("#F5C542"), Color.parseColor("#C9962E"))
            IconType.GOLD_18K -> drawText(c, s, "18K", Color.parseColor("#F5C542"))
            IconType.GOLD_NUGGET -> drawNuggets(c, s, Color.parseColor("#F5C542"), Color.parseColor("#C9962E"))
            IconType.COIN -> drawCoin(c, s, Color.parseColor("#F5C542"), Color.parseColor("#B8862A"))
            IconType.SAFE -> drawSafe(c, s)
            IconType.SILVER_BAR -> drawBars(c, s, Color.parseColor("#E5E7EB"), Color.parseColor("#94A3B8"))
            IconType.BITCOIN -> drawBitcoin(c, s)
            IconType.ETHEREUM -> drawEthereum(c, s)
            IconType.CHART -> drawChart(c, s)
            IconType.WAVE -> drawWave(c, s)
            IconType.GENERIC -> drawDot(c, s)
        }

        cache[key] = bmp
        return bmp
    }

    private fun holderColors(type: IconType): Pair<Int, Int> {
        val fill = Color.parseColor("#1A2438")
        val stroke = when (type) {
            IconType.DOLLAR -> Color.parseColor("#5934D399")
            IconType.GOLD_BAR, IconType.GOLD_18K, IconType.GOLD_NUGGET, IconType.COIN ->
                Color.parseColor("#59F5C542")
            IconType.SAFE -> Color.parseColor("#59A8B2C1")
            IconType.SILVER_BAR -> Color.parseColor("#59E5E7EB")
            IconType.BITCOIN -> Color.parseColor("#59F7931A")
            IconType.ETHEREUM -> Color.parseColor("#598B5CF6")
            IconType.CHART -> Color.parseColor("#5938BDF8")
            IconType.WAVE -> Color.parseColor("#592DD4BF")
            IconType.GENERIC -> Color.parseColor("#59A8B2C1")
        }
        return fill to stroke
    }

    private fun drawHolder(c: Canvas, s: Float, fill: Int, stroke: Int) {
        val r = s * 0.22f
        val rect = RectF(1f, 1f, s - 1f, s - 1f)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; style = Paint.Style.FILL }
        c.drawRoundRect(rect, r, r, fillPaint)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke
            style = Paint.Style.STROKE
            strokeWidth = s * 0.035f
        }
        c.drawRoundRect(rect, r, r, strokePaint)
    }

    private fun drawText(c: Canvas, s: Float, text: String, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = s * (if (text.length > 2) 0.26f else 0.4f)
        }
        val fm = paint.fontMetrics
        val y = s / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(text, s / 2f, y, paint)
    }

    private fun drawDollar(c: Canvas, s: Float, inner: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34D399")
            style = Paint.Style.STROKE
            strokeWidth = s * 0.045f
        }
        c.drawCircle(s / 2f, s / 2f, inner.width() / 2f, paint)
        drawText(c, s, "$", Color.parseColor("#34D399"))
    }

    private fun drawBars(c: Canvas, s: Float, top: Int, bottom: Int) {
        val paintTop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = top }
        val paintBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bottom }
        val r = s * 0.05f

        val bottomBar = RectF(s * 0.26f, s * 0.58f, s * 0.74f, s * 0.72f)
        c.drawRoundRect(bottomBar, r, r, paintBottom)

        val topBar = RectF(s * 0.32f, s * 0.40f, s * 0.68f, s * 0.54f)
        c.drawRoundRect(topBar, r, r, paintTop)
    }

    private fun drawNuggets(c: Canvas, s: Float, top: Int, bottom: Int) {
        val paintBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bottom }
        val paintTop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = top }
        val rBig = s * 0.135f
        val rSmall = s * 0.12f
        c.drawCircle(s * 0.35f, s * 0.62f, rBig, paintBottom)
        c.drawCircle(s * 0.65f, s * 0.62f, rBig, paintBottom)
        c.drawCircle(s * 0.5f, s * 0.42f, rSmall, paintTop)
    }

    private fun drawCoin(c: Canvas, s: Float, fill: Int, rim: Int) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
        c.drawCircle(s / 2f, s / 2f, s * 0.26f, fillPaint)
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rim
            style = Paint.Style.STROKE
            strokeWidth = s * 0.03f
        }
        c.drawCircle(s / 2f, s / 2f, s * 0.18f, rimPaint)
        c.drawCircle(s / 2f, s / 2f, s * 0.045f, rimPaint.apply { style = Paint.Style.FILL })
    }

    private fun drawSafe(c: Canvas, s: Float) {
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A4358") }
        val body = RectF(s * 0.26f, s * 0.26f, s * 0.74f, s * 0.74f)
        c.drawRoundRect(body, s * 0.06f, s * 0.06f, bodyPaint)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5C542")
            style = Paint.Style.STROKE
            strokeWidth = s * 0.03f
        }
        c.drawRoundRect(body, s * 0.06f, s * 0.06f, outline)

        val dial = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5C542")
            style = Paint.Style.STROKE
            strokeWidth = s * 0.025f
        }
        c.drawCircle(s / 2f, s / 2f, s * 0.1f, dial)
        c.drawLine(s / 2f, s / 2f, s / 2f, s * 0.42f, dial)

        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5C542")
            style = Paint.Style.STROKE
            strokeWidth = s * 0.025f
        }
        c.drawLine(s * 0.68f, s * 0.42f, s * 0.68f, s * 0.58f, handlePaint)
    }

    private fun drawBitcoin(c: Canvas, s: Float) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F7931A") }
        c.drawCircle(s / 2f, s / 2f, s * 0.26f, fillPaint)
        drawText(c, s, "B", Color.WHITE)
    }

    private fun drawEthereum(c: Canvas, s: Float) {
        val cx = s / 2f
        val topY = s * 0.24f
        val midY = s * 0.5f
        val botY = s * 0.76f
        val leftX = s * 0.28f
        val rightX = s * 0.72f

        val topPath = Path().apply {
            moveTo(cx, topY)
            lineTo(rightX, midY)
            lineTo(cx, midY + s * 0.08f)
            lineTo(leftX, midY)
            close()
        }
        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A78BFA") }
        c.drawPath(topPath, topPaint)

        val botPath = Path().apply {
            moveTo(cx, midY + s * 0.08f)
            lineTo(rightX, midY)
            lineTo(cx, botY)
            lineTo(leftX, midY)
            close()
        }
        val botPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7C3AED") }
        c.drawPath(botPath, botPaint)
    }

    private fun drawChart(c: Canvas, s: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38BDF8") }
        val r = s * 0.03f
        val barW = s * 0.14f
        val baseY = s * 0.72f
        c.drawRoundRect(RectF(s * 0.28f, s * 0.54f, s * 0.28f + barW, baseY), r, r, paint)
        c.drawRoundRect(RectF(s * 0.46f, s * 0.42f, s * 0.46f + barW, baseY), r, r, paint)
        c.drawRoundRect(RectF(s * 0.64f, s * 0.30f, s * 0.64f + barW, baseY), r, r, paint)
    }

    private fun drawWave(c: Canvas, s: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2DD4BF")
            style = Paint.Style.STROKE
            strokeWidth = s * 0.045f
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path().apply {
            moveTo(s * 0.24f, s * 0.56f)
            cubicTo(s * 0.36f, s * 0.34f, s * 0.40f, s * 0.34f, s * 0.5f, s * 0.5f)
            cubicTo(s * 0.60f, s * 0.66f, s * 0.64f, s * 0.66f, s * 0.76f, s * 0.44f)
        }
        c.drawPath(path, paint)
    }

    private fun drawDot(c: Canvas, s: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A8B2C1") }
        c.drawCircle(s / 2f, s / 2f, s * 0.14f, paint)
    }
}
