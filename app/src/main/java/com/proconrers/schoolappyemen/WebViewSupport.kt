package com.proconrers.schoolappyemen

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast

/**
 * WebViewSupport — أدوات مشتركة لكل شاشات الـ WebView (مبدأ DRY).
 *
 * يجمع المنطق الذي كان مكرّراً في MainActivity / StudentActivity / TeacherActivity:
 *   - إعدادات WebView الموحّدة (applyDefaults)
 *   - صفحة خطأ أنيقة بزر **إعادة محاولة حقيقية** (عبر جسر AndroidApp.retry())
 *   - تنزيل الملفات (تصدير الدرجات/الجداول …) عبر DownloadManager
 *
 * كائن خالص بلا حالة ولا دورة حياة → آمن للاستخدام من أي نشاط.
 */
object WebViewSupport {

    private const val TAG = "WebViewSupport"

    /**
     * User-Agent موحّد. العلامتان "wv" و "SchoolAppYemen" تُستخدمان من JavaScript
     * للكشف عن أننا داخل تطبيق WebView (إلغاء target=_blank، سلوك ديناميكي…).
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; wv; SchoolAppYemen/1.0) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
        "Chrome/119.0.0.0 Mobile Safari/537.36"

    /** اسم جسر JavaScript المحقون: window.AndroidApp */
    const val JS_BRIDGE = "AndroidApp"

    /** إعدادات WebView الموحّدة لكل الشاشات. */
    fun applyDefaults(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = USER_AGENT
        }
        webView.isScrollbarFadingEnabled = true
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
    }

    /** هل الرابط من نطاقات Google الموثوقة (إعادة توجيه Apps Script، صور Drive…)؟ */
    fun isGoogleDomain(url: String): Boolean =
        url.contains("google.com") || url.contains("googleusercontent.com")

    /**
     * صفحة خطأ HTML موحّدة. زر «إعادة المحاولة» ينادي الجسر الأصلي
     * `AndroidApp.retry()` (وليس location.reload الذي كان يعيد تحميل صفحة الخطأ نفسها).
     */
    fun errorPageHtml(title: String, body: String, showRetry: Boolean): String {
        val retry = if (showRetry) {
            "<button onclick=\"window.$JS_BRIDGE && window.$JS_BRIDGE.retry()\" " +
                "style='padding:13px 34px;background:#0f3b5c;color:#fff;border:none;" +
                "border-radius:26px;font-size:16px;margin-top:20px;font-weight:bold;'>" +
                "إعادة المحاولة</button>"
        } else ""
        return """
            <!doctype html>
            <html dir='rtl' lang='ar'>
            <head>
              <meta charset='utf-8'>
              <meta name='viewport' content='width=device-width, initial-scale=1'>
            </head>
            <body style='margin:0;display:flex;flex-direction:column;align-items:center;
                         justify-content:center;min-height:100vh;text-align:center;
                         font-family:-apple-system,Segoe UI,Roboto,sans-serif;
                         background:#f8fafc;color:#334155;padding:24px;'>
              <div style='font-size:56px;margin-bottom:10px;'>⚠️</div>
              <h2 style='color:#e76f51;margin:6px 0;'>$title</h2>
              <p style='font-size:15px;line-height:1.7;color:#64748b;max-width:340px;'>$body</p>
              $retry
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * تنزيل ملف (تصدير Excel للدرجات/الجداول، شهادات … إلخ).
     * - أندرويد 10+ : عبر DownloadManager إلى مجلد التنزيلات (بلا صلاحيات).
     * - أقدم / روابط blob: : يُفتح في المتصفح ليتولّى التنزيل والصلاحيات.
     */
    fun handleDownload(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val canUseManager =
                url.startsWith("http") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            if (canUseManager) {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent ?: USER_AGENT)
                    setTitle(fileName)
                    setDescription("جارٍ التنزيل…")
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(context, "بدأ التنزيل: $fileName", Toast.LENGTH_SHORT).show()
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $url", e)
            Toast.makeText(context, "تعذّر بدء التنزيل", Toast.LENGTH_SHORT).show()
        }
    }
}
