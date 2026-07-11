# إصلاح تعارض PTR الأصلي (SwipeRefreshLayout) مع الدرج الجانبي في منصة المعلم

**التاريخ:** 2026-07-11
**الحالة:** ✅ الكود مدموج في `main` (هذا المستودع + `school-app-yemen-gas`). ⛔ **لم يُنشر إلى
Play Store بعد** — بانتظار طلب المالك الصريح (يريد إضافة تعديلات أخرى أولاً وينشر بنفسه لاحقاً).

---

## المشكلة الأصلية

سحب-التحديث (Pull-to-Refresh) يحدث/"يختلط" مع فتح الدرج الجانبي في منصة المعلم داخل **تطبيق
أندرويد تحديداً** — لا في المتصفح أو PWA. المالك وصفها بأن الأمر "غير متوافق فقط مع التطبيق".

## السبب الجذري (اكتُشف بالتحقيق المباشر في مستودعين، لا افتراض)

يوجد **نظاما سحب-تحديث منفصلان تماماً** يعملان في نفس الوقت:

1. **حارس الويب** (`_setupPullToRefreshGuard`، `teacher/Teacher Dashboard.html` في مستودع
   `school-app-yemen-gas`) — كان **سليماً فعلاً** قبل هذا الإصلاح: يستبعد أي لمسة فور أن `#sidebar`
   يحمل الصنف `open`، ويستبعد التمرير لأعلى، ويعتمد `overscroll-behavior-y:contain`.

2. **`SwipeRefreshLayout` الأصلي** (`activity_webview.xml`، هذا المستودع) — يلفّ الـ`WebView` بمكوّن
   أندرويد أصلي **لا يعرف شيئاً عن حالة DOM** (فتح/إغلاق الدرج الجانبي). يعترض اللمس على مستوى نظام
   أندرويد **فوق** الـWebView مباشرة، معتمداً فقط على `canChildScrollUp()` الافتراضي — بصرف النظر
   عن حالة الدرج في صفحة الويب.

هذا يفسّر تماماً لماذا العطل "غير متوافق فقط مع التطبيق": حارس الويب ممتاز، لكنه **لا يستطيع إيقاف**
طبقة أندرويد الأصلية إطلاقاً — طبقتان مستقلتان مكدّستان فوق بعضهما، إحداهما لا ترى الأخرى.

## الحل

جسر JS↔أندرويد جديد يسمح لصفحة الويب بتعطيل/تفعيل `SwipeRefreshLayout` الأصلي صراحةً عند فتح/إغلاق
الدرج، عبر الجسر الموجود أصلاً `window.AndroidApp` (`WebViewSupport.JS_BRIDGE`).

### التغييرات في هذا المستودع (أندرويد، PR#8، مدموج)

- **`app/src/main/java/com/proconrers/schoolappyemen/SchoolJsBridge.kt`**:
  - معامِل رابع جديد للمُنشئ: `setSwipeEnabled: (Boolean) -> Unit` (نفس نمط `targetUrl: () -> String`
    القائم أصلاً).
  - دالة جديدة `@JavascriptInterface fun setSwipeRefreshEnabled(enabled: Boolean)` تستدعي
    `setSwipeEnabled` على `runOnUiThread` (نفس نمط `retry()`/`saveBase64()` القائمتين).

- **`app/src/main/java/com/proconrers/schoolappyemen/BaseWebViewActivity.kt`** (يستخدمه
  `TeacherActivity` و`StudentActivity` كلاهما عبر الوراثة):
  - نقطة إنشاء `SchoolJsBridge`: تمرير `{ enabled -> binding.swipeRefresh.isEnabled = enabled }`.
  - إضافة دفاعية في `onPageStarted`: `binding.swipeRefresh.isEnabled = true` — يمنع بقاء
    `SwipeRefreshLayout` معطَّلاً للأبد لو انقطع تنفيذ `closeSidebar()` في JS منتصف الطريق (تنقّل
    مفاجئ، خطأ JS، إلخ) — أي تحميل صفحة جديد يعيد الحالة الافتراضية (مفعَّل).

- **`app/src/main/java/com/proconrers/schoolappyemen/MainActivity.kt`** (نشاط الموقع الرسمي/الصفحة
  الرئيسية، لا يرث `BaseWebViewActivity`، له `SwipeRefreshLayout` خاص به): نفس التعديلين تماماً
  للاتساق — رغم أن هذا النشاط لا يحتوي درجاً جانبياً حالياً يحتاج التعطيل فعلياً، لكن الجسر متاح له
  بلا تكلفة إضافية لأي استخدام مستقبلي مشابه.

### التغييرات في `school-app-yemen-gas` (الويب، PR#230، مدموج، **منشور حيّاً بالفعل**
(`teacher@552`))

- `toggleSidebar()`/`closeSidebar()` في `teacher/Teacher Dashboard.html` تستدعيان
  `window.AndroidApp.setSwipeRefreshEnabled(false/true)` — محروسة بـ`typeof`+`try/catch`، **بلا أثر
  إطلاقاً في المتصفح/PWA** حيث `window.AndroidApp` غير معرَّف (الفحص يُسقِط الاستدعاء بصمت).
- إضافة `touch-action: pan-y` على `.sidebar` + تبديل `document.body.style.touchAction` بين
  `'none'`/`''` — طبقة دفاع إضافية لثغرات دعم `overscroll-behavior` في بعض محركات WebView الأقدم.

