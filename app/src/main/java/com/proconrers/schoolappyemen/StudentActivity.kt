package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.proconrers.schoolappyemen.databinding.ActivityWebviewBinding

class StudentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (fileUploadCallback == null) return@registerForActivityResult
        val data: Intent? = result.data
        val results = if (result.resultCode == RESULT_OK && data != null) {
            val targetUri = data.data
            if (targetUri != null) arrayOf(targetUri) else null
        } else {
            null
        }
        fileUploadCallback?.onReceiveValue(results)
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()

        // تحميل عنوان URL الخاص بمنصة الطالب
        binding.webView.loadUrl(AppConfig.STUDENT_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            // ✅ نفس User‑Agent المعدل (لتثبيت اكتشاف WebView)
            userAgentString = "Mozilla/5.0 (Linux; Android 13; wv; SchoolAppYemen/1.0) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                    "Chrome/119.0.0.0 Mobile Safari/537.36"
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                if (intent != null) {
                    try {
                        fileChooserLauncher.launch(intent)
                    } catch (e: Exception) {
                        fileUploadCallback = null
                        return false
                    }
                } else {
                    fileUploadCallback = null
                    return false
                }
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.progress = newProgress
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(TAG, "JS: ${consoleMessage.message()} [${consoleMessage.sourceId()}]")
                return true
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
                Log.d(TAG, "Page started: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                Log.d(TAG, "Page finished: $url")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e(TAG, "HTTP ${errorResponse?.statusCode} for ${request?.url}")
            }

            // ─── ✅ التوجيه الذكي للروابط (معكوس: الطالب يبقى، المعلم ينتقل) ───
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return when {
                    // الرابط يخص منصة الطالب نفسه → نبقى داخل هذا الـ WebView
                    AppConfig.isStudentUrl(url) -> false

                    // الرابط يخص منصة المعلم → نفتح TeacherActivity
                    AppConfig.isTeacherUrl(url) -> {
                        startActivity(Intent(this@StudentActivity, TeacherActivity::class.java))
                        true
                    }

                    // الرابط يخص CMS أو الصفحة الرئيسية → نفتح MainActivity وننهي النشاط الحالي
                    AppConfig.isCmsUrl(url) || AppConfig.isHomeUrl(url) -> {
                        val intent = Intent(this@StudentActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish()
                        true
                    }

                    // الروابط التي تحتوي على نطاقات Google الموثوقة → نتركها للـ WebView
                    url.contains("google.com") || url.contains("googleusercontent.com") -> false

                    // أي رابط آخر (خارجي) → نفتحه في متصفح الهاتف
                    else -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Log.e(TAG, "Cannot open external URL: $url", e)
                        }
                        true
                    }
                }
            }

            // ─── SSL handling (نفس ما في TeacherActivity) ───
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val failingUrl = error?.url ?: view?.url ?: ""
                if (AppConfig.isTrustedSslDomain(failingUrl)) {
                    handler?.proceed()
                    Log.d(TAG, "SSL accepted for trusted domain: $failingUrl")
                } else {
                    handler?.cancel()
                    Log.e(TAG, "SSL REJECTED for untrusted domain: $failingUrl")
                    showErrorPage(view, sslError = true)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.url.toString().contains("favicon.ico")) return
                Log.e(TAG, "Error ${error?.errorCode}: ${error?.description}")
                binding.progressBar.visibility = View.GONE
                showErrorPage(view, sslError = false)
            }
        }
    }

    private fun showErrorPage(view: WebView?, sslError: Boolean) {
        val title = if (sslError) "خطأ في الاتصال الآمن" else "عذراً، تعذر تحميل الصفحة"
        val body = if (sslError) {
            "تعذر التحقق من أمان الاتصال. تواصل مع الدعم الفني."
        } else {
            "يرجى التأكد من اتصالك بالإنترنت ثم حاول مجدداً."
        }
        val html = """
            <html dir='rtl'>
            <head><meta name='viewport' content='width=device-width, initial-scale=1'></head>
            <body style='text-align:center;padding:50px 24px;font-family:sans-serif;
                         background:#f8fafc;color:#334155;'>
                <div style='font-size:50px;'>⚠️</div>
                <h2 style='color:#e76f51;'>$title</h2>
                <p>$body</p>
                ${if (!sslError) "<button onclick='location.reload()' style='padding:12px 30px;background:#0f3b5c;color:white;border:none;border-radius:25px;font-size:16px;cursor:pointer;'>إعادة المحاولة</button>" else ""}
            </body></html>
        """.trimIndent()
        view?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onDestroy() {
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StudentWebView"
    }
}