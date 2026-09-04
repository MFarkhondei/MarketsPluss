package com.marketpluss.widget

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ChartActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var chart: DailyChartView
    private lateinit var status: TextView
    private lateinit var minValue: TextView
    private lateinit var maxValue: TextView
    private lateinit var currentValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        val name = intent.getStringExtra(EXTRA_MARKET_NAME).orEmpty()
        val current = intent.getDoubleExtra(EXTRA_CURRENT_VALUE, 0.0)
        findViewById<TextView>(R.id.tv_chart_title).text = name.ifBlank { "نمودار روزانه" }
        chart = findViewById(R.id.daily_chart)
        status = findViewById(R.id.tv_chart_status)
        minValue = findViewById(R.id.tv_min_value)
        maxValue = findViewById(R.id.tv_max_value)
        currentValue = findViewById(R.id.tv_current_value)
        findViewById<TextView>(R.id.btn_chart_back).setOnClickListener { finish() }

        if (name.isBlank()) {
            status.text = "ارز نامعتبر است"
            return
        }

        status.text = "در حال دریافت تاریخچه…"
        scope.launch {
            try {
                val points = withContext(Dispatchers.IO) {
                    SupabaseClient.fetchDailyPrices(name).toMutableList().apply {
                        if (current > 0.0) {
                            val today = SupabaseClient.todayTehran()
                            val index = indexOfFirst { it.date == today }
                            val todayPoint = DailyPrice(today, current)
                            if (index >= 0) set(index, todayPoint) else add(todayPoint)
                        }
                    }.sortedBy { it.date }
                }
                chart.setData(points)
                val values = points.map { it.value }
                if (values.isNotEmpty()) {
                    minValue.text = "کف: ${formatValue(values.minOrNull() ?: 0.0)}"
                    maxValue.text = "سقف: ${formatValue(values.maxOrNull() ?: 0.0)}"
                    currentValue.text = "آخرین: ${formatValue(values.last())}"
                    status.text = "${points.size} رکورد روزانه · به‌روزرسانی با قیمت فعلی"
                } else {
                    status.text = "برای این ارز سابقه‌ای ثبت نشده است"
                }
            } catch (_: Exception) {
                status.text = "دریافت تاریخچه ناموفق بود"
            }
        }
    }

    private fun formatValue(value: Double): String =
        if (value >= 1000.0) NumberUtils.format(value, 0)
        else String.format(Locale.US, "%.2f", value)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MARKET_NAME = "market_name"
        const val EXTRA_CURRENT_VALUE = "current_value"
    }
}
