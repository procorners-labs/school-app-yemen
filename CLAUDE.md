# CLAUDE.md — school-app-yemen (Android)

> تطبيق أندرويد (WebView shell) لمنصّة مدارس الإبداع والتميز الدولية. جزء من منظومة SchoolApp
> الأوسع — راجع أيضاً `CLAUDE.md` في `school-app-yemen-gas` (الخلفية) و`school-app-yemen-web`
> (الواجهة/Worker)، لكن هذا المستودع **مستقلّ ومنفصل الدورة** عنهما (لا CI مشترك، لا مزامنة تلقائية).

---

## دور Claude هنا

- تحليل الأكواد وتصحيح الأخطاء.
- توثيق التدفقات بين الأنظمة.
- توجيه إعدادات GCP و GitHub.
- التأكد من التوافق مع ES5 عند لمس أي جسر JS داخل الـWebView.

---

## نوع المحتوى (كلاهما، رغم أن GitHub يصنّف اللغة "PowerShell")

مشروع Android/Kotlin/Gradle حقيقي + سكربتات PowerShell للنشر إلى Google Play — السكربتات هي ما
يجعل GitHub يصنّف لغة المستودع "PowerShell" رغم أن المحتوى الفعلي Kotlin بالأساس.

- **`applicationId`:** `com.proconrers.schoolappyemen`.
- **بنية Gradle كاملة:** `build.gradle.kts`، `settings.gradle.kts`، `gradlew`، `app/build.gradle.kts`،
  `google-services.json`.
- **الكود الرئيسي** في `app/src/main/java/com/proconrers/schoolappyemen/`:
  - `MainActivity.kt`، `SplashActivity.kt`، `BaseWebViewActivity.kt`
  - `TeacherActivity.kt`، `StudentActivity.kt`
  - `WebViewSupport.kt`، `NetworkReloadController.kt`، `SchoolJsBridge.kt`
  - **`AppConfig.kt`** — مصدر الحقيقة الوحيد لروابط الـWebView.
- **سكربتات PowerShell:** `build-and-test.ps1`، `build-release.ps1`، `check-signing.ps1`،
  `deploy-to-play.ps1`، `diagnose-signing.ps1`، `test-all.ps1`.

---

## حقيقة تشغيلية جوهرية — المزامنة الديناميكية معطَّلة عمداً

`AppConfig.kt` يشير حالياً إلى روابط **Cloudflare Worker ثابتة** مكتوبة حرفياً في الكود:
```
https://school.procorners.com/{home,teacher,student,cms,schedule}/index.html
```
(النطاق الأساسي منذ 2026-07-17 — نطاق مخصّص يتفادى حجب يمن نت لـ`workers.dev`؛ القديم
`school-teacher-proxy.procorners-shop.workers.dev` لا يزال في `trustedSslDomains` كمسار احتياطي،
لم يُحذف. التفاصيل الكاملة في `school-app-yemen-gas/_docs/2026-07-16-حجب-يمن-نت-workers-dev-ونطاق-مخصص.md`.)

يوجد آلية مزامنة ديناميكية جاهزة تقرأ `action=deployments` من GAS (`syncIfNeeded()`)، لكنها **مُعطَّلة
عمداً** حالياً — التعليق في الكود نفسه صريح:
```kotlin
// ⛔ مُعطّلة: التطبيق يستخدم روابط الـ Worker الثابتة (لا مزامنة GAS)
// syncIfNeeded()
```

**الأثر العملي:** أي تغيير مستقبلي في نمط التوزيع (deployment pattern) في `school-app-yemen-gas` أو
`school-app-yemen-web` **لا ينعكس تلقائياً** على هذا التطبيق المنشور. يحتاج تحديثاً يدوياً لـ
`AppConfig.kt` + إصدار جديد عبر `deploy-to-play.ps1`. لا تفترض أن تعديل روابط الـWorker في المستودعين
الآخرين يكفي — تحقّق من هذا الملف تحديداً إن كان التغيير يمسّ التوجيه.

`MASTER_URL` (`KEY_MASTER`) لا يزال يشير إلى رابط Apps Script `/exec` مباشرة (لا عبر الـWorker) —
منفصل عن مسار المزامنة المعطَّل أعلاه.

---

## العلاقة مع `school-app-yemen-gas`

- **لا يوجد `_sync/`** في هذا المستودع (ذاك خاصّ بمزامنة Access المالية في `SchoolApp-gas`).
- `deploy-to-play.ps1` يستخدم `service-account-key.json` المحلّي (جذر المستودع) لرفع الإصدارات إلى
  Google Play Console — منفصل تماماً عن `_sync/access-sync.ps1` المالي في `SchoolApp-gas`؛ لا تخلط
  بينهما.
