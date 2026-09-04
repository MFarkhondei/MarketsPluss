package com.marketpluss.widget

import java.util.Locale

/** Converts the ISO Gregorian dates stored by Supabase to Solar Hijri labels. */
object PersianDateUtils {
    fun format(gregorianDate: String): String {
        val date = parse(gregorianDate) ?: return gregorianDate
        val jalali = toJalali(date[0], date[1], date[2])
        return String.format(Locale.US, "%04d/%02d/%02d", jalali[0], jalali[1], jalali[2])
    }

    fun shortFormat(gregorianDate: String): String {
        val date = parse(gregorianDate) ?: return gregorianDate
        val jalali = toJalali(date[0], date[1], date[2])
        return String.format(Locale.US, "%02d/%02d", jalali[1], jalali[2])
    }

    private fun parse(value: String): IntArray? {
        val parts = value.split('-')
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return intArrayOf(year, month, day)
    }

    // Standard Gregorian-to-Jalali conversion (Birashk algorithm).
    private fun toJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val cumulativeDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val adjustedYear = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((adjustedYear + 3) / 4) -
            ((adjustedYear + 99) / 100) + ((adjustedYear + 399) / 400) + gd + cumulativeDays[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return intArrayOf(jy, jm, jd)
    }
}
