package com.example.muraqib.service

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.muraqib.MainActivity
import com.example.muraqib.data.model.AppRestriction
import com.example.muraqib.data.model.LimitPeriod
import com.example.muraqib.data.model.isSettingsPackage
import com.example.muraqib.data.repository.AppInfoManager
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.ui.block.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * خدمة خلفية هادئة وخفيفة جدًا لمراقبة التطبيق الحالي في الواجهة وتطبيق القيود
 */
class AppBlockerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var restrictionsRepo: AppRestrictionsRepository
    private lateinit var appInfoManager: AppInfoManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var securityRepo: com.example.muraqib.data.repository.SecurityRepository

    private var lastBlockedPackage: String? = null
    private var lastBlockTimestamp: Long = 0L
    private var lastHeartbeatTime: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                if (BlockOverlayManager.isShowing || lastBlockedPackage != null) {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        startActivity(homeIntent)
                    } catch (e: Exception) {}
                    BlockOverlayManager.dismiss()
                    lastBlockedPackage = null
                }
            }
        }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "muraqib_blocker_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // التعامل مع قيود بدء الخدمات في بعض الأجهزة
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        restrictionsRepo = AppRestrictionsRepository.getInstance(this)
        appInfoManager = AppInfoManager(this)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        securityRepo = com.example.muraqib.data.repository.SecurityRepository(this)

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

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.core.app.ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildForegroundNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
        }

        securityRepo.recordHeartbeat()
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            val hasActive = restrictionsRepo.getAllRestrictions().any { it.isEnabled }
            if (hasActive) {
                // إعادة تشغيل الخدمة تلقائياً في حال قام المستخدم بإغلاق التطبيق من قائمة التطبيقات الحديثة
                val restartServiceIntent = Intent(applicationContext, AppBlockerService::class.java).also {
                    it.setPackage(packageName)
                }
                val restartPendingIntent = PendingIntent.getService(
                    this,
                    101,
                    restartServiceIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmService = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmService?.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 1000L,
                    restartPendingIntent
                )
            }
        } catch (e: Exception) {
            // منع أي انهيار للخدمة
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {}
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastHeartbeatTime > 10_000L) {
                        lastHeartbeatTime = now
                        securityRepo.recordHeartbeat()
                    }

                    // إذا كانت الشاشة مغلقة أو الهاتف مقفلاً، ننتظر لتوفير البطارية وتفادي إظهار أي شاشات حظر
                    if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
                        if (BlockOverlayManager.isShowing) {
                            BlockOverlayManager.dismiss()
                        }
                        delay(3000)
                        continue
                    }

                    checkForegroundApp()
                } catch (e: Exception) {
                    // منع أي انهيار للخدمة
                }

                delay(1200) // فحص دوري كل 1.2 ثانية
            }
        }
    }

    private fun checkForegroundApp() {
        if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
            if (BlockOverlayManager.isShowing) {
                BlockOverlayManager.dismiss()
            }
            return
        }

        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - 45_000, now)
        val event = UsageEvents.Event()

        var currentForegroundPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == 29)
            ) {
                currentForegroundPackage = event.packageName
            }
        }

        // في حال لم ترجع الأحداث شيئًا، نستخدم استعلام الإحصائيات الأحدث كبديل
        if (currentForegroundPackage == null) {
            val recentStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60_000, now)
            currentForegroundPackage = recentStats?.filter {
                it.packageName != packageName &&
                !it.packageName.startsWith("com.android.systemui") &&
                !it.packageName.startsWith("com.google.android.inputmethod")
            }?.maxByOrNull { it.lastTimeUsed }?.packageName
        }

        val topPackage = currentForegroundPackage ?: return

        // تجاهل تطبيقنا وشاشات النظام
        if (topPackage == packageName ||
            topPackage == "android" ||
            topPackage.startsWith("com.google.android.inputmethod") ||
            topPackage.startsWith("com.android.systemui")
        ) {
            return
        }

        // البحث عما إذا كان هناك قيد مفعل لهذا التطبيق
        val restriction = restrictionsRepo.getRestrictionForPackage(topPackage)
        if (restriction == null || !restriction.isEnabled) {
            if (lastBlockedPackage == topPackage) {
                lastBlockedPackage = null
            }
            if (BlockOverlayManager.currentShowingPackage == topPackage) {
                BlockOverlayManager.dismiss()
            }
            return
        }

        // التحقق مما إذا كان هناك تخطٍ مؤقت ساري المفعول لهذا التطبيق
        if (restrictionsRepo.isPackageBypassed(topPackage)) {
            if (lastBlockedPackage == topPackage) {
                lastBlockedPackage = null
            }
            if (BlockOverlayManager.currentShowingPackage == topPackage) {
                BlockOverlayManager.dismiss()
            }
            return
        }

        // حساب وقت الاستهلاك للمجموعة أو التطبيق
        val consumedMinutes = calculateConsumedMinutes(restriction, topPackage)

        val calendar = Calendar.getInstance()
        val evaluation = restrictionsRepo.evaluateRestriction(restriction, consumedMinutes, calendar, topPackage)

        if (evaluation.isBlocked) {
            // منع تكرار فتح شاشة الحظر للتطبيق نفسه في فترة أقل من 2 ثانية
            if (lastBlockedPackage == topPackage && (now - lastBlockTimestamp) < 2000L) {
                return
            }

            lastBlockedPackage = topPackage
            lastBlockTimestamp = now

            val appName = appInfoManager.getAppName(topPackage)
            val canDrawOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
            val isSettingsApp = isSettingsPackage(topPackage)

            if (isSettingsApp && !MuraqibAccessibilityService.isServiceRunning) {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(homeIntent)
                } catch (e: Exception) {}
            }

            val blockIntent = BlockActivity.createIntent(
                context = this,
                packageName = topPackage,
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

            showBlockNotification(blockIntent, appName, evaluation.detailedReasonText)

            if (canDrawOverlay) {
                // إظهار النافذة العائمة فوق التطبيق المحظور مباشرة
                BlockOverlayManager.show(
                    context = this,
                    packageName = topPackage,
                    appName = appName,
                    reason = evaluation.detailedReasonText,
                    nextAvailable = evaluation.nextAvailableText,
                    onBypassAction = { durationMinutes ->
                        serviceScope.launch {
                            delay(durationMinutes * 60_000L)
                            restrictionsRepo.clearTemporaryBypass(topPackage)
                            checkForegroundApp()
                        }
                    }
                )
            }
        } else {
            if (lastBlockedPackage == topPackage) {
                lastBlockedPackage = null
            }
            if (BlockOverlayManager.currentShowingPackage == topPackage) {
                BlockOverlayManager.dismiss()
            }
        }
    }

    private fun showBlockNotification(intent: Intent, appName: String, reason: String) {
        val channelId = "muraqib_block_alert_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "تنبيهات الحظر", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "إشعار حظر التطبيق المقيد"
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }

    private fun calculateConsumedMinutes(restriction: AppRestriction, targetPackage: String? = null): Int {
        return restrictionsRepo.calculateConsumedMinutes(this, restriction, targetPackage)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "خدمة مراقبة القيود",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار تشغيل خدمة مراقبة قيود استخدام التطبيقات"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("مراقب الاستخدام")
            .setContentText("نظام حماية وتطبيق حدود الاستخدام نشط")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
