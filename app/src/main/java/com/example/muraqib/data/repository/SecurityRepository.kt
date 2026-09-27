package com.example.muraqib.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * مستودع الأمان المسؤول عن تشفير رمز PIN وحماية التطبيق والتعامل مع حالات النسيان
 */
class SecurityRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // حالة فتح القفل في الجلسة الحالية
    @Volatile
    private var isSessionUnlocked: Boolean = false
    private var lastBackgroundTimestamp: Long = 0L

    companion object {
        private const val PREFS_NAME = "muraqib_security_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_IS_SETUP = "is_pin_setup"
        private const val KEY_SECURITY_QUESTION = "security_question"
        private const val KEY_SECURITY_ANSWER_HASH = "security_answer_hash"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_LOCK_TIMEOUT_SECONDS = "lock_timeout_seconds"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_ANTI_TAMPER_ENABLED = "anti_tamper_enabled"
        private const val KEY_ANTI_UNINSTALL_ENABLED = "anti_uninstall_enabled"
        private const val KEY_SAFE_MODE_PROTECTION_ENABLED = "safe_mode_protection_enabled"
        private const val KEY_SAFE_MODE_VIOLATION_DETECTED = "safe_mode_violation_detected"
        private const val KEY_SAFE_MODE_VIOLATION_MESSAGE = "safe_mode_violation_message"
        private const val KEY_LAST_HEARTBEAT_TIMESTAMP = "last_heartbeat_timestamp"
        private const val KEY_LAST_BOOT_TIME = "last_boot_time"

        private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
        private const val LOCKOUT_DURATION_MS = 30_000L // 30 ثانية
    }

    /**
     * التحقق مما إذا كانت ميزة الحماية الذاتية (منع فتح إعدادات التطبيق أو الإيقاف الإجباري ومسح البيانات) مفعلة
     */
    fun isAntiTamperEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANTI_TAMPER_ENABLED, true)
    }

    fun setAntiTamperEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANTI_TAMPER_ENABLED, enabled).apply()
    }

    /**
     * التحقق مما إذا كانت ميزة حماية منع إلغاء التثبيت مفعلة
     */
    fun isAntiUninstallEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANTI_UNINSTALL_ENABLED, true)
    }

    fun setAntiUninstallEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANTI_UNINSTALL_ENABLED, enabled).apply()
    }

    /**
     * التحقق مما إذا كانت ميزة الحماية ضد تجاوز القيود عبر الوضع الآمن مفعلة
     */
    fun isSafeModeProtectionEnabled(): Boolean {
        return prefs.getBoolean(KEY_SAFE_MODE_PROTECTION_ENABLED, true)
    }

    fun setSafeModeProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAFE_MODE_PROTECTION_ENABLED, enabled).apply()
    }

    /**
     * تسجيل نبضة نشاط زمنية مستمرة لمراقبة الفترات التي قد يتعطل فيها التطبيق
     */
    fun recordHeartbeat() {
        prefs.edit().putLong(KEY_LAST_HEARTBEAT_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    fun getLastHeartbeatTimestamp(): Long {
        return prefs.getLong(KEY_LAST_HEARTBEAT_TIMESTAMP, 0L)
    }

    fun recordBootTime(bootTime: Long) {
        prefs.edit().putLong(KEY_LAST_BOOT_TIME, bootTime).apply()
    }

    fun getLastBootTime(): Long {
        return prefs.getLong(KEY_LAST_BOOT_TIME, 0L)
    }

    /**
     * تسجيل رصد مخالفة أمنية مرتبطة بالوضع الآمن أو تجاوز القيود أثناء توقف الحماية
     */
    fun recordSafeModeViolation(message: String) {
        prefs.edit()
            .putBoolean(KEY_SAFE_MODE_VIOLATION_DETECTED, true)
            .putString(KEY_SAFE_MODE_VIOLATION_MESSAGE, message)
            .apply()
        // قفل التطبيق فوراً عند اكتشاف مخالفة أمنية
        isSessionUnlocked = false
    }

    fun isSafeModeViolationDetected(): Boolean {
        return prefs.getBoolean(KEY_SAFE_MODE_VIOLATION_DETECTED, false)
    }

    fun getSafeModeViolationMessage(): String? {
        return prefs.getString(KEY_SAFE_MODE_VIOLATION_MESSAGE, null)
    }

    fun clearSafeModeViolation() {
        prefs.edit()
            .putBoolean(KEY_SAFE_MODE_VIOLATION_DETECTED, false)
            .remove(KEY_SAFE_MODE_VIOLATION_MESSAGE)
            .apply()
    }

    /**
     * التحقق مما إذا كان المستخدم قد قام بإنشاء رمز حماية مسبقًا
     */
    fun isPinConfigured(): Boolean {
        return prefs.getBoolean(KEY_IS_SETUP, false) && prefs.getString(KEY_PIN_HASH, null) != null
    }

    /**
     * حفظ الرمز وسؤال الأمان لأول مرة
     */
    fun setupSecurity(pin: String, question: String, answer: String): Boolean {
        if (pin.length < 4 || question.isBlank() || answer.isBlank()) return false

        val salt = generateSalt()
        val pinHash = hashWithSalt(pin, salt)
        val answerHash = hashWithSalt(normalizeAnswer(answer), salt)

        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, pinHash)
            .putString(KEY_SECURITY_QUESTION, question.trim())
            .putString(KEY_SECURITY_ANSWER_HASH, answerHash)
            .putBoolean(KEY_IS_SETUP, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()

        isSessionUnlocked = true
        return true
    }

    /**
     * التحقق من صحة الرمز المدخل
     */
    fun verifyPin(enteredPin: String): Boolean {
        if (isCurrentlyLockedOut()) return false

        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false

        val enteredHash = hashWithSalt(enteredPin, salt)
        val isMatch = enteredHash == savedHash

        if (isMatch) {
            resetFailedAttempts()
            isSessionUnlocked = true
        } else {
            recordFailedAttempt()
        }

        return isMatch
    }

    /**
     * استرجاع سؤال الأمان
     */
    fun getSecurityQuestion(): String {
        return prefs.getString(KEY_SECURITY_QUESTION, "ما هو اسم مدينتك المفضلة؟")
            ?: "ما هو اسم مدينتك المفضلة؟"
    }

    /**
     * التحقق من إجابة سؤال الأمان
     */
    fun verifySecurityAnswer(enteredAnswer: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val savedAnswerHash = prefs.getString(KEY_SECURITY_ANSWER_HASH, null) ?: return false

        val normalized = normalizeAnswer(enteredAnswer)
        val enteredHash = hashWithSalt(normalized, salt)

        return enteredHash == savedAnswerHash
    }

    /**
     * إعادة تعيين الرمز بعد الإجابة الصحيحة على سؤال الأمان
     */
    fun resetPinWithAnswer(enteredAnswer: String, newPin: String): Boolean {
        if (!verifySecurityAnswer(enteredAnswer)) return false
        if (newPin.length < 4) return false

        val salt = prefs.getString(KEY_PIN_SALT, null) ?: generateSalt()
        val newPinHash = hashWithSalt(newPin, salt)

        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, newPinHash)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()

        isSessionUnlocked = true
        return true
    }

    /**
     * تغيير الرمز عند معرفة الرمز الحالي
     */
    fun changePin(currentPin: String, newPin: String): Boolean {
        if (!verifyPin(currentPin)) return false
        if (newPin.length < 4) return false

        val salt = prefs.getString(KEY_PIN_SALT, null) ?: generateSalt()
        val newPinHash = hashWithSalt(newPin, salt)

        prefs.edit()
            .putString(KEY_PIN_HASH, newPinHash)
            .apply()

        return true
    }

    /**
     * تحديث سؤال الأمان والإجابة
     */
    fun updateSecurityQuestion(pin: String, newQuestion: String, newAnswer: String): Boolean {
        if (!verifyPin(pin)) return false
        if (newQuestion.isBlank() || newAnswer.isBlank()) return false

        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val answerHash = hashWithSalt(normalizeAnswer(newAnswer), salt)

        prefs.edit()
            .putString(KEY_SECURITY_QUESTION, newQuestion.trim())
            .putString(KEY_SECURITY_ANSWER_HASH, answerHash)
            .apply()

        return true
    }

    /**
     * التحقق مما إذا كان قفل التطبيق مفعلًا في الإعدادات
     */
    fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, true)
    }

    /**
     * تفعيل أو تعطيل قفل التطبيق برمز مرور
     */
    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        if (!enabled) {
            isSessionUnlocked = true
        }
    }

    /**
     * التحقق مما إذا كان التطبيق مقفلاً في الجلسة الحالية
     */
    fun isAppLocked(): Boolean {
        if (!isPinConfigured()) return false
        if (!isAppLockEnabled()) return false
        return !isSessionUnlocked
    }

    fun markUnlocked() {
        isSessionUnlocked = true
    }

    fun markLocked() {
        isSessionUnlocked = false
    }

    /**
     * استدعاء عند انتقال التطبيق إلى الخلفية
     */
    fun onAppBackgrounded() {
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    /**
     * استدعاء عند عودة التطبيق للواجهة، يحدد إن كان يجب إعادة القفل
     */
    fun onAppForegrounded(): Boolean {
        if (!isPinConfigured() || !isAppLockEnabled()) return false

        val timeoutSeconds = prefs.getInt(KEY_LOCK_TIMEOUT_SECONDS, 0)
        val elapsedMs = System.currentTimeMillis() - lastBackgroundTimestamp

        if (timeoutSeconds == 0 || elapsedMs > (timeoutSeconds * 1000L)) {
            isSessionUnlocked = false
        }
        return !isSessionUnlocked
    }

    fun getRemainingLockoutSeconds(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val diff = lockoutUntil - System.currentTimeMillis()
        return if (diff > 0) (diff / 1000) + 1 else 0L
    }

    private fun isCurrentlyLockedOut(): Boolean {
        return getRemainingLockoutSeconds() > 0
    }

    private fun recordFailedAttempt() {
        val current = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, current)

        if (current >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            editor.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_DURATION_MS)
        }
        editor.apply()
    }

    private fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    private fun normalizeAnswer(answer: String): String {
        return answer.trim().lowercase(Locale.getDefault())
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashWithSalt(input: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val combined = "$salt:$input"
        val digest = md.digest(combined.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
