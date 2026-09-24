package com.example.muraqib.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muraqib.data.repository.SecurityRepository
import com.example.muraqib.theme.ErrorRed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * شاشة قفل التطبيق وطلب رمز PIN مع إمكانية الاستعادة عند النسيان
 */
@Composable
fun UnlockScreen(
    securityRepository: SecurityRepository,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }

    val shakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val lockoutSeconds = securityRepository.getRemainingLockoutSeconds()

    fun triggerShake() {
        coroutineScope.launch {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 300
                    0f at 0
                    -20f at 50
                    20f at 100
                    -15f at 150
                    15f at 200
                    -5f at 250
                    0f at 300
                }
            )
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            val isCorrect = securityRepository.verifyPin(enteredPin)
            if (isCorrect) {
                errorMessage = null
                onUnlocked()
            } else {
                val remaining = securityRepository.getRemainingLockoutSeconds()
                errorMessage = if (remaining > 0) {
                    "تم تجاوز المحاولات. انتظر $remaining ثانية"
                } else {
                    "رمز المرور غير صحيح"
                }
                triggerShake()
                enteredPin = ""
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // الشعار والعنوان
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "مراقب الاستخدام",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "أدخل رمز المرور الخاص بالتطبيق",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // مؤشر النقاط لرمز المرور
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            ) {
                PinDotsIndicator(
                    pinLength = enteredPin.length,
                    maxPinLength = 4,
                    hasError = errorMessage != null
                )

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        modifier = Modifier.padding(top = 12.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // لوحة المفاتيح الرقمية
            NumericKeypad(
                onDigitClick = { digit ->
                    if (lockoutSeconds == 0L && enteredPin.length < 4) {
                        errorMessage = null
                        enteredPin += digit
                    }
                },
                onBackspaceClick = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                        errorMessage = null
                    }
                },
                onClearClick = {
                    enteredPin = ""
                    errorMessage = null
                }
            )

            // زر نسيت كلمة المرور
            TextButton(
                onClick = { showForgotDialog = true },
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "نسيت رمز المرور؟",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showForgotDialog) {
        ForgotPinDialog(
            securityRepository = securityRepository,
            onDismiss = { showForgotDialog = false },
            onPinResetSuccess = {
                showForgotDialog = false
                onUnlocked()
            }
        )
    }
}

/**
 * مؤشر الدوائر الأنيق لرمز المرور
 */
@Composable
fun PinDotsIndicator(
    pinLength: Int,
    maxPinLength: Int = 4,
    hasError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxPinLength) { index ->
            val isFilled = index < pinLength
            val color = when {
                hasError -> ErrorRed
                isFilled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * لوحة مفاتيح رقمية مريحة وعصرية
 */
@Composable
fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "DEL")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    when (key) {
                        "DEL" -> {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .clickable { onBackspaceClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "حذف",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        "C" -> {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .clickable { onClearClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "مسح",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable { onDigitClick(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * نافذة استعادة رمز المرور عبر سؤال الأمان
 */
@Composable
fun ForgotPinDialog(
    securityRepository: SecurityRepository,
    onDismiss: () -> Unit,
    onPinResetSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1 = الإجابة على السؤال, 2 = كتابة الرمز الجديد
    var answerInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    val question = remember { securityRepository.getSecurityQuestion() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (step == 1) "استعادة رمز المرور" else "تعيين رمز جديد",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == 1) {
                    Text(
                        text = "أجب عن سؤال الأمان لاستعادة الوصول:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = question,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedTextField(
                        value = answerInput,
                        onValueChange = { answerInput = it; dialogError = null },
                        label = { Text("إجابتك السرية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = dialogError != null
                    )
                } else {
                    Text(
                        text = "أدخل الرمز الجديد المكون من 4 أرقام:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 4) newPinInput = it; dialogError = null },
                        label = { Text("رمز المرور الجديد (4 أرقام)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 4) confirmPinInput = it; dialogError = null },
                        label = { Text("تأكيد رمز المرور") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (dialogError != null) {
                    Text(
                        text = dialogError!!,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) {
                        if (securityRepository.verifySecurityAnswer(answerInput)) {
                            dialogError = null
                            step = 2
                        } else {
                            dialogError = "الإجابة غير صحيحة، حاول مجددًا"
                        }
                    } else {
                        if (newPinInput.length != 4) {
                            dialogError = "يجب أن يتكون الرمز من 4 أرقام"
                        } else if (newPinInput != confirmPinInput) {
                            dialogError = "الرمزان غير متطابقين"
                        } else {
                            val success = securityRepository.resetPinWithAnswer(answerInput, newPinInput)
                            if (success) {
                                onPinResetSuccess()
                            } else {
                                dialogError = "حدث خطأ أثناء حفظ الرمز"
                            }
                        }
                    }
                }
            ) {
                Text(if (step == 1) "متابعة" else "تأكيد وحفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
