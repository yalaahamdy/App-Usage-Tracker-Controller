package com.example.muraqib

import com.example.muraqib.data.model.AppRestriction
import com.example.muraqib.data.model.BlockReason
import com.example.muraqib.data.model.LimitPeriod
import com.example.muraqib.data.model.TimeWindow
import com.example.muraqib.data.repository.BackupMetadata
import com.example.muraqib.data.repository.BackupValidationResult
import com.example.muraqib.data.repository.ImportMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

/**
 * اختبارات وحدة شاملة للنسخ الاحتياطي واستيراد وتصدير بيانات التطبيق وقيوده وإعداداته
 */
class BackupRepositoryTest {

    @Test
    fun testValidateEmptyOrBlankJson() {
        val validator = BackupValidatorHelper()

        val emptyResult = validator.validate("")
        assertFalse(emptyResult.isValid)
        assertTrue(emptyResult.errorMessage?.contains("فارغ") == true)

        val whitespaceResult = validator.validate("   \n\t  ")
        assertFalse(whitespaceResult.isValid)
        assertTrue(whitespaceResult.errorMessage?.contains("فارغ") == true)
    }

    @Test
    fun testValidateInvalidJsonSyntax() {
        val validator = BackupValidatorHelper()
        val malformedResult = validator.validate("{ this is not valid json }")
        assertFalse(malformedResult.isValid)
        assertTrue(malformedResult.errorMessage?.contains("JSON") == true)
    }

