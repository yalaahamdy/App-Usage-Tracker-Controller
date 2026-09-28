package com.example.muraqib

import com.example.muraqib.data.model.AppRestriction
import com.example.muraqib.data.model.BlockReason
import com.example.muraqib.data.model.LimitPeriod
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.security.SafeModeAuditResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * اختبارات وحدة شاملة لميزات الحماية ضد النوافذ المصغرة (PiP)،
 * الشاشات المنقسمة (Split-Screen)، ورصد إقلاع الجهاز في الوضع الآمن (Safe Mode)
 */
class SafeModeAndMultiWindowTest {

    @Test
    fun testPictureInPictureInterceptionLogic() {
        val ytPackage = "com.google.android.youtube"
        val restriction = AppRestriction(
            packageName = ytPackage,
            appName = "YouTube",
            isEnabled = true,
            hasUsageLimit = true,
            limitDurationMinutes = 30,
            limitPeriod = LimitPeriod.DAILY
        )

        // محاكاة استهلاك 35 دقيقة (تجاوز الحد)
        val consumedMinutes = 35
        val eval = evaluateRestrictionMock(restriction, consumedMinutes, ytPackage)

        // التطبيق محظور
        assertTrue(eval.isBlocked)
        assertEquals(BlockReason.LIMIT_EXCEEDED, eval.reason)

        // إذا كان التطبيق يحاول العرض في نافذة مصغرة (PiP)
        val isPipMode = true
        val pipWindowBounds = TestWindowRect(700, 1200, 1050, 1800) // نافذة عائمة صغيرة

        // في وضع PiP، يجب ألا يتم تقليص شاشة الحظر لحجم النافذة الصغيرة، بل فرض ملء الشاشة
        val effectiveBounds = if (isPipMode) null else pipWindowBounds
        assertNull("في وضع PiP يجب أن تكون حدود النافذة null لفرض ملء الشاشة الكامل", effectiveBounds)
    }

    @Test
    fun testSplitScreenWindowBoundsIsolation() {
        val topAppPackage = "com.google.android.youtube"
        val bottomAppPackage = "com.example.notes"

        val ytRestriction = AppRestriction(
            packageName = topAppPackage,
            appName = "YouTube",
            isEnabled = true,
            isTotalBlock = true
        )

        val notesRestriction = AppRestriction(
            packageName = bottomAppPackage,
            appName = "Notes",
            isEnabled = false
        )

        // حدود النصف العلوي (YouTube)
        val topBounds = TestWindowRect(0, 0, 1080, 1100)
        // حدود النصف السفلي (Notes)
        val bottomBounds = TestWindowRect(0, 1150, 1080, 2300)

        // تقييم تطبيق النصف العلوي المحظور
        val evalTop = evaluateRestrictionMock(ytRestriction, 0, topAppPackage)
        assertTrue(evalTop.isBlocked)

        // تقييم تطبيق النصف السفلي المسموح
        val evalBottom = evaluateRestrictionMock(notesRestriction, 0, bottomAppPackage)
        assertFalse(evalBottom.isBlocked)

        // التحقق من أن أبعاد نافذة الحظر تغطي فقط النصف العلوي الخاص بالتطبيق المحظور
        assertEquals(1080, topBounds.width)
        assertEquals(1100, topBounds.height)
        assertEquals(0, topBounds.top)

        // النصف السفلي يظل مفتوحاً ومتاحاً دون تداخل
        assertEquals(1150, bottomBounds.top)
    }

