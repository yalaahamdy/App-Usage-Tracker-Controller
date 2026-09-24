package com.example.muraqib.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.muraqib.MainActivity
import com.example.muraqib.data.repository.AppInfoManager
import com.example.muraqib.data.repository.AppRestrictionsRepository
import com.example.muraqib.data.repository.SecurityRepository

/**
 * مدير النافذة العائمة فوق التطبيقات المحظورة
 * يدعم TYPE_ACCESSIBILITY_OVERLAY الذي يعمل فوراً وبدقة 100% دون الحاجة لأي إذن خاص
 * ويدعم TYPE_APPLICATION_OVERLAY في حال توفر صلاحية الظهور فوق التطبيقات
 */
object BlockOverlayManager {

    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var currentShowingPackage: String? = null
        private set

    val isShowing: Boolean
        get() = overlayRootView != null

    /**
     * عرض بطاقة الحظر العائمة فوق التطبيق المحظور مباشرة
     */
    fun show(
        context: Context,
        packageName: String,
        appName: String,
        reason: String,
        nextAvailable: String?,
        windowBounds: Rect? = null,
        onHomeAction: (() -> Unit)? = null,
        onBypassAction: ((Int) -> Unit)? = null
    ) {
        val isAccessibility = context is AccessibilityService

        // إذا لم تكن خدمة وصول، نتحقق من إذن الظهور
        if (!isAccessibility && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }

        mainHandler.post {
            try {
                if (isShowing && currentShowingPackage == packageName) {
                    return@post
                }

                dismissInternal()

                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                windowManager = wm

                val appContext = context.applicationContext
                val appInfoManager = AppInfoManager(appContext)
                val appIcon = appInfoManager.getAppIcon(packageName)

                // 1. الحاوية الرئيسية معتمة بنسبة 100% لتغطية التطبيق بالكامل
                val rootView = FrameLayout(context).apply {
                    setBackgroundColor(Color.parseColor("#0A0E17")) // تعتيم كامل 100% يحجب التطبيق المحظور خلفه
                    isClickable = true
                    isFocusable = true
                }

                // 2. كرت الحظر المركزي الأنيق
                val cardLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val pad = dpToPx(context, 24)
                    setPadding(pad, pad, pad, pad)

                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 24).toFloat()
                        setColor(Color.parseColor("#1B2232"))
                        setStroke(dpToPx(context, 1), Color.parseColor("#2D3748"))
                    }

                    isClickable = true
                    isFocusable = true
                }

