package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.proconrers.schoolappyemen.databinding.ActivityMainBinding

/**
 * MainActivity — بوابة الدخول الرئيسية (الموقع الرسمي العام للمدرسة).
 *
 * يعرض HOME_URL (الأخبار/الصور/الإحصائيات + أزرار المنصات). التوجيه:
 *   - الموقع الرسمي / CMS  → يبقى داخل هذا الـ WebView
 *   - منصة المعلمين/الطلاب → يفتح النشاط المخصّص
 *   - نطاقات Google         → يبقى داخل WebView (إعادة توجيه Apps Script)
 *   - أي رابط خارجي         → متصفح النظام
 *
 * المنطق المشترك (إعدادات، تنزيل، صفحة خطأ، إعادة محاولة) في [WebViewSupport].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var lastFailedUrl: String? = null

    private val mainUrl: String get() = AppConfig.HOME_URL

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = fileUploadCallback ?: return@registerForActivityResult
        val data = result.data
        val results: Array<Uri>? =
            if (result.resultCode == RESULT_OK && data != null) {
                val clip = data.clipData
                when {
                    clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                    data.data != null -> arrayOf(data.data!!)
                    else -> null
                }
            } else null
        cb.onReceiveValue(results)
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppConfig.init(applicationContext)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        CookieManager.getInstance().setAcceptCookie(true)

        buildViews()
        setupWebView()

        Log.d(TAG, "Loading HOME URL: $mainUrl")
        webView.loadUrl(mainUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun buildViews() {
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        swipeRefresh = SwipeRefreshLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(webView)
            setOnRefreshListener { webView.reload() }
        }
        binding.mainContainer.addView(swipeRefresh)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            max = 100
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 12)
        }
        binding.mainContainer.addView(progressBar)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        WebViewSupport.applyDefaults(webView)

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun retry() {
                runOnUiThread { webView.loadUrl(lastFailedUrl ?: mainUrl) }
            }
        }, WebViewSupport.JS_BRIDGE)

        webView.setDownloadListener { url, ua, cd, mime, _ ->
            WebViewSupport.handleDownload(this, url, ua, cd, mime)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                return if (intent != null) {
                    try {
                        fileChooserLauncher.launch(intent); true
                    } catch (e: Exception) {
                        fileUploadCallback = null; false
                    }
                } else {
                    fileUploadCallback = null; false
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return when {
                    AppConfig.isHomeUrl(url) -> false
                    AppConfig.isTeacherUrl(url) -> {
                        startActivity(Intent(this@MainActivity, TeacherActivity::class.java)); true
                    }
                    AppConfig.isStudentUrl(url) -> {
                        startActivity(Intent(this@MainActivity, StudentActivity::class.java)); true
                    }
                    AppConfig.isCmsUrl(url) -> false
                    WebViewSupport.isGoogleDomain(url) -> false
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

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val failing = error?.url ?: view?.url ?: ""
                if (AppConfig.isTrustedSslDomain(failing)) {
                    handler?.proceed()
                } else {
                    handler?.cancel()
                    Log.e(TAG, "SSL REJECTED: $failing")
                    lastFailedUrl = failing.ifBlank { mainUrl }
                    stopIndicators()
                    showError(sslError = true)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame != true) return
                Log.e(TAG, "Error ${error?.errorCode}: ${error?.description}")
                lastFailedUrl = request.url?.toString() ?: mainUrl
                stopIndicators()
                showError(sslError = false)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                Log.e(TAG, "Render process gone (crashed=${detail?.didCrash()})")
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
                recreate()
                return true
            }
        }
    }

    private fun stopIndicators() {
        progressBar.visibility = View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun showError(sslError: Boolean) {
        val title = if (sslError) "خطأ في الاتصال الآمن" else "عذراً، تعذّر الاتصال"
        val body = if (sslError) {
            "تعذّر التحقق من أمان الاتصال بالخادم. إذا استمرت المشكلة تواصل مع الدعم الفني."
        } else {
            "تأكد من اتصالك بالإنترنت ثم اضغط إعادة المحاولة."
        }
        webView.loadDataWithBaseURL(
            null,
            WebViewSupport.errorPageHtml(title, body, showRetry = !sslError),
            "text/html", "UTF-8", null
        )
    }

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
