package com.marketpluss.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat

/**
 * RemoteViews on many launchers (esp. Samsung) ignores custom android:fontFamily on TextViews.
 * Draw text with Vazir Typeface onto a Bitmap and set via setImageViewBitmap.
 * Fonts from res/font: vazirmatn_regular, vazirmatn_bold.
 */
object WidgetText {
    @Volatile private var regular: Typeface? = null
    @Volatile private var bold: Typeface? = null

    private fun typeface(ctx: Context, isBold: Boolean): Typeface {
        val cached = if (isBold) bold else regular
        if (cached != null) return cached
        synchronized(this) {
            val again = if (isBold) bold else regular
            if (again != null) return again
            val id = if (isBold) R.font.vazirmatn_bold else R.font.vazirmatn_regular
            val tf = ResourcesCompat.getFont(ctx.applicationContext, id) ?: Typeface.DEFAULT
            if (isBold) bold = tf else regular = tf
            return tf
        }
    }

    private fun sp(ctx: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.resources.displayMetrics)

    private fun dp(ctx: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)

    private fun isRtl(text: String): Boolean {
        for (ch in text) {
            val d = Character.getDirectionality(ch)
            if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
            ) return true
            if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false
        }
        return false
    }

    fun render(
        ctx: Context,
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        maxWidthDp: Float = 200f,
        maxLines: Int = 1,
        center: Boolean = false
    ): Bitmap {
        val safe = text.ifBlank { " " }
        val metrics = ctx.resources.displayMetrics
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            textSize = sp(ctx, sizeSp)
            typeface = typeface(ctx, bold)
        }
        val maxW = dp(ctx, maxWidthDp).toInt().coerceAtLeast(1)
        val rtl = isRtl(safe)
        val align = when {
            center -> Layout.Alignment.ALIGN_CENTER
            rtl -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val layout = StaticLayout.Builder
            .obtain(safe, 0, safe.length, paint, maxW)
            .setAlignment(align)
            .setIncludePad(true)
            .setLineSpacing(0f, 1.05f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .build()

        var contentW = 1f
        for (i in 0 until layout.lineCount) contentW = maxOf(contentW, layout.getLineMax(i))
        val padX = dp(ctx, 2f)
        val padY = dp(ctx, 1f)
        val w = (contentW + padX * 2).toInt().coerceAtLeast(1)
        val h = (layout.height + padY * 2).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.density = metrics.densityDpi
        val canvas = Canvas(bmp)
        canvas.translate(padX, padY)
        val tight = StaticLayout.Builder
            .obtain(safe, 0, safe.length, paint, (w - padX * 2).toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setIncludePad(true)
            .setLineSpacing(0f, 1.05f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .build()
        tight.draw(canvas)
        return bmp
    }

    /** One full table row (name | value+unit | change) as a single Bitmap — keeps columns aligned. */
    fun renderRow(
        ctx: Context,
        name: String,
        value: String,
        unit: String,
        change: String,
        changeColor: Int,
        widthDp: Float = 320f,
        nameColor: Int = 0xFFF8FAFC.toInt(),
        valueColor: Int = 0xFFF8FAFC.toInt(),
        unitColor: Int = 0xFF94A3B8.toInt()
    ): Bitmap {
        val metrics = ctx.resources.displayMetrics
        val totalW = dp(ctx, widthDp).toInt().coerceAtLeast(200)
        val nameW = (totalW * 1.35f / 3.35f).toInt()
        val valueW = (totalW * 1.15f / 3.35f).toInt()
        val changeW = totalW - nameW - valueW

        val namePaint = textPaint(ctx, 12f, nameColor, bold = true)
        val valuePaint = textPaint(ctx, 12f, valueColor, bold = true)
        val unitPaint = textPaint(ctx, 9f, unitColor, bold = false)
        val changePaint = textPaint(ctx, 11f, changeColor, bold = true)

        val padV = dp(ctx, 6f)
        val gap = dp(ctx, 2f)

        val nameLayout = makeLayout(name.ifBlank { " " }, namePaint, nameW - dp(ctx, 4f).toInt(), maxLines = 2, rtl = true)
        val valueLayout = makeLayout(value.ifBlank { " " }, valuePaint, valueW, maxLines = 1, center = true, rtl = false)
        val unitLayout = makeLayout(unit.ifBlank { " " }, unitPaint, valueW, maxLines = 1, center = true, rtl = isRtl(unit))
        val changeLayout = makeLayout(change.ifBlank { " " }, changePaint, changeW - dp(ctx, 4f).toInt(), maxLines = 1, center = true, rtl = false)

        val valueBlockH = valueLayout.height + gap + unitLayout.height
        val contentH = maxOf(nameLayout.height, valueBlockH, changeLayout.height)
        val totalH = (contentH + padV * 2).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        bmp.density = metrics.densityDpi
        val canvas = Canvas(bmp)

        val nameLeft = totalW - nameW
        val valueLeft = changeW
        val changeLeft = 0

        canvas.save()
        canvas.translate(nameLeft + dp(ctx, 2f), padV + (contentH - nameLayout.height) / 2f)
        nameLayout.draw(canvas)
        canvas.restore()

        val valueTop = padV + (contentH - valueBlockH) / 2f
        canvas.save()
        canvas.translate(valueLeft.toFloat(), valueTop)
        valueLayout.draw(canvas)
        canvas.restore()
        canvas.save()
        canvas.translate(valueLeft.toFloat(), valueTop + valueLayout.height + gap)
        unitLayout.draw(canvas)
        canvas.restore()

        canvas.save()
        canvas.translate(changeLeft.toFloat(), padV + (contentH - changeLayout.height) / 2f)
        changeLayout.draw(canvas)
        canvas.restore()

        return bmp
    }

    fun renderHeader(
        ctx: Context,
        name: String,
        value: String,
        change: String,
        widthDp: Float = 320f,
        gold: Int = 0xFFF5C542.toInt(),
        secondary: Int = 0xFF94A3B8.toInt()
    ): Bitmap {
        val metrics = ctx.resources.displayMetrics
        val totalW = dp(ctx, widthDp).toInt().coerceAtLeast(200)
        val nameW = (totalW * 1.35f / 3.35f).toInt()
        val valueW = (totalW * 1.15f / 3.35f).toInt()
        val changeW = totalW - nameW - valueW
        val padV = dp(ctx, 4f)

        val nameLayout = makeLayout(name, textPaint(ctx, 11f, gold, true), nameW - 4, maxLines = 1, rtl = true)
        val valueLayout = makeLayout(value, textPaint(ctx, 11f, secondary, true), valueW, maxLines = 1, center = true)
        val changeLayout = makeLayout(change, textPaint(ctx, 11f, secondary, true), changeW - 4, maxLines = 1, center = true)

        val h = (maxOf(nameLayout.height, valueLayout.height, changeLayout.height) + padV * 2).toInt()
        val bmp = Bitmap.createBitmap(totalW, h, Bitmap.Config.ARGB_8888)
        bmp.density = metrics.densityDpi
        val c = Canvas(bmp)
        c.save(); c.translate((totalW - nameW + 2f), padV); nameLayout.draw(c); c.restore()
        c.save(); c.translate(changeW.toFloat(), padV); valueLayout.draw(c); c.restore()
        c.save(); c.translate(0f, padV); changeLayout.draw(c); c.restore()
        return bmp
    }

    private fun textPaint(ctx: Context, sizeSp: Float, color: Int, bold: Boolean): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            textSize = sp(ctx, sizeSp)
            typeface = typeface(ctx, bold)
        }

    private fun makeLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int = 1,
        center: Boolean = false,
        rtl: Boolean = false
    ): StaticLayout {
        val w = width.coerceAtLeast(1)
        val align = when {
            center -> Layout.Alignment.ALIGN_CENTER
            rtl -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, w)
            .setAlignment(align)
            .setIncludePad(true)
            .setLineSpacing(0f, 1.05f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .build()
    }
}