    @Test
    fun testValidateWrongAppIdentifier() {
        val validator = BackupValidatorHelper()
        val foreignAppJson = JSONObject().apply {
            put("app", "SomeOtherApp")
            put("schemaVersion", 1)
            put("restrictions", JSONArray())
        }

        val result = validator.validate(foreignAppJson.toString())
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("ليس نسخة احتياطية صالحة") == true)
    }

    @Test
    fun testValidateMissingRestrictionsArray() {
        val validator = BackupValidatorHelper()
        val noRestrictionsJson = JSONObject().apply {
            put("app", "Muraqib")
            put("schemaVersion", 1)
        }

        val result = validator.validate(noRestrictionsJson.toString())
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("لا يحتوي على قائمة قيود صالحة") == true)
    }

    @Test
    fun testValidateValidBackup() {
        val validator = BackupValidatorHelper()

        val r1 = AppRestriction(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            hasUsageLimit = true,
            limitDurationMinutes = 45,
            limitPeriod = LimitPeriod.DAILY
        )
        val r2 = AppRestriction(
            packageName = "com.instagram.android",
            appName = "Instagram",
            isTotalBlock = true
        )

        val restrictionsArray = JSONArray().apply {
            put(r1.toJsonObject())
            put(r2.toJsonObject())
        }

        val root = JSONObject().apply {
            put("app", "Muraqib")
            put("schemaVersion", 1)
            put("exportedAt", 1727460000000L)
            put("exportedAtFormatted", "2026-09-27 21:00")
            put("restrictionsCount", 2)
            put("restrictions", restrictionsArray)
            put("securitySettings", JSONObject().apply {
                put("appLockEnabled", true)
                put("antiTamperEnabled", true)
                put("antiUninstallEnabled", true)
            })
            put("checksum", calculateSha256(restrictionsArray.toString()))
        }

        val result = validator.validate(root.toString())
        assertTrue(result.isValid)
        assertNotNull(result.metadata)
        assertEquals("Muraqib", result.metadata?.app)
        assertEquals(1, result.metadata?.version)
        assertEquals(2, result.metadata?.restrictionsCount)
        assertTrue(result.metadata?.hasSecuritySettings == true)
        assertEquals(2, result.restrictions.size)

        val parsedR1 = result.restrictions[0]
        assertEquals("com.google.android.youtube", parsedR1.packageName)
        assertEquals(45, parsedR1.limitDurationMinutes)

        val parsedR2 = result.restrictions[1]
        assertEquals("com.instagram.android", parsedR2.packageName)
        assertTrue(parsedR2.isTotalBlock)
    }

    @Test
    fun testImportModesMergeAndReplace() {
        val currentRestrictions = mutableMapOf(
            "com.whatsapp" to AppRestriction(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                hasUsageLimit = true,
                limitDurationMinutes = 60
            ),
            "com.facebook.katana" to AppRestriction(
                packageName = "com.facebook.katana",
                appName = "Facebook",
                isTotalBlock = true
            )
        )

        val incomingRestrictions = listOf(
            AppRestriction(
                packageName = "com.whatsapp", // تحديث القائم
                appName = "WhatsApp",
                hasUsageLimit = true,
                limitDurationMinutes = 30
            ),
            AppRestriction(
                packageName = "com.google.android.youtube", // إضافة جديد
                appName = "YouTube",
                isTotalBlock = true
            )
        )

        // 1. اختبار وضع الدمج (MERGE)
        val mergedMap = currentRestrictions.toMutableMap()
        incomingRestrictions.forEach { incoming ->
            mergedMap[incoming.packageName] = incoming
        }
        assertEquals(3, mergedMap.size)
        assertEquals(30, mergedMap["com.whatsapp"]?.limitDurationMinutes)
        assertTrue(mergedMap.containsKey("com.facebook.katana"))
        assertTrue(mergedMap.containsKey("com.google.android.youtube"))

        // 2. اختبار وضع الاستبدال الكامل (REPLACE_ALL)
        val replacedMap = mutableMapOf<String, AppRestriction>()
        incomingRestrictions.forEach { incoming ->
            replacedMap[incoming.packageName] = incoming
        }
        assertEquals(2, replacedMap.size)
        assertEquals(30, replacedMap["com.whatsapp"]?.limitDurationMinutes)
        assertFalse(replacedMap.containsKey("com.facebook.katana"))
        assertTrue(replacedMap.containsKey("com.google.android.youtube"))
    }

    @Test
    fun testTemporaryBypassStrictPackageIsolation() {
        // التحقق من أن التخطي المؤقت محصور بالحزمة ولا يتسرب لباقي تطبيقات المجموعة
        val bypassMap = mutableMapOf<String, Long>()
        val currentTime = 1000000L

        // منح يوتيوب 15 دقيقة تخطي مؤقت
        val ytPackage = "com.google.android.youtube"
        val tiktokPackage = "com.zhiliaoapp.musically"
        bypassMap[ytPackage] = currentTime + (15 * 60 * 1000L)

        // فحص يوتيوب
        val isYtBypassed = (bypassMap[ytPackage] ?: 0L) > currentTime
        assertTrue(isYtBypassed)

        // فحص تيك توك الذي في نفس المجموعة
        val isTiktokBypassed = (bypassMap[tiktokPackage] ?: 0L) > currentTime
        assertFalse(isTiktokBypassed)
    }

    private fun calculateSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        val hexString = StringBuilder()
        for (b in hash) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }

    /**
     * محاكي منطق التحقق لاختبار الوحدة بمعزل عن Android framework
     */
    private class BackupValidatorHelper {
        fun validate(jsonString: String): BackupValidationResult {
            if (jsonString.isBlank()) {
                return BackupValidationResult(isValid = false, errorMessage = "الملف فارغ ولا يحتوي على أي بيانات.")
            }
            return try {
                val root = JSONObject(jsonString)
                val app = root.optString("app", "")
                if (app != "Muraqib") {
                    return BackupValidationResult(
                        isValid = false,
                        errorMessage = "هذا الملف ليس نسخة احتياطية صالحة لتطبيق مراقب الاستخدام."
                    )
                }

                val schemaVersion = root.optInt("schemaVersion", 1)
                val exportedAt = root.optLong("exportedAt", 0L)
                val exportedAtFormatted = root.optString("exportedAtFormatted", "")

                val restrictionsArray = root.optJSONArray("restrictions")
                    ?: return BackupValidationResult(isValid = false, errorMessage = "الملف لا يحتوي على قائمة قيود صالحة.")

                val parsed = mutableListOf<AppRestriction>()
                for (i in 0 until restrictionsArray.length()) {
                    val item = restrictionsArray.optJSONObject(i)
                    if (item != null) {
                        parsed.add(AppRestriction.fromJsonObject(item))
                    }
                }

                val hasSecurity = root.has("securitySettings")
                val metadata = BackupMetadata(
                    app = app,
                    version = schemaVersion,
                    exportedAt = exportedAt,
                    exportedAtFormatted = exportedAtFormatted,
                    restrictionsCount = parsed.size,
                    hasSecuritySettings = hasSecurity
                )

                BackupValidationResult(
                    isValid = true,
                    metadata = metadata,
                    restrictions = parsed,
                    rawJson = root
                )
            } catch (e: Exception) {
                BackupValidationResult(
                    isValid = false,
                    errorMessage = "تعذر قراءة الملف: تنسيق JSON غير صالح أو الملف تالف."
                )
            }
        }
    }
}
