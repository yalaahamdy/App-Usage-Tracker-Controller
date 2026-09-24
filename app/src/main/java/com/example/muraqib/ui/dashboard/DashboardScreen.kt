package com.example.muraqib.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muraqib.data.model.AppUsageInfo
import com.example.muraqib.data.model.DashboardData
import com.example.muraqib.data.model.PeriodType
import com.example.muraqib.data.model.UsagePeriod
import com.example.muraqib.ui.components.AppIcon
import com.example.muraqib.ui.components.CustomDateRangeDialog
import com.example.muraqib.ui.components.EmptyUsageState
import com.example.muraqib.ui.components.HourlyUsageBarChart
import com.example.muraqib.ui.components.MainOverviewCard
import com.example.muraqib.ui.components.PeriodSelectorBar
import com.example.muraqib.ui.components.TimeOfDayDistribution

enum class SortMode(val titleAr: String) {
    DURATION("الأطول وقتاً"),
    LAUNCHES("الأكثر فتحاً"),
    NAME("الأبجدي")
}

/**
 * لوحة التحكم الرئيسية لتطبيق مراقب الاستخدام
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    data: DashboardData,
    isLoading: Boolean,
    currentPeriod: UsagePeriod,
    onPeriodChanged: (UsagePeriod) -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToDataTab: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.DURATION) }

    val filteredApps = remember(data.appList, searchQuery, sortMode) {
        val list = if (searchQuery.isBlank()) {
            data.appList
        } else {
            data.appList.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        when (sortMode) {
            SortMode.DURATION -> list.sortedByDescending { it.totalTimeForegroundMs }
            SortMode.LAUNCHES -> list.sortedByDescending { it.launchCount }
            SortMode.NAME -> list.sortedBy { it.appName }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث عن تطبيق...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchActive = false
                                }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "إغلاق")
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "مراقب الاستخدام",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "بحث")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "تحديث")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "الإعدادات")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // شريط اختيار الفترة الزمنية
            PeriodSelectorBar(
                currentPeriod = currentPeriod,
                onPeriodSelected = onPeriodChanged,
                onOpenCustomPicker = { showCustomDateDialog = true }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // البطاقة الرئيسية لملخص الهاتف
                    item {
                        MainOverviewCard(
                            totalScreenTimeMs = data.totalScreenTimeMs,
                            totalLaunches = data.totalAppLaunches,
                            appsCount = data.appList.size,
                            comparison = data.comparison,
                            periodTitle = currentPeriod.type.titleAr
                        )
                    }

                    // بطاقة الانتقال السريع لمراقبة استهلاك البيانات
                    if (onNavigateToDataTab != null) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clickable { onNavigateToDataTab() },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DataUsage,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "مراقبة استهلاك البيانات",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "تحليل استهلاك الواي فاي وبيانات الجوال لكل تطبيق",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // مخطط توزيع الاستخدام الساعي خلال اليوم
                    if (data.hourlyDistribution.any { it.durationMs > 0 }) {
                        item {
                            HourlyUsageBarChart(
                                hourlyData = data.hourlyDistribution,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item {
                            TimeOfDayDistribution(
                                hourlyData = data.hourlyDistribution,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // رأس قائمة التطبيقات مع الترتيب
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "التطبيقات الأكثر استخدامًا (${filteredApps.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        sortMode = when (sortMode) {
                                            SortMode.DURATION -> SortMode.LAUNCHES
                                            SortMode.LAUNCHES -> SortMode.NAME
                                            SortMode.NAME -> SortMode.DURATION
                                        }
                                    }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = sortMode.titleAr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // في حال خلو القائمة
                    if (filteredApps.isEmpty()) {
                        item {
                            EmptyUsageState(
                                message = if (searchQuery.isNotBlank()) "لم يتم العثور على تطبيقات تطابق البحث" else "لا توجد بيانات استخدام مسجلة لهذه الفترة"
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = filteredApps,
                            key = { _, app -> app.packageName }
                        ) { index, app ->
                            AppUsageRowItem(
                                rank = index + 1,
                                appInfo = app,
                                onClick = { onAppClick(app.packageName) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // نافذة التاريخ المخصص
    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomDateDialog = false },
            onDateRangeSelected = { start, end ->
                showCustomDateDialog = false
                onPeriodChanged(
                    UsagePeriod(
                        type = PeriodType.CUSTOM,
                        customStartMillis = start,
                        customEndMillis = end
                    )
                )
            }
        )
    }
}

/**
 * صف عنصر التطبيق في قائمة الاستخدام
 */
@Composable
fun AppUsageRowItem(
    rank: Int,
    appInfo: AppUsageInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // شارة الترتيب
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> MaterialTheme.colorScheme.primary
                            2 -> MaterialTheme.colorScheme.secondary
                            3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // أيقونة التطبيق
            AppIcon(
                icon = appInfo.icon,
                appName = appInfo.appName,
                modifier = Modifier.size(44.dp)
            )

            // تفاصيل الاسم والشريط
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = appInfo.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = appInfo.formattedDuration,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // شريط التقدم النسبي
                LinearProgressIndicator(
                    progress = { (appInfo.percentageOfTotal / 100f).coerceIn(0.01f, 1.0f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = AppUsageInfo.formatLaunchCount(appInfo.launchCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "%.1f%%".format(appInfo.percentageOfTotal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
