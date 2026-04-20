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

    // ✅ نظام اختيار الملفات الحديث (لرفع الواجبات والصور من الطلاب)
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

        // ✅ رابط منصة الطلاب وأولياء الأمور الصحيح
        val studentUrl = "https://script.google.com/macros/s/AKfycbz6wFJBq6RUg7buXM5LIGfEa4eVXZguPeIyrkg-T-kbOUhWlJMypO3Ame6lmcHzdcwq/exec"
        binding.webView.loadUrl(studentUrl)

        // ✅ معالج زر الرجوع (العودة للخلف داخل الموقع أو إغلاق الشاشة)
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
            domStorageEnabled = true          // ضروري لعمل روابط Google Scripts
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT

            // 🔥 الحل السحري لمنع خطأ ERR_CONNECTION_CLOSED
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            // دعم رفع الملفات من الهاتف
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

            // تحديث شريط التقدم (ProgressBar)
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.progress = newProgress
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                android.util.Log.d("StudentWebView", "${consoleMessage.message()} -- ${consoleMessage.sourceId()}")
                return true
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
                android.util.Log.d("StudentWebView", "Page started: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                android.util.Log.d("StudentWebView", "Page finished: $url")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                android.util.Log.e("StudentWebView", "HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
            }

            // ✅ تجاوز أخطاء SSL لنطاقات جوجل الموثوقة فقط
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val url = view?.url ?: ""
                if (url.contains("google.com") || url.contains("gstatic.com") || url.contains("googleusercontent.com")) {
                    handler?.proceed()
                    android.util.Log.d("StudentWebView", "SSL error accepted for trusted domain: $url")
                } else {
                    handler?.cancel()
                    android.util.Log.e("StudentWebView", "SSL error rejected for untrusted domain: $url")
                }
            }

            // ✅ عرض صفحة خطأ عربية احترافية عند تعذر التحميل
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.url.toString().contains("favicon.ico")) return

                android.util.Log.e("StudentWebView", "Error: ${error?.errorCode} - ${error?.description}")

                binding.progressBar.visibility = View.GONE
                val errorHtml = """
                    <html dir='rtl'>
                    <head><meta name='viewport' content='width=device-width, initial-scale=1'></head>
                    <body style='text-align:center; padding:50px; font-family:sans-serif; background:#f8fafc; color:#334155;'>
                        <div style='font-size:50px;'>⚠️</div>
                        <h2 style='color:#e76f51;'>عذراً، تعذر تحميل الصفحة</h2>
                        <p>يرجى التأكد من اتصالك بالإنترنت ثم حاول مجدداً.</p>
                        <button onclick='location.reload()' style='padding:12px 30px; background:#0f3b5c; color:white; border:none; border-radius:25px; font-size:16px; cursor:pointer;'>إعادة المحاولة</button>
                    </body>
                    </html>
                """.trimIndent()
                view?.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
            }
        }
    }
}