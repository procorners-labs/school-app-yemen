package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.proconrers.schoolappyemen.databinding.ActivityWebviewBinding

/**
 * BaseWebViewActivity — الأساس المشترك لمنصّتَي الطالب والمعلّم.
 *
 * يوحّد كل منطق الـ WebView في مكان واحد (كان مكرّراً حرفياً بين النشاطين):
 *   - الإعدادات الموحّدة + الكوكيز
 *   - شريط التقدّم + رفع الملفات (متعدّد) + تنزيل الملفات (يشمل blob/Excel)
 *   - معالجة SSL للنطاقات الموثوقة
 *   - صفحة خطأ بإعادة محاولة **حقيقية** عبر جسر AndroidApp
 *   - تجاهل أخطاء الموارد الفرعية (لا نُخفي الصفحة كاملةً لفشل صورة)
 *   - سحب-للتحديث (SwipeRefresh)
 *   - الصمود عند انهيار محرّك عرض WebView (onRenderProcessGone)
 *   - **وعي بالاتصال**: فحص مسبق + إعادة تحميل تلقائية عند عودة الإنترنت
 *
 * كل نشاط فرعي يحدّد فقط: [startUrl] و[logTag] ومنطق [routeUrl].
 */
abstract class BaseWebViewActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityWebviewBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var lastFailedUrl: String? = null
    private var showingError = false
    private lateinit var netController: NetworkReloadController

    /** الرابط الأولي الذي يُحمَّل عند فتح الشاشة. */
    protected abstract val startUrl: String

    /** وسم السجل (Logcat). */
    protected abstract val logTag: String

    /**
     * منطق التوجيه الخاص بكل منصة.
     * @return true إذا عُولج الرابط خارجياً/بنشاط آخر (لا يُكمل التحميل هنا)،
     *         false ليبقى الرابط داخل هذا الـ WebView.
     */
    protected abstract fun routeUrl(url: String): Boolean

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
        super.onCreate(savedInstanceState)
        AppConfig.init(applicationContext)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        netController = NetworkReloadController(this) { onNetworkAvailable() }

        setupWebView()
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.progress_indicator_color)
        )
        binding.swipeRefresh.setOnRefreshListener { loadTarget(binding.webView.url ?: startUrl) }

        // فحص مسبق للاتصال: إن لا إنترنت، أظهِر صفحة الخطأ فوراً بدل تحميل فاشل
        if (WebViewSupport.isOnline(this)) {
            loadTarget(startUrl)
        } else {
            lastFailedUrl = startUrl
            showError(sslError = false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        WebViewSupport.applyDefaults(binding.webView)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        // جسر JS: إعادة محاولة حقيقية + حفظ ملفات blob (تصدير Excel)
        binding.webView.addJavascriptInterface(
            SchoolJsBridge(this, binding.webView) { lastFailedUrl ?: startUrl },
            WebViewSupport.JS_BRIDGE
        )

        // تنزيل الملفات (http عبر DownloadManager، blob عبر الجسر)
        WebViewSupport.installDownloadHandler(binding.webView, this)

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
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
                binding.progressBar.progress = newProgress
            }

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.d(logTag, "JS: ${m.message()} [${m.sourceId()}:${m.lineNumber()}]")
                return true
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                // صفحة حقيقية حُمّلت بنجاح → ألغِ حالة الخطأ
                if (url != null && url.startsWith("http")) showingError = false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return routeUrl(url)
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
                    Log.e(logTag, "SSL REJECTED: $failing")
                    lastFailedUrl = failing.ifBlank { startUrl }
                    stopIndicators()
                    showError(sslError = true)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                Log.e(logTag, "HTTP ${errorResponse?.statusCode} for ${request?.url}")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // تجاهل أخطاء الموارد الفرعية (صور/أيقونات) — لا نُخفي الصفحة كاملةً
                if (request?.isForMainFrame != true) return
                Log.e(logTag, "Error ${error?.errorCode}: ${error?.description}")
                lastFailedUrl = request.url?.toString() ?: startUrl
                stopIndicators()
                showError(sslError = false)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                // صمود: محرّك عرض WebView انهار (نفاد ذاكرة عادةً) → لا نُسقط التطبيق
                Log.e(logTag, "Render process gone (crashed=${detail?.didCrash()})")
                (binding.webView.parent as? ViewGroup)?.removeView(binding.webView)
                binding.webView.destroy()
                recreate()
                return true
            }
        }
    }

    /** يحمّل رابطاً حقيقياً ويُلغي حالة الخطأ. */
    private fun loadTarget(url: String) {
        showingError = false
        binding.webView.loadUrl(url)
    }

    /** عند عودة الاتصال: إن كنا على صفحة خطأ، أعِد التحميل تلقائياً. */
    private fun onNetworkAvailable() {
        if (showingError) {
            android.widget.Toast.makeText(
                this, "تمت استعادة الاتصال — جارٍ إعادة التحميل", android.widget.Toast.LENGTH_SHORT
            ).show()
            loadTarget(lastFailedUrl ?: startUrl)
        }
    }

    private fun stopIndicators() {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun showError(sslError: Boolean) {
        showingError = true
        val title = if (sslError) "خطأ في الاتصال الآمن" else "عذراً، تعذّر تحميل الصفحة"
        val body = if (sslError) {
            "تعذّر التحقق من أمان الاتصال بالخادم. إذا استمرت المشكلة تواصل مع الدعم الفني."
        } else {
            "تأكد من اتصالك بالإنترنت. سنعيد التحميل تلقائياً عند عودة الاتصال، أو اضغط إعادة المحاولة."
        }
        binding.webView.loadDataWithBaseURL(
            null,
            WebViewSupport.errorPageHtml(title, body, showRetry = !sslError),
            "text/html", "UTF-8", null
        )
    }

    /** فتح رابط خارجي في متصفح النظام (متاح للأنشطة الفرعية). */
    protected fun openExternal(url: String) {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e(logTag, "Cannot open external URL: $url", e)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        netController.start()
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        netController.stop()
    }

    override fun onDestroy() {
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }
}
