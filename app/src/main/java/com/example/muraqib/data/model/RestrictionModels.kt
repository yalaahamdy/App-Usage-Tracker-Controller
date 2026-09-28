package com.example.muraqib.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * نافذة زمنية مسموحة للاستخدام (من - إلى)
 */
data class TimeWindow(
    val startHour: Int, // 0..23
    val startMinute: Int, // 0..59
    val endHour: Int, // 0..23
    val endMinute: Int // 0..59
) {
    val displayRange: String
        get() = "%02d:%02d – %02d:%02d".format(Locale.getDefault(), startHour, startMinute, endHour, endMinute)

    /**
     * التحقق مما إذا كان الوقت الحالي يقع ضمن هذه النافذة الزمنية
     */
    fun contains(hour: Int, minute: Int): Boolean {
        val currentTotalMinutes = hour * 60 + minute
        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute

        return if (startTotalMinutes <= endTotalMinutes) {
            currentTotalMinutes in startTotalMinutes until endTotalMinutes
        } else {
            // فترة تمتد بعد منتصف الليل
            currentTotalMinutes >= startTotalMinutes || currentTotalMinutes < endTotalMinutes
        }
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("startHour", startHour)
            put("startMinute", startMinute)
            put("endHour", endHour)
            put("endMinute", endMinute)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): TimeWindow {
            return TimeWindow(
                startHour = json.optInt("startHour", 0),
                startMinute = json.optInt("startMinute", 0),
                endHour = json.optInt("endHour", 23),
                endMinute = json.optInt("endMinute", 59)
            )
        }
    }
}

/**
 * نوع دورة حد الاستخدام
 */
enum class LimitPeriod(val titleAr: String) {
    DAILY("يوميًا"),
    WEEKLY("أسبوعيًا")
}

/**
 * سبب الحظر
 */
enum class BlockReason(val titleAr: String) {
    NONE("متاح"),
    TOTAL_BLOCK("تم تفعيل الحظر الكامل لهذا التطبيق"),
    LIMIT_EXCEEDED("انتهى وقت الاستخدام المسموح"),
    OUTSIDE_SCHEDULE("خارج جدول الأوقات المسموحة")
}

/**
 * تمثيل قيد الاستخدام الخاص بتطبيق أو عدة تطبيقات معاً
 */
