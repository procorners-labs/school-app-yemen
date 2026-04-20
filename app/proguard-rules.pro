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
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }

# 5. قواعد خاصة بـ Kotlin (لمنع الأخطاء بعد التصغير)
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keepclassmembernames class kotlin.jvm.internal.Intrinsics {
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
}