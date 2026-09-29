package com.example.muraqib.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.app.NotificationCompat
import com.example.muraqib.R
import com.example.muraqib.data.model.AppRestriction
import com.example.muraqib.data.model.LimitPeriod
import com.example.muraqib.data.model.isSettingsPackage
import com.example.muraqib.data.model.isSettingsPopupOrDialog
import com.example.muraqib.data.repository.AppInfoManager
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.data.repository.SecurityRepository
import com.example.muraqib.ui.block.BlockActivity
import java.util.Calendar
import java.util.Locale

/**
 * خدمة إمكانية الوصول لمراقبة النوافذ وحظر التطبيقات فورًا وبدقة 100% دون أي تأخير
 */
class MuraqibAccessibilityService : AccessibilityService() {

    private lateinit var restrictionsRepo: AppRestrictionsRepository
    private lateinit var appInfoManager: AppInfoManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var securityRepo: SecurityRepository
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastBlockedPackage: String? = null
    private var lastBlockTimestamp: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                if (BlockOverlayManager.isShowing || lastBlockedPackage != null) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    BlockOverlayManager.dismiss()
                    lastBlockedPackage = null
                }
            }
        }
    }

    companion object {
        var isServiceRunning: Boolean = false
            private set
        var instance: MuraqibAccessibilityService? = null
            private set

        /**
         * التحقق مما إذا كانت خدمة إمكانية الوصول مفعلة في إعدادات النظام
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)

            val expectedComponentName = ComponentName(context, MuraqibAccessibilityService::class.java).flattenToString()
            val shortExpectedComponentName = ComponentName(context, MuraqibAccessibilityService::class.java).flattenToShortString()

            while (colonSplitter.hasNext()) {
                val component = colonSplitter.next()
                if (component.equals(expectedComponentName, ignoreCase = true) ||
                    component.equals(shortExpectedComponentName, ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        instance = this
        restrictionsRepo = AppRestrictionsRepository.getInstance(this)
        appInfoManager = AppInfoManager(this)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        securityRepo = SecurityRepository(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenOffReceiver, filter)
            }
        } catch (e: Exception) {
            try {
                registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            } catch (ignored: Exception) {}
        }

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 20
        }
        serviceInfo = info

        // استعادة مزامنة الخدمات والتأكد من تشغيل خدمة المراقبة الأمامية كخط دفاع ثانٍ
        com.example.muraqib.security.BootResilienceManager.restoreServicesOnBoot(this)

        // استعادة جدولة مؤقتات التخطي المؤقت النشطة بعد إعادة التشغيل
        restoreActiveBypassTimers()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        if (!::powerManager.isInitialized) {
            powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        }
        if (!::keyguardManager.isInitialized) {
            keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        }
        if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
            if (BlockOverlayManager.isShowing) {
                BlockOverlayManager.dismiss()
            }
            return
        }

        val pkgName = event.packageName?.toString()

        // 1. تسجيل نبضة النشاط الأمني المستمرة
        if (!::securityRepo.isInitialized) {
            securityRepo = SecurityRepository(this)
        }
        securityRepo.recordHeartbeat()

        // 2. فحص وتطبيق الحظر الفوري لتطبيق الضبط/الإعدادات فور فتحه دون أي تأخير
        if (pkgName != null && isSettingsPackage(pkgName)) {
            // استثناء النوافذ المنبثقة ومربعات الحوار التابعة للضبط تماماً من الحظر
            if (isSettingsPopupOrDialog(event)) {
                // إذا كانت نافذة الحظر العائمة معروضة، يتم إخفاؤها للسماح بالتفاعل مع النافذة المنبثقة
                if (BlockOverlayManager.isShowing && isSettingsPackage(BlockOverlayManager.currentShowingPackage)) {
                    BlockOverlayManager.dismiss()
                }
                return
            }

            if (!::restrictionsRepo.isInitialized) restrictionsRepo = AppRestrictionsRepository.getInstance(this)
            if (!::appInfoManager.isInitialized) appInfoManager = AppInfoManager(this)

            val settingsRestriction = restrictionsRepo.getRestrictionForPackage(pkgName)
            if (settingsRestriction != null && settingsRestriction.isEnabled && !restrictionsRepo.isPackageBypassed(pkgName)) {
                val consumed = calculateConsumedMinutes(settingsRestriction)
                val eval = restrictionsRepo.evaluateRestriction(settingsRestriction, consumed, Calendar.getInstance(), pkgName)
                if (eval.isBlocked) {
                    // إغلاق تطبيق الإعدادات فوراً والعودة للشاشة الرئيسية لمنع التفاعل معه نهائياً
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    performGlobalAction(GLOBAL_ACTION_BACK)

                    val appName = appInfoManager.getAppName(pkgName)
                    val blockIntent = BlockActivity.createIntent(
                        context = this,
                        packageName = pkgName,
                        appName = appName,
                        reason = eval.detailedReasonText,
                        nextAvailable = eval.nextAvailableText,
                        consumedMinutes = eval.consumedMinutes,
                        allowedMinutes = eval.allowedMinutes
                    ).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }
                    try {
                        startActivity(blockIntent)
                    } catch (e: Exception) {}

                    BlockOverlayManager.show(
                        context = this,
                        packageName = pkgName,
                        appName = appName,
                        reason = eval.detailedReasonText,
                        nextAvailable = eval.nextAvailableText,
                        onHomeAction = {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        },
                        onBypassAction = { durationMinutes ->
                            scheduleBypassExpiration(pkgName, durationMinutes)
                        }
                    )
                    return
                }
            }
        }

        // 3. فحص محاولات تعطيل التطبيق أو إلغاء تثبيته أو مسح بياناته أو إيقافه إجبارياً
        if (pkgName != null && isAttemptingToTamperWithMuraqib(pkgName, event)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val intent = BlockActivity.createIntent(
                context = this,
                packageName = packageName,
                appName = getString(R.string.app_name),
                reason = "إعدادات التطبيق محمية ضد الإيقاف الإجباري ومسح البيانات وإلغاء التثبيت.",
                nextAvailable = "أدخل رمز مرور التطبيق للمتابعة",
                consumedMinutes = 0,
                allowedMinutes = 0
            )
            startActivity(intent)
            return
        }

        // 4. في حالة حدث تغير النوافذ (TYPE_WINDOWS_CHANGED): فحص وضع صورة داخل صورة (PiP) فقط دون لمس إغلاق النوافذ
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val windowList = windows ?: return
                    for (w in windowList) {
                        if (isWindowInPip(w)) {
                            val nodePkg = w.root?.packageName?.toString() ?: continue
                            if (isIgnoredSystemPackage(nodePkg)) continue
                            if (!::restrictionsRepo.isInitialized) restrictionsRepo = AppRestrictionsRepository.getInstance(this)
                            val restriction = restrictionsRepo.getRestrictionForPackage(nodePkg)
                            if (restriction != null && restriction.isEnabled && !restrictionsRepo.isPackageBypassed(nodePkg)) {
                                val consumed = calculateConsumedMinutes(restriction)
                                val eval = restrictionsRepo.evaluateRestriction(restriction, consumed, Calendar.getInstance(), nodePkg)
                                if (eval.isBlocked) {
                                    performGlobalAction(GLOBAL_ACTION_HOME)
                                    checkAndBlockIfNeeded(nodePkg, event)
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            return
        }

        // 5. استبعاد الأحداث التي لا تحتوي على اسم حزمة أو تنتمي لشاشات النظام ولوحات المفاتيح
        if (pkgName == null || isIgnoredSystemPackage(pkgName)) {
            return
        }

        // 6. إدارة إغلاق نافذة الحظر فقط عندما يغادر المستخدم التطبيق المحظور وينتقل لتطبيق آخر مسموح أو الشاشة الرئيسية
        if (BlockOverlayManager.isShowing && BlockOverlayManager.currentShowingPackage != null) {
            val showingPkg = BlockOverlayManager.currentShowingPackage!!
            // إذا كان المعروض نافذة منبثقة أو مربع حوار من الإعدادات، نغلق نافذة الحظر فوراً للسماح بالتفاعل
            if (isSettingsPackage(showingPkg) && isSettingsPopupOrDialog(event)) {
                BlockOverlayManager.dismiss()
            } else if (!isSameAppOrSubComponent(showingPkg, pkgName)) {
                // التحقق مما إذا كان التطبيق المحظور لا يزال ظاهراً في شاشة منقسمة نشطة (Split-Screen)
                val stillInSplit = isPackageVisibleInSplitScreen(showingPkg)
                if (!stillInSplit) {
                    BlockOverlayManager.dismiss()
                }
            }
        }

        // 7. تقييم وحظر التطبيق النشط الحالي فوراً إذا كان مقيداً
        checkAndBlockIfNeeded(pkgName, event)
    }

    /**
     * فحص ما إذا كان المستخدم يحاول الدخول لصفحة إعدادات مراقب الاستخدام
     * لإيقافه إجبارياً أو مسح بياناته أو إلغاء تثبيته عبر مثبت الحزم أو تعطيل إمكانية الوصول
     */
    private fun isAttemptingToTamperWithMuraqib(pkgName: String, event: AccessibilityEvent? = null): Boolean {
        if (!::securityRepo.isInitialized) {
            securityRepo = SecurityRepository(this)
        }
        if (!securityRepo.isPinConfigured()) {
            return false
        }

        // إذا كان الحدث يمثل نافذة منبثقة أو مربع حوار من الإعدادات، لا نعتبره محاولة عبث أبداً
        if (event != null && isSettingsPopupOrDialog(event)) {
            return false
        }

        val isInstaller = pkgName == "com.android.packageinstaller" ||
                pkgName == "com.google.android.packageinstaller" ||
                pkgName.contains("packageinstaller")

        val isSettings = isSettingsPackage(pkgName)

        if (!isInstaller && !isSettings) return false

        if (isInstaller && !securityRepo.isAntiUninstallEnabled()) return false
        if (isSettings && !securityRepo.isAntiTamperEnabled()) return false

        val rootNode = rootInActiveWindow ?: return false
        return try {
            val textList = mutableListOf<String>()
            collectNodeTexts(rootNode, textList)
            val combinedText = textList.joinToString(" ").lowercase(Locale.getDefault())

            val ourPkg = packageName.lowercase(Locale.getDefault())
            val ourAppName = getString(R.string.app_name).lowercase(Locale.getDefault())

            val mentionsMuraqib = combinedText.contains(ourPkg) || combinedText.contains(ourAppName) || combinedText.contains("muraqib")
            if (!mentionsMuraqib) return false

            val className = event?.className?.toString() ?: ""

            if (isInstaller) {
                // في مثبت الحزم، التأكد من أنها شاشة إلغاء التثبيت وليست شاشة تثبيت أو طلب أذونات
                val isUninstallScreen = combinedText.contains("إلغاء التثبيت") ||
                        combinedText.contains("إلغاء تثبيت") ||
                        combinedText.contains("uninstall")
                return isUninstallScreen
            }

            // في تطبيق الإعدادات:
            // استبعاد قوائم التطبيقات العامة وقوائم إمكانية الوصول وقوائم أذونات النظام
            // الحماية تطبق فقط عندما يكون المستخدم داخل صفحة التفاصيل المحددة لتطبيق "مراقب الاستخدام" تحديداً
            val isSpecificAppDetailsScreen = className.contains("InstalledAppDetails", ignoreCase = true) ||
                    className.contains("AppInfo", ignoreCase = true) ||
                    className.contains("AppDetails", ignoreCase = true) ||
                    className.contains("AppButtons", ignoreCase = true)

            // إذا كان المستخدم في قائمة عامة (مثل قائمة التطبيقات، إمكانية الوصول، مدير الأذونات)، لا نمنعه من التصفح
            if (!isSpecificAppDetailsScreen && (className.contains("ManageApplications", ignoreCase = true) ||
                className.contains("AccessibilitySettings", ignoreCase = true) ||
                (className.contains("SubSettings", ignoreCase = true) && !combinedText.contains("إيقاف إجباري") && !combinedText.contains("force stop")))
            ) {
                return false
            }

            // التحقق من أزرار الإجراءات التدميرية الصريحة لتطبيق مراقب (إيقاف إجباري، مسح البيانات، إلغاء التثبيت)
            val hasExplicitTamperAction = combinedText.contains("إيقاف إجباري") ||
                    combinedText.contains("force stop") ||
                    combinedText.contains("مسح البيانات") ||
                    combinedText.contains("clear data") ||
                    combinedText.contains("مسح التخزين") ||
                    combinedText.contains("clear storage") ||
                    combinedText.contains("إلغاء التثبيت") ||
                    combinedText.contains("uninstall")

            isSpecificAppDetailsScreen && hasExplicitTamperAction
        } catch (e: Exception) {
            false
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * التحقق مما إذا كان الحدث يمثل نافذة منبثقة أو مربع حوار تابع للضبط
     */
    private fun isSettingsPopupOrDialog(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString()
        val pkgName = event.packageName?.toString()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        var winWidth = 0
        var winHeight = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val root = rootInActiveWindow
                if (root != null) {
                    val rect = Rect()
                    root.getBoundsInScreen(rect)
                    winWidth = rect.width()
                    winHeight = rect.height()
                    root.recycle()
                }
            } catch (e: Exception) {}
        }

        return isSettingsPopupOrDialog(
            className = className,
            packageName = pkgName,
            windowWidth = winWidth,
            windowHeight = winHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
    }

    private fun collectNodeTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { list.add(it) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectNodeTexts(child, list)
                child.recycle()
            }
        }
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        return pkg == packageName ||
                pkg == "android" ||
                pkg.startsWith("com.android.systemui") ||
                pkg.startsWith("com.google.android.inputmethod") ||
                pkg.startsWith("com.samsung.android.honeyboard") ||
                pkg.contains("inputmethod") ||
                pkg.contains("permissioncontroller")
    }

    private fun isSameAppOrSubComponent(currentPkg: String, newPkg: String): Boolean {
        if (currentPkg == newPkg) return true
        if (isSettingsPackage(currentPkg) && isSettingsPackage(newPkg)) {
            return true
        }
        return false
    }

    private fun checkAndBlockIfNeeded(targetPackage: String, event: AccessibilityEvent? = null) {
        if (!::powerManager.isInitialized) {
            powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        }
        if (!::keyguardManager.isInitialized) {
            keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        }
        if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
            if (BlockOverlayManager.isShowing) {
                BlockOverlayManager.dismiss()
            }
            return
        }

        if (!::restrictionsRepo.isInitialized) {
            restrictionsRepo = AppRestrictionsRepository.getInstance(this)
        }
        if (!::appInfoManager.isInitialized) {
            appInfoManager = AppInfoManager(this)
        }
        if (!::usageStatsManager.isInitialized) {
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        }

        val isSettings = isSettingsPackage(targetPackage)
        if (isSettings && event != null && isSettingsPopupOrDialog(event)) {
            if (BlockOverlayManager.isShowing && isSettingsPackage(BlockOverlayManager.currentShowingPackage)) {
                BlockOverlayManager.dismiss()
            }
            return
        }

        val restriction = restrictionsRepo.getRestrictionForPackage(targetPackage)
        if (restriction == null || !restriction.isEnabled) {
            if (lastBlockedPackage == targetPackage) {
                lastBlockedPackage = null
            }
            if (BlockOverlayManager.currentShowingPackage == targetPackage) {
                BlockOverlayManager.dismiss()
            }
            return
        }

        // التحقق مما إذا كان هناك تخطٍ مؤقت ساري المفعول لهذا التطبيق
        if (restrictionsRepo.isPackageBypassed(targetPackage)) {
            if (lastBlockedPackage == targetPackage) {
                lastBlockedPackage = null
            }
            if (BlockOverlayManager.currentShowingPackage == targetPackage) {
                BlockOverlayManager.dismiss()
            }
            return
        }

        val consumedMinutes = calculateConsumedMinutes(restriction)
        val calendar = Calendar.getInstance()
        val evaluation = restrictionsRepo.evaluateRestriction(restriction, consumedMinutes, calendar, targetPackage)

        if (evaluation.isBlocked) {
            val now = System.currentTimeMillis()

            if (isSettings) {
                if (event != null && isSettingsPopupOrDialog(event)) {
                    if (BlockOverlayManager.isShowing && isSettingsPackage(BlockOverlayManager.currentShowingPackage)) {
                        BlockOverlayManager.dismiss()
                    }
                    return
                }
                performGlobalAction(GLOBAL_ACTION_HOME)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            // منع إعادة تشغيل شاشة الحظر لنفس التطبيق إذا فتحت قبل أقل من 1.5 ثانية (باستثناء الإعدادات لضمان عدم إفلاتها)
            if (lastBlockedPackage == targetPackage && (now - lastBlockTimestamp) < 1500L && BlockOverlayManager.isShowing && !isSettings) {
                return
            }

            lastBlockedPackage = targetPackage
            lastBlockTimestamp = now

            val appName = appInfoManager.getAppName(targetPackage)
            val isPip = isPackageInPipMode(targetPackage)
            if (isPip) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            val windowBounds = if (isPip) null else getAppWindowBounds(targetPackage)

            if (isSettings) {
                val blockIntent = BlockActivity.createIntent(
                    context = this,
                    packageName = targetPackage,
                    appName = appName,
                    reason = evaluation.detailedReasonText,
                    nextAvailable = evaluation.nextAvailableText,
                    consumedMinutes = evaluation.consumedMinutes,
                    allowedMinutes = evaluation.allowedMinutes
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                try {
                    startActivity(blockIntent)
                } catch (e: Exception) {}
            }

            // إطلاق النافذة العائمة لتغطية التطبيق بالكامل بنظام TYPE_ACCESSIBILITY_OVERLAY
            BlockOverlayManager.show(
                context = this,
                packageName = targetPackage,
                appName = appName,
                reason = evaluation.detailedReasonText,
                nextAvailable = evaluation.nextAvailableText,
                windowBounds = windowBounds,
                isPipMode = isPip,
                onHomeAction = {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                },
                onBypassAction = { durationMinutes ->
                    scheduleBypassExpiration(targetPackage, durationMinutes)
                }
            )
        } else {
            if (lastBlockedPackage == targetPackage) {
                lastBlockedPackage = null
            }
            if (BlockOverlayManager.currentShowingPackage == targetPackage) {
                BlockOverlayManager.dismiss()
            }
        }
    }

    /**
     * استخراج حدود وأبعاد نافذة التطبيق المفتوح لتغطيتها بالكامل بدقة
     */
    private fun getAppWindowBounds(targetPackage: String): Rect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val windowList = windows
                for (w in windowList) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION || w.type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                        val node = w.root
                        val nodePkg = node?.packageName?.toString()
                        if (nodePkg != null && (nodePkg == targetPackage || isSameAppOrSubComponent(targetPackage, nodePkg))) {
                            val rect = Rect()
                            w.getBoundsInScreen(rect)
                            if (rect.width() > 0 && rect.height() > 0) {
                                return rect
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return null
    }

    /**
     * التحقق مما إذا كانت نافذة معينة في وضع صورة داخل صورة (PiP)
     * باستخدام الاستعلام المباشر عبر Reflection في API 33+ أو عبر القياس الهندسي للشاشة
     */
    private fun isWindowInPip(w: AccessibilityWindowInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val method = w.javaClass.methods.firstOrNull {
                it.name == "isInPictureInPictureMode" || it.name == "isInPictureInPicture"
            }
            if (method != null) {
                (method.invoke(w) as? Boolean) ?: false
            } else {
                val rect = Rect()
                w.getBoundsInScreen(rect)
                val metrics = resources.displayMetrics
                rect.width() > 0 && rect.height() > 0 &&
                        rect.width() < (metrics.widthPixels * 0.65f) &&
                        rect.height() < (metrics.heightPixels * 0.65f)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * التحقق مما إذا كان التطبيق المحدد يعمل حالياً في وضع صورة داخل صورة (PiP)
     */
    private fun isPackageInPipMode(targetPackage: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val windowList = windows
                for (w in windowList) {
                    if (isWindowInPip(w)) {
                        val nodePkg = w.root?.packageName?.toString()
                        if (nodePkg != null && (nodePkg == targetPackage || isSameAppOrSubComponent(targetPackage, nodePkg))) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return false
    }

    /**
     * التحقق مما إذا كان التطبيق المحدد لا يزال معروضاً في وضع الشاشات المنقسمة (Split-Screen)
     */
    private fun isPackageVisibleInSplitScreen(targetPackage: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        return try {
            val windowList = windows ?: return false
            val metrics = resources.displayMetrics
            val screenHeight = metrics.heightPixels
            val screenWidth = metrics.widthPixels

            var splitWindowCount = 0
            var hasTargetOrNullRoot = false

            for (w in windowList) {
                if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val rect = Rect()
                    w.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0 &&
                        (rect.height() < (screenHeight * 0.88f) || rect.width() < (screenWidth * 0.88f))
                    ) {
                        splitWindowCount++
                        val p = w.root?.packageName?.toString()
                        if (p == null || p == targetPackage || isSameAppOrSubComponent(targetPackage, p)) {
                            hasTargetOrNullRoot = true
                        }
                    }
                }
            }
            splitWindowCount >= 2 && hasTargetOrNullRoot
        } catch (e: Exception) {
            false
        }
    }

    /**
     * جدولة إنهاء التخطي المؤقت وإعادة إغلاق وحظر التطبيق فور انقضاء المدة
     */
    private fun scheduleBypassExpiration(targetPackage: String, durationMinutes: Int) {
        handler.postDelayed({
            restrictionsRepo.clearTemporaryBypass(targetPackage)
            // إذا كان المستخدم لا يزال داخل التطبيق، نغلقه فوراً ونعرض شاشة الحظر
            performGlobalAction(GLOBAL_ACTION_HOME)
            checkAndBlockIfNeeded(targetPackage)
        }, durationMinutes * 60_000L)
    }

    /**
     * استعادة جدولة مؤقتات التخطي المؤقت النشطة بعد إعادة تشغيل الهاتف
     */
    private fun restoreActiveBypassTimers() {
        try {
            if (!::restrictionsRepo.isInitialized) {
                restrictionsRepo = AppRestrictionsRepository.getInstance(this)
            }
            val restrictions = restrictionsRepo.getAllRestrictions()
            val allPackages = restrictions.flatMap { it.allPackages }.toSet()
            for (pkg in allPackages) {
                if (restrictionsRepo.isPackageBypassed(pkg)) {
                    val remainingSec = restrictionsRepo.getTemporaryBypassRemainingSeconds(pkg)
                    if (remainingSec > 0) {
                        handler.postDelayed({
                            restrictionsRepo.clearTemporaryBypass(pkg)
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            checkAndBlockIfNeeded(pkg)
                        }, remainingSec * 1000L)
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun showFullScreenBlockNotification(intent: Intent, appName: String, reason: String) {
        val channelId = "muraqib_block_alert_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "تنبيهات الحظر", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "إشعار حظر التطبيق المقيد"
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("مراقب الاستخدام - تطبيق مقيد")
            .setContentText("تم حظر $appName: $reason")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }

    private fun calculateConsumedMinutes(restriction: AppRestriction): Int {
        if (restriction.isTotalBlock) return 0

        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()

        val startTime = when (restriction.limitPeriod) {
            LimitPeriod.DAILY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            LimitPeriod.WEEKLY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
        }

        val targetPackages = restriction.allPackages.toSet()
        var totalDurationMs = 0L

        val bootTime = now - android.os.SystemClock.elapsedRealtime()

        try {
            val events = usageStatsManager.queryEvents(startTime, now)
            val event = UsageEvents.Event()
            val startTimes = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val time = event.timeStamp
                val type = event.eventType

                // إغلاق أي جلسات مفتوحة فور حدوث إغلاق للنظام أو إطفاء للشاشة
                if (type == 16 || type == 26 || type == 27) { // SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN, DEVICE_STARTUP
                    for ((_, start) in startTimes) {
                        if (time > start) {
                            totalDurationMs += (time - start)
                        }
                    }
                    startTimes.clear()
                    continue
                }

                val pkg = event.packageName ?: continue
                if (!targetPackages.contains(pkg)) continue

                if (type == UsageEvents.Event.ACTIVITY_RESUMED ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == 29)
                ) {
                    startTimes[pkg] = time
                } else if (type == UsageEvents.Event.ACTIVITY_PAUSED ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == 30)
                ) {
                    val start = startTimes.remove(pkg)
                    if (start != null && time > start) {
                        totalDurationMs += (time - start)
                    }
                }
            }

            // احتساب الجلسة المفتوحة حالياً مع عزل وتأمين فترة توقف الهاتف وإعادة التشغيل
            for ((_, start) in startTimes) {
                if (start >= bootTime && now > start) {
                    totalDurationMs += (now - start)
                } else if (start < bootTime) {
                    // جلسة لم تسجل إغلاقاً قبل الإقلاع؛ لا تحتسب فترة إيقاف الهاتف
                    val safeDuration = (bootTime - start).coerceIn(0L, 60_000L)
                    totalDurationMs += safeDuration
                }
            }
        } catch (e: Exception) {
            // تجاهل
        }

        // خطة بديلة باستخدام queryUsageStats في حال كانت queryEvents غير متوفرة
        if (totalDurationMs == 0L) {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, now)
            totalDurationMs = stats?.filter { targetPackages.contains(it.packageName) }
                ?.sumOf { it.totalTimeInForeground } ?: 0L
        }

        return (totalDurationMs / 60_000L).toInt()
    }

    override fun onInterrupt() {
        // لا يوجد إجراء مطلوب عند المقاطعة
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {}
    }
}
