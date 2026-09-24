package com.example.muraqib.ui.restrictions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.muraqib.data.model.AppRestriction
import com.example.muraqib.data.model.AppUsageInfo
import com.example.muraqib.data.model.LimitPeriod
import com.example.muraqib.data.model.TimeWindow
import com.example.muraqib.data.repository.AppInfoManager
import com.example.muraqib.theme.ErrorRed
import com.example.muraqib.theme.WarningOrange
import com.example.muraqib.ui.components.AppIcon
import java.util.Calendar

/**
 * نافذة إضافة أو تعديل قيد استخدام مع خيارات مرنة للحظر الكامل، والحدود الزمنية، وجدولة الساعات
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditRestrictionDialog(
    initialRestriction: AppRestriction? = null,
    prefilledPackage: String? = null,
    availableApps: List<AppUsageInfo> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (AppRestriction) -> Unit
) {
    val context = LocalContext.current
    val appInfoManager = remember { AppInfoManager(context) }

    // دعم اختيار تطبيق واحد أو حزمة تطبيقات متعددة
    var selectedPackages by remember {
        val initialPkgs = initialRestriction?.allPackages?.toSet()
            ?: if (!prefilledPackage.isNullOrBlank()) setOf(prefilledPackage) else emptySet()
        mutableStateOf(initialPkgs)
    }

    var ruleName by remember {
        mutableStateOf(initialRestriction?.appName ?: "")
    }

    var showAppPicker by remember { mutableStateOf(selectedPackages.isEmpty()) }

    // نوع القيد: حظر دائم كامل أو حد زمني/جدول
    var isTotalBlock by remember { mutableStateOf(initialRestriction?.isTotalBlock ?: false) }

    // حد الاستخدام
    var hasUsageLimit by remember { mutableStateOf(initialRestriction?.hasUsageLimit ?: (!isTotalBlock)) }
    var limitMinutes by remember { mutableIntStateOf(initialRestriction?.limitDurationMinutes ?: 30) }
    var limitPeriod by remember { mutableStateOf(initialRestriction?.limitPeriod ?: LimitPeriod.DAILY) }

    // جدول الساعات
    var hasSchedule by remember { mutableStateOf(initialRestriction?.hasSchedule ?: false) }
    var timeWindows by remember {
        mutableStateOf(
            if (!initialRestriction?.timeWindows.isNullOrEmpty()) initialRestriction.timeWindows
            else listOf(TimeWindow(18, 0, 21, 0))
        )
    }
    var activeDays by remember {
        mutableStateOf(initialRestriction?.activeDays ?: (1..7).toSet())
    }

    var showTimeWindowDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // حساب الاسم التلقائي المناسب في حال عدم إدخال اسم مخصص
    val resolvedRuleName = remember(selectedPackages, ruleName) {
        if (ruleName.isNotBlank()) ruleName
        else when {
            selectedPackages.isEmpty() -> ""
            selectedPackages.size == 1 -> appInfoManager.getAppName(selectedPackages.first())
            else -> {
                val firstAppName = appInfoManager.getAppName(selectedPackages.first())
                "$firstAppName و ${selectedPackages.size - 1} تطبيقات أخرى"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // رأس النافذة الأنيق
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialRestriction == null) "إضافة قيد استخدام" else "تعديل القيد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", modifier = Modifier.size(20.dp))
                    }
                }

                // المحتوى القابل للتمرير
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // اختيار التطبيقات
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAppPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (selectedPackages.isNotEmpty()) {
                                if (selectedPackages.size == 1) {
                                    val pkg = selectedPackages.first()
                                    AppIcon(
                                        icon = appInfoManager.getAppIcon(pkg),
                                        appName = appInfoManager.getAppName(pkg),
                                        modifier = Modifier.size(38.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Apps,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = resolvedRuleName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${selectedPackages.size} تطبيق مشمول (اضغط للتعديل)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "اضغط لاختيار التطبيقات المستهدفة...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // خيار نوع التحكم بالقيد: حظر دائم فوري أو حدود وجدولة
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isTotalBlock) WarningOrange.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = "طريقة التحكم بالتطبيق:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = isTotalBlock,
                                    onClick = {
                                        isTotalBlock = true
                                        hasUsageLimit = false
                                        hasSchedule = false
                                    },
                                    label = { Text("حظر دائم كامل", fontWeight = if (isTotalBlock) FontWeight.Bold else FontWeight.Normal) },
                                    modifier = Modifier.weight(1f)
                                )

                                FilterChip(
                                    selected = !isTotalBlock,
                                    onClick = {
                                        isTotalBlock = false
                                        hasUsageLimit = true
                                    },
                                    label = { Text("حد زمني وجدولة", fontWeight = if (!isTotalBlock) FontWeight.Bold else FontWeight.Normal) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (isTotalBlock) {
                                Text(
                                    text = "سيتم حظر التطبيق فوراً وبشكل كامل بمجرد محاولة فتحه في أي وقت دون انتظار.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarningOrange,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // في حال عدم اختيار الحظر الكامل، نعرض خيارات الحد الزمني والجدولة
                    if (!isTotalBlock) {
                        // القسم الأول: حد الاستخدام الزمني
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(text = "حد الاستخدام الزمني", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Switch(checked = hasUsageLimit, onCheckedChange = { hasUsageLimit = it })
                                }

                                AnimatedVisibility(visible = hasUsageLimit) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        // اختيار الدورة (يومي أو أسبوعي)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(
                                                selected = limitPeriod == LimitPeriod.DAILY,
                                                onClick = { limitPeriod = LimitPeriod.DAILY },
                                                label = { Text("يوميًا") },
                                                leadingIcon = if (limitPeriod == LimitPeriod.DAILY) {
                                                    { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                            FilterChip(
                                                selected = limitPeriod == LimitPeriod.WEEKLY,
                                                onClick = { limitPeriod = LimitPeriod.WEEKLY },
                                                label = { Text("أسبوعيًا") },
                                                leadingIcon = if (limitPeriod == LimitPeriod.WEEKLY) {
                                                    { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }

                                        // خيارات الفترات السريعة الجاهزة (Quick Duration Presets)
                                        Text(text = "خيارات سريعة للمدة:", style = MaterialTheme.typography.labelMedium)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val presets = listOf(
                                                1 to "1 دقيقة (تجربة سريعة)",
                                                5 to "5 دقائق",
                                                15 to "15 دقيقة",
                                                30 to "30 دقيقة",
                                                45 to "45 دقيقة",
                                                60 to "ساعة واحدة",
                                                120 to "ساعتان"
                                            )
                                            presets.forEach { (mins, label) ->
                                                FilterChip(
                                                    selected = limitMinutes == mins,
                                                    onClick = { limitMinutes = mins },
                                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }

                                        // سلايدر المدة المسموحة
                                        val hours = limitMinutes / 60
                                        val mins = limitMinutes % 60
                                        val durationText = when {
                                            hours > 0 && mins > 0 -> "$hours ساعة و $mins دقيقة"
                                            hours > 0 -> "$hours ساعة"
                                            else -> "$mins دقيقة"
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "المدة المحددة:", style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                text = "$durationText ${limitPeriod.titleAr}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Slider(
                                            value = limitMinutes.toFloat(),
                                            onValueChange = { limitMinutes = it.toInt().coerceAtLeast(1) },
                                            valueRange = 1f..(if (limitPeriod == LimitPeriod.DAILY) 480f else 2400f)
                                        )
                                    }
                                }
                            }
                        }

                        // القسم الثاني: جدول أوقات الاستخدام المسموحة
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(text = "جدول الأوقات المسموحة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Switch(checked = hasSchedule, onCheckedChange = { hasSchedule = it })
                                }

                                AnimatedVisibility(visible = hasSchedule) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = "يُسمح بالتطبيق خلال هذه الساعات فقط، ويُحظر في باقي الأوقات.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // قوالب الأيام الجاهزة
                                        Text(text = "قوالب الأيام السريعة:", style = MaterialTheme.typography.labelMedium)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val isWorkDays = activeDays == setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY)
                                            val isWeekend = activeDays == setOf(Calendar.FRIDAY, Calendar.SATURDAY)
                                            val isAllDays = activeDays.size == 7

                                            FilterChip(
                                                selected = isAllDays,
                                                onClick = { activeDays = (1..7).toSet() },
                                                label = { Text("طوال الأسبوع", style = MaterialTheme.typography.labelSmall) }
                                            )
                                            FilterChip(
                                                selected = isWorkDays,
                                                onClick = {
                                                    activeDays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY)
                                                },
                                                label = { Text("أيام العمل (أحد-خميس)", style = MaterialTheme.typography.labelSmall) }
                                            )
                                            FilterChip(
                                                selected = isWeekend,
                                                onClick = {
                                                    activeDays = setOf(Calendar.FRIDAY, Calendar.SATURDAY)
                                                },
                                                label = { Text("عطلة نهاية الأسبوع", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }

                                        // أيام الأسبوع الفردية
                                        Text(text = "تحديد الأيام بالتفصيل:", style = MaterialTheme.typography.labelMedium)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val days = listOf(
                                                Calendar.SATURDAY to "السبت",
                                                Calendar.SUNDAY to "الأحد",
                                                Calendar.MONDAY to "الاثنين",
                                                Calendar.TUESDAY to "الثلاثاء",
                                                Calendar.WEDNESDAY to "الأربعاء",
                                                Calendar.THURSDAY to "الخميس",
                                                Calendar.FRIDAY to "الجمعة"
                                            )
                                            days.forEach { (calDay, name) ->
                                                val isSelected = activeDays.contains(calDay)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        activeDays = if (isSelected) {
                                                            if (activeDays.size > 1) activeDays - calDay else activeDays
                                                        } else {
                                                            activeDays + calDay
                                                        }
                                                    },
                                                    label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }

                                        // قوالب الساعات المسبقة
                                        Text(text = "فترات شائعة جاهزة:", style = MaterialTheme.typography.labelMedium)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val slotPresets = listOf(
                                                TimeWindow(18, 0, 22, 0) to "المساء (6 - 10 م)",
                                                TimeWindow(16, 0, 20, 0) to "بعد الدوام (4 - 8 م)",
                                                TimeWindow(8, 0, 12, 0) to "الصباح (8 - 12 ظ)",
                                                TimeWindow(12, 0, 14, 0) to "استراحة الغداء"
                                            )
                                            slotPresets.forEach { (window, title) ->
                                                val isAdded = timeWindows.any { it.startHour == window.startHour && it.endHour == window.endHour }
                                                FilterChip(
                                                    selected = isAdded,
                                                    onClick = {
                                                        timeWindows = if (isAdded) {
                                                            if (timeWindows.size > 1) timeWindows.filterNot { it.startHour == window.startHour && it.endHour == window.endHour }
                                                            else timeWindows
                                                        } else {
                                                            timeWindows + window
                                                        }
                                                    },
                                                    label = { Text(title, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }

                                        // قائمة الفترات الزمنية المضافة
                                        Text(text = "الفترات المسموحة النشطة:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        timeWindows.forEach { window ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Text(text = window.displayRange, fontWeight = FontWeight.SemiBold)
                                                }
                                                if (timeWindows.size > 1) {
                                                    IconButton(onClick = { timeWindows = timeWindows - window }) {
                                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = ErrorRed, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { showTimeWindowDialog = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(6.dp))
                                            Text("إضافة فترة زمنية مخصصة", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (validationError != null) {
                        Text(text = validationError!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // زر الحفظ النهائي
                Button(
                    onClick = {
                        if (selectedPackages.isEmpty()) {
                            validationError = "يرجى اختيار تطبيق واحد على الأقل"
                            return@Button
                        }
                        if (!isTotalBlock && !hasUsageLimit && !hasSchedule) {
                            validationError = "يرجى تفعيل حد الاستخدام أو جدول الأوقات أو الحظر الدائم"
                            return@Button
                        }
                        if (!isTotalBlock && hasSchedule && timeWindows.isEmpty()) {
                            validationError = "يرجى إضافة فترة زمنية واحدة على الأقل للجدول"
                            return@Button
                        }

                        val targetList = selectedPackages.toList()
                        val restriction = AppRestriction(
                            id = initialRestriction?.id ?: java.util.UUID.randomUUID().toString(),
                            packageName = targetList.first(),
                            appName = resolvedRuleName,
                            targetPackages = targetList,
                            isEnabled = true,
                            isTotalBlock = isTotalBlock,
                            hasUsageLimit = if (isTotalBlock) false else hasUsageLimit,
                            limitDurationMinutes = limitMinutes,
                            limitPeriod = limitPeriod,
                            hasSchedule = if (isTotalBlock) false else hasSchedule,
                            timeWindows = timeWindows,
                            activeDays = activeDays
                        )
                        onSave(restriction)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "حفظ وتفعيل القيد", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // نافذة اختيار التطبيقات مع دعم التحديد المتعدد والأقسام
    val effectiveApps = remember(availableApps) {
        val list = if (availableApps.isNotEmpty()) availableApps else appInfoManager.getInstalledAppsList()
        list.sortedBy { it.appName.lowercase() }
    }

    if (showAppPicker) {
        AppSelectionDialog(
            apps = effectiveApps,
            initiallySelected = selectedPackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { chosenPackages ->
                selectedPackages = chosenPackages
                showAppPicker = false
                validationError = null
            }
        )
    }

    // نافذة إضافة فترة زمنية مخصصة
    if (showTimeWindowDialog) {
        AddTimeWindowDialog(
            onDismiss = { showTimeWindowDialog = false },
            onConfirm = { newWindow ->
                timeWindows = timeWindows + newWindow
                showTimeWindowDialog = false
            }
        )
    }
}

/**
 * نافذة تحديد ساعات فترة زمنية مخصصة
 */