- إعداد GCP: مشروع `school-494822`، حساب خدمة `teacher-platform-drive@school-494822.iam.gserviceaccount.com`
  + `play-publisher@school-494822.iam.gserviceaccount.com` (نشر Play) — **`GCP_CONFIGURATION.md`
  بجذر هذا المستودع هو المرجع الكامل لكل حسابات/مشاريع Google** (Firebase، حسابات الخدمة، وحساب
  Google Analytics الموجود على مستوى الحساب لكن **غير مُفعَّل عمداً** في أي كود حيّ — لا تقترح
  ربطه دون طلب صريح).

---

## تغييرات معلّقة بانتظار النشر (`main` محدَّث، Play Store ليس بعد)

- **2026-07-11 — جسر تعطيل SwipeRefreshLayout الأصلي عند فتح الدرج الجانبي (منصة المعلم).** مدموج
  في `main` (`SchoolJsBridge.kt`/`BaseWebViewActivity.kt`/`MainActivity.kt`)، `compileDebugKotlin`+
  `assembleDebug` نجحا محلياً، الطرف المقابل في الويب (`school-app-yemen-gas`) **منشور حيّاً بالفعل**
  (`teacher@552`) وآمن بلا أي أثر إنتاجي حتى تُثبَّت نسخة أندرويد جديدة. **لم يُرفَع Play Store بعد
  بطلب صريح من المالك** (يريد إضافة تعديلات أخرى أولاً وينشر بنفسه لاحقاً). التفاصيل الكاملة + سبب
  الخلل الجذري + قائمة اختبار يدوي كاملة قبل/بعد الرفع في
  [`_docs/2026-07-11-swiperefresh-sidebar-bridge.md`](_docs/2026-07-11-swiperefresh-sidebar-bridge.md)
  — راجعه أولاً قبل أي محاولة نشر لاحقة لهذا التغيير تحديداً.
- **2026-07-17 — تحديث الروابط الافتراضية إلى `school.procorners.com` + سقالة Firebase Cloud
  Messaging (FCM) وصوت التنبيهات + تحسينات Dark Mode/أمان.** راجع
  `C:\Users\osama\.claude\plans\snug-wishing-balloon.md` للخطة الكاملة وحالة كل مرحلة.
  **ما أُنجِز وبُني محلياً بنجاح (`compileDebugKotlin`):** `AppConfig.kt` (روابط + trustedSslDomains)،
  `SchoolApplication.kt`/`SchoolFcmService.kt` جديدان (قناة إشعارات بصوت مخصّص `res/raw/notify_chime.wav`
  + عرض إشعار)، Dark Mode لصفحة الخطأ (`WebViewSupport.errorPageHtml` عبر `prefers-color-scheme`) +
  الثيم صار `Theme.AppCompat.DayNight.NoActionBar` مطبَّقاً فعلياً في المانيفست (كان يُطبَّق
  `Theme.AppCompat.Light.NoActionBar` مباشرة بدل الثيم المخصّص — ثغرة سابقة)، `mixedContentMode`
  شُدِّد من `ALWAYS_ALLOW` إلى `COMPATIBILITY_MODE`، `SchoolAppyemen.zip` القديم (7MB) أُزيل من git.
  **مؤجَّل عمداً (خطر انحدار بلا جهاز حقيقي للتحقّق):** دمج `MainActivity` مع `BaseWebViewActivity`
  (توسيع بدل تكرار) — تغيير معماري على شاشة الإقلاع (LAUNCHER) بلا إمكانية اختبار حيّ في هذه الجلسة؛
  وتضييق `cleartextTrafficPermitted` (لا يزال `true` عاماً) — نفس السبب. كلاهما بحاجة جلسة قادمة مع
  اختبار جهاز فعلي قبل التنفيذ.
  **لا يُنشر Play Store قبل:** (أ) تدوير كلمة مرور الـkeystore الحقيقية (مكتوبة حالياً كنص صريح
  `123456` في `check-signing.ps1`/`diagnose-signing.ps1`، يجب تدويرها أولاً)، (ب) اختبار FCM حيّ على
  جهاز حقيقي، (ج) تنفيذ قائمة اختبار جسر PTR أعلاه.

---

## قواعد

- **Kotlin** للتطبيق نفسه (لا قيد ES5 هنا — ذاك خاصّ بكود GAS في `school-app-yemen-gas` فقط). قيد
  ES5 ينطبق فقط عند لمس أي جسر JS يُحقَن داخل الـWebView (`SchoolJsBridge.kt` والمكافئات).
- **لا تغيّر `applicationId`** — يكسر التحديثات المنشورة على Play Store.
- **فرع لكل تغيير** — لا دفع مباشر إلى `main`.
- قبل أي رفع فعلي عبر `deploy-to-play.ps1`: تأكيد حيّ من المالك (نفس منطق 🔴 النشر الحيّ في
  `SchoolApp-gas` — رفع إصدار على Play Store عملية يصعب التراجع عنها).

آخر تحديث: 2026-07-11 (توثيق جسر SwipeRefreshLayout المعلّق بانتظار النشر)
