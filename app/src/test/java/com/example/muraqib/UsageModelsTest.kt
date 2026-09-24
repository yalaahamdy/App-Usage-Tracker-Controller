package com.example.muraqib

import com.example.muraqib.data.model.AppUsageInfo
import com.example.muraqib.data.model.PeriodComparison
import com.example.muraqib.data.model.PeriodType
import com.example.muraqib.data.model.UsagePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * اختبارات وحدة للنماذج وحسابات الفترات الزمنية والمقارنات والتنسيقات العربية
 */
class UsageModelsTest {

    @Test
    fun testDurationFormatting() {
        // 0 ms
        assertEquals("0 د", AppUsageInfo.formatDuration(0L))
        // 45 seconds
        assertEquals("45 ث", AppUsageInfo.formatDuration(45_000L))
        // 5 minutes
        assertEquals("5 د", AppUsageInfo.formatDuration(5 * 60 * 1000L))
        // 2 hours exactly
        assertEquals("2 س", AppUsageInfo.formatDuration(2 * 3600 * 1000L))
        // 2 hours and 30 minutes
        assertEquals("2 س 30 د", AppUsageInfo.formatDuration((2 * 3600 + 30 * 60) * 1000L))
    }

    @Test
    fun testLaunchCountFormatting() {
        assertEquals("لم يفتح", AppUsageInfo.formatLaunchCount(0))
        assertEquals("مرة واحدة", AppUsageInfo.formatLaunchCount(1))
        assertEquals("مرتان", AppUsageInfo.formatLaunchCount(2))
        assertEquals("5 مرات", AppUsageInfo.formatLaunchCount(5))
        assertEquals("10 مرات", AppUsageInfo.formatLaunchCount(10))
        assertEquals("15 مرة", AppUsageInfo.formatLaunchCount(15))
    }

    @Test
    fun testPeriodRangeToday() {
        val period = UsagePeriod(PeriodType.TODAY)
        val (start, end) = period.getTimeRange()

        assertTrue(end >= start)

        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testPeriodRangeYesterday() {
        val period = UsagePeriod(PeriodType.YESTERDAY)
        val (start, end) = period.getTimeRange()

        assertTrue(end > start)
        val duration = end - start
        // Yesterday must be exactly 24 hours (86,400,000 ms minus 1 ms)
        assertTrue(duration in 86_399_000L..86_401_000L)
    }

    @Test
    fun testPeriodComparisonIncrease() {
        val comparison = PeriodComparison(
            currentPeriodMs = 120 * 60 * 1000L, // 2 hours
            previousPeriodMs = 60 * 60 * 1000L  // 1 hour
        )

        assertTrue(comparison.isIncrease)
        assertEquals(60 * 60 * 1000L, comparison.diffMs)
        assertEquals(100f, comparison.percentageChange, 0.1f)
        assertTrue(comparison.formattedChangeText.contains("أعلى"))
    }

    @Test
    fun testPeriodComparisonDecrease() {
        val comparison = PeriodComparison(
            currentPeriodMs = 30 * 60 * 1000L,  // 30 min
            previousPeriodMs = 60 * 60 * 1000L  // 60 min
        )

        assertFalse(comparison.isIncrease)
        assertEquals(-30 * 60 * 1000L, comparison.diffMs)
        assertEquals(-50f, comparison.percentageChange, 0.1f)
        assertTrue(comparison.formattedChangeText.contains("أقل"))
    }

    @Test
    fun testPeriodComparisonEqual() {
        val comparison = PeriodComparison(
            currentPeriodMs = 60 * 60 * 1000L,
            previousPeriodMs = 60 * 60 * 1000L
        )

        assertEquals(0L, comparison.diffMs)
        assertEquals("مماثل للفترة السابقة", comparison.formattedChangeText)
    }
}
