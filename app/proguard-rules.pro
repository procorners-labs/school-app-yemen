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
# ملاحظة: أُزيل الـ-keep الشامل لـMaterial وKotlin (2026-07-17) — القواعد المُستهلَكة
# (consumer-rules) المرفقة مع كل AAR + آلية keep التلقائية لعناصر XML كافية فعلياً؛
# الـkeep الشامل كان يُبطل فائدة isMinifyEnabled بلا داعٍ حقيقي (لا استخدام انعكاس/reflection
# مباشر لهذه المكتبات في الكود).
-dontwarn com.google.android.material.**

# 5. قواعد خاصة بـ Kotlin (لمنع الأخطاء بعد التصغير)
-dontwarn kotlin.**
-keepclassmembernames class kotlin.jvm.internal.Intrinsics {
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
}