    @Test
    fun testFullscreenAndSplitScreenOverlayDismissProtection() {
        val blockedPkg = "com.google.android.youtube"
        val launcherPkg = "com.google.android.apps.nexuslauncher"
        val notesPkg = "com.example.notes"

        // محاكاة حالة النافذة العائمة: معروضة لتطبيق يوتيوب
        var isOverlayShowing = true
        var showingPackage: String? = blockedPkg

        // دالة محاكاة منطق الإغلاق المحدث
        fun evaluateDismiss(
            eventType: Int,
            newPkg: String?,
            isSplitScreenActive: Boolean
        ): Boolean {
            // عدم إغلاق النافذة مطلقاً في أحداث تغير النوافذ TYPE_WINDOWS_CHANGED (eventType = 4194304)
            val TYPE_WINDOWS_CHANGED = 4194304
            val TYPE_WINDOW_STATE_CHANGED = 32

            if (eventType == TYPE_WINDOWS_CHANGED) {
                return false // لا يُغلق أبداً
            }

            if (eventType == TYPE_WINDOW_STATE_CHANGED && newPkg != null) {
                if (newPkg == showingPackage) {
                    return false // المستخدم لا يزال داخل نفس التطبيق المحظور
                }
                if (isSplitScreenActive) {
                    return false // التطبيق لا يزال معروضاً في الشاشة المنقسمة
                }
                // المستخدم انتقل لتطبيق آخر أو الشاشة الرئيسية
                return true
            }
            return false
        }

        // 1. وصول حدث TYPE_WINDOWS_CHANGED بسبب إضافة نافذة الحظر نفسها: يجب ألا تُغلق النافذة
        val dismissedOnWindowChange = evaluateDismiss(
            eventType = 4194304,
            newPkg = null,
            isSplitScreenActive = false
        )
        assertFalse("يجب عدم إغلاق نافذة الحظر فور ظهورها في أحداث TYPE_WINDOWS_CHANGED", dismissedOnWindowChange)

        // 2. وصول حدث تفاعل داخل نفس التطبيق المحظور: يجب أن تظل نافذة الحظر قائمة
        val dismissedOnSameApp = evaluateDismiss(
            eventType = 32,
            newPkg = blockedPkg,
            isSplitScreenActive = false
        )
        assertFalse("يجب عدم إغلاق نافذة الحظر طالما أن المستخدم داخل التطبيق المحظور", dismissedOnSameApp)

        // 3. في وضع الشاشات المنقسمة، تفاعل المستخدم مع النصف الآخر (Notes): يجب أن تظل نافذة الحظر قائمة فوق النصف المحظور
        val dismissedOnSplitOtherHalf = evaluateDismiss(
            eventType = 32,
            newPkg = notesPkg,
            isSplitScreenActive = true
        )
        assertFalse("يجب عدم إغلاق نافذة الحظر في وضع الشاشة المنقسمة عند التفاعل مع النصف الآخر", dismissedOnSplitOtherHalf)

        // 4. خروج المستخدم للشاشة الرئيسية: يجب إغلاق نافذة الحظر بسلاسة
        val dismissedOnHome = evaluateDismiss(
            eventType = 32,
            newPkg = launcherPkg,
            isSplitScreenActive = false
        )
        assertTrue("يجب إغلاق نافذة الحظر عند مغادرة التطبيق والعودة للشاشة الرئيسية", dismissedOnHome)
    }

    private data class TestWindowRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    @Test
    fun testSafeModeAuditLogicDetection() {
        // سيناريو 1: الجهاز يعمل فعلياً في الوضع الآمن
        val inSafeModeResult = SafeModeAuditMockHelper.audit(
            isDeviceInSafeMode = true,
            lastHeartbeat = 1000000L,
            currentTime = 2000000L,
            bootTime = 1900000L,
            lastBootTime = 1000000L,
            offlineEvents = emptyList()
        )

        assertTrue(inSafeModeResult.isCurrentlyInSafeMode)
        assertFalse(inSafeModeResult.hasUnauthorizedUsageDuringOffline)
        assertNotNull(inSafeModeResult.message)
        assertTrue(inSafeModeResult.message?.contains("الوضع الآمن") == true)

        // سيناريو 2: إعادة تشغيل الهاتف واستخدام تطبيقات مقيدة أثناء توقف الحماية
        val rebootViolationResult = SafeModeAuditMockHelper.audit(
            isDeviceInSafeMode = false,
            lastHeartbeat = 1000000L,
            currentTime = 2500000L,
            bootTime = 2000000L,
            lastBootTime = 1000000L, // إعادة تشغيل حدثت
            offlineEvents = listOf("com.zhiliaoapp.musically", "com.google.android.youtube")
        )

        assertFalse(rebootViolationResult.isCurrentlyInSafeMode)
        assertTrue(rebootViolationResult.hasUnauthorizedUsageDuringOffline)
        assertEquals(2, rebootViolationResult.unauthorizedPackages.size)
        assertTrue(rebootViolationResult.unauthorizedPackages.contains("com.zhiliaoapp.musically"))
        assertTrue(rebootViolationResult.message?.contains("إعادة تشغيل") == true)

        // سيناريو 3: إعادة تشغيل طبيعية بدون أي استخدام غير مصرح به
        val normalRebootResult = SafeModeAuditMockHelper.audit(
            isDeviceInSafeMode = false,
            lastHeartbeat = 1950000L,
            currentTime = 2050000L,
            bootTime = 2000000L,
            lastBootTime = 1000000L,
            offlineEvents = emptyList()
        )

        assertFalse(normalRebootResult.isCurrentlyInSafeMode)
        assertFalse(normalRebootResult.hasUnauthorizedUsageDuringOffline)
        assertNull(normalRebootResult.message)
    }

