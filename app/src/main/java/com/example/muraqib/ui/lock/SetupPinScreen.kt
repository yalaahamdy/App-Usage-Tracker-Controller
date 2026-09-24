package com.example.muraqib.ui.lock

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.muraqib.data.repository.SecurityRepository
import com.example.muraqib.theme.ErrorRed

/**
 * شاشة إعداد رمز حماية التطبيق لأول مرة وسؤال استعادة الأمان
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupPinScreen(
    securityRepository: SecurityRepository,
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(1) } // 1: إدخال الرمز, 2: تأكيد الرمز, 3: سؤال الأمان
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val predefinedQuestions = listOf(
        "ما هو اسم مدينتك المفضلة؟",
        "ما هو اسم أول مدرسة التحقت بها؟",
        "ما هو اسم حيوانك الأليف الأول؟",
        "ما هو طعامك المفضل؟"
    )

    var selectedQuestion by remember { mutableStateOf(predefinedQuestions[0]) }
    var customQuestion by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }
    var securityAnswer by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

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
            Spacer(modifier = Modifier.height(16.dp))

            // رأس الشاشة ومؤشر الخطوة
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (step < 3) Icons.Default.Lock else Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = when (step) {
                        1 -> "إنشاء رمز المرور"
                        2 -> "تأكيد رمز المرور"
                        else -> "حماية استعادة الرمز"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = when (step) {
                        1 -> "أنشئ رمزًا سريًا لحماية بياناتك من المتطفلين (4 أرقام)"
                        2 -> "أعد إدخال الرمز للتأكد من صحته"
                        else -> "في حال نسيت رمزك، ستتمكن من استعادته بهذا السؤال دون أي إنترنت"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // محتوى الخطوة الحالية
            when (step) {
                1 -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PinDotsIndicator(
                            pinLength = firstPin.length,
                            maxPinLength = 4,
                            hasError = errorMsg != null
                        )
                        if (errorMsg != null) {
                            Text(text = errorMsg!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    NumericKeypad(
                        onDigitClick = { digit ->
                            if (firstPin.length < 4) {
                                firstPin += digit
                                errorMsg = null
                                if (firstPin.length == 4) {
                                    step = 2
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
                        },
                        onClearClick = { firstPin = "" }
                    )
                }

                2 -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PinDotsIndicator(
                            pinLength = confirmPin.length,
                            maxPinLength = 4,
                            hasError = errorMsg != null
                        )
                        if (errorMsg != null) {
                            Text(text = errorMsg!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    NumericKeypad(
                        onDigitClick = { digit ->
                            if (confirmPin.length < 4) {
                                confirmPin += digit
                                errorMsg = null
                                if (confirmPin.length == 4) {
                                    if (confirmPin == firstPin) {
                                        step = 3
                                    } else {
                                        errorMsg = "الرمزان غير متطابقين، حاول ثانية"
                                        confirmPin = ""
                                    }
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                        },
                        onClearClick = { confirmPin = "" }
                    )
                }

                3 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = isDropdownExpanded,
                            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = if (isCustomSelected) "سؤال مخصص..." else selectedQuestion,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                                label = { Text("سؤال الأمان") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )

                            ExposedDropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false }
                            ) {
                                predefinedQuestions.forEach { q ->
                                    DropdownMenuItem(
                                        text = { Text(q) },
                                        onClick = {
                                            selectedQuestion = q
                                            isCustomSelected = false
                                            isDropdownExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("كتابة سؤال مخصص...") },
                                    onClick = {
                                        isCustomSelected = true
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        if (isCustomSelected) {
                            OutlinedTextField(
                                value = customQuestion,
                                onValueChange = { customQuestion = it; errorMsg = null },
                                label = { Text("اكتب سؤال الأمان الخاص بك") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = securityAnswer,
                            onValueChange = { securityAnswer = it; errorMsg = null },
                            label = { Text("الإجابة السرية") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (errorMsg != null) {
                            Text(text = errorMsg!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = {
                                val questionToSave = if (isCustomSelected) customQuestion.trim() else selectedQuestion
                                if (questionToSave.isBlank()) {
                                    errorMsg = "يرجى كتابة سؤال الأمان"
                                    return@Button
                                }
                                if (securityAnswer.trim().isBlank()) {
                                    errorMsg = "يرجى إدخال إجابة السؤال"
                                    return@Button
                                }

                                val saved = securityRepository.setupSecurity(
                                    pin = firstPin,
                                    question = questionToSave,
                                    answer = securityAnswer
                                )

                                if (saved) {
                                    onSetupComplete()
                                } else {
                                    errorMsg = "حدث خطأ أثناء حفظ البيانات"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("إتمام الإعداد وبدء الاستخدام", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // زر العودة للخلف إن لم نكن في الخطوة الأولى
            if (step > 1) {
                TextButton(
                    onClick = {
                        if (step == 2) {
                            step = 1
                            firstPin = ""
                            confirmPin = ""
                        } else if (step == 3) {
                            step = 2
                            confirmPin = ""
                        }
                        errorMsg = null
                    }
                ) {
                    Text("رجوع للخطوة السابقة")
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
