package com.example.muraqib.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.app.NotificationCompat
import com.example.muraqib.data.model.AppRestriction
import com.example.muraqib.data.model.LimitPeriod
import com.example.muraqib.data.repository.AppInfoManager
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.ui.block.BlockActivity
import java.util.Calendar

/**
 * خدمة إمكانية الوصول لمراقبة النوافذ وحظر التطبيقات فورًا وبدقة 100% دون أي تأخير
 */
class MuraqibAccessibilityService : AccessibilityService() {

    private lateinit var restrictionsRepo: AppRestrictionsRepository
    private lateinit var appInfoManager: AppInfoManager
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastBlockedPackage: String? = null
    private var lastBlockTimestamp: Long = 0L

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

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 20
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val pkgName = event.packageName?.toString() ?: return

        // 1. استبعاد تطبيقنا وشاشات النظام ولوحات المفاتيح فوراً قبل فحص إغلاق النافذة
        if (isIgnoredSystemPackage(pkgName)) {
            return
        }

        // 2. إذا كان هناك نافذة حظر معروضة لتطبيق معين، ولكن المستخدم غادر هذا التطبيق وانتقل لتطبيق آخر مختلف
        if (BlockOverlayManager.isShowing &&
            BlockOverlayManager.currentShowingPackage != null &&
            !isSameAppOrSubComponent(BlockOverlayManager.currentShowingPackage!!, pkgName)
        ) {
            BlockOverlayManager.dismiss()
        }

        checkAndBlockIfNeeded(pkgName)
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        return pkg == packageName ||
                pkg == "android" ||
                pkg.startsWith("com.android.systemui") ||
                pkg.startsWith("com.google.android.inputmethod") ||
                pkg.startsWith("com.samsung.android.honeyboard") ||
                pkg.contains("inputmethod")
    }

    private fun isSameAppOrSubComponent(currentPkg: String, newPkg: String): Boolean {
        if (currentPkg == newPkg) return true
        if (currentPkg == "com.android.settings" || currentPkg.startsWith("com.android.settings.")) {
            if (newPkg == "com.android.settings" ||
                newPkg.startsWith("com.android.settings.") ||
                newPkg.startsWith("com.google.android.settings.") ||
                newPkg.startsWith("com.samsung.android.settings") ||
                newPkg == "com.google.android.settings.intelligence" ||
                newPkg == "com.android.settings.intelligence"
            ) {
                return true
            }
        }
        return false
    }

    private fun checkAndBlockIfNeeded(targetPackage: String) {
        if (!::restrictionsRepo.isInitialized) {
            restrictionsRepo = AppRestrictionsRepository.getInstance(this)
        }
        if (!::appInfoManager.isInitialized) {
            appInfoManager = AppInfoManager(this)
        }
        if (!::usageStatsManager.isInitialized) {
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
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
            // منع إعادة تشغيل شاشة الحظر لنفس التطبيق إذا فتحت قبل أقل من 1.5 ثانية
            if (lastBlockedPackage == targetPackage && (now - lastBlockTimestamp) < 1500L && BlockOverlayManager.isShowing) {
                return
            }

            lastBlockedPackage = targetPackage
            lastBlockTimestamp = now

            val appName = appInfoManager.getAppName(targetPackage)
            val windowBounds = getAppWindowBounds(targetPackage)

            // إطلاق النافذة العائمة بنفس حجم نافذة التطبيق المفتوح لتغطيته بالكامل بنظام TYPE_ACCESSIBILITY_OVERLAY
            BlockOverlayManager.show(
                context = this,
                packageName = targetPackage,
                appName = appName,
                reason = evaluation.detailedReasonText,
                nextAvailable = evaluation.nextAvailableText,
                windowBounds = windowBounds,
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

        try {
            val events = usageStatsManager.queryEvents(startTime, now)
            val event = UsageEvents.Event()
            val startTimes = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (!targetPackages.contains(pkg)) continue

                val time = event.timeStamp
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == 29)
                ) {
                    startTimes[pkg] = time
                } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                    event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == 30)
                ) {
                    val start = startTimes.remove(pkg)
                    if (start != null && time > start) {
                        totalDurationMs += (time - start)
                    }
                }
            }

            // احتساب الجلسة المفتوحة حالياً في هذه اللحظة
            for ((_, start) in startTimes) {
                if (now > start) {
                    totalDurationMs += (now - start)
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
    }
}