                val cardParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                    val marginH = dpToPx(context, 28)
                    setMargins(marginH, 0, marginH, 0)
                }

                // شارة الحظر العلوية
                val badgeContainer = FrameLayout(context).apply {
                    val size = dpToPx(context, 60)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = dpToPx(context, 12)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.argb(38, 245, 158, 11))
                    }
                }

                val badgeIcon = ImageView(context).apply {
                    val iconSize = dpToPx(context, 32)
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                        gravity = Gravity.CENTER
                    }
                    setImageResource(android.R.drawable.ic_lock_idle_lock)
                    setColorFilter(Color.parseColor("#F59E0B"))
                }
                badgeContainer.addView(badgeIcon)
                cardLayout.addView(badgeContainer)

                // عنوان التنبيه بدون إيموجي
                val titleView = TextView(context).apply {
                    text = "وقت مستقطع ومحمي"
                    setTextColor(Color.parseColor("#F59E0B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(context, 16))
                }
                cardLayout.addView(titleView)

                // أيقونة التطبيق المحظور
                val appIconView = ImageView(context).apply {
                    val iconSize = dpToPx(context, 58)
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = dpToPx(context, 10)
                    }
                    if (appIcon != null) {
                        setImageDrawable(appIcon)
                    } else {
                        setImageResource(android.R.drawable.sym_def_app_icon)
                    }
                }
                cardLayout.addView(appIconView)

                // اسم التطبيق المحظور
                val appNameView = TextView(context).apply {
                    text = appName
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(context, 6))
                }
                cardLayout.addView(appNameView)

                // سبب الحظر
                val reasonView = TextView(context).apply {
                    text = reason
                    setTextColor(Color.parseColor("#A0AEC0"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(context, 16))
                }
                cardLayout.addView(reasonView)

                // صندوق موعد الإتاحة القادم إن وجد
                if (!nextAvailable.isNullOrBlank()) {
                    val nextLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        val nextPad = dpToPx(context, 10)
                        setPadding(nextPad, nextPad, nextPad, nextPad)
                        background = GradientDrawable().apply {
                            cornerRadius = dpToPx(context, 12).toFloat()
                            setColor(Color.parseColor("#151D2A"))
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = dpToPx(context, 18)
                        }
                    }

                    val nextLabel = TextView(context).apply {
                        text = "سيكون التطبيق متاحًا مجددًا:"
                        setTextColor(Color.parseColor("#718096"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        gravity = Gravity.CENTER
                    }
                    val nextTime = TextView(context).apply {
                        text = nextAvailable
                        setTextColor(Color.parseColor("#63B3ED"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setPadding(0, dpToPx(context, 2), 0, 0)
                    }
                    nextLayout.addView(nextLabel)
                    nextLayout.addView(nextTime)
                    cardLayout.addView(nextLayout)
                }

                // زر العودة للشاشة الرئيسية
                val homeButton = Button(context).apply {
                    text = "العودة للشاشة الرئيسية"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 14).toFloat()
                        setColor(Color.parseColor("#3182CE"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(context, 50)
                    ).apply {
                        bottomMargin = dpToPx(context, 10)
                    }
                    setOnClickListener {
                        dismissInternal()
                        if (onHomeAction != null) {
                            onHomeAction.invoke()
                        } else {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(homeIntent)
                        }
                    }
                }
                cardLayout.addView(homeButton)

                // زر وميزة تخطي الحظر المؤقت المحمية برمز المرور
                val bypassContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(context, 10)
                    }
                }

                // زر إظهار خيارات التخطي المؤقت
                val bypassBtn = Button(context).apply {
                    text = "تخطي الحظر مؤقتًا"
                    setTextColor(Color.parseColor("#F59E0B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 14).toFloat()
                        setColor(Color.parseColor("#231E15"))
                        setStroke(dpToPx(context, 1), Color.parseColor("#B45309"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(context, 48)
                    )
                }

                // لوحة التخطي المنظمة (الخطوة 1: ضبط المدة، الخطوة 2: تأكيد رمز المرور)
                val bypassPanel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    visibility = View.GONE
                    val pPad = dpToPx(context, 16)
                    setPadding(pPad, pPad, pPad, pPad)
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 18).toFloat()
                        setColor(Color.parseColor("#151D2A"))
                        setStroke(dpToPx(context, 1), Color.parseColor("#2D3748"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                var selectedMinutes = 15

                // -------------------------------------------------------------
                // الخطوة الأولى: تحديد مدة التخطي (Duration Step)
                // -------------------------------------------------------------
                val durationStepLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val panelTitle = TextView(context).apply {
                    text = "تخطي الحظر مؤقتًا"
                    setTextColor(Color.parseColor("#F59E0B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(context, 4))
                }
                durationStepLayout.addView(panelTitle)

                val panelSubtitle = TextView(context).apply {
                    text = "حدد مدة السماح المؤقت للتطبيق:"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(context, 10))
                }
                durationStepLayout.addView(panelSubtitle)

                // بطاقة عرض المدة المحددة بشكل بارز وأنيق
                val durationBadge = TextView(context).apply {
                    setTextColor(Color.parseColor("#FBBF24"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    val vPad = dpToPx(context, 6)
                    val hPad = dpToPx(context, 16)
                    setPadding(hPad, vPad, hPad, vPad)
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 12).toFloat()
                        setColor(Color.parseColor("#241D12"))
                        setStroke(dpToPx(context, 1), Color.parseColor("#D97706"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(context, 10)
                    }
                }
                durationStepLayout.addView(durationBadge)

                // سلايدر السحب السريع (1 إلى 300 دقيقة)
                val seekBar = SeekBar(context).apply {
                    max = 299 // 0..299 -> 1..300
                    progress = 14 // 15 دقيقة افتراضيًا
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(context, 32)
                    ).apply {
                        bottomMargin = dpToPx(context, 8)
                    }
                }
                durationStepLayout.addView(seekBar)

                // صف أزرار الضبط الدقيق (+ / -)
                val stepperLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(context, 10)
                    }
                }

                // شريط التمرير الأفقي للخيارات الشائعة
                val scrollPresets = HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(context, 10)
                    }
                }
                val presetsLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                scrollPresets.addView(presetsLayout)

                val presetItems = listOf(
                    1 to "1 د",
                    5 to "5 د",
                    15 to "15 د",
                    30 to "30 د",
                    60 to "1 س",
                    120 to "2 س",
                    180 to "3 س",
                    300 to "5 س"
                )
                val presetButtons = mutableListOf<Pair<Int, Button>>()

                fun syncDurationUI(minutes: Int) {
                    selectedMinutes = minutes.coerceIn(1, 300)
                    val formatted = AppRestrictionsRepository.formatBypassDuration(selectedMinutes)
                    durationBadge.text = "المدة المحددة: $formatted"

                    presetButtons.forEach { (mins, btn) ->
                        val isSel = (mins == selectedMinutes)
                        btn.background = GradientDrawable().apply {
                            cornerRadius = dpToPx(context, 8).toFloat()
                            setColor(if (isSel) Color.parseColor("#2563EB") else Color.parseColor("#1E293B"))
                            if (isSel) {
                                setStroke(dpToPx(context, 1), Color.parseColor("#60A5FA"))
                            }
                        }
                        btn.setTextColor(if (isSel) Color.WHITE else Color.parseColor("#94A3B8"))
                    }
                }

                // أزرار الضبط الدقيق
                val stepDeltas = listOf(-15 to "-15د", -1 to "-1د", 1 to "+1د", 15 to "+15د")
                stepDeltas.forEach { (delta, label) ->
                    val stepBtn = Button(context).apply {
                        text = label
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.parseColor("#E2E8F0"))
                        background = GradientDrawable().apply {
                            cornerRadius = dpToPx(context, 8).toFloat()
                            setColor(Color.parseColor("#1E293B"))
                            setStroke(dpToPx(context, 1), Color.parseColor("#334155"))
                        }
                        layoutParams = LinearLayout.LayoutParams(0, dpToPx(context, 34), 1f).apply {
                            val m = dpToPx(context, 3)
                            setMargins(m, 0, m, 0)
                        }
                        setOnClickListener {
                            val newMins = (selectedMinutes + delta).coerceIn(1, 300)
                            seekBar.progress = newMins - 1
                            syncDurationUI(newMins)
                        }
                    }
                    stepperLayout.addView(stepBtn)
                }
                durationStepLayout.addView(stepperLayout)

                // تعبئة أزرار الخيارات السريعة
                presetItems.forEach { (mins, label) ->
                    val btn = Button(context).apply {
                        text = label
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                        typeface = Typeface.DEFAULT_BOLD
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            dpToPx(context, 34)
                        ).apply {
                            val m = dpToPx(context, 3)
                            setMargins(m, 0, m, 0)
                        }
                        setOnClickListener {
                            seekBar.progress = mins - 1
                            syncDurationUI(mins)
                        }
                    }
                    presetButtons.add(mins to btn)
                    presetsLayout.addView(btn)
                }
                durationStepLayout.addView(scrollPresets)

                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            syncDurationUI(progress + 1)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })

                syncDurationUI(15)

                // تنبيه الأمان
                val securityNotice = TextView(context).apply {
                    text = "محمي برمز المرور: سيطلب منك رمز مرور التطبيق في الخطوة التالية لتأكيد التخطي."
                    setTextColor(Color.parseColor("#64748B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    gravity = Gravity.CENTER
                    val nPad = dpToPx(context, 6)
                    setPadding(nPad, nPad, nPad, nPad)
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 8).toFloat()
                        setColor(Color.parseColor("#0F172A"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(context, 10)
                    }
                }
                durationStepLayout.addView(securityNotice)

                // صف أزرار الخطوة الأولى (إلغاء ومتابعة)
                val durationActionsLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(context, 44)
                    )
                }

                val cancelDurationBtn = Button(context).apply {
                    text = "إلغاء"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 10).toFloat()
                        setColor(Color.TRANSPARENT)
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setOnClickListener {
                        bypassPanel.visibility = View.GONE
                        bypassBtn.visibility = View.VISIBLE
                    }
                }

                val proceedToPinBtn = Button(context).apply {
                    text = "المتابعة لإدخال رمز المرور"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 10).toFloat()
                        setColor(Color.parseColor("#D97706"))
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)
                }

                durationActionsLayout.addView(cancelDurationBtn)
                durationActionsLayout.addView(proceedToPinBtn)
                durationStepLayout.addView(durationActionsLayout)

                // -------------------------------------------------------------
                // الخطوة الثانية: التحقق من رمز مرور التطبيق (PIN Verification Step)
                // -------------------------------------------------------------
                val pinStepLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    visibility = View.GONE
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val pinTitle = TextView(context).apply {
                    text = "تأكيد برمز المرور"
                    setTextColor(Color.parseColor("#F59E0B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(context, 4))
                }
                pinStepLayout.addView(pinTitle)

                val pinSubtitle = TextView(context).apply {
                    setTextColor(Color.parseColor("#CBD5E0"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(context, 10))
                }
                pinStepLayout.addView(pinSubtitle)

                // مؤشر الدوائر الأربعة لرمز المرور
                val dotsContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(context, 8)
                    }
                }
                val dotViews = (0..3).map {
                    View(context).apply {
                        val dotSize = dpToPx(context, 14)
                        layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                            val m = dpToPx(context, 8)
                            setMargins(m, 0, m, 0)
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#334155"))
                        }
                    }
                }
                dotViews.forEach { dotsContainer.addView(it) }
                pinStepLayout.addView(dotsContainer)

                // نص رسالة الخطأ
                val pinErrorView = TextView(context).apply {
                    setTextColor(Color.parseColor("#EF4444"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    visibility = View.GONE
                    setPadding(0, 0, 0, dpToPx(context, 6))
                }
                pinStepLayout.addView(pinErrorView)

                // لوحة المفاتيح الرقمية المدمجة
                val keypadContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(context, 10)
                    }
                }

                var enteredPin = ""
                var isCheckingPin = false

                fun updateDots(isError: Boolean = false) {
                    dotViews.forEachIndexed { index, dot ->
                        val gd = dot.background as? GradientDrawable ?: GradientDrawable().apply { shape = GradientDrawable.OVAL }
                        if (isError) {
                            gd.setColor(Color.parseColor("#EF4444"))
                        } else if (index < enteredPin.length) {
                            gd.setColor(Color.parseColor("#F59E0B"))
                        } else {
                            gd.setColor(Color.parseColor("#334155"))
                        }
                        dot.background = gd
                    }
                }

                fun onDigitEntered(digit: String) {
                    if (isCheckingPin || enteredPin.length >= 4) return
                    pinErrorView.visibility = View.GONE
                    enteredPin += digit
                    updateDots()

                    if (enteredPin.length == 4) {
                        isCheckingPin = true
                        val secRepo = SecurityRepository(context)
                        if (secRepo.verifyPin(enteredPin)) {
                            // تم التحقق بنجاح وتأكيد رمز المرور
                            val repo = AppRestrictionsRepository.getInstance(context)
                            repo.setTemporaryBypass(packageName, selectedMinutes)
                            val formatted = AppRestrictionsRepository.formatBypassDuration(selectedMinutes)
                            Toast.makeText(
                                context,
                                "تم تفعيل تخطي الحظر لـ $appName لمدة $formatted",
                                Toast.LENGTH_SHORT
                            ).show()
                            dismissInternal()
                            onBypassAction?.invoke(selectedMinutes)
                        } else {
                            // رمز المرور غير صحيح
                            val remainingSec = secRepo.getRemainingLockoutSeconds()
                            val errorMsg = if (remainingSec > 0) {
                                "تم تجاوز المحاولات. انتظر $remainingSec ثانية"
                            } else {
                                "رمز المرور غير صحيح"
                            }
                            pinErrorView.text = errorMsg
                            pinErrorView.visibility = View.VISIBLE
                            updateDots(isError = true)

                            mainHandler.postDelayed({
                                enteredPin = ""
                                updateDots(isError = false)
                                isCheckingPin = false
                            }, 450)
                        }
                    }
                }

                fun onBackspace() {
                    if (!isCheckingPin && enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                        pinErrorView.visibility = View.GONE
                        updateDots()
                    }
                }

                fun onClear() {
                    if (!isCheckingPin) {
                        enteredPin = ""
                        pinErrorView.visibility = View.GONE
                        updateDots()
                    }
                }

                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("مسح", "0", "حذف")
                )

                keypadRows.forEach { rowKeys ->
                    val rowLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = dpToPx(context, 6)
                        }
                    }
                    rowKeys.forEach { key ->
                        val keyBtn = Button(context).apply {
                            text = key
                            setTextColor(if (key == "مسح" || key == "حذف") Color.parseColor("#94A3B8") else Color.WHITE)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (key.length > 1) 12f else 16f)
                            typeface = Typeface.DEFAULT_BOLD
                            background = GradientDrawable().apply {
                                cornerRadius = dpToPx(context, 10).toFloat()
                                setColor(Color.parseColor("#1E293B"))
                                setStroke(dpToPx(context, 1), Color.parseColor("#334155"))
                            }
                            layoutParams = LinearLayout.LayoutParams(0, dpToPx(context, 40), 1f).apply {
                                val m = dpToPx(context, 3)
                                setMargins(m, 0, m, 0)
                            }
                            setOnClickListener {
                                when (key) {
                                    "حذف" -> onBackspace()
                                    "مسح" -> onClear()
                                    else -> onDigitEntered(key)
                                }
                            }
                        }
                        rowLayout.addView(keyBtn)
                    }
                    keypadContainer.addView(rowLayout)
                }
                pinStepLayout.addView(keypadContainer)

                // صف أزرار التنقل في شاشة التحقق
                val pinNavLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(context, 40)
                    )
                }

                val backToDurationBtn = Button(context).apply {
                    text = "تعديل المدة"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 8).toFloat()
                        setColor(Color.TRANSPARENT)
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setOnClickListener {
                        pinStepLayout.visibility = View.GONE
                        durationStepLayout.visibility = View.VISIBLE
                        enteredPin = ""
                        pinErrorView.visibility = View.GONE
                        updateDots()
                    }
                }

                val cancelPinBtn = Button(context).apply {
                    text = "إلغاء"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 8).toFloat()
                        setColor(Color.TRANSPARENT)
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setOnClickListener {
                        bypassPanel.visibility = View.GONE
                        bypassBtn.visibility = View.VISIBLE
                        pinStepLayout.visibility = View.GONE
                        durationStepLayout.visibility = View.VISIBLE
                        enteredPin = ""
                        pinErrorView.visibility = View.GONE
                        updateDots()
                    }
                }

                pinNavLayout.addView(backToDurationBtn)
                pinNavLayout.addView(cancelPinBtn)
                pinStepLayout.addView(pinNavLayout)

                // ربط الانتقال من المدة إلى رمز المرور
                proceedToPinBtn.setOnClickListener {
                    val secRepo = SecurityRepository(context)
                    if (!secRepo.isPinConfigured()) {
                        // في حال لم يقم بتعيين رمز مرور في التطبيق، التخطي مباشرة
                        val repo = AppRestrictionsRepository.getInstance(context)
                        repo.setTemporaryBypass(packageName, selectedMinutes)
                        val formatted = AppRestrictionsRepository.formatBypassDuration(selectedMinutes)
                        Toast.makeText(
                            context,
                            "تم تفعيل تخطي الحظر لـ $appName لمدة $formatted",
                            Toast.LENGTH_SHORT
                        ).show()
                        dismissInternal()
                        onBypassAction?.invoke(selectedMinutes)
                    } else {
                        // الانتقال لخطوة إدخال رمز المرور وتحديث النص
                        val formatted = AppRestrictionsRepository.formatBypassDuration(selectedMinutes)
                        pinSubtitle.text = "أدخل رمز مرور التطبيق لتأكيد تخطي الحظر لمدة $formatted:"
                        durationStepLayout.visibility = View.GONE
                        pinStepLayout.visibility = View.VISIBLE
                        enteredPin = ""
                        pinErrorView.visibility = View.GONE
                        updateDots()
                    }
                }

                bypassPanel.addView(durationStepLayout)
                bypassPanel.addView(pinStepLayout)

                bypassBtn.setOnClickListener {
                    bypassBtn.visibility = View.GONE
                    bypassPanel.visibility = View.VISIBLE
                    durationStepLayout.visibility = View.VISIBLE
                    pinStepLayout.visibility = View.GONE
                    syncDurationUI(selectedMinutes)
                }

                bypassContainer.addView(bypassBtn)
                bypassContainer.addView(bypassPanel)
                cardLayout.addView(bypassContainer)

                // زر إدارة القيود
                val settingsButton = Button(context).apply {
                    text = "إدارة القيود في مراقب الاستخدام"
                    setTextColor(Color.parseColor("#CBD5E0"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 14).toFloat()
                        setColor(Color.TRANSPARENT)
                        setStroke(dpToPx(context, 1), Color.parseColor("#4A5568"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(context, 46)
                    )
                    setOnClickListener {
                        dismissInternal()
                        val mainIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(mainIntent)
                    }
                }
                cardLayout.addView(settingsButton)

                // النقر على الخلفية المعتمة يغلق النافذة ويأخذ المستخدم للشاشة الرئيسية
                rootView.setOnClickListener {
                    dismissInternal()
                    if (onHomeAction != null) {
                        onHomeAction.invoke()
                    } else {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(homeIntent)
                    }
                }

                // اعتراض زر الرجوع (Back) لمنع تجاوز شاشة الحظر
                rootView.isFocusableInTouchMode = true
                rootView.requestFocus()
                rootView.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        dismissInternal()
                        if (onHomeAction != null) {
                            onHomeAction.invoke()
                        } else {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(homeIntent)
                        }
                        true
                    } else {
                        false
                    }
                }

                // وضع الكرت داخل ScrollView لضمان ظهوره كاملاً دون اقتصاص حتى في الشاشات الصغيرة أو المنقسمة
                val scrollView = ScrollView(context).apply {
                    isFillViewport = true
                    isVerticalScrollBarEnabled = false
                }
                scrollView.addView(cardLayout, cardParams)
                rootView.addView(
                    scrollView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                // اختيار نوع نافذة النظام الأمثل
                val windowType = if (isAccessibility) {
                    // نافذة الوصول: تعمل فورا وتظهر فوق أي تطبيق بدون الحاجة لإذن الظهور الخاص
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                // مطابقة حجم وموقع نافذة التطبيق المفتوح لتغطيته بالكامل
                val params = if (windowBounds != null && windowBounds.width() > 0 && windowBounds.height() > 0) {
                    WindowManager.LayoutParams(
                        windowBounds.width(),
                        windowBounds.height(),
                        windowBounds.left,
                        windowBounds.top,
                        windowType,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                    }
                } else {
                    WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        windowType,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                }

                wm.addView(rootView, params)
                overlayRootView = rootView
                currentShowingPackage = packageName
            } catch (e: Exception) {
                // منع أي انهيار
            }
        }
    }

    /**
     * إغلاق النافذة العائمة
     */
    fun dismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal()
        } else {
            mainHandler.post { dismissInternal() }
        }
    }

    private fun dismissInternal() {
        try {
            overlayRootView?.let { view ->
                windowManager?.removeViewImmediate(view)
            }
        } catch (e: Exception) {
            try {
                overlayRootView?.let { view ->
                    windowManager?.removeView(view)
                }
            } catch (ex: Exception) {}
        } finally {
            overlayRootView = null
            currentShowingPackage = null
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
