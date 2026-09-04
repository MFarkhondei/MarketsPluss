package com.marketpluss.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.max

class DailyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val values = mutableListOf<DailyPrice>()
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val lineColor = Color.rgb(245, 197, 66)
    private val gridColor = Color.argb(70, 168, 178, 193)
    private val textColor = Color.rgb(168, 178, 193)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gridColor
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sp(11f)
        typeface = FontHelper.regular(context)
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.FILL
    }

    fun setData(points: List<DailyPrice>) {
        values.clear()
        values.addAll(points.filter { it.value > 0.0 }.takeLast(90))
        progress = 0f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 950L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    progress = 1f
                    invalidate()
                }
            })
            start()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val left = dp(42f)
        val right = width - dp(14f)
        val top = dp(18f)
        val bottom = height - dp(30f)
        val chartWidth = max(dp(1f), right - left)
        val chartHeight = max(dp(1f), bottom - top)
        val minValue = values.minOf { it.value }
        val maxValue = values.maxOf { it.value }
        val range = if (maxValue > minValue) maxValue - minValue else max(1.0, maxValue * 0.02)
        val low = minValue - range * 0.08
        val high = maxValue + range * 0.08
        val scale = high - low

        for (i in 0..4) {
            val y = top + chartHeight * i / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
            val labelValue = high - (high - low) * i / 4.0
            canvas.drawText(NumberUtils.format(labelValue, 0), 2f, y + textPaint.textSize / 3f, textPaint)
        }

        val points = values.mapIndexed { index, item ->
            val x = if (values.size == 1) left else left + chartWidth * index / (values.size - 1)
            val y = bottom - ((item.value - low) / scale).toFloat() * chartHeight
            x to y
        }
        val revealRight = left + chartWidth * progress
        canvas.save()
        canvas.clipRect(left, top - dp(4f), revealRight + dp(5f), bottom + dp(4f))

        val area = Path().apply {
            moveTo(points.first().first, bottom)
            points.forEach { lineTo(it.first, it.second) }
            lineTo(points.last().first, bottom)
            close()
        }
        fillPaint.shader = LinearGradient(0f, top, 0f, bottom,
            Color.argb(100, 245, 197, 66), Color.argb(0, 245, 197, 66), Shader.TileMode.CLAMP)
        canvas.drawPath(area, fillPaint)
        fillPaint.shader = null

        val line = Path().apply {
            moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { lineTo(it.first, it.second) }
        }
        canvas.drawPath(line, linePaint)
        points.forEachIndexed { index, point ->
            if (point.first <= revealRight + dp(3f)) {
                canvas.drawCircle(point.first, point.second, dp(if (index == points.lastIndex) 5f else 2.2f), pointPaint)
            }
        }
        canvas.restore()

        val labels = listOf(0, values.size / 2, values.lastIndex).distinct()
        labels.forEach { index ->
            val x = if (values.size == 1) left else left + chartWidth * index / (values.size - 1)
            val label = values[index].date.removePrefix("20").replace('-', '/')
            textPaint.textAlign = when (index) {
                0 -> Paint.Align.LEFT
                values.lastIndex -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(label, x, height - dp(8f), textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
