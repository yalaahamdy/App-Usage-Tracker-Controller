package com.example.muraqib.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.muraqib.data.model.AppRestriction
import com.example.muraqib.data.model.DashboardData
import com.example.muraqib.data.model.DataUsageOverview
import com.example.muraqib.data.model.HourlyUsage
import com.example.muraqib.data.model.NetworkTypeFilter
import com.example.muraqib.data.model.PeriodType
import com.example.muraqib.data.model.UsagePeriod
import com.example.muraqib.data.repository.AppInfoManager
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.data.repository.DataUsageRepository
import com.example.muraqib.data.repository.SecurityRepository
import com.example.muraqib.data.repository.UsageStatsRepository
import com.example.muraqib.service.AppBlockerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * التبويبات الرئيسية في أسفل التطبيق
 */
enum class MainTab(val titleAr: String) {
    ANALYTICS("الاستخدام"),
    DATA_USAGE("البيانات"),
    RESTRICTIONS("القيود")
}

/**
 * شاشات التطبيق للتنقل الداخلي السلس
 */
sealed interface AppScreen {
    data object MainContainer : AppScreen
    data class AppDetail(val packageName: String) : AppScreen
    data object Settings : AppScreen
}

/**
 * نموذج العرض الرئيسي (ViewModel) لإدارة حالة التطبيق، البيانات، الأمان، والقيود
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val appInfoManager = AppInfoManager(application)
    val securityRepository = SecurityRepository(application)
    val usageRepository = UsageStatsRepository(application, appInfoManager)
    val dataUsageRepository = DataUsageRepository(application, appInfoManager)
    val restrictionsRepo = AppRestrictionsRepository.getInstance(application)

    // التبويب النشط حاليًا (الاستخدام أو البيانات أو القيود)
    private val _selectedTab = MutableStateFlow(MainTab.ANALYTICS)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    // بيانات استهلاك الإنترنت
    private val _dataUsageOverview = MutableStateFlow<DataUsageOverview?>(null)
    val dataUsageOverview: StateFlow<DataUsageOverview?> = _dataUsageOverview.asStateFlow()

    private val _isDataLoading = MutableStateFlow(false)
    val isDataLoading: StateFlow<Boolean> = _isDataLoading.asStateFlow()

    private val _dataNetworkFilter = MutableStateFlow(NetworkTypeFilter.ALL)
    val dataNetworkFilter: StateFlow<NetworkTypeFilter> = _dataNetworkFilter.asStateFlow()

    // قيود التطبيقات
    val restrictions: StateFlow<List<AppRestriction>> = restrictionsRepo.restrictionsFlow

    // حالة الأمان ورمز المرور
    private val _isPinConfigured = MutableStateFlow(securityRepository.isPinConfigured())
    val isPinConfigured: StateFlow<Boolean> = _isPinConfigured.asStateFlow()

    private val _isLocked = MutableStateFlow(securityRepository.isAppLocked())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isSafeModeViolation = MutableStateFlow(securityRepository.isSafeModeViolationDetected())
    val isSafeModeViolation: StateFlow<Boolean> = _isSafeModeViolation.asStateFlow()

    private val _safeModeViolationMessage = MutableStateFlow(securityRepository.getSafeModeViolationMessage())
    val safeModeViolationMessage: StateFlow<String?> = _safeModeViolationMessage.asStateFlow()

    // حالة الصلاحيات
    private val _hasPermission = MutableStateFlow(usageRepository.hasUsagePermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    // الفترة الزمنية المحددة
    private val _currentPeriod = MutableStateFlow(UsagePeriod(PeriodType.TODAY))
    val currentPeriod: StateFlow<UsagePeriod> = _currentPeriod.asStateFlow()

    // بيانات لوحة التحكم
    private val _dashboardData = MutableStateFlow(
        DashboardData(
            period = UsagePeriod(PeriodType.TODAY),
            totalScreenTimeMs = 0L,
            appList = emptyList(),
            hourlyDistribution = (0..23).map { HourlyUsage(it) },
            comparison = null,
            totalAppLaunches = 0
        )
    )
    val dashboardData: StateFlow<DashboardData> = _dashboardData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // كافة التطبيقات المثبتة على الجهاز للاختيار الشامل للقيود
    private val _installedApps = MutableStateFlow<List<com.example.muraqib.data.model.AppUsageInfo>>(emptyList())
    val installedApps: StateFlow<List<com.example.muraqib.data.model.AppUsageInfo>> = _installedApps.asStateFlow()

    // شاشة العرض الحالية في التنقل الداخلي
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.MainContainer)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    init {
        refreshAll()
        loadInstalledApps()
        checkAndStartBlockerService()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val apps = appInfoManager.getInstalledAppsList()
                _installedApps.value = apps
            } catch (e: Exception) {
                // منع أي انهيار
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
        if (tab == MainTab.DATA_USAGE && _dataUsageOverview.value == null && _hasPermission.value) {
            loadDataUsageData()
        }
    }

    fun setDataNetworkFilter(filter: NetworkTypeFilter) {
        _dataNetworkFilter.value = filter
    }

    fun refreshAll() {
        checkSecurityState()
        checkPermission()
        if (_hasPermission.value) {
            loadDashboardData()
            loadDataUsageData()
        }
        checkAndStartBlockerService()
    }

    fun checkSecurityState() {
        _isPinConfigured.value = securityRepository.isPinConfigured()
        _isLocked.value = securityRepository.isAppLocked()
        _isSafeModeViolation.value = securityRepository.isSafeModeViolationDetected()
        _safeModeViolationMessage.value = securityRepository.getSafeModeViolationMessage()
    }

    fun dismissSafeModeViolation() {
        securityRepository.clearSafeModeViolation()
        checkSecurityState()
    }

    fun onPinSetupCompleted() {
        _isPinConfigured.value = true
        _isLocked.value = false
        refreshAll()
    }

    fun onUnlockSuccess() {
        securityRepository.markUnlocked()
        _isLocked.value = false
        if (_hasPermission.value) {
            loadDashboardData()
            loadDataUsageData()
        }
    }

    fun checkPermission() {
        val granted = usageRepository.hasUsagePermission()
        _hasPermission.value = granted
        if (granted && !_isLocked.value) {
            loadDashboardData()
            loadDataUsageData()
        }
    }

    fun setPeriod(period: UsagePeriod) {
        _currentPeriod.value = period
        loadDashboardData()
        loadDataUsageData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val data = usageRepository.getDashboardData(_currentPeriod.value)
                _dashboardData.value = data
            } catch (e: Exception) {
                // منع أي انهيار
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadDataUsageData() {
        viewModelScope.launch {
            _isDataLoading.value = true
            try {
                val overview = dataUsageRepository.getDataUsageOverview(_currentPeriod.value)
                _dataUsageOverview.value = overview
            } catch (e: Exception) {
                // منع أي انهيار
            } finally {
                _isDataLoading.value = false
            }
        }
    }

    fun saveRestriction(restriction: AppRestriction) {
        restrictionsRepo.saveRestriction(restriction)
        checkAndStartBlockerService()
    }

    fun deleteRestriction(id: String) {
        restrictionsRepo.deleteRestriction(id)
        checkAndStartBlockerService()
    }

    fun toggleRestriction(id: String, isEnabled: Boolean) {
        restrictionsRepo.toggleRestriction(id, isEnabled)
        checkAndStartBlockerService()
    }

    fun checkAndStartBlockerService() {
        val hasActive = restrictionsRepo.getAllRestrictions().any { it.isEnabled }
        if (hasActive) {
            AppBlockerService.start(getApplication())
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        return if (_currentScreen.value != AppScreen.MainContainer) {
            _currentScreen.value = AppScreen.MainContainer
            true
        } else {
            false
        }
    }

    fun onAppForeground() {
        if (securityRepository.isPinConfigured() && securityRepository.isAppLockEnabled()) {
            _isLocked.value = securityRepository.onAppForegrounded()
        } else {
            _isLocked.value = false
        }
        checkPermission()
        checkAndStartBlockerService()
    }

    fun onAppBackground() {
        securityRepository.onAppBackgrounded()
    }
}
