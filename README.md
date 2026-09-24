# Muraqib: App Usage Tracker & Controller
### مراقب الاستخدام والتحكم في التطبيقات

[![Android CI](https://github.com/yalaahamdy/App-Usage-Tracker-Controller/actions/workflows/android.yml/badge.svg)](https://github.com/yalaahamdy/App-Usage-Tracker-Controller/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/yalaahamdy/App-Usage-Tracker-Controller?color=blue&label=Release)](https://github.com/yalaahamdy/App-Usage-Tracker-Controller/releases)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-informational.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-informational.svg)](https://developer.android.com)
[![Offline First](https://img.shields.io/badge/Privacy-100%25%20Offline-success.svg)](#privacy--security)

تطبيق أندرويد مفتوح المصدر مصمم لمعالجة إدمان الهاتف وتعزيز الإنتاجية عبر تتبع دقيق لاستخدام التطبيقات ومراقبة استهلاك بيانات الإنترنت مع محرك قيود وجدولة متقدم وشاشة حظر فورية عائمة تعمل بنسبة 100% بدون إنترنت حفاظاً على الخصوصية التامة.

An enterprise-grade, offline-first Android application designed to boost digital wellbeing, monitor application usage and network data consumption, and enforce flexible restrictions with scheduling and foreground blocking overlays.

---

## الميزات الرئيسية (Key Features)

### 1. إحصائيات استخدام دقيقة وشاملة (Usage Analytics)
- **مدة الاستخدام الفعلي (Screen Time):** رصد دقيق لوقت نشاط الشاشة الفعلي لكل تطبيق بالساعات والدقائق والثواني بالاعتماد على `UsageStatsManager` و `UsageEvents`.
- **عدد مرات التشغيل (Launch Count):** حساب دقيق لعدد مرات فتح كل تطبيق وتكرار تشغيله.
- **توزيع ساعات اليوم (Hourly Distribution):** رسم بياني تفاعلي يغطي ساعات اليوم الـ 24 مع تحديد ساعة الذروة وأوقات الاستخدام المكثف.
- **فترات زمنية مرنة ومقارنة ذكية:** فلاتر جاهزة (اليوم، أمس، الأسبوع، الشهر، أو فترة مخصصة) مع إظهار الفروقات ومعدل التغير مقارنة بالفترة السابقة.

### 2. مراقبة استهلاك بيانات الإنترنت (Data Consumption Monitoring)
- **فصل دقيق للشبكات:** مراقبة حجم البيانات المستهلكة عبر شبكات الهاتف المحمول (Mobile Data) وشبكات الواي فاي (Wi-Fi).
- **تصنيف حركة البيانات:** إحصائيات منفصلة للرفع (Upload) والتحميل (Download) لكل تطبيق بالاعتماد على `NetworkStatsManager`.
- **تحديد التطبيقات المستهلكة للباقة:** كشف فوري للتطبيقات التي تستهلك سعات الإنترنت في الخلفية والواجهة.

### 3. محرك تقييد وجدولة التطبيقات الذكي (Restriction Engine)
- **حد الاستخدام الزمني (Usage Limit):** تعيين حد أقصى للاستخدام اليومي أو الأسبوعي لكل تطبيق أو لمجموعة تطبيقات معاً، مع تجدد تلقائي للرصيد عند بداية كل دورة زمنية.
- **جدول أوقات الاستخدام (Usage Schedules):** حظر التطبيق خارج الساعات المصرح بها، مع دعم فترات متعددة في نفس اليوم وتخصيص أيام الأسبوع (مثل عطلة نهاية الأسبوع أو أيام العمل).
- **الحظر الكلي (Total Block):** إمكانية إيقاف تشغيل تطبيقات معينة بشكل كامل ومستمر.
- **دمج القيود:** القدرة على تفعيل حد زمني وجدول زمني معاً، حيث يتم حظر التطبيق إذا تحقق أي من الشرطين.

### 4. نظام التخطي المؤقت المعزول والمحمي (Secure Temporary Bypass)
- **فترات مرنة:** إمكانية تخطي الحظر مؤقتاً لمرة واحدة لفترة تتراوح بين دقيقة واحدة وحتى 5 ساعات (300 دقيقة).
- **حماية برمز المرور (PIN Protection):** اشتراط إدخال رمز المرور السري للتطبيق لتأكيد التخطي ومنع التلاعب، مع لوحة مفاتيح رقمية مدمجة وشاشة تحقق تفاعلية.
- **عزل التخطي لتطبيقات المجموعات (Group Bypass Isolation):** عند وضع قيد على مجموعة تطبيقات، فإن تخطي تطبيق واحد لا يفتح بقية تطبيقات المجموعة بل يظل كل تطبيق معزولاً ومحكوماً بقوانينه بدقة.
- **إعادة الحظر التلقائي:** فور انتهاء الدقائق المحددة للتخطي، يتم إعادة تفعيل الحظر على التطبيق فورياً دون الحاجة لتدخل المستخدم.

### 5. شاشة الحظر العائمة الفائقة (Full-Screen Blocking Overlay)
- **تغطية فورية ومانعة:** نافذة عائمة بنظام `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` ترصد فتح التطبيق المحظور عبر خدمة إمكانية الوصول (`MuraqibAccessibilityService`) وتغطيه بالكامل وتمنع التفاعل معه.
- **شاشة حظر احتياطية (`BlockActivity`):** طبقة أمان ثانية تعمل عبر خدمة الفحص الدوري (`AppBlockerService`) لضمان عدم تجاوز القيود تحت أي ظرف.
- **استعادة الحالة التلقائية (Boot Persistence):** إعادة تفعيل جميع القيود واستئناف خدمات الحظر تلقائياً بمجرد إعادة تشغيل الهاتف عبر `BootReceiver`.

---

## الأمان والخصوصية (Privacy & Security)

- **بدون إذن إنترنت (Zero Internet Permission):** التطبيق لا يطلب ولا يحتوي على إذن الوصول للإنترنت (`android.permission.INTERNET`). كافة البيانات والتحليلات تتم وتُحفظ محلياً 100% على جهازك.
- **تشفير رمز المرور وسؤال الأمان:** تشفير محلي يعتمد على خوارزمية SHA-256 مع تمليح عشوائي (Salted SHA-256)، ولا يتم حفظ الرمز الحقيقي في أي مكان.
- **حماية من التخمين (Brute-Force Protection):** إغلاق مؤقت تصاعدي بعد تكرار إدخال رمز المرور بشكل خاطئ.
- **حماية التعديل والحذف:** لا يمكن تغيير القيود أو تعديلها أو حذفها دون إدخال رمز المرور السري.

---

## البنية المعمارية والتقنيات (Architecture & Tech Stack)

المشروع مبني باتباع أفضل الممارسات الهندسية الرسمية لنظام أندرويد:

```text
com.example.muraqib/
├── data/
│   ├── models/            # Data entities & restriction models
│   └── repository/        # UsageStats, NetworkStats, Security & Restrictions repositories
├── service/
│   ├── MuraqibAccessibilityService.kt   # Real-time foreground app detection
│   ├── AppBlockerService.kt            # Periodic background blocker service
│   ├── BlockOverlayManager.kt          # Fullscreen blocking overlay window
│   └── BootReceiver.kt                 # Device reboot persistence handler
├── ui/
│   ├── dashboard/         # Usage overview, hourly charts & period filters
│   ├── network/           # Mobile & Wi-Fi data consumption tracker
│   ├── restrictions/      # App restriction manager, cards & add/edit dialogs
│   ├── security/          # PIN creation, verification & lock screens
│   ├── theme/             # Material 3 colors, typography & RTL shapes
│   └── Navigation.kt      # Jetpack Compose Navigation graph
└── MainActivity.kt        # App entry point with PIN verification lifecycle
```

- **اللغة:** Kotlin
- **واجهة المستخدم:** Jetpack Compose + Material 3 (دعم كامل لاتجاه RTL وبدون أي رموز تعبيرية)
- **المعمارية:** Clean Architecture / MVVM مع Kotlin Coroutines و StateFlow
- **الخدمات الخلفية:** Android AccessibilityService و Foreground Service و BroadcastReceiver
- **جلب البيانات:** `UsageStatsManager`, `UsageEvents`, `NetworkStatsManager`

---

## متطلبات النظام والأذونات (Requirements & Permissions)

### متطلبات التشغيل
- نظام أندرويد 8.0 (API Level 26) أو أحدث.

### الأذونات المطلوبة وسببها
1. **الوصول لبيانات الاستخدام (`PACKAGE_USAGE_STATS`):** لقراءة وقت الشاشة وعدد مرات تشغيل التطبيقات واستهلاك شبكة الإنترنت.
2. **الظهور فوق التطبيقات الأخرى (`SYSTEM_ALERT_WINDOW`):** لعرض نافذة الحظر الفورية وتغطية التطبيقات المقيدة عند فتحها.
3. **خدمة إمكانية الوصول (`BIND_ACCESSIBILITY_SERVICE`):** للرصد الفوري واللحظي للتطبيق النشط في الواجهة وتطبيق الحظر دون أي تأخير.
4. **التشغيل عند بدء التشغيل (`RECEIVE_BOOT_COMPLETED`):** لإعادة تشغيل محرك الحظر والقيود تلقائياً فور إعادة تشغيل الجهاز.

---

## التثبيت والتحميل (Download & Installation)

### تحميل حزمة التطبيق الجاهزة (Pre-built APK)
يمكنك تحميل أحدث نسخة مستقرة ومبنية وجاهزة للتثبيت مباشرة من صفحة الإصدارات:
- **[صفحة الإصدارات على GitHub (GitHub Releases)](https://github.com/yalaahamdy/App-Usage-Tracker-Controller/releases)**
- اسم الملف: `muraqib-v1.0.0.apk`
- التجزئة الرقمية للتحقق (SHA-256):
  ```text
  9A10B0DD0FB567AEC3DAC53BC235B48747986BEEDE1B192B2922CC0E269D6C43
  ```

### التثبيت عبر ADB
```bash
adb install -r muraqib-v1.0.0.apk
```

---

## البناء من المصدر (Build from Source)

لبناء المشروع محلياً على جهازك:

```bash
# 1. استنساخ المستودع
git clone https://github.com/yalaahamdy/App-Usage-Tracker-Controller.git
cd App-Usage-Tracker-Controller

# 2. تشغيل اختبارات الوحدة الآلية
./gradlew test

# 3. بناء نسخة الـ Debug
./gradlew assembleDebug

# ملف الـ APK الناتج ستجده في المسار التالي:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## المساهمة في المشروع (Contributing)

نرحب بكافة المساهمات والاقتراحات. يرجى قراءة [دليل المساهمة (CONTRIBUTING.md)](CONTRIBUTING.md) للتعرف على معايير الكود، تنسيق الرسائل، وآلية تقديم طلبات السحب (Pull Requests).

---

## الترخيص (License)

هذا المشروع مرخص بموجب رخصة **Apache License 2.0**. للمزيد من التفاصيل، يرجى الاطلاع على ملف [LICENSE](LICENSE).
