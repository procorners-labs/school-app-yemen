# 1. قواعد خاصة بالـ WebView والـ JavaScript
-keepattributes EnclosingMethod,InnerClasses,Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
    public void onPageStarted(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public void onPageFinished(android.webkit.WebView, java.lang.String);
    public boolean shouldOverrideUrlLoading(android.webkit.WebView, java.lang.String);
    public void onReceivedError(android.webkit.WebView, int, java.lang.String, java.lang.String);
}

-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, int);
    public void onProgressChanged(android.webkit.WebView, int);
    public boolean onShowFileChooser(android.webkit.WebView, android.webkit.ValueCallback, android.webkit.WebChromeClient$FileChooserParams);
}

# 2. دعم @JavascriptInterface (إذا استخدمتها)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 3. قواعد خاصة بـ ViewBinding
-keep class com.proconrers.schoolappyemen.databinding.** { *; }

# 4. قواعد عامة لأندرويد
# 🔴 `-dontwarn com.google.android.material.**` حُذفت 2026-09-06 مع التبعيّة نفسِها.
#    إخراسُ تحذيراتِ مكتبةٍ لم تعد موجودة يُخفي عودتَها لو أُضيفت بلا قصد.

# 🔴 حُذفت 2026-09-06: -keep class com.google.android.material.** { *; }
#
# **القياس من خريطة vc34 المرفوعة فعلاً** (‏app/build/outputs/mapping/release/mapping.txt):
#   Material ١٬٠٩٢ صنفاً مُبقىً · منها ٧٣ في …material.datepicker
#   كودُنا نحن ٣٤ صنفاً · المجموع في الحزمة ٤٬٥٠٦
# **والتطبيقُ يستعمل عنصرَ Material واحداً في المستودع كلِّه**: LinearProgressIndicator
# في res/layout/activity_webview.xml — وصفرُ استيرادِ Material في أيّ ملفّ ‎.kt‎.
#
# 🔴 وأثرُها ليس الحجمَ وحدَه: تحذيرُ Play «واجهاتٌ متوقّفة نهائياً» على vc31
# (‏Window.setStatusBarColor · setNavigationBarColor · LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
# يسمّي مصدرَه صراحةً MaterialDatePicker.onStart — في تطبيقٍ لا يحوي كلمة DatePicker
# في أيّ سطرِ مصدر. وMaterialDatePicker كانت **مُبقاةً وغيرَ معتَّمة** في الخريطة،
# وتطابقُ الهويّة هو بصمةُ ‎-keep‎ بعينها. ⇒ التحذيرُ كان سيتكرّر على vc34 بلا هذا الحذف.
#
# 🟢 وما يُبقي العنصرَ المستعمَل بعد الحذف: قواعدُ المكتبة المستهلَكة (consumer rules)
# و‎proguard-android-optimize.txt‎ الذي يحفظ بانيَ الـView ذا (Context, AttributeSet).
# 🔴 والضابطُ المعاكس إلزاميّ — الانكسارُ هنا **صامتٌ وقتَ التشغيل لا وقتَ البناء**:
#   ① LinearProgressIndicator ما زال في mapping.txt بعد البناء.
#   ② وشريطُ التقدّم يظهر فعلاً عند تحميل صفحةٍ في شاشتَي المعلّم والطالب على جهاز.

# 5. قواعد خاصة بـ Kotlin (لمنع الأخطاء بعد التصغير)
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keepclassmembernames class kotlin.jvm.internal.Intrinsics {
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
}

# 6. تجريدُ سجلّات التشخيص من الإصدار — أُضيف 2026-09-06
#
# **المقيس:** ٣٠ نداءَ ‎Log.‎ في كود الإنتاج، وصفرُ ‎-assumenosideeffects‎ في هذا الملفّ
# ⇒ الثلاثون كلُّها كانت تُنفَّذ في الإصدار الذي يقيسه Play. و‎isMinifyEnabled‎ وحدَه
# لا يحذفها.
#
# **والأشدُّ أثراً** ‎BaseWebViewActivity.onConsoleMessage‎: يبني سلسلةً نصّيّةً من
# رسالة الكونسول ومصدرِها ورقمِ سطرِها **لكلّ رسالةٍ تصدرها الصفحة**، على الخيط الرئيسي.
# وصفحاتُنا مسهبةُ الكونسول فعلاً (‏رُصد ذلك على صفحة الخبر 2026-09-06).
#
# ⚠️ **‎w‎ و‎e‎ مُستثناتان عمداً** — تشخيصُ جهازِ مستخدمٍ عبر ‎adb logcat‎ يبقى ممكناً.
# والمُجرَّدُ هو المسارُ الساخنُ وحدَه.
# 🔴 **والقاعدةُ تحذف النداء ولا تضمن حذفَ بناءِ وسائطه** ⇒ ومعها حارسُ
# ‎BuildConfig.DEBUG‎ حول جسم ‎onConsoleMessage‎ في الكود.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}