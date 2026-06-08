package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
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
    private var showingError = false
    private lateinit var netController: NetworkReloadController

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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        AppConfig.init(applicationContext)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        CookieManager.getInstance().setAcceptCookie(true)

        buildViews()
        setupWebView()
        netController = NetworkReloadController(this) { onNetworkAvailable() }
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.progress_indicator_color)
        )

        Log.d(TAG, "Loading HOME URL: $mainUrl")
        if (WebViewSupport.isOnline(this)) {
            loadTarget(mainUrl)
        } else {
            lastFailedUrl = mainUrl
            showError(sslError = false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("الخروج")
                        .setMessage("هل تريد الخروج من التطبيق؟")
                        .setPositiveButton("نعم") { _, _ -> finish() }
                        .setNegativeButton("لا") { d, _ -> d.dismiss() }
                        .show()
                }
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
            setOnRefreshListener { loadTarget(webView.url ?: mainUrl) }
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

        webView.addJavascriptInterface(
            SchoolJsBridge(this, webView) { lastFailedUrl ?: mainUrl },
            WebViewSupport.JS_BRIDGE
        )

        WebViewSupport.installDownloadHandler(webView, this)

        // شبكة أمان: target="_blank" تُعالَج عبر onCreateWindow
        webView.settings.setSupportMultipleWindows(true)

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

            /**
             * شبكة أمان لروابط target="_blank" التي لم تُعالَج بـ shouldOverrideUrlLoading.
             * نستخرج الـ URL ونوجّه النشاط المناسب (المعلمين / الطلاب / خارجي).
             */
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean {
                val transportWebView = WebView(this@MainActivity)
                transportWebView.settings.javaScriptEnabled = true
                transportWebView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView?, url: String?, fav: Bitmap?) {
                        url ?: return
                        // وقف التحميل فور معرفة الـ URL
                        transportWebView.stopLoading()
                        transportWebView.destroy()
                        // توجيه نفس الـ URL كما لو كانت نقرة عادية
                        when {
                            AppConfig.isTeacherUrl(url) ->
                                startActivity(Intent(this@MainActivity, TeacherActivity::class.java))
                            AppConfig.isStudentUrl(url) ->
                                startActivity(Intent(this@MainActivity, StudentActivity::class.java))
                            url.startsWith("http") && !AppConfig.isHomeUrl(url) &&
                                    !AppConfig.isCmsUrl(url) -> try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (e: Exception) {
                                Log.e(TAG, "Cannot open: $url", e)
                            }
                        }
                    }
                }
                (resultMsg?.obj as? WebView.WebViewTransport)?.also { transport ->
                    transport.webView = transportWebView
                    resultMsg.sendToTarget()
                }
                return true
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
                if (url != null && url.startsWith("http")) showingError = false
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

    private fun loadTarget(url: String) {
        showingError = false
        webView.loadUrl(url)
    }

    private fun onNetworkAvailable() {
        if (showingError) {
            android.widget.Toast.makeText(
                this, "تمت استعادة الاتصال — جارٍ إعادة التحميل", android.widget.Toast.LENGTH_SHORT
            ).show()
            loadTarget(lastFailedUrl ?: mainUrl)
        }
    }

    private fun showError(sslError: Boolean) {
        showingError = true
        val title = if (sslError) "خطأ في الاتصال الآمن" else "عذراً، تعذّر الاتصال"
        val body = if (sslError) {
            "تعذّر التحقق من أمان الاتصال بالخادم. إذا استمرت المشكلة تواصل مع الدعم الفني."
        } else {
            "تأكد من اتصالك بالإنترنت. سنعيد التحميل تلقائياً عند عودة الاتصال، أو اضغط إعادة المحاولة."
        }
        webView.loadDataWithBaseURL(
            null,
            WebViewSupport.errorPageHtml(title, body, showRetry = !sslError),
            "text/html", "UTF-8", null
        )
    }

    /**
     * يُستدعى عند العودة من TeacherActivity / StudentActivity عبر navigateToMain()
     * (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP). نُعيد تحميل الصفحة الرئيسية
     * لضمان حالة نظيفة: JavaScript UA-fix يعمل من جديد والأزرار جاهزة للنقر.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG, "onNewIntent: reloading home for fresh state")
        loadTarget(mainUrl)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        netController.start()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        netController.stop()
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
