package com.example.muraqib.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.os.UserManagerCompat
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.data.repository.SecurityRepository
import com.example.muraqib.security.BootResilienceManager
import com.example.muraqib.security.SafeModeManager
import com.example.muraqib.service.AppBlockerService

/**
 * مستقبل استشعار إعادة تشغيل الهاتف لتشغيل خدمات المراقبة والحماية تلقائياً
 * مصمم ليكون متوافقاً بالكامل مع وضع الإقلاع المباشر (Direct Boot)، فك القفل الأولي (USER_UNLOCKED)،
 * ومختلف إصدارات أندرويد وواجهات الشركات المصنعة (OEM Quick Boot).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            // التحقق من فك تشفير مساحة التخزين الخاصة بالمستخدم (Direct Boot)
            // إذا كان الهاتف لا يزال في مرحلة ما قبل إدخال رمز الشاشة لأول مرة،
            // نتجنب محاولة قراءة التخزين المشفر لتفادي أي استثناء، وسيعاد استدعاء المستقبل
            // فور فك القفل عبر ACTION_USER_UNLOCKED أو ACTION_BOOT_COMPLETED
            if (!UserManagerCompat.isUserUnlocked(context)) {
                return
            }

            try {
                // تدقيق أمني لحالة الوضع الآمن ورصد أي استخدام غير مصرح به أثناء توقف الحماية
                val auditResult = SafeModeManager.performBootAudit(context)

                val repo = AppRestrictionsRepository.getInstance(context)
                val securityRepo = SecurityRepository(context)
                val hasActiveRestrictions = repo.getAllRestrictions().any { it.isEnabled }
                val hasProtection = securityRepo.isAppLockEnabled() ||
                        securityRepo.isAntiTamperEnabled() ||
                        securityRepo.isAntiUninstallEnabled() ||
                        securityRepo.isSafeModeProtectionEnabled()

                if (hasActiveRestrictions || hasProtection || auditResult.hasUnauthorizedUsageDuringOffline || auditResult.isCurrentlyInSafeMode) {
                    AppBlockerService.start(context)
                }
            } catch (e: Exception) {
                // خطة طوارئ بديلة: بدء الخدمة في حال حدوث أي خطأ غير متوقع
                try {
                    BootResilienceManager.restoreServicesOnBoot(context)
                } catch (ex: Exception) {
                    // منع أي انهيار للـ BroadcastReceiver
                }
            }
        }
    }
}