data class AppRestriction(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String = "", // الحزمة الأساسية للتوافق
    val appName: String = "",
    val targetPackages: List<String> = emptyList(), // دعم تطبيقات متعددة في قيد واحد
    val isEnabled: Boolean = true,
    // الحظر الكامل التام فور الفتح
    val isTotalBlock: Boolean = false,
    // أولاً: حد الاستخدام
    val hasUsageLimit: Boolean = false,
    val limitDurationMinutes: Int = 30,
    val limitPeriod: LimitPeriod = LimitPeriod.DAILY,
    // ثانياً: جدول أوقات الاستخدام
    val hasSchedule: Boolean = false,
    val timeWindows: List<TimeWindow> = emptyList(),
    val activeDays: Set<Int> = (1..7).toSet(), // أيام الأسبوع حسب Calendar (1 = الأحد .. 7 = السبت)
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * استرجاع جميع الحزم الخاضعة لهذا القيد
     */
    val allPackages: List<String>
        get() = if (targetPackages.isNotEmpty()) targetPackages else if (packageName.isNotBlank()) listOf(packageName) else emptyList()

    /**
     * التحقق مما إذا كان القيد ينطبق على حزمة معينة
     */
    fun appliesTo(pkg: String): Boolean {
        if (allPackages.contains(pkg)) return true
        // إذا كان قيد الحظر يشمل تطبيق الضبط، نطبق الحظر على جميع الشاشات والخدمات والأنشطة التابعة للضبط مهما اختلفت واجهة الجهاز
        val hasSettings = allPackages.any { isSettingsPackage(it) }
        if (hasSettings && isSettingsPackage(pkg)) {
            return true
        }
        return false
    }

    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("packageName", packageName)
        json.put("appName", appName)

        val pkgsArray = JSONArray()
        allPackages.forEach { pkgsArray.put(it) }
        json.put("targetPackages", pkgsArray)

        json.put("isEnabled", isEnabled)
        json.put("isTotalBlock", isTotalBlock)
        json.put("hasUsageLimit", hasUsageLimit)
        json.put("limitDurationMinutes", limitDurationMinutes)
        json.put("limitPeriod", limitPeriod.name)
        json.put("hasSchedule", hasSchedule)

        val windowsArray = JSONArray()
        timeWindows.forEach { windowsArray.put(it.toJsonObject()) }
        json.put("timeWindows", windowsArray)

        val daysArray = JSONArray()
        activeDays.forEach { daysArray.put(it) }
        json.put("activeDays", daysArray)

        json.put("createdAt", createdAt)
        return json
    }

    companion object {
        fun fromJsonObject(json: JSONObject): AppRestriction {
            val windows = mutableListOf<TimeWindow>()
            val windowsArray = json.optJSONArray("timeWindows")
            if (windowsArray != null) {
                for (i in 0 until windowsArray.length()) {
                    windowsArray.optJSONObject(i)?.let { windows.add(TimeWindow.fromJsonObject(it)) }
                }
            }

            val days = mutableSetOf<Int>()
            val daysArray = json.optJSONArray("activeDays")
            if (daysArray != null) {
                for (i in 0 until daysArray.length()) {
                    days.add(daysArray.optInt(i))
                }
            } else {
                days.addAll(1..7)
            }

            val targetPkgs = mutableListOf<String>()
            val pkgsArray = json.optJSONArray("targetPackages")
            if (pkgsArray != null) {
                for (i in 0 until pkgsArray.length()) {
                    targetPkgs.add(pkgsArray.optString(i))
                }
            }

            val basePackage = json.optString("packageName", "")
            if (targetPkgs.isEmpty() && basePackage.isNotBlank()) {
                targetPkgs.add(basePackage)
            }

            return AppRestriction(
                id = json.optString("id", UUID.randomUUID().toString()),
                packageName = basePackage.ifBlank { targetPkgs.firstOrNull() ?: "" },
                appName = json.optString("appName", ""),
                targetPackages = targetPkgs,
                isEnabled = json.optBoolean("isEnabled", true),
                isTotalBlock = json.optBoolean("isTotalBlock", false),
                hasUsageLimit = json.optBoolean("hasUsageLimit", false),
                limitDurationMinutes = json.optInt("limitDurationMinutes", 30),
                limitPeriod = try {
                    LimitPeriod.valueOf(json.optString("limitPeriod", LimitPeriod.DAILY.name))
                } catch (e: Exception) {
                    LimitPeriod.DAILY
                },
                hasSchedule = json.optBoolean("hasSchedule", false),
                timeWindows = windows,
                activeDays = days,
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }

        fun getDayNameAr(calendarDay: Int): String {
            return when (calendarDay) {
                Calendar.SUNDAY -> "الأحد"
                Calendar.MONDAY -> "الاثنين"
                Calendar.TUESDAY -> "الثلاثاء"
                Calendar.WEDNESDAY -> "الأربعاء"
                Calendar.THURSDAY -> "الخميس"
                Calendar.FRIDAY -> "الجمعة"
                Calendar.SATURDAY -> "السبت"
                else -> ""
            }
        }

        /**
         * تصنيف التطبيق تلقائياً للمساعدة في التحديد السريع
         */
        fun guessAppCategory(packageName: String): String {
            val lower = packageName.lowercase(Locale.getDefault())
            return when {
                lower.contains("whatsapp") || lower.contains("instagram") ||
                        lower.contains("facebook") || lower.contains("twitter") ||
                        lower.contains("snapchat") || lower.contains("tiktok") ||
                        lower.contains("telegram") || lower.contains("messenger") ||
                        lower.contains("discord") || lower.contains("threads") -> "تواصل اجتماعي"

                lower.contains("youtube") || lower.contains("netflix") ||
                        lower.contains("spotify") || lower.contains("twitch") ||
                        lower.contains("primevideo") || lower.contains("shahid") ||
                        lower.contains("music") || lower.contains("video") -> "ترفيه وفيديو"

                lower.contains("game") || lower.contains("pubg") ||
                        lower.contains("roblox") || lower.contains("clash") ||
                        lower.contains("candycrush") || lower.contains("subway") -> "ألعاب"

                else -> "تطبيقات أخرى"
            }
        }
    }
}

/**
 * نتيجة تقييم حالة قيد التطبيق في اللحظة الحالية
 */
