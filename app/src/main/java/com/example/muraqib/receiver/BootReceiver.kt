package com.example.muraqib.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.service.AppBlockerService

/**
 * مستقبل استشعار إعادة تشغيل الهاتف لتشغيل خدمة القيود تلقائياً
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            try {
                val repo = AppRestrictionsRepository.getInstance(context)
                val securityRepo = com.example.muraqib.data.repository.SecurityRepository(context)
                val hasActiveRestrictions = repo.getAllRestrictions().any { it.isEnabled }
                val hasProtection = securityRepo.isAppLockEnabled() || securityRepo.isAntiTamperEnabled() || securityRepo.isAntiUninstallEnabled()
                if (hasActiveRestrictions || hasProtection) {
                    AppBlockerService.start(context)
                }
            } catch (e: Exception) {
                // منع أي انهيار للـ BroadcastReceiver
            }
        }
    }
}
