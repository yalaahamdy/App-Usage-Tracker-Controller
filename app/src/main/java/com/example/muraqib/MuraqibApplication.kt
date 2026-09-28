package com.example.muraqib

import android.app.Application
import android.util.Log
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.service.AppBlockerService

/**
 * فئة التطبيق المركزية لإدارة دورة حياة التطبيق، الاستعادة التلقائية، والتعامل مع حالات الانهيار
 */
class MuraqibApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. تثبيت معالج استثناءات عام لمنع فقدان البيانات وضمان الاستقرار
        setupCrashHandler()

        // 2. فحص فوري ومزامنة للخدمات عند بدء تشغيل العملية (Cold Start / Process Recovery)
        restoreServicesIfNeeded()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MuraqibApp", "Uncaught exception caught in thread: ${thread.name}", throwable)
            try {
                // حفظ ومزامنة حالة القيود قبل أي إغلاق مفاجئ
                val repo = AppRestrictionsRepository.getInstance(this)
                repo.loadRestrictions()
            } catch (e: Exception) {
                Log.e("MuraqibApp", "Failed to flush state during crash handling", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun restoreServicesIfNeeded() {
        try {
            if (androidx.core.os.UserManagerCompat.isUserUnlocked(this)) {
                com.example.muraqib.security.BootResilienceManager.restoreServicesOnBoot(this)
            }
        } catch (e: Exception) {
            Log.e("MuraqibApp", "Error restoring blocker service on process start", e)
        }
    }
}
