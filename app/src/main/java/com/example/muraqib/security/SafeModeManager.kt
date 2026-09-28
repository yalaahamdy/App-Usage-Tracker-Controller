package com.example.muraqib.security

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.data.repository.SecurityRepository

/**
 * نتيجة فحص وتدقيق حالة الوضع الآمن وإقلاع النظام
 */
data class SafeModeAuditResult(
    val isCurrentlyInSafeMode: Boolean,
    val hasUnauthorizedUsageDuringOffline: Boolean,
    val unauthorizedPackages: List<String> = emptyList(),
    val unauthorizedMinutes: Int = 0,
    val message: String? = null
)

/**
 * مدير حماية ورصد إقلاع الجهاز في الوضع الآمن وتدقيق الاستخدام أثناء توقف الحماية
 */
object SafeModeManager {

    /**
     * التحقق المباشر مما إذا كان نظام أندرويد يعمل حالياً في الوضع الآمن
     */
    fun isDeviceInSafeMode(context: Context): Boolean {
        return try {
            context.packageManager.isSafeMode
        } catch (e: Exception) {
            false
        }
    }

    /**
     * إجراء تدقيق شامل عند إقلاع النظام لاكتشاف أي استخدام للتطبيقات المقيدة
     * أثناء إيقاف الحماية أو عند تشغيل الهاتف في الوضع الآمن
     */
    fun performBootAudit(context: Context): SafeModeAuditResult {
        val securityRepo = SecurityRepository(context)
        if (!securityRepo.isSafeModeProtectionEnabled()) {
            return SafeModeAuditResult(
                isCurrentlyInSafeMode = false,
                hasUnauthorizedUsageDuringOffline = false
            )
        }

        val inSafeMode = isDeviceInSafeMode(context)
        val currentTime = System.currentTimeMillis()
        val currentBootTime = currentTime - SystemClock.elapsedRealtime()
        val lastHeartbeat = securityRepo.getLastHeartbeatTimestamp()
        val lastBootTime = securityRepo.getLastBootTime()

        // 1. في حال كان الجهاز يعمل فعلياً في الوضع الآمن
        if (inSafeMode) {
            val message = "الجهاز يعمل حالياً في الوضع الآمن (Safe Mode). الحماية مقيدة بواسطة النظام."
            securityRepo.recordSafeModeViolation(message)
            securityRepo.recordBootTime(currentBootTime)
            securityRepo.recordHeartbeat()
            return SafeModeAuditResult(
                isCurrentlyInSafeMode = true,
                hasUnauthorizedUsageDuringOffline = false,
                message = message
            )
        }

        // 2. إذا كانت هذه أول مرة يعمل فيها التطبيق ولم يتم تسجيل نبضة سابقة
        if (lastHeartbeat <= 0L) {
            securityRepo.recordBootTime(currentBootTime)
            securityRepo.recordHeartbeat()
            return SafeModeAuditResult(
                isCurrentlyInSafeMode = false,
                hasUnauthorizedUsageDuringOffline = false
            )
        }

        val restrictionsRepo = AppRestrictionsRepository.getInstance(context)
        val restrictedPackages = restrictionsRepo.getAllRestrictions()
            .filter { it.isEnabled }
            .flatMap { it.allPackages }
            .toSet()

        if (restrictedPackages.isEmpty()) {
            securityRepo.recordBootTime(currentBootTime)
            securityRepo.recordHeartbeat()
            return SafeModeAuditResult(
                isCurrentlyInSafeMode = false,
                hasUnauthorizedUsageDuringOffline = false
            )
        }

        // 3. فحص سجل أحداث الاستخدام أثناء الفترة التي توقف فيها التطبيق
        // (بين آخر نبضة نشاط مسجلة ووقت الإقلاع الحالي)
        val auditStartTime = lastHeartbeat.coerceAtMost(currentTime - 1000L)
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val unauthorizedFound = mutableSetOf<String>()
        var totalUnauthorizedDurationMs = 0L

        if (usageStatsManager != null && currentTime > auditStartTime) {
            try {
                val events = usageStatsManager.queryEvents(auditStartTime, currentTime)
                val event = UsageEvents.Event()
                val sessionStarts = mutableMapOf<String, Long>()

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val eventTime = event.timeStamp
                    val type = event.eventType

                    if (type == 16 || type == 26 || type == 27) { // SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN, DEVICE_STARTUP
                        for ((_, start) in sessionStarts) {
                            if (eventTime > start) {
                                totalUnauthorizedDurationMs += (eventTime - start)
                            }
                        }
                        sessionStarts.clear()
                        continue
                    }

                    val pkg = event.packageName ?: continue
                    if (!restrictedPackages.contains(pkg)) continue

                    if (type == UsageEvents.Event.ACTIVITY_RESUMED ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == 29)
                    ) {
                        sessionStarts[pkg] = eventTime
                        unauthorizedFound.add(pkg)
                    } else if (type == UsageEvents.Event.ACTIVITY_PAUSED ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == 30)
                    ) {
                        val start = sessionStarts.remove(pkg)
                        if (start != null && eventTime > start) {
                            totalUnauthorizedDurationMs += (eventTime - start)
                        }
                    }
                }

