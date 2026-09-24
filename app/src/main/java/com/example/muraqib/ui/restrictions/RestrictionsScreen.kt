package com.example.muraqib.ui.restrictions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.muraqib.data.model.AppRestriction
import com.example.muraqib.data.model.AppUsageInfo
import com.example.muraqib.data.model.BlockReason
import com.example.muraqib.data.model.LimitPeriod
import com.example.muraqib.data.model.RestrictionEvaluation
import com.example.muraqib.data.repository.AppInfoManager
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.data.repository.UsageStatsRepository
import com.example.muraqib.service.MuraqibAccessibilityService
import com.example.muraqib.theme.ErrorRed
import com.example.muraqib.theme.SuccessGreen
import com.example.muraqib.theme.WarningOrange
import com.example.muraqib.ui.components.AppIcon
import java.util.Calendar

/**
 * تفاصيل التطبيق المستفيد من التخطي المؤقت
 */
data class BypassedAppDetail(
    val packageName: String,
    val appName: String,
    val remainingMinutes: Int
)

/**
 * شاشة إدارة قيود التطبيقات الاحترافية والعصرية
 * توفر تحكمًا كاملاً وفوريًا بالحظر دون الحاجة لأي رمز دخول
 */
@Composable
fun RestrictionsScreen(
    restrictions: List<AppRestriction>,
    availableApps: List<AppUsageInfo>,
    restrictionsRepo: AppRestrictionsRepository,
    usageRepository: UsageStatsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appInfoManager = remember { AppInfoManager(context) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingRestriction by remember { mutableStateOf<AppRestriction?>(null) }
    var deletingRestriction by remember { mutableStateOf<AppRestriction?>(null) }

    // التحقق من صلاحية الظهور فوق التطبيقات لعرض النافذة العائمة
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        )
    }

    // التحقق من تفعيل خدمة إمكانية الوصول للحظر الفوري
    var isAccessibilityEnabled by remember {
        mutableStateOf(MuraqibAccessibilityService.isAccessibilityServiceEnabled(context))
    }

    // تحديث الحالة عند العودة للشاشة
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    hasOverlayPermission = Settings.canDrawOverlays(context)
                }
                isAccessibilityEnabled = MuraqibAccessibilityService.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                text = { Text("إضافة قيد جديد", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. ترويسة مركز التحكم الموحدة (إحصائيات وحالة الحظر مدمجة بذكاء)
            item {
                ControlCenterHeaderCard(
                    activeRestrictionsCount = restrictions.count { it.isEnabled },
                    totalTargetAppsCount = restrictions.filter { it.isEnabled }.flatMap { it.allPackages }.distinct().size,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onOpenAccessibilitySettings = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 3. قسم قائمة القيود أو الحالة الفارغة
            if (restrictions.isEmpty()) {
                item {
                    EmptyRestrictionsCard(onAddClick = { showAddDialog = true })
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "القيود المحددة (${restrictions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تحكم مباشر وفوري",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(restrictions, key = { it.id }) { restriction ->
                    val consumedMinutes = remember(restriction) {
                        restrictionsRepo.calculateConsumedMinutes(context, restriction)
                    }

                    val evaluation = remember(restriction, consumedMinutes) {
                        restrictionsRepo.evaluateRestriction(restriction, consumedMinutes, Calendar.getInstance())
                    }

                    val bypassedApps = remember(restriction, restrictions) {
                        restriction.allPackages.filter { restrictionsRepo.isPackageBypassed(it) }.map { pkg ->
                            val sec = restrictionsRepo.getTemporaryBypassRemainingSeconds(pkg)
                            val mins = ((sec + 59) / 60).toInt().coerceAtLeast(1)
                            BypassedAppDetail(
                                packageName = pkg,
                                appName = appInfoManager.getAppName(pkg),
                                remainingMinutes = mins
                            )
                        }
                    }

                    val appIcon = remember(restriction.packageName) {
                        if (restriction.allPackages.size == 1) {
                            appInfoManager.getAppIcon(restriction.allPackages.first())
                        } else null
                    }

                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ModernRestrictionCard(
                            restriction = restriction,
                            evaluation = evaluation,
                            appIcon = appIcon,
                            appInfoManager = appInfoManager,
                            bypassedApps = bypassedApps,
                            onCancelBypassForPackage = { pkg ->
                                restrictionsRepo.clearTemporaryBypass(pkg)
                            },
                            onToggle = { isEnabled ->
                                restrictionsRepo.toggleRestriction(restriction.id, isEnabled)
                            },
                            onEdit = { editingRestriction = restriction },
                            onDelete = { deletingRestriction = restriction }
                        )
                    }
                }
            }
        }
    }

    // نافذة الإضافة
    if (showAddDialog) {
        AddEditRestrictionDialog(
            availableApps = availableApps,
            onDismiss = { showAddDialog = false },
            onSave = { newRestriction ->
                restrictionsRepo.saveRestriction(newRestriction)
                showAddDialog = false
            }
        )
    }

    // نافذة التعديل
    if (editingRestriction != null) {
        AddEditRestrictionDialog(
            initialRestriction = editingRestriction,
            availableApps = availableApps,
            onDismiss = { editingRestriction = null },
            onSave = { updated ->
                restrictionsRepo.saveRestriction(updated)
                editingRestriction = null
            }
        )
    }

    // تأكيد الحذف
    if (deletingRestriction != null) {
        AlertDialog(
            onDismissRequest = { deletingRestriction = null },
            title = { Text("حذف القيد", fontWeight = FontWeight.Bold) },
            text = {
                Text("هل أنت متأكد من حذف قيد الاستخدام الخاص بـ (${deletingRestriction!!.appName})؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        restrictionsRepo.deleteRestriction(deletingRestriction!!.id)
                        deletingRestriction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRestriction = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

/**
 * ترويسة مركز التحكم الموحدة: تجمع ملخص القيود، مؤشر الحالة، وتنبيه الصلاحية بذكاء مدمج ودون تكرار
 */
@Composable
private fun ControlCenterHeaderCard(
    activeRestrictionsCount: Int,
    totalTargetAppsCount: Int,
    isAccessibilityEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مركز التحكم بالقيود",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$activeRestrictionsCount قيد نشط • $totalTargetAppsCount تطبيق مقيد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // شارة الحالة المدمجة
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isAccessibilityEnabled) SuccessGreen.copy(alpha = 0.12f) else WarningOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isAccessibilityEnabled) SuccessGreen else WarningOrange)
                    )
                    Text(
                        text = if (isAccessibilityEnabled) "الحظر نشط" else "يتطلب تفعيل",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isAccessibilityEnabled) SuccessGreen else WarningOrange
                    )
                }
            }

            // إذا كانت الخدمة غير مفعلة، يظهر شريط تنبيه مباشر مع زر واحد
            if (!isAccessibilityEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(WarningOrange.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "خدمة إمكانية الوصول غير مفعلة للحظر الفوري",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningOrange,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text("تفعيل الآن", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * بطاقة عرض القيد المتطورة والأنيقة
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModernRestrictionCard(
    restriction: AppRestriction,
    evaluation: RestrictionEvaluation,
    appIcon: android.graphics.drawable.Drawable?,
    appInfoManager: AppInfoManager,
    bypassedApps: List<BypassedAppDetail> = emptyList(),
    onCancelBypassForPackage: (String) -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isMultiApp = restriction.allPackages.size > 1
    val isAllBypassed = isMultiApp && bypassedApps.isNotEmpty() && bypassedApps.size == restriction.allPackages.size
    val isPartiallyBypassed = isMultiApp && bypassedApps.isNotEmpty() && !isAllBypassed
    val isSingleBypassed = !isMultiApp && bypassedApps.isNotEmpty()

    val (statusText, statusColor, statusBg) = when {
        !restriction.isEnabled -> Triple("موقوف مؤقتًا", MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        isSingleBypassed -> Triple("تخطي مؤقت نشط", WarningOrange, WarningOrange.copy(alpha = 0.15f))
        isAllBypassed -> Triple("تخطي مؤقت للكل", WarningOrange, WarningOrange.copy(alpha = 0.15f))
        isPartiallyBypassed -> Triple("تخطي جزئي (${bypassedApps.size} من ${restriction.allPackages.size})", WarningOrange, WarningOrange.copy(alpha = 0.15f))
        evaluation.isBlocked -> Triple("محظور حاليًا", WarningOrange, WarningOrange.copy(alpha = 0.15f))
        else -> Triple("متاح للاستخدام", SuccessGreen, SuccessGreen.copy(alpha = 0.12f))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // الترويسة: الأيقونة، الاسم، الشارة، مفتاح التبديل
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isMultiApp) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    AppIcon(icon = appIcon, appName = restriction.appName, modifier = Modifier.size(42.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = restriction.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isMultiApp) {
                            Text(
                                text = "• ${restriction.allPackages.size} تطبيقات",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Switch(
                    checked = restriction.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // التطبيقات المشمولة في حال كانت مجموعة
            if (isMultiApp) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val previewPackages = restriction.allPackages.take(4)
                    previewPackages.forEach { pkg ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = appInfoManager.getAppName(pkg),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (restriction.allPackages.size > 4) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "+${restriction.allPackages.size - 4} أخرى",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // شريط حالة التخطي المؤقت النشط (لكل تطبيق مستفيد من التخطي على حدة)
            if (bypassedApps.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bypassedApps.forEach { bypassedApp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WarningOrange.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                val displayText = if (isMultiApp) {
                                    "تخطي مؤقت لـ ${bypassedApp.appName}: متبقي ${AppRestrictionsRepository.formatBypassDuration(bypassedApp.remainingMinutes)}"
                                } else {
                                    "تخطي مؤقت نشط: متبقي ${AppRestrictionsRepository.formatBypassDuration(bypassedApp.remainingMinutes)}"
                                }
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange
                                )
                            }

                            TextButton(
                                onClick = { onCancelBypassForPackage(bypassedApp.packageName) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "إلغاء التخطي",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // تفاصيل نوع القيد:
            // 1. حظر دائم كامل
            if (restriction.isTotalBlock) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarningOrange.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "حظر دائم كامل: يُمنع فتح التطبيق نهائيًا في أي وقت",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = WarningOrange
                    )
                }
            }

            // 2. حد الاستخدام الزمني
            if (restriction.hasUsageLimit) {
                val allowed = restriction.limitDurationMinutes
                val consumed = evaluation.consumedMinutes
                val progress = if (allowed > 0) (consumed.toFloat() / allowed.toFloat()).coerceIn(0f, 1f) else 0f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "حد الاستخدام (${restriction.limitPeriod.titleAr}):",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "$consumed د من أصل $allowed د",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (progress >= 1f) ErrorRed else MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (progress >= 1f) ErrorRed else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            // 3. جدول الساعات المسموحة
            if (restriction.hasSchedule && restriction.timeWindows.isNotEmpty()) {
                val windowsText = restriction.timeWindows.joinToString("، ") { it.displayRange }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "الساعات المسموحة: $windowsText",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // موعد الإتاحة القادم إن كان محظورًا
            if (evaluation.isBlocked && !evaluation.nextAvailableText.isNullOrBlank()) {
                Text(
                    text = "سيكون التطبيق متاحًا مجددًا: ${evaluation.nextAvailableText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningOrange,
                    fontWeight = FontWeight.Bold
                )
            }

            // أزرار الإجراء (تعديل وحذف مباشر دون PIN)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("تعديل", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("حذف", color = ErrorRed, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * بطاقة الحالة الفارغة المشجعة
 */
@Composable
private fun EmptyRestrictionsCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "لا توجد قيود مفعلة حاليًا",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "يمكنك وضع حد زمني يومي، أو أوقات محددة، أو حظر كامل لأي تطبيق لمساعدتك على التحكم في وقتك والتركيز.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("إضافة قيد استخدام جديد", fontWeight = FontWeight.Bold)
            }
        }
    }
}
