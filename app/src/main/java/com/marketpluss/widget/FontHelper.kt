package com.marketpluss.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.widget.RemoteViews
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max

/**
 * Samsung / many launchers strip custom typefaces from RemoteViews TextViews.
 * Drawing text to Bitmap + ImageView is the reliable way to show Vazir on home-screen widgets.
 */
object FontHelper {

    private val cache = ConcurrentHashMap<String, Typeface>()

    fun regular(context: Context): Typeface = load(context, "fonts/vazirmatn_regular.ttf")
    fun bold(context: Context): Typeface = load(context, "fonts/vazirmatn_bold.ttf")

    private fun load(context: Context, path: String): Typeface {
        cache[path]?.let { return it }
        val assets = context.applicationContext.assets
        val alt = when (path) {
            "fonts/vazirmatn_regular.ttf" -> "fonts/Vazirmatn-Regular.ttf"
            "fonts/vazirmatn_bold.ttf" -> "fonts/Vazirmatn-Bold.ttf"
            else -> null
        }
        val tf = try {
            Typeface.createFromAsset(assets, path)
        } catch (_: Exception) {
            try {
                if (alt != null) Typeface.createFromAsset(assets, alt)
                else Typeface.DEFAULT
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        cache[path] = tf
        return tf
    }

    enum class Align { START, CENTER, END }

    fun setTextBitmap(
        views: RemoteViews,
        context: Context,
        viewId: Int,
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        align: Align = Align.START,
        maxWidthDp: Float? = null,
        fixedWidth: Boolean = false
    ) {
        val bmp = render(context, text, sizeSp, color, bold, align, maxWidthDp, fixedWidth)
        views.setImageViewBitmap(viewId, bmp)
    }

    fun render(
        context: Context,
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        align: Align = Align.START,
        maxWidthDp: Float? = null,
        fixedWidth: Boolean = false
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = if (bold) bold(context) else regular(context)
            textSize = sizeSp * density
            this.color = color
            textAlign = Paint.Align.LEFT
        }

        var drawText = text.ifEmpty { " " }
        var textWidth = paint.measureText(drawText)

        if (maxWidthDp != null) {
            val maxPx = maxWidthDp * density
            if (textWidth > maxPx) {
                val ellipsis = "…"
                var end = drawText.length
                while (end > 0 && paint.measureText(drawText.substring(0, end) + ellipsis) > maxPx) {
                    end--
                }
                drawText = drawText.substring(0, end) + ellipsis
                textWidth = paint.measureText(drawText)
            }
        }

        val fm = paint.fontMetrics
        val height = max(1, ceil(fm.bottom - fm.top).toInt())
        val width = if (fixedWidth && maxWidthDp != null) {
            max(1, ceil(maxWidthDp * density).toInt())
        } else {
            max(1, ceil(textWidth).toInt() + 2)
        }

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val x = when (align) {
            Align.START -> 1f
            Align.CENTER -> width / 2f
            Align.END -> width - 1f
        }
        paint.textAlign = when (align) {
            Align.START -> Paint.Align.LEFT
            Align.CENTER -> Paint.Align.CENTER
            Align.END -> Paint.Align.RIGHT
        }
        canvas.drawText(drawText, x, -fm.top, paint)
        return bmp
    }
}
