# 🏫 SchoolAppYemen — دليل المستودع للمساعد التقني Claude

> منظومة **مدارس الإبداع والتميز الدولية**.
> هذا الملف هو المرجع الأساسي لأي مساعد ذكي يعمل على هذا المستودع: يشرح البنية،
> وسير العمل، والاصطلاحات الإلزامية. اقرأه بالكامل قبل أي تعديل.

آخر تحديث: 2026-05-28

---

## 1) نظرة عامة

`school-app-yemen` هو **تطبيق أندرويد أصلي مكتوب بلغة Kotlin** يعمل كـ **غلاف WebView**
(عميل رفيع) حول عدة تطبيقات ويب من **Google Apps Script**. المنطق والبيانات الفعلية
(الأخبار، الطلاب، المعلمون، إدارة المحتوى CMS، الحصص) تعيش على الخادم في تطبيقات
Apps Script المدعومة بـ Google Sheets و Google Drive ضمن مشروع GCP `school-494822`.

التطبيق نفسه **لا يحتوي على منطق أعمال**؛ مهمته:
- تحميل الروابط الصحيحة لكل منصة داخل WebView.
- توجيه التنقل بين المنصات (الموقع العام / المعلم / الطالب / CMS).
- إدارة الروابط ديناميكيًا (تحديثها من الخادم دون إصدار APK جديد).
- التعامل مع SSL ورفع الملفات وأخطاء الشبكة.

**الجمهور:** الزوار وأولياء الأمور (الموقع العام)، الطلاب، المعلمون، والإدارة.

---

## 2) المعمارية

### تدفق الأنشطة (Activities)

```
SplashActivity (LAUNCHER)
   │  AppConfig.init()  ← يبدأ مزامنة الروابط في الخلفية (بلا تأخير اصطناعي)
   ▼
MainActivity  ──(يحمّل AppConfig.HOME_URL = الموقع الرسمي العام)
   │
   ├─ رابط منصة المعلم  → TeacherActivity (AppConfig.TEACHER_URL)
   ├─ رابط منصة الطالب  → StudentActivity (AppConfig.STUDENT_URL)
   ├─ رابط CMS          → يبقى داخل نفس الـ WebView
   └─ رابط Google موثوق → يبقى داخل الـ WebView
```

- **`SplashActivity`** نقطة الدخول الوحيدة (LAUNCHER) عبر AndroidX SplashScreen API؛
  تُهيّئ `AppConfig` ثم تنتقل فورًا إلى `MainActivity`.
- **`MainActivity`** يستضيف الموقع العام ويحوي منطق التوجيه الرئيسي في `shouldOverrideUrlLoading`.
- **`TeacherActivity`** و **`StudentActivity`** يتشاركان تخطيط `activity_webview.xml`
  ويدعمان رفع الملفات (`WebChromeClient.onShowFileChooser`) وأشرطة التقدّم.

### `AppConfig` — المصدر المركزي للروابط

`object` مفرد (`app/src/main/java/com/proconrers/schoolappyemen/AppConfig.kt`) مسؤول عن **كل**
روابط النشر الستة: `home`, `cms`, `teacher`, `student`, `schedule`, `master`.

- يحتفظ بروابط افتراضية ثابتة (تُستخدم في الإقلاع الأول فقط).
- يجلب أحدث الروابط من الخادم عبر `?action=deployments` **كل 6 ساعات**، ويخزّنها في
  `SharedPreferences` باسم `deployment_config_v2`.
- التوجيه لا يعتمد على مطابقة نصية كاملة للرابط، بل على **معرّف النشر** المُستخرج بـ regex
  `"""/macros/s/([^/]+)/exec"""` (الدوال `isHomeUrl` / `isTeacherUrl` / `isStudentUrl` / `isCmsUrl`)
  — هذا يحلّ مشكلة إعادة التوجيه عبر `/macros/r/`.
- يحتفظ بقائمة **نطاقات Google موثوقة** لقبول شهادات SSL منها فقط (`isTrustedSslDomain`).

**الأثر العملي:** لإعادة نشر أي منصة، يكفي تحديث `ScriptProperties` في Apps Script على الخادم،
وسيلتقط التطبيق الرابط الجديد في الإقلاع التالي **دون الحاجة لإصدار APK جديد**.

---

## 3) بنية المشروع

| المسار | الوصف |
|--------|-------|
| `app/src/main/java/com/proconrers/schoolappyemen/AppConfig.kt` | المصدر المركزي للروابط + المزامنة الديناميكية + منطق SSL/التوجيه |
| `…/SplashActivity.kt` | نقطة الدخول (LAUNCHER)، تهيئة `AppConfig` |
| `…/MainActivity.kt` | الموقع العام + منطق التوجيه الرئيسي |
| `…/TeacherActivity.kt` | منصة المعلمين والإدارة (WebView + رفع ملفات) |
| `…/StudentActivity.kt` | منصة الطلاب وأولياء الأمور (WebView + رفع ملفات) |
| `app/src/main/AndroidManifest.xml` | تعريف الأنشطة، الصلاحيات، إعداد أمان الشبكة |
| `app/src/main/res/layout/activity_main.xml` | حاوية WebView للموقع العام (يُضاف WebView برمجيًا) |
| `app/src/main/res/layout/activity_webview.xml` | تخطيط مشترك لـ Teacher/Student مع شريط تقدّم خطّي |
| `app/src/main/res/xml/network_security_config.xml` | السماح بنطاقات Google (cleartext + شهادات النظام) |
| `app/proguard-rules.pro` | قواعد R8/ProGuard للحفاظ على دوال WebView/JS عند التصغير |
| `app/build.gradle.kts` | إعدادات وحدة التطبيق (الإصدارات، البناء، الاعتماديات) |
| `gradle/libs.versions.toml` | كتالوج إصدارات Gradle |
| `GCP_CONFIGURATION.md` | توثيق مشروع GCP ومعرّفات النشر وحساب الخدمة |