                // إضافة الجلسات المفتوحة التي لم تُغلق قبل الإقلاع مع مراعاة وقت الإقلاع
                for ((_, start) in sessionStarts) {
                    if (start >= currentBootTime && currentTime > start) {
                        totalUnauthorizedDurationMs += (currentTime - start)
                    } else if (start < currentBootTime) {
                        val safeDuration = (currentBootTime - start).coerceIn(0L, 60_000L)
                        totalUnauthorizedDurationMs += safeDuration
                    }
                }
            } catch (e: Exception) {
                // في حال تعذر الاستعلام نتجاوز بهدوء
            }
        }

        // فحص التلاعب بالوقت عبر تأخير ساعة النظام للخلف بعد إعادة التشغيل
        val hasClockTampering = lastHeartbeat > 0L && currentTime < (lastHeartbeat - 60_000L)
        if (hasClockTampering) {
            val clockMessage = "تم رصد تقديم أو تأخير ساعة النظام يدوياً لتجاوز قيود الاستخدام."
            securityRepo.recordSafeModeViolation(clockMessage)
        }

        val unauthorizedMinutes = (totalUnauthorizedDurationMs / 60_000L).toInt()

        // 4. التحقق مما إذا كان هناك إقلاع غير مسجل مع نشاط محظور
        val isNewReboot = lastBootTime > 0L && Math.abs(currentBootTime - lastBootTime) > 60_000L
        val hasViolation = unauthorizedFound.isNotEmpty()

        val violationMessage = when {
            hasViolation && isNewReboot -> {
                "تم رصد إعادة تشغيل الهاتف واستخدام ${unauthorizedFound.size} تطبيق مقيد أثناء توقف الحماية (احتمال تشغيل في الوضع الآمن)."
            }
            hasViolation -> {
                "تم رصد فتح تطبيقات مقيدة أثناء توقف خدمة المراقبة بمعدل $unauthorizedMinutes دقيقة."
            }
            else -> null
        }

        if (violationMessage != null) {
            securityRepo.recordSafeModeViolation(violationMessage)
        }

        // تحديث النبضة ووقت الإقلاع
        securityRepo.recordBootTime(currentBootTime)
        securityRepo.recordHeartbeat()

        return SafeModeAuditResult(
            isCurrentlyInSafeMode = false,
            hasUnauthorizedUsageDuringOffline = hasViolation,
            unauthorizedPackages = unauthorizedFound.toList(),
            unauthorizedMinutes = unauthorizedMinutes,
            message = violationMessage
        )
    }
}
