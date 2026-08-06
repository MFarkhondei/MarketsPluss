package com.servatmandi.widget

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object NumberUtils {
    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    fun format(value: Double, decimals: Int = 0): String {
        val pattern = if (decimals <= 0) "#,###" else "#,##0." + "0".repeat(decimals)
        return DecimalFormat(pattern, symbols).format(value)
    }

    fun formatChange(pct: Double): String {
        val sign = if (pct > 0) "+" else ""
        return String.format(Locale.US, "%s%.2f%%", sign, pct)
    }

    fun parseNumber(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        return raw.replace(",", "").replace(" ", "").toDoubleOrNull() ?: 0.0
    }
}
