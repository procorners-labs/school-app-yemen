# إعدادات Google Cloud Platform – منظومة مدارس الإبداع والتميز الدولية

**رقم المشروع:** `108410742569`
**اسم المشروع:** `school-494822`
**المالك:** `procorners.shop@gmail.com`

> ملاحظة تاريخية: تطبيق الأندرويد كان يستخدم بالخطأ `app/google-services.json` لمشروع Firebase
> مختلف تماماً (`mkrh-181cd`) — سُرِّب مفتاح API الخاص به علناً عبر GitHub Secret Scanning
> (commit `14f5409`). المالك حذف مشروع `mkrh-181cd` بنفسه، وصُحِّح الملف إلى `school-494822`
> الصحيح (PR #6، مدموج). تنبيه GitHub الأمني #1 لا يزال يحتاج رفضاً يدوياً من المالك
> (Security → Secret scanning → Alert #1 → Close as Revoked).

## المشاريع/الملفات المرتبطة بـ GCP (عبر Apps Script)

| التطبيق | المعرف | الحالة |
|---------|--------|--------|
| الموقع الرسمي | `1J7DY-Z2PZU5y5HH-LR3vhuEhPAkjWz22vMu1rYLcse0` | ✅ مرتبط |
| منصة الطالب | `1BPtHUMB8kdi2exbfPVaKoSOcjGGZrnbJANXPHnWTD_A` | ✅ مرتبط |
| منصة المعلم | `1G6sLNJZqZ2pazx22nNS6X6GIYfAE-rT2IjcrF9NSheM` | ✅ مرتبط |
| CMS | `1J7DY-Z2PZU5y5HH-LR3vhuEhPAkjWz22vMu1rYLcse0` | ✅ مرتبط |
| أداة الحصص | `14VflEuGRCXIOz22_cYp2HmUTZYYsMJ1fNtvDMxbYSZA` | ✅ مرتبط |

## الخدمات المفعلة

- ✅ Drive API
- ✅ Sheets API (عبر Apps Script)
- ✅ Script API

## تطبيقات Firebase المسجَّلة تحت `school-494822`

| المنصّة | App ID | اسم الحزمة | تاريخ التسجيل |
|---|---|---|---|
| Android | `1:108410742569:android:cd60f6317dbb3f4714b938` | `com.proconrers.schoolappyemen` | 2026-07-09 (PR #6، يستبدل تسجيل `mkrh-181cd` الخاطئ) |

**متبقٍّ:** إضافة بصمة SHA-1 لهذا التطبيق (Firebase Console → Project Settings → هذا التطبيق →
Add fingerprint، أو عبر أداة `firebase_create_android_sha` بعد أن يزوّد المالك البصمة الناتجة من
تشغيله `check-signing.ps1`/`keytool` بنفسه محلياً — لا تُشغَّل هذه الأداة بكلمة مرور الـkeystore
ظاهرة في سجلّ الأوامر).

## حسابات الخدمة (Service Accounts)

| الحساب | الاستخدام | أين يُستدعى |
|---|---|---|
| `teacher-platform-drive@school-494822.iam.gserviceaccount.com` | رفع/قراءة ملفات Drive (صلاحية `roles/drive.file`) لمرفقات الأخبار وCMS | Apps Script (خلفية teacher/cms)، عبر `DriveApp` |
| `play-publisher@school-494822.iam.gserviceaccount.com` | نشر إصدارات Android على Google Play Console (Android Publisher API v3) | `deploy-to-play.ps1` محلياً، عبر `service-account-key.json` (غير مُتعقَّب في git) |

لا تخلط بين الاثنين: الأول يخصّ محتوى المنصّة (Sheets/Drive)، والثاني يخصّ نشر تطبيق الأندرويد نفسه
حصراً — منفصل تماماً عن أي مزامنة GAS.

## المجلدات المرتبطة (Drive)

- مرفقات أخبار المعلمين: `17KtwtRIsk0I96fl5UakRKhfoSk0ZjUQF`
- مرفقات CMS: `13A82NOATnZTuk5EtVfo2hplZih5NFSnT`

## Google Analytics (GA4) — موجود على مستوى الحساب، غير مُفعَّل في أي كود حيّ (عمداً)

يوجد حساب Google Analytics باسم **"Procorners Education"** (`accounts/397644779`) تحت نفس مالك
GCP، بخاصيتين:

| الخاصية | تاريخ الإنشاء | الحالة |
|---|---|---|
| "مدارس الإبداع والتميز" (`properties/541178372`) | 2026-06-10 | كانت مربوطة بوسم `gtag` في الموقع، **أُزيل نهائياً بقرار صريح من المالك (2026-07-04) لأنه سبّب مشاكل** — لا تُقترح إعادة ربطها |
| "school-494822" (`properties/544239746`) | 2026-07-05 (آخر تعديل 2026-07-08) | خاصية غير مرتبطة بأي كود حيّ حالياً — أُنشئت أثناء عمل تصحيح Firebase في جلسة سابقة، مُبقاة كما هي دون حذف بقرار المالك |

**⚠️ لأي جلسة/وكيل مستقبلي:** وجود هاتين الخاصيتين على مستوى حساب Google Analytics **لا يعني** أن
تتبّع الزوار مفعَّل — تحقّقتُ أنه لا يوجد أي `gtag`/`measurementId`/معرّف `G-` في كود الموقع
(`school-app-yemen-web`) أو الواجهة المُولَّدة. هذا وضع **مقصود**؛ لا تقترح ربط أي منهما بالكود من
تلقاء نفسك دون طلب صريح من المالك.

## ملاحظات

- تم حل مشكلة رفع الصور عبر `DriveApp` باستخدام صلاحية المجلد العام.
- تاريخ آخر تحديث: 2026-07-09