---

## 4) أوامر التطوير

```bash
./gradlew assembleDebug          # بناء APK تجريبي
./gradlew assembleRelease        # بناء نسخة الإصدار (R8/minify + shrink مفعّلان)
./gradlew test                   # اختبارات الوحدة (JVM)
./gradlew connectedAndroidTest   # اختبارات الأجهزة (تتطلب جهازًا/محاكيًا)
./gradlew lint                   # فحص Lint
```

- **البيئة:** AGP 8.5.0، compileSdk/targetSdk = 35، minSdk = 24، JDK 17، ViewBinding مُفعّل.
- لا تُضِف `org.gradle.java.home` في `gradle.properties` (مسار خاص بجهاز معيّن يكسر CI)؛
  اعتمد على `JAVA_HOME`.

---

## 5) الاصطلاحات والقواعد الإلزامية للمساعد

1. **ES5 فقط** لأي كود Google Apps Script على الخادم — التزم بصياغة ES5 لتفادي أخطاء التشغيل.
2. **لا تُغيّر `applicationId`** (`com.proconrers.schoolappyemen`) رغم الخطأ الإملائي في
   `proconrers`؛ فهو المعرّف الرسمي للتطبيق على المتجر، وتغييره يكسر التحديثات.
3. **لا تُعدّل سلسلة User-Agent** المخصّصة؛ تحتوي العلامتين `wv` و`SchoolAppYemen/1.0`
   اللتين يعتمد عليهما فرونت-إند Apps Script لاكتشاف أنه يعمل داخل التطبيق.
4. **عند إضافة منصة أو رابط جديد** عدّل في `AppConfig`: المفتاح + الرابط الافتراضي + الـ getter
   + دالة `is*Url` المقابلة + `parseAndStore`، ثم أضف فرع التوجيه في `shouldOverrideUrlLoading`
   داخل **كل** نشاط معني.
5. **اتساق إعدادات WebView:** الإعدادات مكرّرة عمدًا في الأنشطة الثلاثة؛ أي تغيير سلوكي
   (User-Agent، السماح بالملفات، SSL، …) يجب تطبيقه على الثلاثة معًا.
6. **اللغة:** التعليقات ونصوص الواجهة بالعربية (RTL). حافظ على هذا الأسلوب.
7. **الإصدارات:** عند أي إصدار جديد، ارفع `versionCode` (عدد صحيح متزايد، آخر إصدار منشور = 17)
   وحدّث `versionName` في `app/build.gradle.kts`.

---

## 6) GCP و Apps Script

من `GCP_CONFIGURATION.md`:

- **المشروع:** `school-494822` (رقم `108410742569`)، المالك `procorners.shop@gmail.com`.
- **الخدمات المفعّلة:** Drive API، Sheets API (عبر Apps Script)، Script API.
- **حساب الخدمة:** `teacher-platform-drive@school-494822.iam.gserviceaccount.com`
  بصلاحية `roles/drive.file`.
- **معرّفات النشر (Apps Script):** الموقع الرسمي، منصة الطالب، منصة المعلم، CMS، أداة الحصص —
  راجع `GCP_CONFIGURATION.md` للقيم الكاملة.
- **مجلدات Drive:** مرفقات أخبار المعلمين، مرفقات CMS.

> تحديث روابط المنصات يتم من جانب الخادم (ScriptProperties) ويلتقطه التطبيق تلقائيًا عبر
> مزامنة `AppConfig` — لا حاجة لإصدار APK جديد لمجرد تغيير رابط نشر.

---

## 7) سير العمل في Git

- طوّر على فرع المهمة (`claude/...`)، ولا تدفع إلى `main` دون إذن صريح.
- رسائل الـ commit عربية ووصفية، على نمط المستودع الحالي (مثل: "إصلاح MainActivity: …").
- بعد الدفع، افتح Pull Request **كمسودة (draft)**.
- لا تُضِف ثنائيات كبيرة (APK/AAB/ZIP) إلى الـ commits.

---

## 8) مشكلات وملاحظات معروفة

- **إصدار Kotlin غير متّسق:** الجذر `build.gradle.kts` يثبّت `1.8.10` بينما
  `gradle/libs.versions.toml` يذكر `1.9.0`؛ الإصدار الفعّال المطبّق على وحدة التطبيق هو `1.8.10`.
- **ثنائيات كبيرة متتبَّعة في Git:** `SchoolAppyemen.zip` و`app/release/*.aab`/`*.apk` مُضافة
  للمستودع بسبب تلف سطور الاستثناء في `.gitignore` (مسافات بين الأحرف). يُفضَّل تنظيف `.gitignore`
  وإزالتها من التتبّع.
- **لا يوجد CI:** لا توجد workflows في `.github/`.
- **الاختبارات افتراضية:** `ExampleUnitTest.kt` و`ExampleInstrumentedTest.kt` هي قوالب
  المعالج فقط ولا تغطي منطقًا فعليًا.

---

## 🤖 دور Claude في المنظومة

كلود هو المساعد التقني المعتمد، ويقوم بـ:
- تحليل الأكواد وتصحيح الأخطاء.
- توثيق التدفقات بين الأنظمة (التطبيق ↔ Apps Script ↔ Sheets/Drive).
- توجيه إعدادات GCP و GitHub.
- التأكد من التوافق مع ES5 في كود الخادم.
