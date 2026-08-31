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

---

## الإصدارات — المستودع كان متأخّراً إصداراً عن المنشور (صُولِح 2026-08-11)

| المصدر | القيمة |
|---|---|
| **Play (إنتاج)** | `versionCode` **31** · `versionName` **2.8** · نُشر 2026-07-17 07:40 · 663 تثبيتاً · 4 بلدان |
| **هذا المستودع قبل التصحيح** | `versionCode` 30 · `versionName` "2.7" — والشجرة **نظيفة** |

أي أن `vc31` بُني ونُشر **دون أن يُسجَّل رفع الرقم في git**، فمن يقرأ `build.gradle.kts` وحده
يستنتج أن المنشور 2.7. (‏`versionName = 2.8` مُستخرَج من **مانيفست الحزمة نفسها** لا من اسم ملفها،
ومطابق لما يقوله Play — مصدران مستقلّان.)

🔴 **والقاعدة تصف العلاقة لا القيمة: الرقم التالي = آخرُ ما رُفع + 1، لا آخرُ ما نُشر + 1.**
Play يحجز أيَّ `versionCode` **رُفع** ولو بقي **مسوّدةً لم تُنشَر**، ويرفض إعادة استخدامه ولو لم
يعرفه المستودع — وخطؤه («رمز الإصدار مستخدَم») يبدو غامضاً بلا هذا السطر.