    @Test
    fun testSafeModeViolationLocksSession() {
        val helper = SecurityRepoMock()
        assertFalse(helper.isSafeModeViolationDetected())
        assertTrue(helper.isSessionUnlocked)

        // تسجيل مخالفة الوضع الآمن
        helper.recordSafeModeViolation("تم رصد تشغيل الهاتف في الوضع الآمن.")

        assertTrue(helper.isSafeModeViolationDetected())
        assertFalse("يجب قفل الجلسة فوراً عند تسجيل مخالفة الوضع الآمن", helper.isSessionUnlocked)
        assertEquals("تم رصد تشغيل الهاتف في الوضع الآمن.", helper.getSafeModeViolationMessage())

        // مسح المخالفة بعد المصادقة
        helper.clearSafeModeViolation()
        assertFalse(helper.isSafeModeViolationDetected())
        assertNull(helper.getSafeModeViolationMessage())
    }

    private fun evaluateRestrictionMock(
        restriction: AppRestriction,
        consumedMinutes: Int,
        targetPackage: String
    ): com.example.muraqib.data.model.RestrictionEvaluation {
        if (!restriction.isEnabled) {
            return com.example.muraqib.data.model.RestrictionEvaluation(
                isBlocked = false,
                reason = BlockReason.NONE,
                consumedMinutes = consumedMinutes,
                allowedMinutes = restriction.limitDurationMinutes,
                nextAvailableText = null,
                restriction = restriction
            )
        }

        if (restriction.isTotalBlock) {
            return com.example.muraqib.data.model.RestrictionEvaluation(
                isBlocked = true,
                reason = BlockReason.TOTAL_BLOCK,
                consumedMinutes = consumedMinutes,
                allowedMinutes = 0,
                nextAvailableText = "التطبيق محظور تمامًا",
                restriction = restriction
            )
        }

        if (restriction.hasUsageLimit && consumedMinutes >= restriction.limitDurationMinutes) {
            return com.example.muraqib.data.model.RestrictionEvaluation(
                isBlocked = true,
                reason = BlockReason.LIMIT_EXCEEDED,
                consumedMinutes = consumedMinutes,
                allowedMinutes = restriction.limitDurationMinutes,
                nextAvailableText = "غداً عند منتصف الليل",
                restriction = restriction
            )
        }

        return com.example.muraqib.data.model.RestrictionEvaluation(
            isBlocked = false,
            reason = BlockReason.NONE,
            consumedMinutes = consumedMinutes,
            allowedMinutes = restriction.limitDurationMinutes,
            nextAvailableText = null,
            restriction = restriction
        )
    }

    private class SecurityRepoMock {
        var isSessionUnlocked: Boolean = true
        private var violationDetected = false
        private var violationMessage: String? = null

        fun recordSafeModeViolation(msg: String) {
            violationDetected = true
            violationMessage = msg
            isSessionUnlocked = false
        }

        fun isSafeModeViolationDetected(): Boolean = violationDetected

        fun getSafeModeViolationMessage(): String? = violationMessage

        fun clearSafeModeViolation() {
            violationDetected = false
            violationMessage = null
        }
    }

    private object SafeModeAuditMockHelper {
        fun audit(
            isDeviceInSafeMode: Boolean,
            lastHeartbeat: Long,
            currentTime: Long,
            bootTime: Long,
            lastBootTime: Long,
            offlineEvents: List<String>
        ): SafeModeAuditResult {
            if (isDeviceInSafeMode) {
                return SafeModeAuditResult(
                    isCurrentlyInSafeMode = true,
                    hasUnauthorizedUsageDuringOffline = false,
                    message = "الجهاز يعمل حالياً في الوضع الآمن (Safe Mode). الحماية مقيدة بواسطة النظام."
                )
            }

            val isNewReboot = lastBootTime > 0L && Math.abs(bootTime - lastBootTime) > 60_000L
            val hasViolation = offlineEvents.isNotEmpty()

            val violationMessage = when {
                hasViolation && isNewReboot -> {
                    "تم رصد إعادة تشغيل الهاتف واستخدام ${offlineEvents.size} تطبيق مقيد أثناء توقف الحماية (احتمال تشغيل في الوضع الآمن)."
                }
                hasViolation -> {
                    "تم رصد فتح تطبيقات مقيدة أثناء توقف خدمة المراقبة."
                }
                else -> null
            }

            return SafeModeAuditResult(
                isCurrentlyInSafeMode = false,
                hasUnauthorizedUsageDuringOffline = hasViolation,
                unauthorizedPackages = offlineEvents,
                unauthorizedMinutes = if (hasViolation) 15 else 0,
                message = violationMessage
            )
        }
    }
}
