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

/**
 * RemoteViews (AppWidget host) does not reliably apply a custom android:fontFamily
 * to TextViews — the launcher process often falls back to the system font, so Persian
 * text renders in the wrong typeface (or mismatched glyph widths) inside the widget.
 *
 * To guarantee the Vazir font is used, we draw the text ourselves with a Canvas using
 * the bundled Vazirmatn typeface and hand RemoteViews a plain Bitmap via
 * setImageViewBitmap — bitmaps always render identically regardless of host process.
 */
object BitmapTextRenderer {
    private var regularTypeface: Typeface? = null
    private var boldTypeface: Typeface? = null

    private fun typeface(ctx: Context, bold: Boolean): Typeface {
        val cached = if (bold) boldTypeface else regularTypeface
        if (cached != null) return cached
        val tf = Typeface.createFromAsset(
            ctx.applicationContext.assets,
            if (bold) "fonts/Vazirmatn-Bold.ttf" else "fonts/Vazirmatn-Regular.ttf"
        )
        if (bold) boldTypeface = tf else regularTypeface = tf
        return tf
    }

    private fun sp(ctx: Context, sizeSp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, ctx.resources.displayMetrics)

    private fun dp(ctx: Context, sizeDp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeDp, ctx.resources.displayMetrics).toInt()

    /** True when the string is predominantly RTL (Persian/Arabic). */
    private fun isRtlText(text: String): Boolean {
        for (ch in text) {
            val dir = Character.getDirectionality(ch)
            if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
            ) return true
            if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false
        }
        return false
    }

    /**
     * Renders [text] into a transparently-backed bitmap using the Vazir font.
     * @param maxWidthDp constrains wrapping/ellipsis; pass a generous value for single-line labels.
     * @param alignStart align to start edge of the paragraph (right for RTL, left for LTR).
     *                   When false, text is centered within the measured width.
     */
    fun render(
        ctx: Context,
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        maxWidthDp: Float = 220f,
        maxLines: Int = 1,
        alignStart: Boolean = true
    ): Bitmap {
        val safeText = text.ifBlank { " " }
        val metrics = ctx.resources.displayMetrics
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            this.textSize = sp(ctx, sizeSp)
            this.typeface = typeface(ctx, bold)
        }
        val maxWidthPx = dp(ctx, maxWidthDp).coerceAtLeast(1)
        val rtl = isRtlText(safeText)

        val alignment = when {
            !alignStart -> Layout.Alignment.ALIGN_CENTER
            rtl -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        val layout = StaticLayout.Builder
            .obtain(safeText, 0, safeText.length, paint, maxWidthPx)
            .setAlignment(alignment)
            .setIncludePad(true)
            .setLineSpacing(0f, 1.05f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setTextDirection(
                if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
            )
            .build()

        var contentWidth = 1f
        for (i in 0 until layout.lineCount) {
            contentWidth = maxOf(contentWidth, layout.getLineMax(i))
        }
        val padX = dp(ctx, 2f).toFloat()
        val padY = dp(ctx, 1f)
        val bmpWidth = (contentWidth + padX * 2f).toInt().coerceAtLeast(1).coerceAtMost(maxWidthPx + padX.toInt() * 2)
        val bmpHeight = (layout.height + padY * 2).coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        // Critical: match device density so RemoteViews / launcher scales the bitmap
        // correctly (otherwise text looks huge/tiny and columns appear "messed up").
        bitmap.density = metrics.densityDpi

        val canvas = Canvas(bitmap)
        canvas.translate(padX, padY.toFloat())
        val tightLayout = if (bmpWidth - padX.toInt() * 2 < maxWidthPx) {
            StaticLayout.Builder
                .obtain(safeText, 0, safeText.length, paint, (bmpWidth - padX * 2).toInt().coerceAtLeast(1))
                .setAlignment(alignment)
                .setIncludePad(true)
                .setLineSpacing(0f, 1.05f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setTextDirection(
                    if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
                )
                .build()
        } else {
            layout
        }
        tightLayout.draw(canvas)
        return bitmap
    }
}
