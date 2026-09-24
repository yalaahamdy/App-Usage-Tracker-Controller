package com.example.muraqib.data.model

import android.graphics.drawable.Drawable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * أنواع الفترات الزمنية المتاحة للتحليل
 */
enum class PeriodType(val titleAr: String) {
    TODAY("اليوم"),
    YESTERDAY("أمس"),
    WEEK("الأسبوع"),
    MONTH("الشهر"),
    CUSTOM("فترة مخصصة")
}

/**
 * تمثيل الفترة الزمنية مع حساب نطاق البداية والنهاية
 */
data class UsagePeriod(
    val type: PeriodType,
    val customStartMillis: Long = 0L,
    val customEndMillis: Long = 0L
) {
    fun getTimeRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()

        return when (type) {
            PeriodType.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            PeriodType.YESTERDAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            PeriodType.WEEK -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            PeriodType.MONTH -> {
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            PeriodType.CUSTOM -> {
                val start = if (customStartMillis > 0) customStartMillis else now - (24 * 3600 * 1000)
                val end = if (customEndMillis > 0) customEndMillis else now
                Pair(start, end)
            }
        }
    }

    /**
     * حساب الفترة السابقة المقابلة للمقارنة
     */
    fun getPreviousPeriodRange(): Pair<Long, Long> {
        val (currentStart, currentEnd) = getTimeRange()
        val duration = currentEnd - currentStart
        val prevEnd = currentStart
        val prevStart = prevEnd - duration
        return Pair(prevStart, prevEnd)
    }
}

/**
 * بيانات استخدام تطبيق معين
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val totalTimeForegroundMs: Long,
    val launchCount: Int = 0,
    val lastTimeUsed: Long = 0L,
    val percentageOfTotal: Float = 0f
) {
    val formattedDuration: String
        get() = formatDuration(totalTimeForegroundMs)

    companion object {
        fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return when {
                hours > 0 && minutes > 0 -> "$hours س $minutes د"
                hours > 0 -> "$hours س"
                minutes > 0 -> "$minutes د"
                seconds > 0 -> "$seconds ث"
                durationMs > 0 -> "< 1 ث"
                else -> "0 د"
            }
        }

        fun formatLaunchCount(count: Int): String {
            return when {
                count == 0 -> "لم يفتح"
                count == 1 -> "مرة واحدة"
                count == 2 -> "مرتان"
                count in 3..10 -> "$count مرات"
                else -> "$count مرة"
            }
        }
    }
}

/**
 * توزيع الاستخدام خلال ساعة محددة من الـ 24 ساعة
 */
data class HourlyUsage(
    val hour: Int, // 0..23
    val durationMs: Long = 0L,
    val launchCount: Int = 0
) {
    val hourLabel: String
        get() = when (hour) {
            0 -> "12 ص"
            in 1..11 -> "$hour ص"
            12 -> "12 م"
            else -> "${hour - 12} م"
        }
}

/**
 * مقارنة الاستخدام مع الفترة السابقة
 */
data class PeriodComparison(
    val currentPeriodMs: Long,
    val previousPeriodMs: Long
) {
    val diffMs: Long = currentPeriodMs - previousPeriodMs
    val isIncrease: Boolean = diffMs > 0
    val percentageChange: Float = if (previousPeriodMs > 0) {
        ((diffMs.toDouble() / previousPeriodMs.toDouble()) * 100).toFloat()
    } else if (currentPeriodMs > 0) {
        100f
    } else {
        0f
    }

    val formattedChangeText: String
        get() {
            val absChange = kotlin.math.abs(percentageChange)
            val formattedDiff = AppUsageInfo.formatDuration(kotlin.math.abs(diffMs))
            return if (diffMs == 0L) {
                "مماثل للفترة السابقة"
            } else if (isIncrease) {
                "أعلى بـ %.0f%% (%s) مقارنة بالسابق".format(Locale.getDefault(), absChange, formattedDiff)
            } else {
                "أقل بـ %.0f%% (%s) مقارنة بالسابق".format(Locale.getDefault(), absChange, formattedDiff)
            }
        }
}

/**
 * ملخص استخدام الهاتف الشامل لفترة محددة
 */
data class DashboardData(
    val period: UsagePeriod,
    val totalScreenTimeMs: Long,
    val appList: List<AppUsageInfo>,
    val hourlyDistribution: List<HourlyUsage>,
    val comparison: PeriodComparison?,
    val totalAppLaunches: Int
)
