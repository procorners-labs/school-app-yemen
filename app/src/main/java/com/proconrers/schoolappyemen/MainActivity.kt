package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.proconrers.schoolappyemen.databinding.ActivityMainBinding

/**
 * MainActivity — بوابة الدخول الرئيسية لمنظومة مدارس الإبداع والتميز.
 *
 * ─── المسؤولية ─────────────────────────────────────────────────────────────
 * تعرض **الموقع الرسمي العام للمدرسة** (Code.gs / Index.html):
 *   - الأخبار، الصور، الفيديوهات، الإحصائيات
 *   - أزرار الانتقال إلى منصة المعلمين / الطلاب / CMS
 *
 * ─── سبب التغيير الجوهري (مهم) ────────────────────────────────────────────
 * النسخة السابقة كانت تحمّل `AppConfig.CMS_URL` تلقائياً عند الإقلاع، فيرى
 * المستخدم العادي صفحة إدارة المحتوى بدلاً من الموقع الرسمي الذي يخدم الزوار
 * وأولياء الأمور والطلاب. النسخة الحالية تحمّل `AppConfig.HOME_URL` افتراضياً،
 * وتفتح CMS فقط حين يطلبه المستخدم صراحةً عبر روابط داخل الموقع.
 *
 * ─── منطق التوجيه (shouldOverrideUrlLoading) ──────────────────────────────
 *   - رابط الموقع الرسمي  → يبقى داخل MainActivity (false)
 *   - رابط منصة المعلمين  → يفتح TeacherActivity
 *   - رابط منصة الطلاب    → يفتح StudentActivity
 *   - رابط CMS            → يبقى داخل MainActivity (نفس الـ WebView)
 *   - نطاقات Google موثوقة → يبقى داخل WebView (إعادة توجيه Apps Script)
 *   - أي رابط خارجي آخر    → يفتح في متصفح النظام
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    /**
     * الرابط الرئيسي الذي يُحمَّل عند بدء التطبيق.
     * ⭐ تم التغيير من CMS_URL إلى HOME_URL ليعرض الموقع الرسمي للمدرسة.
     * AppConfig يقرأ القيمة من SharedPreferences (المُحدَّثة من الخادم تلقائياً).
     */
    private val mainUrl: String get() = AppConfig.HOME_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ضمان تهيئة AppConfig (في حال جاء المستخدم من خارج SplashActivity)
        AppConfig.init(applicationContext)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        CookieManager.getInstance().setAcceptCookie(true)

        initViews()
        setupWebView()

        // ⭐ تحميل الموقع الرسمي للمدرسة (وليس CMS)
        Log.d(TAG, "Loading HOME URL: $mainUrl")
        webView.loadUrl(mainUrl)
    }

    private fun initViews() {
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        binding.mainContainer.addView(webView)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 12)
        }
        binding.mainContainer.addView(progressBar)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            // علامتا "wv" و "SchoolAppYemen" تُستخدمان من JavaScript للكشف
            // عن WebView وإلغاء target=_blank وضبط السلوك الديناميكي
            userAgentString = "Mozilla/5.0 (Linux; Android 13; wv; SchoolAppYemen/1.0) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                    "Chrome/119.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {

            // ─── ⭐ التوجيه الذكي للروابط ───
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                Log.d(TAG, "shouldOverride: $url")

                return when {
                    // الرابط يخص الموقع الرسمي → نبقى داخل MainActivity
                    AppConfig.isHomeUrl(url) -> false

                    // الرابط يخص منصة المعلمين → نفتح TeacherActivity
                    AppConfig.isTeacherUrl(url) -> {
                        startActivity(Intent(this@MainActivity, TeacherActivity::class.java))
                        true
                    }

                    // الرابط يخص منصة الطلاب → نفتح StudentActivity
                    AppConfig.isStudentUrl(url) -> {
                        startActivity(Intent(this@MainActivity, StudentActivity::class.java))
                        true
                    }

                    // الرابط يخص CMS → نبقى داخل WebView نفسه (ضمن MainActivity)
                    // المستخدم اختار الانتقال صراحةً عبر زر/رابط
                    AppConfig.isCmsUrl(url) -> false

                    // نطاقات Google الموثوقة (إعادة توجيه Apps Script، صور Drive، …)
                    url.contains("google.com") || url.contains("googleusercontent.com") -> false

                    // أي رابط خارجي آخر → يُفتح في متصفح النظام
                    else -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        } catch (e: Exception) {
                            Log.e(TAG, "Cannot open external URL: $url", e)
                        }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }

            // ─── معالجة SSL — نطاقات Google الموثوقة فقط ───
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val failingUrl = error?.url ?: view?.url ?: ""
                if (AppConfig.isTrustedSslDomain(failingUrl)) {
                    Log.d(TAG, "SSL accepted for trusted domain: $failingUrl")
                    handler?.proceed()
                } else {
                    Log.e(TAG, "SSL REJECTED for untrusted domain: $failingUrl " +
                            "| Error: ${error?.primaryError}")
                    handler?.cancel()
                    showSslErrorPage(failingUrl)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "Page load error: ${error?.errorCode} — ${error?.description}")
                    showErrorPage()
                }
            }
        }
    }

    // ─── صفحات الخطأ ────────────────────────────────────────────────────────

    private fun showErrorPage() {
        val html = buildErrorHtml(
            title = "عذراً، تعذر الاتصال",
            body = "يرجى التأكد من اتصالك بالإنترنت ثم حاول مجدداً.",
            showRetry = true
        )
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun showSslErrorPage(@Suppress("UNUSED_PARAMETER") url: String) {
        val html = buildErrorHtml(
            title = "تحذير: خطأ في الاتصال الآمن",
            body = "لم يتمكن التطبيق من التحقق من أمان الاتصال بالخادم.\n" +
                    "إذا استمرت المشكلة، تواصل مع الدعم الفني.",
            showRetry = false
        )
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun buildErrorHtml(title: String, body: String, showRetry: Boolean): String {
        val retryButton = if (showRetry) {
            "<button onclick='location.reload()' " +
                    "style='padding:12px 30px;background:#0f3b5c;color:white;" +
                    "border:none;border-radius:25px;font-size:16px;cursor:pointer;" +
                    "margin-top:16px;'>إعادة المحاولة</button>"
        } else ""

        return """
            <html dir='rtl'>
            <head><meta name='viewport' content='width=device-width, initial-scale=1'></head>
            <body style='text-align:center;padding:60px 24px;font-family:sans-serif;
                         background:#f8fafc;color:#334155;'>
                <div style='font-size:50px;margin-bottom:16px;'>⚠️</div>
                <h2 style='color:#e76f51;margin-bottom:12px;'>$title</h2>
                <p style='font-size:15px;line-height:1.6;color:#64748b;'>$body</p>
                $retryButton
            </body>
            </html>
        """.trimIndent()
    }

    // ─── زر الرجوع ─────────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ─── دورة حياة WebView ─────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainWebView"
    }
}