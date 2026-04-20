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

class TeacherActivity : AppCompatActivity() {

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

        // URL sourced from AppConfig — update there if deployment changes
        binding.webView.loadUrl(AppConfig.TEACHER_URL)

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
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
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

            // ─── SSL handling: trusted Google domains only, via AppConfig ──────
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
        private const val TAG = "TeacherWebView"
    }
}