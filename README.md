# Muraqib: App Usage Tracker & Controller
### مراقب الاستخدام والتحكم في التطبيقات

[![Release](https://img.shields.io/github/v/release/yalaahamdy/App-Usage-Tracker-Controller?color=blue&label=Release)](https://github.com/yalaahamdy/App-Usage-Tracker-Controller/releases)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-informational.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-informational.svg)](https://developer.android.com)
[![Offline First](https://img.shields.io/badge/Privacy-100%25%20Offline-success.svg)](#privacy--security)

تطبيق أندرويد متكامل مفتوح المصدر مصمم لمعالجة إدمان الهاتف وتعزيز الإنتاجية عبر تتبع فائق الدقة لاستخدام التطبيقات ومراقبة استهلاك بيانات الإنترنت (الواي فاي وبيانات الهاتف) مع محرك قيود وجدولة متقدم، دروع حماية ضد الإيقاف وإلغاء التثبيت، اعتراض النوافذ المصغرة (PiP) والشاشات المنقسمة، حماية ضد الالتفاف عبر الوضع الآمن (Safe Mode)، ومحرك نسخ احتياطي مشفر بالكامل يعمل بدون إنترنت بنسبة 100% حفاظاً على الخصوصية التامة.

An enterprise-grade, offline-first Android application designed to boost digital wellbeing, monitor application usage and network data consumption, and enforce flexible restrictions with scheduling, foreground blocking overlays, multi-window & PiP shields, safe mode boot audits, and encrypted backup/restore capabilities.

---

## الميزات الرئيسية (Key Features)

### 1. إحصائيات استخدام دقيقة وشاملة (Usage Analytics)
- **مدة الاستخدام الفعلي (Screen Time):** رصد دقيق لوقت نشاط الشاشة الفعلي لكل تطبيق بالساعات والدقائق والثواني بالاعتماد على `UsageStatsManager` و `UsageEvents`.
- **عدد مرات التشغيل (Launch Count):** حساب دقيق لعدد مرات فتح كل تطبيق وتكرار تشغيله.
- **توزيع ساعات اليوم (Hourly Distribution):** رسم بياني تفاعلي يغطي ساعات اليوم الـ 24 مع تحديد ساعة الذروة وأوقات الاستخدام المكثف.
- **فترات زمنية مرنة ومقارنة ذكية:** فلاتر جاهزة (اليوم، أمس، الأسبوع، الشهر، أو فترة مخصصة) مع إظهار الفروقات ومعدل التغير مقارنة بالفترة السابقة.

### 2. مراقبة استهلاك بيانات الإنترنت (Data Consumption Monitoring)
- **فصل دقيق للشبكات:** مراقبة دقيقة لحجم البيانات المستهلكة عبر شبكات الهاتف المحمول (Mobile Data) وشبكات الواي فاي (Wi-Fi).
- **تصنيف حركة البيانات:** إحصائيات منفصلة للرفع (Upload) والتحميل (Download) لكل تطبيق بالاعتماد على `NetworkStatsManager`.
- **كشف التطبيقات المستهلكة للباقة:** رصد فوري للتطبيقات التي تستهلك سعات الإنترنت في الخلفية والواجهة للمساعدة في ترشيد استهلاك الباقة.

### 3. محرك تقييد وجدولة التطبيقات الذكي (Restriction Engine)
- **حد الاستخدام الزمني (Usage Limit):** تعيين حد أقصى للاستخدام اليومي أو الأسبوعي لكل تطبيق أو لمجموعة تطبيقات معاً، مع تجدد تلقائي للرصيد عند بداية كل دورة زمنية.
- **جدول أوقات الاستخدام (Usage Schedules):** حظر التطبيق خارج الساعات المصرح بها، مع دعم فترات متعددة في نفس اليوم وتخصيص أيام الأسبوع (مثل عطلة نهاية الأسبوع أو أيام العمل).
- **الحظر الكلي (Total Block):** إمكانية إيقاف تشغيل تطبيقات معينة بشكل كامل ومستمر بمجرد فتحها.
- **دمج القيود:** القدرة على تفعيل حد زمني وجدول زمني معاً، حيث يتم حظر التطبيق إذا تحقق أي من الشرطين.

### 4. نظام التخطي المؤقت المعزول والمحمي برمز PIN (Isolated Temporary Bypass)
- **فترات مرنة:** إمكانية تخطي الحظر مؤقتاً لمرة واحدة لفترة تتراوح بين دقيقة واحدة وحتى 5 ساعات (300 دقيقة).
- **حماية برمز المرور (PIN Protection):** اشتراط إدخال رمز المرور السري للتطبيق لتأكيد التخطي ومنع التلاعب، مع لوحة مفاتيح رقمية مدمجة وشاشة تحقق تفاعلية.
- **عزل التخطي لتطبيقات المجموعات (Group Bypass Isolation):** عند وضع قيد على مجموعة تطبيقات، فإن تخطي تطبيق واحد لا يفتح بقية تطبيقات المجموعة، بل يظل كل تطبيق معزولاً ومحكوماً بقوانينه بدقة متناهية.
- **إعادة الحظر التلقائي:** فور انتهاء الدقائق المحددة للتخطي، يتم إعادة تفعيل الحظر على التطبيق فورياً دون الحاجة لتدخل المستخدم.

### 5. حماية النوافذ المصغرة والشاشات المنقسمة (Picture-in-Picture & Split-Screen Shield)
- **اعتراض وضع صورة داخل صورة (PiP Interception):** رصد فوري لأي محاولة لتشغيل مقاطع الفيديو أو الصوت في نافذة مصغرة عائمة للتطبيقات المحظورة، وإغلاق النافذة فوراً عبر استدعاء النظام مع فرض شاشة الحظر الكاملة لحجب المحتوى ومنع الاستماع في الخلفية.
- **عزل أبعاد الشاشات المنقسمة (Split-Screen Window Bounds Isolation):** في وضع الشاشات المتعددة، يتم التعرف بدقة على النصف التابع للتطبيق المقيد وتغطيته بنافذة حظر عائمة مطابقة لإحداثياته، مع استمرار عمل التطبيق المسموح به في النصف الآخر بكل سلاسة دون أي تعارض.
- **ثبات درع الحظر:** منع اختفاء شاشة الحظر عند التفاعل مع التطبيق المسموح في النصف الآخر طالما بقي التطبيق المحظور ظاهراً.

### 6. رصد ومكافحة الإقلاع في الوضع الآمن (Android Safe Mode Protection & Boot Audit)
- **كشف بيئة الوضع الآمن:** فحص مباشر لحالة الوضع الآمن للنظام لمنع التحايل على الحظر عبر إيقاف تطبيقات الطرف الثالث.
- **محرك النبضات الأمنية وتدقيق الإقلاع (Heartbeat Engine & Boot Audit):** تسجيل نبضات نشاط دورية مستمرة، وعند كل عملية إقلاع للنظام يقوم المحرك بمقارنة الطابع الزمني وتدقيق سجلات الأحداث خلال فترة التوقف؛ لاكتشاف ما إذا تم تشغيل أي تطبيقات مقيدة أثناء توقف الحماية أو أثناء الإقلاع في الوضع الآمن.
- **القفل الفوري التلقائي:** عند رصد أي تجاوزات أثناء توقف الحماية، يتم إلغاء فتح الجلسة فوراً وإجبار المستخدم على إدخال رمز المرور الرئيسي، مع تسجيل تفاصيل المخالفة وإشعار المسؤول.

### 7. محرك النسخ الاحتياطي ونقل البيانات (Backup & Restore Engine)
- **تصدير كامل للبيانات والإعدادات:** توليد ملفات JSON مهيكلة تتضمن كافة القيود والمجموعات وإعدادات الأمان مع بصمة تحقق رقمية مشفرة بتجزئة SHA-256 لمنع التلاعب.
- **التوافق مع أحدث أنظمة أندرويد (Storage Access Framework):** حفظ النسخة الاحتياطية في أي مجلد أو سحابة يختارها المستخدم دون الحاجة لأذونات تخزين واسعة.
- **مشاركة آمنة عبر FileProvider:** إمكانية إرسال النسخة الاحتياطية مباشرة عبر تطبيقات المراسلة والتخزين السحابي.
- **أنماط استيراد متطورة:** دعم وضعين للاستيراد: وضع الدمج (Merge) للحفاظ على القيود السابقة وإضافة الجديد، ووضع الاستبدال الكامل (Replace All) لمسح القديم وتطبيق محتويات النسخة، مع اشتراط رمز المرور قبل الاستيراد.

### 8. الحماية المتقدمة ومنع تعطيل التطبيق (Advanced Anti-Tamper & Device Admin)
- **درع مسؤول الجهاز (Device Administrator):** يمنع حذف التطبيق أو إلغاء تثبيته نهائياً عبر سياسات حماية نظام أندرويد الرسمية.
- **منع الإيقاف الإجباري ومسح البيانات (Anti-Tamper Interception):** يعترض فوراً أي محاولة لفتح صفحة التطبيق في إعدادات الهاتف لمنع النقر على "إيقاف إجباري" أو "مسح التخزين ومسح البيانات" أو تعطيل إمكانية الوصول بدون إدخال رمز المرور الرئيسي.
- **الاعتراض الذكي لبرامج إلغاء التثبيت (Smart Anti-Uninstall):** رصد أي محاولة لفتح مثبت الحزم (`PackageInstaller`) لحذف مراقب واعتراضها فوراً.
- **حماية الإعدادات الأمنية برمز PIN:** اشتراط تأكيد رمز المرور (PIN) قبل السماح بتعطيل أي درع حماية أو إلغاء صلاحية مسؤول الجهاز.

### 9. ضمان الصمود والاستمرارية بعد إعادة تشغيل الهاتف (Reboot Survival & Resilience)
- **التوافق التام مع الإقلاع المباشر (Direct Boot & User Unlock):** معالجة متقدمة لطبقات التشفير تمنع أي انهيار للمستقبلات قبل فك القفل الأولي للشاشة، مع استجابة فورية لأحداث `ACTION_BOOT_COMPLETED` و `ACTION_USER_UNLOCKED`.
- **استعادة مؤقتات التخطي المؤقت (Bypass Timers Restoration):** الحفاظ على الطوابع الزمنية للتخطي المؤقت بدقة الثواني؛ فإذا تمت إعادة تشغيل الهاتف أثناء فترة التخطي، يتم احتساب الوقت المتبقي فقط واستئناف الحظر فور انتهاء الدقائق المصرح بها.
- **عزل فترة إطفاء الهاتف ومنع تضخم الاستهلاك (Reboot-Aware Consumption Calculation):** رصد أحداث إغلاق وبدء تشغيل النظام (`DEVICE_SHUTDOWN` و `DEVICE_STARTUP`) لضمان عدم احتساب ساعات إيقاف تشغيل الهاتف ضمن وقت استهلاك التطبيقات المقيدة.
- **استثناء تحسين البطارية وبدء التشغيل التلقائي (Battery Exemption & OEM Autostart):** دعم مدمج لطلب استثناء التطبيق من تحسينات البطارية (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)، مع توجيه ذكي لإعدادات البدء التلقائي لأجهزة شاومي وسامسونج وهواوي وأوبو وفيفو.
- **كشف التلاعب بالساعة (Clock Rollback Detection):** رصد ومكافحة أي محاولات لتأخير أو تقديم ساعة الجهاز يدوياً بعد إعادة التشغيل للالتفاف على الحدود الزمنية أو الجداول المجدولة.

### 10. الحظر القاطع وتأمين شاشات الإعدادات والضبط (Hardened Settings Lockdown)
- **معالجة حظر النوافذ العائمة للنظام (Bypassing Overlay-Hiding Restrictions):** يفرض نظام أندرويد حظر ظهور النوافذ العائمة فوق تطبيق الإعدادات لمنع هجمات حجب النقر (Tapjacking). يتغلب مراقب على ذلك عبر عدم الاكتفاء بالنافذة العائمة، بل إجبار النظام على الخروج الفوري للشاشة الرئيسية (`performGlobalAction(GLOBAL_ACTION_HOME)`) وإطلاق شاشة الحظر الكاملة كنشاط مستقل (`BlockActivity`) يطرد المستخدم فوراً قبل أن يتمكن من لمس أي إعداد.
- **التعرف الشامل على كافة واجهات الشركات المصنعة (Universal OEM Settings Recognition):** دعم حزم الإعدادات المخصصة لكافة الشركات مثل سامسونج (`com.samsung.android.settings`)، وشاومي (`com.miui.securitycenter`)، وأوبو وريلمي وون بلس (`com.coloros.settings`, `com.oplus.settings`)، وفيفو (`com.vivo.settings`)، وهواوي وجوجل بكسل، والشاشات الفرعية مثل إعدادات الشبكة والتطبيقات والأمان.
- **تطبيق فوري في أجزاء من الثانية:** دمج الرصد المزدوج عبر فحص النوافذ المتزامنة وأحداث إمكانية الوصول، لضمان استحالة النفاذ للإعدادات حتى في وضع الشاشات المنقسمة أو النوافذ العائمة.

---

## الأمان والخصوصية (Privacy & Security)

- **بدون إذن إنترنت (Zero Internet Permission):** التطبيق لا يطلب ولا يحتوي على إذن الوصول للإنترنت (`android.permission.INTERNET`). كافة البيانات والتحليلات تتم وتُحفظ محلياً 100% على جهازك.
- **منع التعطيل والحذف:** حماية متعددة الطبقات تدمج مسؤول الجهاز والاعتراض الفوري لشاشات الضبط ومثبت الحزم.
- **تشفير رمز المرور وسؤال الأمان:** تشفير محلي يعتمد على خوارزمية SHA-256 مع تمليح عشوائي (Salted SHA-256)، ولا يتم حفظ الرمز الحقيقي في أي مكان.
- **حماية من التخمين (Brute-Force Protection):** إغلاق مؤقت تصاعدي بعد تكرار إدخال رمز المرور بشكل خاطئ.
- **حماية التعديل والحذف:** لا يمكن تغيير القيود أو تعديلها أو حذفها دون إدخال رمز المرور السري.

---

## البنية المعمارية والتقنيات (Architecture & Tech Stack)

المشروع مبني باتباع أفضل الممارسات الهندسية الرسمية لنظام أندرويد:

```text
com.example.muraqib/
├── data/
│   ├── model/             # Data entities, restriction models & time windows
│   └── repository/        # UsageStats, DataUsage, Security, Backup & Restrictions repositories
├── receiver/
│   ├── MuraqibDeviceAdminReceiver.kt   # Device Admin protection against uninstall
│   └── BootReceiver.kt                 # Boot audit & Safe Mode detection trigger
├── security/
│   ├── SafeModeManager.kt              # Safe Mode detection, audit & heartbeat tracking
│   └── BootResilienceManager.kt        # Reboot survival, OEM autostart & battery optimization
├── service/
│   ├── MuraqibAccessibilityService.kt  # Real-time app detection, PiP & split-screen shields
│   ├── AppBlockerService.kt            # Periodic background blocker service & heartbeat
│   └── BlockOverlayManager.kt          # Fullscreen & split-screen blocking overlay window
├── ui/
│   ├── dashboard/         # Usage overview, hourly charts & period filters
│   ├── data/              # Mobile & Wi-Fi data consumption tracker
│   ├── restrictions/      # App restriction manager, cards & add/edit dialogs
│   ├── security/          # PIN creation, verification & lock screens
│   ├── settings/          # Security settings, device admin & backup/restore UI
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
3. **خدمة إمكانية الوصول (`BIND_ACCESSIBILITY_SERVICE`):** للرصد الفوري واللحظي للتطبيق النشط في الواجهة واعتراض نوافذ PiP والشاشات المنقسمة وتطبيق الحظر دون أي تأخير.
4. **التشغيل عند بدء التشغيل (`RECEIVE_BOOT_COMPLETED`):** لإعادة تشغيل محرك الحظر وتدقيق الإقلاع تلقائياً فور إعادة تشغيل الجهاز.

---

## التثبيت والتحميل (Download & Installation)

### تحميل حزمة التطبيق الجاهزة (Pre-built APK)
يمكنك تحميل أحدث نسخة مستقرة ومبنية وجاهزة للتثبيت مباشرة من صفحة الإصدارات:
- **[صفحة الإصدارات على GitHub (GitHub Releases)](https://github.com/yalaahamdy/App-Usage-Tracker-Controller/releases)**
- الإصدار الحالي: `v1.1.3`
- اسم الملف: `Muraqib-v1.1.3.apk`
- التجزئة الرقمية للتحقق (SHA-256):
  ```text
  00703663B8C838DDE20D200A5B863190AD9BD504C0719112D3D483B75E51F99B
  ```

### التثبيت عبر ADB
```bash
adb install -r Muraqib-v1.1.3.apk
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

# 3. بناء نسخة الإصدار الموقعة
./gradlew assembleRelease

# ملف الـ APK الناتج ستجده في المسار التالي:
# app/build/outputs/apk/release/app-release.apk
```

---

## المساهمة في المشروع (Contributing)

نرحب بكافة المساهمات والاقتراحات. يرجى قراءة [دليل المساهمة (CONTRIBUTING.md)](CONTRIBUTING.md) للتعرف على معايير الكود، تنسيق الرسائل، وآلية تقديم طلبات السحب (Pull Requests).

---

## الترخيص (License)

هذا المشروع مرخص بموجب رخصة **Apache License 2.0**. للمزيد من التفاصيل، يرجى الاطلاع على ملف [LICENSE](LICENSE).
