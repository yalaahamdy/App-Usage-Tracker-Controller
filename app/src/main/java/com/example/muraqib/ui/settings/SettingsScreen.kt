package com.example.muraqib.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muraqib.R
import com.example.muraqib.data.repository.SecurityRepository
import com.example.muraqib.receiver.MuraqibDeviceAdminReceiver
import com.example.muraqib.theme.ErrorRed
import com.example.muraqib.theme.SuccessGreen

/**
 * شاشة إعدادات الأمان وحماية التطبيق
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    securityRepository: SecurityRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showUpdateQuestionDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isAppLockEnabled by remember { mutableStateOf(securityRepository.isAppLockEnabled()) }
    var isAntiTamperEnabled by remember { mutableStateOf(securityRepository.isAntiTamperEnabled()) }
    var isAntiUninstallEnabled by remember { mutableStateOf(securityRepository.isAntiUninstallEnabled()) }
    var isDeviceAdminActive by remember {
        mutableStateOf(MuraqibDeviceAdminReceiver.isDeviceAdminActive(context))
    }

    var pendingPinAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPinTitle by remember { mutableStateOf("") }
    var pendingPinDescription by remember { mutableStateOf("") }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val active = MuraqibDeviceAdminReceiver.isDeviceAdminActive(context)
        isDeviceAdminActive = active
        if (active) {
            feedbackMessage = "تم تفعيل صلاحية مسؤول الجهاز وحماية التطبيق بنجاح"
        }
    }

    fun requestPinConfirmation(title: String, description: String, onConfirmed: () -> Unit) {
        if (securityRepository.isPinConfigured()) {
            pendingPinTitle = title
            pendingPinDescription = description
            pendingPinAction = onConfirmed
        } else {
            onConfirmed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "الإعدادات والأمان", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // إشعار نجاح الإجراء
            if (feedbackMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = feedbackMessage!!,
                        color = SuccessGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Text(
                text = "إعدادات حماية الدخول",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // مفتاح تفعيل/تعطيل قفل التطبيق
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "قفل التطبيق برمز المرور",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isAppLockEnabled) "يُطلب الرمز عند فتح التطبيق لحماية خصوصيتك" else "تم إيقاف طلب الرمز عند فتح التطبيق",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isAppLockEnabled,
                            onCheckedChange = { checked ->
                                if (!checked) {
                                    requestPinConfirmation(
                                        title = "تأكيد إيقاف قفل التطبيق",
                                        description = "يرجى إدخال رمز المرور لتأكيد إيقاف قفل التطبيق:"
                                    ) {
                                        securityRepository.setAppLockEnabled(false)
                                        isAppLockEnabled = false
                                        feedbackMessage = "تم إيقاف قفل التطبيق"
                                    }
                                } else {
                                    securityRepository.setAppLockEnabled(true)
                                    isAppLockEnabled = true
                                    feedbackMessage = "تم تفعيل قفل التطبيق برمز المرور"
                                }
                            }
                        )
                    }

                    if (isAppLockEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SettingsOptionItem(
                            icon = Icons.Default.Key,
                            title = "تغيير رمز المرور (PIN)",
                            subtitle = "تحديث رمز المرور الحالي برمز جديد",
                            onClick = { showChangePinDialog = true }
                        )

                        SettingsOptionItem(
                            icon = Icons.Default.QuestionAnswer,
                            title = "تحديث سؤال الأمان",
                            subtitle = "تغيير السؤال أو الإجابة المعتمدة لاستعادة الرمز",
                            onClick = { showUpdateQuestionDialog = true }
                        )
                    }
                }
            }

            // الحماية المتقدمة ومنع تعطيل التطبيق
            Text(
                text = "الحماية المتقدمة ومنع تعطيل التطبيق",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // صلاحية مسؤول الجهاز (منع إلغاء التثبيت عبر النظام)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مسؤول الجهاز (منع إزالة التطبيق)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isDeviceAdminActive)
                                    "مفعل - يمنع نظام أندرويد إلغاء تثبيت التطبيق نهائياً"
                                else
                                    "معطل - انقر لتفعيل الحماية لمنع حذف التطبيق عبر النظام",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isDeviceAdminActive,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(
                                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                            MuraqibDeviceAdminReceiver.getComponentName(context)
                                        )
                                        putExtra(
                                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                            context.getString(R.string.device_admin_description)
                                        )
                                    }
                                    deviceAdminLauncher.launch(intent)
                                } else {
                                    requestPinConfirmation(
                                        title = "تأكيد إلغاء مسؤول الجهاز",
                                        description = "إلغاء مسؤول الجهاز سيلغي الحماية ضد حذف التطبيق. أدخل رمز المرور للمتابعة:"
                                    ) {
                                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                                        dpm?.removeActiveAdmin(MuraqibDeviceAdminReceiver.getComponentName(context))
                                        isDeviceAdminActive = false
                                        feedbackMessage = "تم إلغاء تفعيل مسؤول الجهاز"
                                    }
                                }
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // منع إيقاف التطبيق ومسح البيانات (Anti-Tamper)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "منع الإيقاف الإجباري ومسح البيانات",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isAntiTamperEnabled)
                                    "مفعل - يمنع فتح صفحة التطبيق في الضبط لتفادي إيقافه أو مسح بياناته"
                                else
                                    "معطل - صفحة تفاصيل التطبيق في الضبط متاحة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isAntiTamperEnabled,
                            onCheckedChange = { checked ->
                                if (!checked) {
                                    requestPinConfirmation(
                                        title = "تأكيد تعطيل درع الحماية",
                                        description = "تعطيل هذا الدرع يسمح بالدخول لصفحة الضبط وإيقاف التطبيق. أدخل رمز المرور:"
                                    ) {
                                        securityRepository.setAntiTamperEnabled(false)
                                        isAntiTamperEnabled = false
                                        feedbackMessage = "تم تعطيل حماية منع الإيقاف ومسح البيانات"
                                    }
                                } else {
                                    securityRepository.setAntiTamperEnabled(true)
                                    isAntiTamperEnabled = true
                                    feedbackMessage = "تم تفعيل حماية منع الإيقاف الإجباري ومسح البيانات"
                                }
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // الاعتراض الذكي لمثبت الحزم (Anti-Uninstall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الاعتراض الذكي لبرامج إزالة التثبيت",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isAntiUninstallEnabled)
                                    "مفعل - يعترض أي محاولة لفتح نافذة حذف التطبيق ويطلب رمز المرور"
                                else
                                    "معطل - لن يتم اعتراض شاشات حذف التطبيقات",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isAntiUninstallEnabled,
                            onCheckedChange = { checked ->
                                if (!checked) {
                                    requestPinConfirmation(
                                        title = "تأكيد تعطيل الاعتراض الذكي",
                                        description = "يرجى إدخال رمز المرور لتأكيد تعطيل اعتراض برامج إلغاء التثبيت:"
                                    ) {
                                        securityRepository.setAntiUninstallEnabled(false)
                                        isAntiUninstallEnabled = false
                                        feedbackMessage = "تم تعطيل الاعتراض الذكي لإلغاء التثبيت"
                                    }
                                } else {
                                    securityRepository.setAntiUninstallEnabled(true)
                                    isAntiUninstallEnabled = true
                                    feedbackMessage = "تم تفعيل الاعتراض الذكي لإلغاء التثبيت"
                                }
                            }
                        )
                    }
                }
            }

            Text(
                text = "عن التطبيق",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = "مراقب الاستخدام", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "الإصدار: 1.0 (مستقر)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "تطبيق صُمم ليمنحك تجربة استخدام بسيطة، أنيقة، واحترافية لمتابعة وتحليل استهلاك تطبيقات هاتفك بأعلى درجات الخصوصية والأمان.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // نافذة تأكيد رمز المرور قبل التعديل الأمني
    if (pendingPinAction != null) {
        ConfirmActionPinDialog(
            title = pendingPinTitle,
            description = pendingPinDescription,
            securityRepository = securityRepository,
            onDismiss = { pendingPinAction = null },
            onConfirmed = {
                val action = pendingPinAction
                pendingPinAction = null
                action?.invoke()
            }
        )
    }

    // نافذة تغيير الرمز
    if (showChangePinDialog) {
        ChangePinDialog(
            securityRepository = securityRepository,
            onDismiss = { showChangePinDialog = false },
            onSuccess = {
                showChangePinDialog = false
                feedbackMessage = "تم تغيير رمز المرور بنجاح"
            }
        )
    }

    // نافذة تحديث سؤال الأمان
    if (showUpdateQuestionDialog) {
        UpdateQuestionDialog(
            securityRepository = securityRepository,
            onDismiss = { showUpdateQuestionDialog = false },
            onSuccess = {
                showUpdateQuestionDialog = false
                feedbackMessage = "تم تحديث سؤال الأمان بنجاح"
            }
        )
    }
}

@Composable
private fun SettingsOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ChangePinDialog(
    securityRepository: SecurityRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "تغيير رمز المرور", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 4) currentPin = it },
                    label = { Text("رمز المرور الحالي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4) newPin = it },
                    label = { Text("رمز المرور الجديد (4 أرقام)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4) confirmPin = it },
                    label = { Text("تأكيد الرمز الجديد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(text = error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length != 4) {
                        error = "يجب أن يتكون الرمز من 4 أرقام"
                    } else if (newPin != confirmPin) {
                        error = "الرمز الجديد وتأكيده غير متطابقين"
                    } else {
                        val success = securityRepository.changePin(currentPin, newPin)
                        if (success) {
                            onSuccess()
                        } else {
                            error = "رمز المرور الحالي غير صحيح"
                        }
                    }
                }
            ) {
                Text("حفظ التغيير")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun UpdateQuestionDialog(
    securityRepository: SecurityRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var newQuestion by remember { mutableStateOf("") }
    var newAnswer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "تحديث سؤال الأمان", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    label = { Text("رمز المرور الحالي للتأكيد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newQuestion,
                    onValueChange = { newQuestion = it },
                    label = { Text("سؤال الأمان الجديد") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newAnswer,
                    onValueChange = { newAnswer = it },
                    label = { Text("الإجابة السرية الجديدة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(text = error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newQuestion.isBlank() || newAnswer.isBlank()) {
                        error = "يرجى تعبئة السؤال والإجابة"
                    } else {
                        val success = securityRepository.updateSecurityQuestion(pin, newQuestion, newAnswer)
                        if (success) {
                            onSuccess()
                        } else {
                            error = "رمز المرور غير صحيح"
                        }
                    }
                }
            ) {
                Text("تحديث")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ConfirmActionPinDialog(
    title: String,
    description: String,
    securityRepository: SecurityRepository,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    label = { Text("رمز المرور (PIN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(text = error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length != 4) {
                        error = "يجب إدخال رمز المرور المكون من 4 أرقام"
                    } else if (securityRepository.verifyPin(pin)) {
                        onConfirmed()
                    } else {
                        error = "رمز المرور غير صحيح"
                    }
                }
            ) {
                Text("تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