@Composable
private fun AddTimeWindowDialog(
    onDismiss: () -> Unit,
    onConfirm: (TimeWindow) -> Unit
) {
    var startHour by remember { mutableIntStateOf(18) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(21) }
    var endMinute by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحديد الفترة المسموحة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "من الساعة:")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = "%02d".format(startHour),
                        onValueChange = { startHour = (it.toIntOrNull() ?: 0).coerceIn(0, 23) },
                        label = { Text("ساعة") },
                        modifier = Modifier.weight(1f)
                    )
                    Text(":")
                    OutlinedTextField(
                        value = "%02d".format(startMinute),
                        onValueChange = { startMinute = (it.toIntOrNull() ?: 0).coerceIn(0, 59) },
                        label = { Text("دقيقة") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(text = "إلى الساعة:")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = "%02d".format(endHour),
                        onValueChange = { endHour = (it.toIntOrNull() ?: 0).coerceIn(0, 23) },
                        label = { Text("ساعة") },
                        modifier = Modifier.weight(1f)
                    )
                    Text(":")
                    OutlinedTextField(
                        value = "%02d".format(endMinute),
                        onValueChange = { endMinute = (it.toIntOrNull() ?: 0).coerceIn(0, 59) },
                        label = { Text("دقيقة") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(TimeWindow(startHour, startMinute, endHour, endMinute)) }) {
                Text("إضافة الفترة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
