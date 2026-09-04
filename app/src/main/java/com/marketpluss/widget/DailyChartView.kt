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
import android.view.MotionEvent
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
    private var selectedIndex = -1
    private var pointClickListener: ((DailyPrice) -> Unit)? = null

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
    private val selectionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 245, 197, 66)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val selectionRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val tooltipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(38, 43, 54)
        style = Paint.Style.FILL
    }
    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(11f)
        typeface = FontHelper.regular(context)
    }

    fun setData(points: List<DailyPrice>) {
        values.clear()
        values.addAll(points.filter { it.value > 0.0 }.takeLast(90))
        selectedIndex = -1
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
        // Extend the reveal a little beyond the plot bounds so the first/last
        // round point is not cut in half at the chart edges.
        canvas.clipRect(left - dp(6f), top - dp(6f), revealRight + dp(6f), bottom + dp(6f))

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

        val line = smoothPath(points)
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
            val label = PersianDateUtils.shortFormat(values[index].date)
            textPaint.textAlign = when (index) {
                0 -> Paint.Align.LEFT
                values.lastIndex -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(label, x, height - dp(8f), textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT

        if (selectedIndex in points.indices) {
            val selected = points[selectedIndex]
            canvas.drawLine(selected.first, top, selected.first, bottom, selectionLinePaint)
            canvas.drawCircle(selected.first, selected.second, dp(6.5f), selectionRingPaint)

            val dateLabel = PersianDateUtils.format(values[selectedIndex].date)
            val amountLabel = if (values[selectedIndex].value >= 1000.0) {
                NumberUtils.format(values[selectedIndex].value, 0)
            } else {
                NumberUtils.format(values[selectedIndex].value, 2)
            }
            val tooltip = "$dateLabel  ·  $amountLabel"
            tooltipTextPaint.textAlign = Paint.Align.CENTER
            val tooltipWidth = max(dp(122f), tooltipTextPaint.measureText(tooltip) + dp(20f))
            val tooltipHeight = dp(30f)
            val tooltipLeft = (selected.first - tooltipWidth / 2f)
                .coerceIn(dp(4f), width - tooltipWidth - dp(4f))
            val tooltipTop = (selected.second - tooltipHeight - dp(12f)).coerceAtLeast(dp(4f))
            canvas.drawRoundRect(
                tooltipLeft, tooltipTop, tooltipLeft + tooltipWidth, tooltipTop + tooltipHeight,
                dp(8f), dp(8f), tooltipPaint
            )
            canvas.drawText(
                tooltip,
                tooltipLeft + tooltipWidth / 2f,
                tooltipTop + tooltipHeight / 2f - (tooltipTextPaint.ascent() + tooltipTextPaint.descent()) / 2f,
                tooltipTextPaint
            )
            tooltipTextPaint.textAlign = Paint.Align.LEFT
        }
    }

    fun setOnPointClickListener(listener: ((DailyPrice) -> Unit)?) {
        pointClickListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (values.isEmpty()) return true
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
                val scale = range * 1.16
                val points = values.mapIndexed { index, item ->
                    val x = if (values.size == 1) left else left + chartWidth * index / (values.size - 1)
                    val y = bottom - ((item.value - low) / scale).toFloat() * chartHeight
                    x to y
                }
                // Selection is column-based: the user's vertical position is
                // irrelevant; choose the closest point by its X coordinate.
                val nearest = points.indices.minByOrNull { index ->
                    kotlin.math.abs(points[index].first - event.x)
                } ?: return true
                val horizontalDistance = kotlin.math.abs(points[nearest].first - event.x)
                if (horizontalDistance <= max(dp(36f), chartWidth / max(1, points.size - 1) / 2f)) {
                    selectedIndex = nearest
                    invalidate()
                    pointClickListener?.invoke(values[nearest])
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun smoothPath(points: List<Pair<Float, Float>>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().first, points.first().second)
        if (points.size == 1) return path
        for (index in 0 until points.lastIndex) {
            val current = points[index]
            val next = points[index + 1]
            val previous = points.getOrNull(index - 1) ?: current
            val afterNext = points.getOrNull(index + 2) ?: next
            val control1X = current.first + (next.first - previous.first) / 6f
            val control1Y = current.second + (next.second - previous.second) / 6f
            val control2X = next.first - (afterNext.first - current.first) / 6f
            val control2Y = next.second - (afterNext.second - current.second) / 6f
            path.cubicTo(control1X, control1Y, control2X, control2Y, next.first, next.second)
        }
        return path
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
