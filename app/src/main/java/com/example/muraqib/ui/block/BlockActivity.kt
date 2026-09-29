package com.example.muraqib.ui.block

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.data.repository.SecurityRepository
import com.example.muraqib.ui.lock.NumericKeypad
import com.example.muraqib.ui.lock.PinDotsIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.muraqib.MainActivity
import com.example.muraqib.data.repository.AppInfoManager
import com.example.muraqib.theme.ErrorRed
import com.example.muraqib.theme.MuraqibTheme
import com.example.muraqib.theme.WarningOrange
import com.example.muraqib.ui.components.AppIcon

/**
 * شاشة الحظر التنبيهية الراقية التي تظهر عند محاولة فتح تطبيق مقيد
 */
class BlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_NEXT_AVAILABLE = "extra_next_available"
        const val EXTRA_CONSUMED_MINUTES = "extra_consumed_minutes"
        const val EXTRA_ALLOWED_MINUTES = "extra_allowed_minutes"

        fun createIntent(
            context: Context,
            packageName: String,
            appName: String,
            reason: String,
            nextAvailable: String?,
            consumedMinutes: Int,
            allowedMinutes: Int
        ): Intent {
            return Intent(context, BlockActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_REASON, reason)
                putExtra(EXTRA_NEXT_AVAILABLE, nextAvailable)
                putExtra(EXTRA_CONSUMED_MINUTES, consumedMinutes)
                putExtra(EXTRA_ALLOWED_MINUTES, allowedMinutes)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (powerManager?.isInteractive == false || keyguardManager?.isKeyguardLocked == true) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
            return
        }

        try {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        } catch (e: Exception) {}

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "هذا التطبيق"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "التطبيق غير متاح حاليًا"
        val nextAvailable = intent.getStringExtra(EXTRA_NEXT_AVAILABLE)
        val consumedMinutes = intent.getIntExtra(EXTRA_CONSUMED_MINUTES, 0)
        val allowedMinutes = intent.getIntExtra(EXTRA_ALLOWED_MINUTES, 0)

        setContent {
            MuraqibTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BlockScreenContent(
                        packageName = packageName,
                        appName = appName,
                        reason = reason,
                        nextAvailable = nextAvailable,
                        consumedMinutes = consumedMinutes,
                        allowedMinutes = allowedMinutes,
                        onGoHome = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                            finish()
                        },
                        onOpenMuraqib = {
                            val muraqibIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(muraqibIntent)
                            finish()
                        },
                        onBypass = { durationMinutes ->
                            val repo = AppRestrictionsRepository.getInstance(this)
                            repo.setTemporaryBypass(packageName, durationMinutes)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (powerManager?.isInteractive == false || keyguardManager?.isKeyguardLocked == true) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

/**
 * مراحل نافذة تخطي الحظر المؤقت
 */
enum class BypassDialogStep {
    SELECT_DURATION,
    ENTER_PIN
}

@Composable
fun BlockScreenContent(
    packageName: String,
    appName: String,
    reason: String,
    nextAvailable: String?,
    consumedMinutes: Int,
    allowedMinutes: Int,
    onGoHome: () -> Unit,
    onOpenMuraqib: () -> Unit,
    onBypass: (Int) -> Unit = {}
) {
    // اعتراض زر الرجوع لمنع العودة للتطبيق المحظور خلف النافذة
    BackHandler {
        onGoHome()
    }

    var showBypassDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val securityRepository = remember { SecurityRepository(context) }
    val appInfoManager = remember { AppInfoManager(context) }
    val appIcon = remember(packageName) { appInfoManager.getAppIcon(packageName) }

    // حاوية لكامل الشاشة ذات خلفية معتمة تظهر التطبيق المحظور خلفها
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // النقر على الخلفية المعتمة يغلق النافذة ويعود للشاشة الرئيسية
                onGoHome()
            }
            .padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // منع تمرير النقرات من جسم الكرت للخلفية
                },
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // شارة الحظر
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(WarningOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "وقت مستقطع ومحمي",
                    style = MaterialTheme.typography.titleMedium,
                    color = WarningOrange,
                    fontWeight = FontWeight.Bold
                )

                // أيقونة واسم التطبيق المحظور والسبب
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIcon(
                        icon = appIcon,
                        appName = appName,
                        modifier = Modifier.size(60.dp)
                    )

                    Text(
                        text = appName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // بطاقة موعد الإتاحة القادم
                if (!nextAvailable.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "سيكون التطبيق متاحًا مجددًا:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = nextAvailable,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // أزرار الإجراء
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onGoHome,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "العودة للشاشة الرئيسية", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showBypassDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = WarningOrange
                        )
                    ) {
                        Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "تخطي الحظر مؤقتًا", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenMuraqib,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "إدارة القيود في مراقب الاستخدام")
                    }
                }
            }
        }

        // نافذة اختيار مدة التخطي المؤقت مع التحقق الإلزامي برمز مرور التطبيق
        if (showBypassDialog) {
            var bypassStep by remember { mutableStateOf(BypassDialogStep.SELECT_DURATION) }
            var selectedMinutes by remember { mutableStateOf(15) }
            var enteredPin by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf<String?>(null) }
            val presets = remember {
                listOf(
                    1 to "1 د",
                    5 to "5 د",
                    15 to "15 د",
                    30 to "30 د",
                    60 to "1 س",
                    120 to "2 س",
                    180 to "3 س",
                    300 to "5 س"
                )
            }
            val formattedDuration = remember(selectedMinutes) {
                AppRestrictionsRepository.formatBypassDuration(selectedMinutes)
            }

            LaunchedEffect(enteredPin) {
                if (enteredPin.length == 4) {
                    val isCorrect = securityRepository.verifyPin(enteredPin)
                    if (isCorrect) {
                        pinError = null
                        showBypassDialog = false
                        onBypass(selectedMinutes)
                    } else {
                        val remaining = securityRepository.getRemainingLockoutSeconds()
                        pinError = if (remaining > 0) {
                            "تم تجاوز المحاولات. انتظر $remaining ثانية"
                        } else {
                            "رمز المرور غير صحيح"
                        }
                        enteredPin = ""
                    }
                }
            }

            AlertDialog(
                onDismissRequest = {
                    showBypassDialog = false
                    bypassStep = BypassDialogStep.SELECT_DURATION
                    enteredPin = ""
                    pinError = null
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (bypassStep == BypassDialogStep.SELECT_DURATION) Icons.Default.HourglassTop else Icons.Default.Lock,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (bypassStep == BypassDialogStep.SELECT_DURATION) "تخطي الحظر مؤقتًا" else "تأكيد برمز المرور",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    if (bypassStep == BypassDialogStep.SELECT_DURATION) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "اختر مدة السماح باستخدام $appName (من دقيقة إلى 5 ساعات)، ولن يتم تفعيل التخطي إلا بإدخال رمز مرور التطبيق:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            // شارة عرض المدة الحالية بتنسيق بارز وفاخر
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = WarningOrange.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "المدة المحددة: $formattedDuration",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            // سلايدر السحب التفاعلي من 1 إلى 300 دقيقة
                            Slider(
                                value = selectedMinutes.toFloat(),
                                onValueChange = { selectedMinutes = it.toInt().coerceIn(1, 300) },
                                valueRange = 1f..300f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // أزرار الضبط الدقيق (-15, -1, +1, +15)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    -15 to "-15د",
                                    -1 to "-1د",
                                    1 to "+1د",
                                    15 to "+15د"
                                ).forEach { (delta, label) ->
                                    OutlinedButton(
                                        onClick = {
                                            selectedMinutes = (selectedMinutes + delta).coerceIn(1, 300)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // الخيارات السريعة الشائعة
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(presets) { (mins, label) ->
                                    val isSelected = selectedMinutes == mins
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedMinutes = mins },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            // تنبيه الأمان
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "محمي برمز المرور: سيطلب منك رمز مرور التطبيق للتأكيد لمنع التجاوز غير المصرح به.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // شاشة التحقق من رمز المرور
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "أدخل رمز مرور التطبيق المكون من 4 أرقام لتأكيد تخطي الحظر لـ $appName لمدة $formattedDuration:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            // مؤشر الدوائر لرمز المرور
                            PinDotsIndicator(
                                pinLength = enteredPin.length,
                                maxPinLength = 4,
                                hasError = pinError != null
                            )

                            if (pinError != null) {
                                Text(
                                    text = pinError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // لوحة المفاتيح الرقمية
                            NumericKeypad(
                                onDigitClick = { digit ->
                                    if (enteredPin.length < 4) {
                                        pinError = null
                                        enteredPin += digit
                                    }
                                },
                                onBackspaceClick = {
                                    if (enteredPin.isNotEmpty()) {
                                        enteredPin = enteredPin.dropLast(1)
                                        pinError = null
                                    }
                                },
                                onClearClick = {
                                    enteredPin = ""
                                    pinError = null
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    if (bypassStep == BypassDialogStep.SELECT_DURATION) {
                        Button(
                            onClick = {
                                if (securityRepository.isPinConfigured()) {
                                    bypassStep = BypassDialogStep.ENTER_PIN
                                    enteredPin = ""
                                    pinError = null
                                } else {
                                    showBypassDialog = false
                                    onBypass(selectedMinutes)
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("المتابعة لإدخال رمز المرور", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    if (bypassStep == BypassDialogStep.SELECT_DURATION) {
                        TextButton(onClick = { showBypassDialog = false }) {
                            Text("إلغاء")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                bypassStep = BypassDialogStep.SELECT_DURATION
                                enteredPin = ""
                                pinError = null
                            }) {
                                Text("تعديل المدة")
                            }
                            TextButton(onClick = {
                                showBypassDialog = false
                                bypassStep = BypassDialogStep.SELECT_DURATION
                                enteredPin = ""
                                pinError = null
                            }) {
                                Text("إلغاء")
                            }
                        }
                    }
                }
            )
        }
    }
}