**ملاحظة مهمة:** لأن الاستدعاء من الويب محروس بفحص وجود `window.AndroidApp`، فإن تغيير الويب **بلا
أثر إنتاجي على أي مستخدم حالياً** — لن يبدأ أي تأثير فعلي حتى تُثبَّت نسخة أندرويد جديدة تحتوي هذا
الجسر. الويب مُنشَر حيّاً بالفعل ولا يحتاج انتظار أندرويد ليكون آمناً.

## حالة التحقق حتى الآن

- ✅ `compileDebugKotlin` + `assembleDebug` نجحا محلياً (APK تصحيح، لا توقيع إصدار).
- ✅ الويب منشور حيّاً ومؤكَّد (`teacher@552`، تأكيد مباشر عبر وسيط الـWorker: النصوص
  `setSwipeRefreshEnabled`/`touch-action` موجودة في HTML المخدوم فعلياً).
- ⏳ **لم تُختبَر حيّاً على جهاز أندرويد حقيقي** (لا بيانات دخول/جهاز متاح من جلسة الأتمتة). قائمة
  اختبار يدوي كاملة أدناه — ينفّذها المالك بعد تثبيت APK جديد.

## قائمة اختبار يدوي مطلوبة قبل/بعد النشر (على جهاز أندرويد حقيقي)

**بعد تثبيت APK يحوي هذا الإصلاح:**
1. فتح الدرج الجانبي في منصة المعلم + سحب لأسفل فوقه ⟵ **لا يظهر** مؤشر تحديث أصلي، الدرج يبقى
   مفتوحاً بلا انقطاع.
2. إغلاق الدرج + سحب لأسفل من أعلى الصفحة الرئيسية ⟵ سحب-التحديث الأصلي **يعمل كالمعتاد** (فحص
   ارتداد — التأكد أن التعطيل مقصور على حالة الدرج المفتوح فقط، لا تعطيل عام دائم).
3. سحب لأسفل من داخل قائمة الدرج القابلة للتمرير (لو كان لديها محتوى كافٍ) ⟵ تمرير داخلي فقط، لا
   ظهور لأي من مؤشرَي التحديث.
4. إغلاق قسري للتطبيق (Force Stop) أثناء فتح الدرج، ثم إعادة فتح التطبيق ⟵ التأكد أن سحب-التحديث
   الأصلي لم يبقَ معطَّلاً بشكل دائم (يتحقّق تلقائياً بفضل الضمانة الدفاعية في `onPageStarted`).
5. الضغط على كل عناصر الدرج الجانبي بالتتابع ⟵ التنقّل يعمل طبيعياً (تأكيد أن `touch-action` الجديد
   في الويب لم يكسر أحداث `onclick`).
6. تكرار البنود 1-5 في **منصة الطالب** أيضاً (الجسر مُفعَّل هناك أيضاً عبر الوراثة المشتركة من
   `BaseWebViewActivity`، رغم أن منصة الطالب لا تحتوي درجاً جانبياً حالياً — لا ضرر من التحقق).

## خطوات النشر لاحقاً (عندما يقرّر المالك المتابعة)

1. أضف أي تعديلات إضافية مخطَّطة على فرع جديد (`git checkout -b feature/xxx origin/main`).
2. تأكد أن هذا الإصلاح (SwipeRefreshLayout bridge) لا يزال في `main` — تحقّق:
   `git log --oneline --all | grep -i "swiperefresh"` يجب أن يُظهر الكوميت `fix: تعطيل
   SwipeRefreshLayout الأصلي أثناء فتح الدرج الجانبي (منصة المعلم)`.
3. نفّذ قائمة الاختبار اليدوي أعلاه على جهاز حقيقي أولاً — **قبل** أي رفع لنسخة Play.
4. ارفع نسخة جديدة عبر `deploy-to-play.ps1` (يتطلّب تأكيداً حيّاً صريحاً منفصلاً حسب قاعدة
   `CLAUDE.md` — رفع Play Store عملية يصعب التراجع عنها).
5. راجع رقم الإصدار (`versionCode`/`versionName`) في `app/build.gradle.kts` قبل الرفع — تأكد أنه
   زاد عن آخر نسخة منشورة فعلياً على Play Console (لا الاعتماد على الذاكرة فقط، راجع Console
   مباشرة).

## ملفات ذات صلة

| الملف | الدور |
|---|---|
| `app/src/main/java/com/proconrers/schoolappyemen/SchoolJsBridge.kt` | الجسر — دالة `setSwipeRefreshEnabled` الجديدة |
| `app/src/main/java/com/proconrers/schoolappyemen/BaseWebViewActivity.kt` | ربط الجسر بـ`SwipeRefreshLayout` الفعلي (teacher/student) + الضمانة الدفاعية |
| `app/src/main/java/com/proconrers/schoolappyemen/MainActivity.kt` | نفس الربط للصفحة الرئيسية (اتساقاً، لا استخدام فعلي حالياً) |
| `app/src/main/res/layout/activity_webview.xml` | تعريف `SwipeRefreshLayout` نفسه (لم يتغيّر) |
| `C:\Users\osama\SchoolApp-gas\teacher\Teacher Dashboard.html` | الطرف المقابل في الويب (مستودع منفصل، منشور حيّاً بالفعل @552) |
