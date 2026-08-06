package com.marketpluss.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
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

    /**
     * Renders [text] into a transparently-backed bitmap using the Vazir font.
     * @param maxWidthDp constrains wrapping/ellipsis; pass a generous value for single-line labels.
     */
    fun render(
        ctx: Context,
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        maxWidthDp: Float = 220f,
        maxLines: Int = 1
    ): Bitmap {
        val safeText = text.ifBlank { " " }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = sp(ctx, sizeSp)
            this.typeface = typeface(ctx, bold)
        }
        val maxWidthPx = dp(ctx, maxWidthDp).coerceAtLeast(1)

        val layout = StaticLayout.Builder
            .obtain(safeText, 0, safeText.length, paint, maxWidthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        var width = 1f
        for (i in 0 until layout.lineCount) {
            width = maxOf(width, layout.getLineWidth(i))
        }
        val bmpWidth = (width + 2f).toInt().coerceAtLeast(1)
        val bmpHeight = layout.height.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        layout.draw(canvas)
        return bitmap
    }
}