data class RestrictionEvaluation(
    val isBlocked: Boolean,
    val reason: BlockReason,
    val consumedMinutes: Int,
    val allowedMinutes: Int,
    val nextAvailableText: String?,
    val restriction: AppRestriction
) {
    val detailedReasonText: String
        get() = when (reason) {
            BlockReason.TOTAL_BLOCK -> {
                "هذا التطبيق محظور تمامًا بقرار منك لمنع استخدامه."
            }
            BlockReason.LIMIT_EXCEEDED -> {
                if (restriction.limitPeriod == LimitPeriod.DAILY) {
                    "انتهى وقت الاستخدام المسموح لهذا التطبيق اليوم ($consumedMinutes د من أصل $allowedMinutes د)."
                } else {
                    "انتهى وقت الاستخدام المسموح لهذا التطبيق لهذا الأسبوع ($consumedMinutes د من أصل $allowedMinutes د)."
                }
            }
            BlockReason.OUTSIDE_SCHEDULE -> {
                "هذا التطبيق غير متاح حاليًا وفق جدول الاستخدام المحدد."
            }
            BlockReason.NONE -> "التطبيق متاح للاستخدام."
        }
}

/**
 * التحقق مما إذا كانت الحزمة تنتمي لتطبيق الضبط/الإعدادات بكافة إصداراته وواجهات الشركات المصنعة
 */
fun isSettingsPackage(pkg: String?): Boolean {
    if (pkg.isNullOrBlank()) return false
    val lower = pkg.lowercase(Locale.getDefault())
    return lower == "com.android.settings" ||
            lower.startsWith("com.android.settings.") ||
            lower == "com.google.android.settings" ||
            lower.startsWith("com.google.android.settings.") ||
            lower == "com.samsung.android.settings" ||
            lower.startsWith("com.samsung.android.settings.") ||
            lower == "com.coloros.settings" ||
            lower.startsWith("com.coloros.settings.") ||
            lower == "com.oplus.settings" ||
            lower.startsWith("com.oplus.settings.") ||
            lower == "com.vivo.settings" ||
            lower.startsWith("com.vivo.settings.") ||
            lower == "com.huawei.settings" ||
            lower.startsWith("com.huawei.settings.") ||
            lower == "com.xiaomi.settings" ||
            lower.startsWith("com.xiaomi.settings.") ||
            lower == "com.motorola.android.settings" ||
            lower.startsWith("com.motorola.android.settings.") ||
            lower == "com.miui.securitycenter"
}

/**
 * التحقق مما إذا كان الكائن أو الحدث يمثل نافذة منبثقة أو مربع حوار تابع للضبط
 * (مثل لوحات الإنترنت والواي فاي، حوارات اقتران البلوتوث، لوحات الصوت، مربعات تأكيد الأذونات)
 */
fun isSettingsPopupOrDialog(
    className: String?,
    packageName: String? = null,
    windowWidth: Int = 0,
    windowHeight: Int = 0,
    screenWidth: Int = 0,
    screenHeight: Int = 0
): Boolean {
    val cls = className ?: ""
    val pkg = packageName ?: ""

    // 1. الكلمات الدلالية في اسم الكلاس الدالة صراحة على النوافذ المنبثقة ومربعات الحوار
    val dialogKeywords = listOf(
        "Dialog",
        "AlertDialog",
        "Panel",
        "Popup",
        "BottomSheet",
        "Slice",
        "Prompt",
        "Pairing",
        "Chooser",
        "Toast",
        "Floating"
    )
    if (dialogKeywords.any { cls.contains(it, ignoreCase = true) }) {
        return true
    }

    // 2. الحزم والمكونات الفرعية لنظام أندرويد المخصصة للنوافذ المنبثقة ومربعات الإعدادات السريعة
    val popupPackages = listOf(
        "com.android.settings.panel",
        "com.android.settings.slices",
        "com.android.settings.bluetooth.BluetoothPairingDialog",
        "com.android.settings.wifi.WifiDialogActivity",
        "com.android.settings.wifi.slice",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )
    if (popupPackages.any { pkg.startsWith(it) || cls.startsWith(it) }) {
        return true
    }

    // 3. التحقق من أبعاد النافذة إذا كانت متوفرة (النوافذ العائمة ومربعات الحوار تشغل أقل من ملء الشاشة)
    if (windowWidth > 0 && windowHeight > 0 && screenWidth > 0 && screenHeight > 0) {
        val isNotFullscreen = (windowWidth < screenWidth * 0.92f) || (windowHeight < screenHeight * 0.85f)
        if (isNotFullscreen) {
            return true
        }
    }

    return false
}

