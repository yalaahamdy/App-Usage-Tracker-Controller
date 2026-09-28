package com.example.muraqib

import com.example.muraqib.data.model.AppRestriction
import com.example.muraqib.data.model.BlockReason
import com.example.muraqib.data.model.LimitPeriod
import com.example.muraqib.data.model.TimeWindow
import com.example.muraqib.data.model.isSettingsPackage
import com.example.muraqib.data.model.isSettingsPopupOrDialog
import com.example.muraqib.data.repository.AppRestrictionsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * اختبارات وحدة شاملة لخوارزميات ونماذج قيود الاستخدام وجدولة الأوقات
 */
class RestrictionModelsTest {

    @Test
    fun testTimeWindowContains() {
        val window = TimeWindow(startHour = 18, startMinute = 0, endHour = 21, endMinute = 0)

        assertFalse(window.contains(17, 59))
        assertTrue(window.contains(18, 0))
        assertTrue(window.contains(19, 30))
        assertTrue(window.contains(20, 59))
        assertFalse(window.contains(21, 0))
        assertFalse(window.contains(22, 0))
    }

    @Test
    fun testTimeWindowOvernightContains() {
        val overnight = TimeWindow(startHour = 22, startMinute = 0, endHour = 2, endMinute = 0)

        assertTrue(overnight.contains(22, 0))
        assertTrue(overnight.contains(23, 30))
        assertTrue(overnight.contains(0, 30))
        assertTrue(overnight.contains(1, 59))
        assertFalse(overnight.contains(2, 0))
        assertFalse(overnight.contains(12, 0))
    }

    @Test
    fun testDailyUsageLimitEvaluation() {
        val repo = AppRestrictionsRepositoryMock()
        val restriction = AppRestriction(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            hasUsageLimit = true,
            limitDurationMinutes = 30,
            limitPeriod = LimitPeriod.DAILY
        )

        // استهلاك 20 دقيقة (أقل من الحد)
        val evalUnder = repo.evaluate(restriction, consumedMinutes = 20)
        assertFalse(evalUnder.isBlocked)
        assertEquals(BlockReason.NONE, evalUnder.reason)

        // استهلاك 30 دقيقة (بلغ الحد)
        val evalExact = repo.evaluate(restriction, consumedMinutes = 30)
        assertTrue(evalExact.isBlocked)
        assertEquals(BlockReason.LIMIT_EXCEEDED, evalExact.reason)

        // استهلاك 40 دقيقة (تجاوز الحد)
        val evalOver = repo.evaluate(restriction, consumedMinutes = 40)
        assertTrue(evalOver.isBlocked)
        assertEquals(BlockReason.LIMIT_EXCEEDED, evalOver.reason)
        assertTrue(evalOver.nextAvailableText?.contains("منتصف الليل") == true)
    }

    @Test
    fun testScheduleEvaluationSingleWindow() {
        val repo = AppRestrictionsRepositoryMock()
        val restriction = AppRestriction(
            packageName = "com.instagram.android",
            appName = "Instagram",
            hasSchedule = true,
            timeWindows = listOf(TimeWindow(18, 0, 21, 0)),
            activeDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY)
        )