⚠️ **ولا يُكتب هنا رقمٌ بعينه.** كان مكتوباً «الإصدار القادم يبدأ من 32» فصار كذباً حين
استُهلك 32 (‏بُني ووُقِّع ورُفع مسوّدةً لم تُنشَر، 2026-08-12) وحين بُني 33 (‏2026-09-01).
⇒ **رقمٌ مشتقٌّ يُجمَّد في وثيقة يصير إملاءً بائتاً يُقرأ بثقةٍ ويُطبَّق، بينما غيابُه يدفع
للقياس.** الرقم يُقرأ من `app/build.gradle.kts` بعد `git pull`، ويُراجَع مقابل Play Console.
(الفئةُ نفسُها ضُبطت في تعليق `build.gradle.kts` بمراجعة `claude[bot]` على PR #23.)

> ⚠️ **ودرسٌ في القياس نفسه:** أوّل قراءة لهذا الرقم أُخذت من نسخة `main` محلّية **متأخّرة تسعة
> كوميتات**، فأعطت «29/2.6» وأنتجت ادّعاءً بأن الفجوة إصداران. الرقم الصحيح ظهر فقط بعد
> `git fetch` وقراءة `origin/main`. ⇒ **لا يُقرأ رقم إصدار من شجرة عمل لم تُحدَّث للتوّ.**

### الحزم لا تُلتزَم هنا — والقاعدة كانت ميّتة لسببين معاً

`app/release/app-release.aab` (‏4.2MB) و`.apk` (‏3.3MB) كانا **متتبَّعَين** في هذا المستودع
**العامّ**، رغم وجود سطرين في `.gitignore` يحاولان استبعادهما. فشلا لسببين مستقلَّين:

1. **مكتوبان بترميز UTF-16** (‏58 بايتاً صفرياً بين الأحرف) فلا يقرؤهما git إطلاقاً — أثبته
   `git check-ignore` لا القراءة البصرية، فالسطران يبدوان سليمين تماماً في المحرّر.
2. **و`.gitignore` لا أثر له على ملف متتبَّع** أساساً، فحتى بترميز سليم لما فعل شيئاً.

⇒ أُعيدت كتابته UTF-8، وفُكّ التتبّع بـ`git rm --cached` (**الملفات باقية على القرص** — مُتحقَّق)،
وأُضيف `*.aab`/`*.apk`/`app/release/` و`keystore.properties`/`*.jks`/`*.keystore`.
**التاريخ ما يزال يحوي النسخ القديمة** — إزالتها تحتاج إعادة كتابة تاريخ ودفعاً قسرياً، ولم يُطلب
(ولا سرّ فيها: الحزمة موزَّعة علناً على Play أصلاً، و`keystore.properties` لم يكن متتبَّعاً قطّ).

### أين تعيش الأشياء الآن

| الشيء | المكان |
|---|---|
| حزم الإصدار (13 حزمة، نُقلت من سطح المكتب) | `C:\Users\osama\Workspace\Releases\SchoolAppyemen\` + `README.md` |
| مفتاح التوقيع | `C:\Users\osama\Workspace\Secure\schoolapp.jks` · alias `schoolapp` |
| ملف كلمتَي سرّ المفتاح | `C:\Users\osama\Workspace\Secure\` — **نُقل من سطح المكتب 2026-08-11** |

⚠️ ولا تخلط المفتاح بمفتاح «يمن سكولز» (`schoolzyemen-release.jks` · alias `schoolzyemen`) —
مفتاحان منفصلان تماماً، وتوقيع أحد التطبيقين بمفتاح الآخر يجعل Play يرفض التحديث بلا رجعة.

آخر تحديث: 2026-08-11 (مصالحة الإصدار · فكّ تتبّع الحزم · إصلاح `.gitignore` · ترتيب المفاتيح)

<!-- ci-audit-2026-08-29 -->

---

## 🏗️ CI — بناءُ debug على كل PR (أُضيف 2026-08-29)

قبل هذا التاريخ كان المستودع يحمل `claude.yml` **وحده**: لا شيءَ يتحقّق أن الكود
يُبنى أصلاً — في تطبيقٍ **منشور**. `build.yml` يشغّل `./gradlew assembleDebug`
على كل PR وعلى `push` إلى `main`.

🟢 **و`assembleDebug` عمداً — والسبب أمنيّ لا أدائيّ:**
- لا يحتاج `keystore` ولا أي سرّ توقيع ⇒ **صفرُ مساسٍ بـ`Workspace\Secure`**،
  ولا يلزم أي سرٍّ جديد في GitHub Actions.
- و`app/build.gradle.kts` يحرس ذلك بنفسه: التوقيع مشروطٌ بـ`if (keystoreFile.exists())`
  ⇒ غيابُ `keystore.properties` على العدّاء **مسارٌ مدعومٌ لا عطل**.

🔴 **ولا يُضاف `assembleRelease` بلا قرارٍ صريح:** يستدعي حقن أسرار التوقيع في CI،
وهو قرارٌ أمنيّ منفصل لا ترقيةٌ تقنية.

### 🔴 وأوّلُ تشغيلٍ كشف عطلاً كامناً منذ إنشاء المستودع

`./gradlew: Permission denied` (خروج 126) — **بتُّ التنفيذ غيرُ مضبوطٍ في شجرة git**
(‏`100644` بدل `100755`). وليس خصوصيةَ عدّاء: **أيُّ استنساخٍ على لينكس أو ماك يفشل
بالطريقة نفسها**، وقد ظلّ كذلك بلا أن يظهر لأن لا CI كان يبني.

🟢 **وعولج بالبتّ نفسه لا بـ`chmod +x` في الوركفلو** — ذاك يُخضِّر CI ويترك العطل
قائماً لكل بشريّ يستنسخ. ⇒ خضرةُ البناء اليوم **دليلٌ أن البتّ صحيح**، لا التفافٌ عليه.

### 🤖 `claude.yml` — الصلاحياتُ `write` ولا تُعاد إلى `read`

عطبُ `read` موثَّقٌ رسمياً (`claude-code-action#1562`): الإجراء يعمل ثمّ **لا ينشر
تعليقاً ولا مراجعة، بلا أي رسالة خطأ**.

📖 **والمالك الواحد لحقيقة CI عبر المستودعات: `~/docs/ci-github.md`.**
