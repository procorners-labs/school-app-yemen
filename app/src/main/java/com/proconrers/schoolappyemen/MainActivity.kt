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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // ─── Trusted domains that are permitted to proceed past SSL errors ────────
    // Includes all Google sub-services used by Apps Script web apps.
    // Any domain NOT in this list will have its SSL error rejected and the
    // user will see an Arabic error page. Never add wildcard entries here.
    private val trustedSslDomains = listOf(
        "google.com",
        "script.google.com",
        "script.googleusercontent.com",
        "googleusercontent.com",
        "googleapis.com",
        "gstatic.com",
        "docs.google.com",
        "drive.google.com",
        "accounts.google.com"
    )

    private val mainUrl = AppConfig.CMS_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        CookieManager.getInstance().setAcceptCookie(true)

        initViews()
        setupWebView()
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
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()

                return when {
                    // Route to TeacherActivity when the Teacher script key is detected
                    AppConfig.isTeacherUrl(url) -> {
                        startActivity(Intent(this@MainActivity, TeacherActivity::class.java))
                        true
                    }
                    // Route to StudentActivity when the Student script key is detected
                    AppConfig.isStudentUrl(url) -> {
                        startActivity(Intent(this@MainActivity, StudentActivity::class.java))
                        true
                    }
                    // Let all Google domains load inside this WebView
                    url.contains("google.com") || url.contains("googleusercontent.com") -> {
                        false
                    }
                    // Open any other external URL in the system browser
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

            // ─── FIX: Restricted SSL bypass — trusted domains only ─────────────
            // Previously: handler?.proceed() was called for ALL domains (security bug).
            // Now: only domains in trustedSslDomains list proceed; all others are
            // rejected and the user sees an Arabic error page.
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val failingUrl = error?.url ?: view?.url ?: ""
                val isTrusted = trustedSslDomains.any { domain ->
                    failingUrl.contains(domain, ignoreCase = true)
                }

                if (isTrusted) {
                    Log.d(TAG, "SSL error accepted for trusted domain: $failingUrl")
                    handler?.proceed()
                } else {
                    Log.e(TAG, "SSL error REJECTED for untrusted domain: $failingUrl " +
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

    // ─── Error pages ──────────────────────────────────────────────────────────

    private fun showErrorPage() {
        val html = buildErrorHtml(
            title = "عذراً، تعذر الاتصال",
            body = "يرجى التأكد من اتصالك بالإنترنت ثم حاول مجدداً.",
            showRetry = true
        )
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun showSslErrorPage(url: String) {
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

    // ─── Back navigation ──────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ─── WebView lifecycle ────────────────────────────────────────────────────

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