        val calInside = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
        }
        val evalInside = repo.evaluate(restriction, consumedMinutes = 10, calendar = calInside)
        assertFalse(evalInside.isBlocked)

        val calOutsideHours = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        val evalOutsideHours = repo.evaluate(restriction, consumedMinutes = 10, calendar = calOutsideHours)
        assertTrue(evalOutsideHours.isBlocked)
        assertEquals(BlockReason.OUTSIDE_SCHEDULE, evalOutsideHours.reason)

        val calInactiveDay = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY) // الجمعة غير مفعل
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
        }
        val evalInactiveDay = repo.evaluate(restriction, consumedMinutes = 10, calendar = calInactiveDay)
        assertTrue(evalInactiveDay.isBlocked)
        assertEquals(BlockReason.OUTSIDE_SCHEDULE, evalInactiveDay.reason)
    }

    @Test
    fun testScheduleEvaluationMultipleWindows() {
        val repo = AppRestrictionsRepositoryMock()
        val restriction = AppRestriction(
            packageName = "com.zhiliaoapp.musically",
            appName = "TikTok",
            hasSchedule = true,
            timeWindows = listOf(
                TimeWindow(8, 0, 10, 0),
                TimeWindow(16, 0, 18, 0),
                TimeWindow(20, 0, 22, 0)
            ),
            activeDays = (1..7).toSet()
        )

        val cal = Calendar.getInstance()

        // 09:00 -> نافذة 1 (مسموح)
        cal.set(Calendar.HOUR_OF_DAY, 9)
        assertFalse(repo.evaluate(restriction, 0, cal).isBlocked)

        // 12:00 -> بين نافذة 1 و 2 (محظور)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        assertTrue(repo.evaluate(restriction, 0, cal).isBlocked)

        // 17:00 -> نافذة 2 (مسموح)
        cal.set(Calendar.HOUR_OF_DAY, 17)
        assertFalse(repo.evaluate(restriction, 0, cal).isBlocked)

        // 19:00 -> بين نافذة 2 و 3 (محظور)
        cal.set(Calendar.HOUR_OF_DAY, 19)
        assertTrue(repo.evaluate(restriction, 0, cal).isBlocked)

        // 21:00 -> نافذة 3 (مسموح)
        cal.set(Calendar.HOUR_OF_DAY, 21)
        assertFalse(repo.evaluate(restriction, 0, cal).isBlocked)
    }

    @Test
    fun testCombinedLimitAndSchedule() {
        val repo = AppRestrictionsRepositoryMock()
        val restriction = AppRestriction(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            hasUsageLimit = true,
            limitDurationMinutes = 60, // 60 دقيقة
            hasSchedule = true,
            timeWindows = listOf(TimeWindow(18, 0, 22, 0)), // 18:00 - 22:00 فقط
            activeDays = (1..7).toSet()
        )

        val calInside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
        }

        // 1. داخل الجدول وتحت الحد الزمني (45 د) -> مسموح
        val eval1 = repo.evaluate(restriction, consumedMinutes = 45, calendar = calInside)
        assertFalse(eval1.isBlocked)

        // 2. داخل الجدول ولكن تجاوز الحد الزمني (65 د) -> محظور لانتهاء الحد
        val eval2 = repo.evaluate(restriction, consumedMinutes = 65, calendar = calInside)
        assertTrue(eval2.isBlocked)
        assertEquals(BlockReason.LIMIT_EXCEEDED, eval2.reason)

        val calOutside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }

        // 3. خارج الجدول وتحت الحد الزمني (15 د) -> محظور بسبب الجدول
        val eval3 = repo.evaluate(restriction, consumedMinutes = 15, calendar = calOutside)
        assertTrue(eval3.isBlocked)
        assertEquals(BlockReason.OUTSIDE_SCHEDULE, eval3.reason)
    }

    @Test
    fun testJsonSerialization() {
        val original = AppRestriction(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            isEnabled = true,
            hasUsageLimit = true,
            limitDurationMinutes = 45,
            limitPeriod = LimitPeriod.WEEKLY,
            hasSchedule = true,
            timeWindows = listOf(TimeWindow(10, 0, 12, 30), TimeWindow(17, 15, 20, 0)),
            activeDays = setOf(1, 2, 3)
        )

        val json = original.toJsonObject()
        val reconstructed = AppRestriction.fromJsonObject(json)

        assertEquals(original.packageName, reconstructed.packageName)
        assertEquals(original.appName, reconstructed.appName)
        assertEquals(original.limitDurationMinutes, reconstructed.limitDurationMinutes)
        assertEquals(original.limitPeriod, reconstructed.limitPeriod)
        assertEquals(original.hasSchedule, reconstructed.hasSchedule)
        assertEquals(2, reconstructed.timeWindows.size)
        assertEquals("10:00 – 12:30", reconstructed.timeWindows[0].displayRange)
        assertEquals(setOf(1, 2, 3), reconstructed.activeDays)
    }

    @Test
    fun testMultiAppRestrictionSupport() {
        val restriction = AppRestriction(
            packageName = "com.google.android.youtube",
            appName = "مجموعة الترفيه والتواصل",
            targetPackages = listOf("com.google.android.youtube", "com.instagram.android", "com.zhiliaoapp.musically"),
            isEnabled = true,
            hasUsageLimit = true,
            limitDurationMinutes = 45
        )

        assertTrue(restriction.appliesTo("com.google.android.youtube"))
        assertTrue(restriction.appliesTo("com.instagram.android"))
        assertTrue(restriction.appliesTo("com.zhiliaoapp.musically"))
        assertFalse(restriction.appliesTo("com.whatsapp"))

        assertEquals(3, restriction.allPackages.size)

        // اختبار التسلسل والاسترجاع من JSON
        val json = restriction.toJsonObject()
        val restored = AppRestriction.fromJsonObject(json)
        assertEquals(3, restored.targetPackages.size)
        assertTrue(restored.appliesTo("com.instagram.android"))
    }

    @Test
    fun testAppCategoryGuessing() {
        assertEquals("تواصل اجتماعي", AppRestriction.guessAppCategory("com.whatsapp"))
        assertEquals("تواصل اجتماعي", AppRestriction.guessAppCategory("com.instagram.android"))
        assertEquals("ترفيه وفيديو", AppRestriction.guessAppCategory("com.google.android.youtube"))
        assertEquals("ترفيه وفيديو", AppRestriction.guessAppCategory("com.netflix.mediaclient"))
        assertEquals("ألعاب", AppRestriction.guessAppCategory("com.roblox.client"))
        assertEquals("تطبيقات أخرى", AppRestriction.guessAppCategory("com.android.calculator2"))
    }

    @Test
    fun testTotalBlockRestriction() {
        val repo = AppRestrictionsRepositoryMock()
        val restriction = AppRestriction(
            packageName = "com.zhiliaoapp.musically",
            appName = "TikTok",
            isEnabled = true,
            isTotalBlock = true
        )

        val eval = repo.evaluate(restriction, consumedMinutes = 0)
        assertTrue(eval.isBlocked)
        assertEquals(BlockReason.TOTAL_BLOCK, eval.reason)
        assertEquals("هذا التطبيق محظور تمامًا بقرار منك لمنع استخدامه.", eval.detailedReasonText)

        // اختبار التسلسل إلى JSON
        val json = restriction.toJsonObject()
        val restored = AppRestriction.fromJsonObject(json)
        assertTrue(restored.isTotalBlock)
    }

    @Test
    fun testGroupBypassIsolation() {
        val repo = AppRestrictionsRepositoryMock()
        val groupRestriction = AppRestriction(
            packageName = "com.google.android.youtube",
            appName = "مجموعة الترفيه",
            isEnabled = true,
            isTotalBlock = true,
            targetPackages = listOf("com.google.android.youtube", "com.zhiliaoapp.musically", "com.instagram.android")
        )

        // تعيين تخطي مؤقت لتطبيق YouTube فقط
        repo.bypassedPackages.add("com.google.android.youtube")

        // فحص YouTube (مستفيد من التخطي) -> غير محظور
        val evalYouTube = repo.evaluate(groupRestriction, consumedMinutes = 0, targetPackage = "com.google.android.youtube")
        assertFalse(evalYouTube.isBlocked)

        // فحص TikTok (في نفس المجموعة ولكنه غير مستفيد من التخطي) -> محظور!
        val evalTikTok = repo.evaluate(groupRestriction, consumedMinutes = 0, targetPackage = "com.zhiliaoapp.musically")
        assertTrue(evalTikTok.isBlocked)
        assertEquals(BlockReason.TOTAL_BLOCK, evalTikTok.reason)

        // فحص Instagram (في نفس المجموعة وغير مستفيد) -> محظور!
        val evalInstagram = repo.evaluate(groupRestriction, consumedMinutes = 0, targetPackage = "com.instagram.android")
        assertTrue(evalInstagram.isBlocked)
        assertEquals(BlockReason.TOTAL_BLOCK, evalInstagram.reason)
    }

    @Test
    fun testSettingsAppBlockingInGroup() {
        val repo = AppRestrictionsRepositoryMock()
        val groupWithSettings = AppRestriction(
            packageName = "com.google.android.youtube",
            appName = "مجموعة الحظر الكلي مع الضبط",
            isEnabled = true,
            isTotalBlock = true,
            targetPackages = listOf("com.google.android.youtube", "com.android.settings", "com.zhiliaoapp.musically")
        )

        // التحقق من مطابقة appliesTo لتطبيق الضبط والشاشات والخدمات التابعة له
        assertTrue(groupWithSettings.appliesTo("com.android.settings"))
        assertTrue(groupWithSettings.appliesTo("com.android.settings.intelligence"))
        assertTrue(groupWithSettings.appliesTo("com.google.android.settings.intelligence"))
        assertTrue(groupWithSettings.appliesTo("com.android.settings.wifi.WifiSettings"))

        // تقييم حظر تطبيق الضبط الرئيسي
        val evalSettings = repo.evaluate(groupWithSettings, consumedMinutes = 0, targetPackage = "com.android.settings")
        assertTrue(evalSettings.isBlocked)
        assertEquals(BlockReason.TOTAL_BLOCK, evalSettings.reason)

        // تقييم حظر شاشة فرعية أو بحث الضبط
        val evalSubSettings = repo.evaluate(groupWithSettings, consumedMinutes = 0, targetPackage = "com.google.android.settings.intelligence")
        assertTrue(evalSubSettings.isBlocked)
        assertEquals(BlockReason.TOTAL_BLOCK, evalSubSettings.reason)

        // تقييم بقية تطبيقات المجموعة
        val evalYouTube = repo.evaluate(groupWithSettings, consumedMinutes = 0, targetPackage = "com.google.android.youtube")
        assertTrue(evalYouTube.isBlocked)
        assertEquals(BlockReason.TOTAL_BLOCK, evalYouTube.reason)
    }

    @Test
    fun testSettingsPackageRecognitionAndBlockingAcrossOEMs() {
        // التحقق من التعرف المباشر على حزم الإعدادات لمختلف الشركات المصنعة
        assertTrue(isSettingsPackage("com.android.settings"))
        assertTrue(isSettingsPackage("com.android.settings.SubSettings"))
        assertTrue(isSettingsPackage("com.google.android.settings"))
        assertTrue(isSettingsPackage("com.google.android.settings.intelligence"))
        assertTrue(isSettingsPackage("com.samsung.android.settings"))
        assertTrue(isSettingsPackage("com.coloros.settings"))
        assertTrue(isSettingsPackage("com.coloros.settings.privacy"))
        assertTrue(isSettingsPackage("com.oplus.settings"))
        assertTrue(isSettingsPackage("com.vivo.settings"))
        assertTrue(isSettingsPackage("com.miui.securitycenter"))
        assertTrue(isSettingsPackage("com.huawei.settings"))
        assertTrue(isSettingsPackage("com.motorola.android.settings"))

        // التحقق من عدم التعرف على تطبيقات أخرى تشبه الإعدادات بالخطأ
        assertFalse(isSettingsPackage("com.whatsapp"))
        assertFalse(isSettingsPackage("com.google.android.youtube"))
        assertFalse(isSettingsPackage(null))
        assertFalse(isSettingsPackage(""))

        // التحقق من أن قيد الحظر على الإعدادات يطابق تلقائياً كافة الواجهات والشركات
        val settingsRestriction = AppRestriction(
            packageName = "com.android.settings",
            appName = "الضبط",
            isEnabled = true,
            isTotalBlock = true
        )

        assertTrue(settingsRestriction.appliesTo("com.android.settings"))
        assertTrue(settingsRestriction.appliesTo("com.samsung.android.settings"))
        assertTrue(settingsRestriction.appliesTo("com.coloros.settings"))
        assertTrue(settingsRestriction.appliesTo("com.oplus.settings"))
        assertTrue(settingsRestriction.appliesTo("com.vivo.settings"))
        assertTrue(settingsRestriction.appliesTo("com.miui.securitycenter"))
        assertTrue(settingsRestriction.appliesTo("com.google.android.settings.intelligence"))

        assertFalse(settingsRestriction.appliesTo("com.instagram.android"))
    }

    @Test
    fun testSettingsPopupAndDialogExclusionFromBlocking() {
        // 1. التحقق من التعرف على النوافذ المنبثقة من أسماء الكلاسات
        assertTrue(isSettingsPopupOrDialog(className = "com.android.settings.bluetooth.BluetoothPairingDialog"))
        assertTrue(isSettingsPopupOrDialog(className = "com.android.settings.panel.SettingsPanelActivity"))
        assertTrue(isSettingsPopupOrDialog(className = "com.android.settings.wifi.WifiDialogActivity"))
        assertTrue(isSettingsPopupOrDialog(className = "android.app.AlertDialog"))
        assertTrue(isSettingsPopupOrDialog(className = "androidx.appcompat.app.AlertDialog"))
        assertTrue(isSettingsPopupOrDialog(className = "com.android.systemui.qs.tiles.dialog.InternetDialog"))
        assertTrue(isSettingsPopupOrDialog(className = "com.google.android.material.bottomsheet.BottomSheetDialog"))
        assertTrue(isSettingsPopupOrDialog(className = "com.android.settings.wifi.slice.ConnectToWifiHandler"))
        assertTrue(isSettingsPopupOrDialog(className = "android.widget.PopupWindow"))

        // 2. التحقق من التعرف على الحزم المخصصة للنوافذ المنبثقة ومربعات الحوار والأذونات
        assertTrue(isSettingsPopupOrDialog(className = null, packageName = "com.android.settings.panel"))
        assertTrue(isSettingsPopupOrDialog(className = null, packageName = "com.android.permissioncontroller"))
        assertTrue(isSettingsPopupOrDialog(className = null, packageName = "com.google.android.permissioncontroller"))

        // 3. التحقق من الأبعاد: النوافذ العائمة الأصغر من ملء الشاشة تُعتبر منبثقة
        assertTrue(
            isSettingsPopupOrDialog(
                className = "com.android.settings.SubSettings",
                packageName = "com.android.settings",
                windowWidth = 800,
                windowHeight = 600,
                screenWidth = 1080,
                screenHeight = 2400
            )
        )

        // 4. تطبيق الإعدادات الرئيسي بملء الشاشة لا يُعتبر نافذة منبثقة ويخضع للحظر إذا تم تقييده
        assertFalse(
            isSettingsPopupOrDialog(
                className = "com.android.settings.Settings",
                packageName = "com.android.settings",
                windowWidth = 1080,
                windowHeight = 2400,
                screenWidth = 1080,
                screenHeight = 2400
            )
        )
        assertFalse(
            isSettingsPopupOrDialog(
                className = "com.android.settings.homepage.SettingsHomepageActivity",
                packageName = "com.android.settings",
                windowWidth = 1080,
                windowHeight = 2400,
                screenWidth = 1080,
                screenHeight = 2400
            )
        )
        assertFalse(
            isSettingsPopupOrDialog(
                className = "com.samsung.android.settings.SecSettingsActivity",
                packageName = "com.samsung.android.settings",
                windowWidth = 1080,
                windowHeight = 2400,
                screenWidth = 1080,
                screenHeight = 2400
            )
        )
    }

    @Test
    fun testSettingsOnlyBlockedWhenExplicitlyConfiguredByUser() {
        val repo = AppRestrictionsRepositoryMock()

        // سيناريو 1: المستخدم حظر تطبيقات أخرى (مثل يوتيوب وإنستغرام) ولم يحظر الإعدادات
        val otherAppsRestriction = AppRestriction(
            targetPackages = listOf("com.google.android.youtube", "com.instagram.android"),
            appName = "ترفيه",
            isEnabled = true,
            isTotalBlock = true
        )

        // التحقق من أن قيد التطبيقات الأخرى لا ينطبق مطلقاً على الإعدادات
        assertFalse(otherAppsRestriction.appliesTo("com.android.settings"))
        assertFalse(otherAppsRestriction.appliesTo("com.samsung.android.settings"))

        // محاكاة مستودع القيود: البحث عما إذا كان هناك قيد ينطبق على الإعدادات
        val restrictionsList = listOf(otherAppsRestriction)
        val matchedRestrictionForSettings = restrictionsList.find { it.isEnabled && it.appliesTo("com.android.settings") }
        // لا يوجد أي قيد للإعدادات وبالتالي لا يتم الحظر نهائياً
        org.junit.Assert.assertNull(matchedRestrictionForSettings)

        // سيناريو 2: المستخدم قام بنفسه بتحديد وإضافة تطبيق الإعدادات للقيد
        val settingsRestriction = AppRestriction(
            targetPackages = listOf("com.android.settings"),
            appName = "الضبط",
            isEnabled = true,
            isTotalBlock = true
        )

        // الآن فقط ينطبق القيد على الإعدادات لأن المستخدم اختارها بنفسه
        assertTrue(settingsRestriction.appliesTo("com.android.settings"))
        assertTrue(settingsRestriction.appliesTo("com.samsung.android.settings"))

        val activeListWithSettings = listOf(settingsRestriction)
        val matchedSettings = activeListWithSettings.find { it.isEnabled && it.appliesTo("com.android.settings") }
        org.junit.Assert.assertNotNull(matchedSettings)

        val evalSettingsWhenRestricted = repo.evaluate(
            matchedSettings!!,
            consumedMinutes = 0,
            targetPackage = "com.android.settings"
        )
        assertTrue(evalSettingsWhenRestricted.isBlocked)
        assertEquals(BlockReason.TOTAL_BLOCK, evalSettingsWhenRestricted.reason)
    }

    /**
     * فئة مساعدة لاختبار منطق التقييم دون الاعتماد على Android Context
     */
    private class AppRestrictionsRepositoryMock {
        val bypassedPackages = mutableSetOf<String>()

        fun evaluate(
            restriction: AppRestriction,
            consumedMinutes: Int,
            calendar: Calendar = Calendar.getInstance(),
            targetPackage: String? = null
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

            val packageToCheck = when {
                targetPackage != null -> targetPackage
                restriction.allPackages.size == 1 -> restriction.allPackages.first()
                else -> null
            }

            if (packageToCheck != null && bypassedPackages.contains(packageToCheck)) {
                return com.example.muraqib.data.model.RestrictionEvaluation(
                    isBlocked = false,
                    reason = BlockReason.NONE,
                    consumedMinutes = consumedMinutes,
                    allowedMinutes = restriction.limitDurationMinutes,
                    nextAvailableText = "تخطي مؤقت نشط",
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

            val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            if (restriction.hasSchedule && restriction.timeWindows.isNotEmpty()) {
                val isDayActive = restriction.activeDays.contains(currentDay)
                if (!isDayActive) {
                    return com.example.muraqib.data.model.RestrictionEvaluation(
                        isBlocked = true,
                        reason = BlockReason.OUTSIDE_SCHEDULE,
                        consumedMinutes = consumedMinutes,
                        allowedMinutes = restriction.limitDurationMinutes,
                        nextAvailableText = "يوم آخر",
                        restriction = restriction
                    )
                }

                val isInside = restriction.timeWindows.any { it.contains(currentHour, currentMinute) }
                if (!isInside) {
                    return com.example.muraqib.data.model.RestrictionEvaluation(
                        isBlocked = true,
                        reason = BlockReason.OUTSIDE_SCHEDULE,
                        consumedMinutes = consumedMinutes,
                        allowedMinutes = restriction.limitDurationMinutes,
                        nextAvailableText = "في الفترة القادمة",
                        restriction = restriction
                    )
                }
            }

            if (restriction.hasUsageLimit) {
                if (consumedMinutes >= restriction.limitDurationMinutes) {
                    return com.example.muraqib.data.model.RestrictionEvaluation(
                        isBlocked = true,
                        reason = BlockReason.LIMIT_EXCEEDED,
                        consumedMinutes = consumedMinutes,
                        allowedMinutes = restriction.limitDurationMinutes,
                        nextAvailableText = "غداً عند منتصف الليل",
                        restriction = restriction
                    )
                }
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
    }
}
