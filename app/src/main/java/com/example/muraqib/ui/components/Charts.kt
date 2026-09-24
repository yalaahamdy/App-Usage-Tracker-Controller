package com.example.muraqib.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muraqib.data.model.AppUsageInfo
import com.example.muraqib.data.model.HourlyUsage
import com.example.muraqib.data.model.PeriodComparison
import com.example.muraqib.theme.ErrorRed
import com.example.muraqib.theme.SuccessGreen
import com.example.muraqib.theme.WarningOrange

/**
 * رسم بياني شريطي عصري وخفيف لتوزيع الاستخدام على مدار ساعات اليوم (24 ساعة)
 */
@Composable
fun HourlyUsageBarChart(
    hourlyData: List<HourlyUsage>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    peakBarColor: Color = MaterialTheme.colorScheme.tertiary
) {
    var selectedHourIndex by remember { mutableStateOf<Int?>(null) }

    val maxDurationMs = remember(hourlyData) {
        (hourlyData.maxOfOrNull { it.durationMs } ?: 0L).coerceAtLeast(60_000L) // دقيقة على الأقل
    }

    val peakHour = remember(hourlyData) {
        hourlyData.maxByOrNull { it.durationMs }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        // ترويسة المخطط مع تفاصيل الساعة المحددة أو ذروة الاستخدام
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "توزيع الاستخدام خلال اليوم",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (selectedHourIndex != null && selectedHourIndex!! in hourlyData.indices) {
                val item = hourlyData[selectedHourIndex!!]
                Text(
                    text = "${item.hourLabel}: ${AppUsageInfo.formatDuration(item.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (peakHour != null && peakHour.durationMs > 0) {
                Text(
                    text = "الذروة: ${peakHour.hourLabel} (${AppUsageInfo.formatDuration(peakHour.durationMs)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // منطقة الرسم البياني
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(hourlyData) {
                        detectTapGestures { offset ->
                            val totalWidth = size.width
                            val slotWidth = totalWidth / 24f
                            val clickedHour = (offset.x / slotWidth).toInt().coerceIn(0, 23)
                            selectedHourIndex = if (selectedHourIndex == clickedHour) null else clickedHour
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barWidth = (canvasWidth / 24f) * 0.65f
                val spacing = canvasWidth / 24f

                hourlyData.forEachIndexed { index, hourUsage ->
                    val x = index * spacing + (spacing - barWidth) / 2f
                    val normalizedHeight = (hourUsage.durationMs.toFloat() / maxDurationMs.toFloat()).coerceIn(0.04f, 1.0f)
                    val barHeight = if (hourUsage.durationMs > 0) {
                        normalizedHeight * (canvasHeight - 20.dp.toPx())
                    } else {
                        4.dp.toPx() // شريط رمزي صغير جداً عند انعدام الاستخدام
                    }
                    val y = canvasHeight - barHeight

                    val isSelected = selectedHourIndex == index
                    val isPeak = peakHour?.hour == index && hourUsage.durationMs > 0

                    val color = when {
                        isSelected -> peakBarColor
                        isPeak -> barColor
                        hourUsage.durationMs > 0 -> barColor.copy(alpha = 0.75f)
                        else -> barColor.copy(alpha = 0.15f)
                    }

                    // رسم العمود بزوايا علوية دائرية
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // علامات الساعات الرئيسية في المحور الأفقي
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("12 ص", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("6 ص", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12 م", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("6 م", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("11 م", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * بطاقة ذكية لمقارنة استخدام الفترة الحالية بالفترة السابقة
 */
@Composable
fun PeriodComparisonBadge(
    comparison: PeriodComparison?,
    modifier: Modifier = Modifier
) {
    if (comparison == null) return

    val (bgColor, textColor, icon) = when {
        comparison.diffMs == 0L -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.AutoMirrored.Filled.TrendingFlat
        )
        comparison.isIncrease -> Triple(
            WarningOrange.copy(alpha = 0.15f),
            WarningOrange,
            Icons.AutoMirrored.Filled.TrendingUp
        )
        else -> Triple(
            SuccessGreen.copy(alpha = 0.15f),
            SuccessGreen,
            Icons.AutoMirrored.Filled.TrendingDown
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = comparison.formattedChangeText,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * ملخص أوقات اليوم الأربعة: الصباح، الظهيرة، المساء، الليل
 */
@Composable
fun TimeOfDayDistribution(
    hourlyData: List<HourlyUsage>,
    modifier: Modifier = Modifier
) {
    val morningMs = hourlyData.filter { it.hour in 6..11 }.sumOf { it.durationMs }
    val afternoonMs = hourlyData.filter { it.hour in 12..17 }.sumOf { it.durationMs }
    val eveningMs = hourlyData.filter { it.hour in 18..23 }.sumOf { it.durationMs }
    val nightMs = hourlyData.filter { it.hour in 0..5 }.sumOf { it.durationMs }
    val totalMs = (morningMs + afternoonMs + eveningMs + nightMs).coerceAtLeast(1L)

    val periods = listOf(
        Quadruple("الصباح", "6 ص - 12 م", morningMs, (morningMs * 100f / totalMs)),
        Quadruple("الظهيرة", "12 م - 6 م", afternoonMs, (afternoonMs * 100f / totalMs)),
        Quadruple("المساء", "6 م - 12 ص", eveningMs, (eveningMs * 100f / totalMs)),
        Quadruple("الليل", "12 ص - 6 ص", nightMs, (nightMs * 100f / totalMs))
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "فترات النشاط اليومي",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            periods.forEach { period ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = period.first,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppUsageInfo.formatDuration(period.third),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "%.0f%%".format(period.fourth),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
