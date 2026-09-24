package com.example.muraqib

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.muraqib.theme.ErrorRed
import com.example.muraqib.ui.AppScreen
import com.example.muraqib.ui.MainTab
import com.example.muraqib.ui.MainViewModel
import com.example.muraqib.ui.dashboard.DashboardScreen
import com.example.muraqib.ui.data.DataUsageScreen
import com.example.muraqib.ui.detail.AppDetailScreen
import com.example.muraqib.ui.lock.SetupPinScreen
import com.example.muraqib.ui.lock.UnlockScreen
import com.example.muraqib.ui.permission.PermissionScreen
import com.example.muraqib.ui.restrictions.RestrictionsScreen
import com.example.muraqib.ui.settings.SettingsScreen

/**
 * الموجه الرئيسي للشاشات والصلاحيات والتبويبات
 */
@Composable
fun MainAppNavigation(
    viewModel: MainViewModel = viewModel()
) {
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val isPinConfigured by viewModel.isPinConfigured.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val dashboardData by viewModel.dashboardData.collectAsStateWithLifecycle()
    val restrictions by viewModel.restrictions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentPeriod by viewModel.currentPeriod.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val dataUsageOverview by viewModel.dataUsageOverview.collectAsStateWithLifecycle()
    val isDataLoading by viewModel.isDataLoading.collectAsStateWithLifecycle()
    val dataNetworkFilter by viewModel.dataNetworkFilter.collectAsStateWithLifecycle()

    // دعم كامل وأصيل لاتجاه الكتابة العربي (RTL)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                // 1. إذن إحصائيات الاستخدام غير مفعّل: شاشة الإذن
                !hasPermission -> {
                    PermissionScreen(
                        onCheckPermissionAgain = { viewModel.checkPermission() }
                    )
                }

                // 2. إعداد رمز المرور لأول مرة في حال لم يُحدد بعد
                !isPinConfigured -> {
                    SetupPinScreen(
                        securityRepository = viewModel.securityRepository,
                        onSetupComplete = { viewModel.onPinSetupCompleted() }
                    )
                }

                // 3. شاشة فك قفل التطبيق برمز المرور
                isLocked -> {
                    UnlockScreen(
                        securityRepository = viewModel.securityRepository,
                        onUnlocked = { viewModel.onUnlockSuccess() }
                    )
                }

                // 4. الشاشات الرئيسية للتطبيق
                else -> {
                    BackHandler(enabled = currentScreen != AppScreen.MainContainer) {
                        viewModel.navigateBack()
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ScreenTransition"
                    ) { targetScreen ->
                        when (targetScreen) {
                            is AppScreen.MainContainer -> {
                                Scaffold(
                                    bottomBar = {
                                        NavigationBar(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            tonalElevation = 8.dp
                                        ) {
                                            NavigationBarItem(
                                                selected = selectedTab == MainTab.ANALYTICS,
                                                onClick = { viewModel.selectTab(MainTab.ANALYTICS) },
                                                icon = { Icon(imageVector = Icons.Default.Insights, contentDescription = null) },
                                                label = {
                                                    Text(
                                                        text = MainTab.ANALYTICS.titleAr,
                                                        fontWeight = if (selectedTab == MainTab.ANALYTICS) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            )
                                            NavigationBarItem(
                                                selected = selectedTab == MainTab.DATA_USAGE,
                                                onClick = { viewModel.selectTab(MainTab.DATA_USAGE) },
                                                icon = { Icon(imageVector = Icons.Default.DataUsage, contentDescription = null) },
                                                label = {
                                                    Text(
                                                        text = MainTab.DATA_USAGE.titleAr,
                                                        fontWeight = if (selectedTab == MainTab.DATA_USAGE) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            )
                                            NavigationBarItem(
                                                selected = selectedTab == MainTab.RESTRICTIONS,
                                                onClick = { viewModel.selectTab(MainTab.RESTRICTIONS) },
                                                icon = { Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null) },
                                                label = {
                                                    Text(
                                                        text = MainTab.RESTRICTIONS.titleAr,
                                                        fontWeight = if (selectedTab == MainTab.RESTRICTIONS) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            )
                                        }
                                    }
                                ) { paddingValues ->
                                    Box(modifier = Modifier.padding(paddingValues)) {
                                        when (selectedTab) {
                                            MainTab.ANALYTICS -> {
                                                DashboardScreen(
                                                    data = dashboardData,
                                                    isLoading = isLoading,
                                                    currentPeriod = currentPeriod,
                                                    onPeriodChanged = { viewModel.setPeriod(it) },
                                                    onRefresh = { viewModel.loadDashboardData() },
                                                    onAppClick = { pkg -> viewModel.navigateTo(AppScreen.AppDetail(pkg)) },
                                                    onSettingsClick = { viewModel.navigateTo(AppScreen.Settings) },
                                                    onNavigateToDataTab = { viewModel.selectTab(MainTab.DATA_USAGE) }
                                                )
                                            }
                                            MainTab.DATA_USAGE -> {
                                                DataUsageScreen(
                                                    data = dataUsageOverview,
                                                    isLoading = isDataLoading,
                                                    currentPeriod = currentPeriod,
                                                    networkFilter = dataNetworkFilter,
                                                    onPeriodChanged = { viewModel.setPeriod(it) },
                                                    onFilterChanged = { viewModel.setDataNetworkFilter(it) },
                                                    onRefresh = { viewModel.loadDataUsageData() },
                                                    onAppClick = { pkg -> viewModel.navigateTo(AppScreen.AppDetail(pkg)) },
                                                    onSettingsClick = { viewModel.navigateTo(AppScreen.Settings) }
                                                )
                                            }
                                            MainTab.RESTRICTIONS -> {
                                                val fullAppsList = remember(installedApps, dashboardData.appList) {
                                                    if (installedApps.isEmpty()) {
                                                        dashboardData.appList
                                                    } else {
                                                        val usageMap = dashboardData.appList.associateBy { it.packageName }
                                                        installedApps.map { installed ->
                                                            val used = usageMap[installed.packageName]
                                                            if (used != null) {
                                                                installed.copy(
                                                                    totalTimeForegroundMs = used.totalTimeForegroundMs,
                                                                    launchCount = used.launchCount,
                                                                    lastTimeUsed = used.lastTimeUsed
                                                                )
                                                            } else {
                                                                installed
                                                            }
                                                        }
                                                    }
                                                }

                                                RestrictionsScreen(
                                                    restrictions = restrictions,
                                                    availableApps = fullAppsList,
                                                    restrictionsRepo = viewModel.restrictionsRepo,
                                                    usageRepository = viewModel.usageRepository
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is AppScreen.AppDetail -> {
                                AppDetailScreen(
                                    packageName = targetScreen.packageName,
                                    period = currentPeriod,
                                    usageRepository = viewModel.usageRepository,
                                    dataUsageRepository = viewModel.dataUsageRepository,
                                    restrictionsRepo = viewModel.restrictionsRepo,
                                    onSaveRestriction = { restriction ->
                                        viewModel.saveRestriction(restriction)
                                    },
                                    onBackClick = { viewModel.navigateBack() }
                                )
                            }

                            is AppScreen.Settings -> {
                                SettingsScreen(
                                    securityRepository = viewModel.securityRepository,
                                    onBackClick = { viewModel.navigateBack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
