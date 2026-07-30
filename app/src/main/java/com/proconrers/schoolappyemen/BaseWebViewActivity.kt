package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
    private var lastLoggedInUrl: String? = null
    private var showingError = false
    private lateinit var netController: NetworkReloadController
    private lateinit var jsBridge: SchoolJsBridge

    /** حارس تجاوز فشل النطاق: محاولة واحدة لكل صفحة ناجحة (يُصفَّر في onPageFinished). */
    private var failoverAttempted = false

    /** يمنع إطلاق حوار البصمة مرّتين متتاليتين (النظام قد يُطلق onPause/onResume أثناء عرضه). */
    private var biometricPromptInFlight = false

    /** الرابط الأولي الذي يُحمَّل عند فتح الشاشة. */
    protected abstract val startUrl: String

    /**
     * الرابط الفعلي: يسمح لإشعار (FCM) بفتح صفحة بعينها عبر [NotificationHelper.EXTRA_START_URL]
     * — **بشرط** أن يكون على أحد نطاقات المنصّة. بلا هذا الشرط تستطيع حمولة إشعار تحميل صفحة
     * عشوائية داخل WebView يحمل جلسة مُصادَقة (وكوكيز، وجسر AndroidApp).
     */
    protected val effectiveStartUrl: String
        get() {
            val extra = intent?.getStringExtra(NotificationHelper.EXTRA_START_URL)
            return if (!extra.isNullOrBlank() && AppConfig.isPlatformUrl(extra)) extra else startUrl
        }

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppConfig.init(applicationContext)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // تطبيق padding ديناميكي للـ system bars + لوحة المفاتيح
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        netController = NetworkReloadController(this) { onNetworkAvailable() }

        setupWebView()
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.progress_indicator_color)
        )
        // استخدم lastLoggedInUrl عند التحديث لتجنّب العودة لصفحة الدخول
        binding.swipeRefresh.setOnRefreshListener {
            val target = binding.webView.url
                ?.takeIf { it.startsWith("http") }
                ?: lastLoggedInUrl
                ?: effectiveStartUrl
            loadTarget(target)
        }

        setupBiometricLock()
        checkForUpdates()

        // فحص مسبق للاتصال: إن لا إنترنت، أظهِر صفحة الخطأ فوراً بدل تحميل فاشل
        if (WebViewSupport.isOnline(this)) {
            loadTarget(effectiveStartUrl)
        } else {
            lastFailedUrl = effectiveStartUrl
            showError(sslError = false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    // لا يوجد تاريخ تنقل — تحقّق من حالة الجلسة عبر JS
                    // إصلاح: App.user (منصة المعلم) و APP.user (منصة الطالب)
                    // App.token لم يكن موجوداً في منصة المعلم → الـ dialog لم يظهر أبداً
                    binding.webView.evaluateJavascript(
                        "(function(){ " +
                        "  var t = (typeof App!=='undefined' && App.user); " +
                        "  var u = (typeof APP!=='undefined' && APP.user); " +
                        "  return (t||u) ? 'logged' : 'guest'; " +
                        "})()"
                    ) { result ->
                        when (result?.replace("\"", "")?.trim()) {
                            "logged" -> showLogoutConfirmDialog()
                            "guest"  -> navigateToMain()
                            // null أو قيمة أخرى = الصفحة لا تزال تحمّل → لا تخرج
                            else     -> { /* تجاهل — سيُعالج عند الضغط مجدداً */ }
                        }
                    }
                }
            }
        })
    }

    // ─── قفل البصمة ───────────────────────────────────────────────────────────

    /**
     * يُظهِر شاشة القفل ويطلب البصمة **مرّة واحدة** تلقائياً عند الفتح. القفل يعمل فقط إن فعّله
     * المستخدم صراحةً (راجع [BiometricLock]) — بلا ذلك لا يظهر شيء إطلاقاً.
     */
    private fun setupBiometricLock() {
        binding.btnUnlock.setOnClickListener { promptUnlock() }
        binding.btnLockBack.setOnClickListener { navigateToMain() }

        if (!BiometricLock.needsAuth(this)) {
            binding.lockOverlay.visibility = View.GONE
            return
        }
        binding.lockOverlay.visibility = View.VISIBLE
        promptUnlock()
    }

    private fun promptUnlock() {
        if (biometricPromptInFlight) return
        biometricPromptInFlight = true
        BiometricLock.authenticate(this) { ok ->
            biometricPromptInFlight = false
            if (ok) binding.lockOverlay.visibility = View.GONE
            // فشل/إلغاء: الشاشة تبقى — المستخدم يعيد المحاولة بالزرّ أو يرجع للرئيسية.
        }
    }

    // ─── دعوة التحديث ─────────────────────────────────────────────────────────

    /**
     * بطاقة «تحديث متاح» — تظهر لمن لديه إصدار أقدم فعلاً فقط، وتختفي من تلقاء نفسها بعد
     * التحديث (المقارنة بـ`versionCode` الحقيقي للحزمة المثبَّتة، لا بعلمٍ مخزَّن).
     */
    private fun checkForUpdates() {
        val checker = UpdateChecker(this)
        checker.cachedPlayUrlIfOutdated()?.let { url ->
            if (checker.cachedUpdateIsMandatory()) UpdateBanner.showBlocking(this, url)
            else UpdateBanner.show(this, binding.bannerHost, url)
        }
        checker.checkIfNeeded { url, mandatory ->
            if (mandatory) UpdateBanner.showBlocking(this, url)
            else UpdateBanner.show(this, binding.bannerHost, url)
        }
    }

    /**
     * الانتقال الصريح إلى MainActivity بدل مجرد finish() —
     * يضمن العودة للصفحة الرئيسية حتى لو لم تكن في back stack.
     */
    private fun navigateToMain() {
        startActivity(
            android.content.Intent(this, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }

    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("تسجيل الخروج")
            .setMessage("هل تريد الخروج من حسابك؟")
            .setPositiveButton("نعم، أخرج") { _, _ ->
                // استدعاء دالة الخروج في الصفحة (إن وُجدت)
                binding.webView.evaluateJavascript(
                    "if(typeof doLogout==='function')doLogout();" +
                    "else if(typeof confirmLogout==='function')confirmLogout();",
                    null
                )
                binding.webView.clearHistory()
                // خروج واعٍ ⇒ أبطِل مهلة سماح البصمة كي يُطلَب القفل عند الدخول التالي
                BiometricLock.invalidate()
                // إصلاح: العودة للصفحة الرئيسية بدل إغلاق التطبيق
                navigateToMain()
            }
            .setNegativeButton("لا، ابقَ") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        WebViewSupport.applyDefaults(binding.webView)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        // جسر JS: إعادة محاولة حقيقية + حفظ ملفات blob (تصدير Excel) +
        // تعطيل/تفعيل SwipeRefreshLayout الأصلي (يستدعيه الدرج الجانبي في منصة المعلم)
        jsBridge = SchoolJsBridge(
            this,
            binding.webView,
            { lastFailedUrl ?: effectiveStartUrl }
        ) { enabled -> binding.swipeRefresh.isEnabled = enabled }
        binding.webView.addJavascriptInterface(jsBridge, WebViewSupport.JS_BRIDGE)

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
                // ضمانة: أي تنقّل صفحة جديد يعيد تفعيل PTR الأصلي — يمنع بقاءه معطَّلاً
                // للأبد لو انقطع تنفيذ closeSidebar() في JS (خطأ/تنقّل مفاجئ والدرج مفتوح).
                binding.swipeRefresh.isEnabled = true
                // حارس سياق الجسر: يُضبَط هنا (الخيط الرئيسي) لأن قراءة webView.url من خيط
                // الجسر غير آمنة — WebView ليس thread-safe.
                jsBridge.setPageTrusted(url != null && AppConfig.isTrustedSslDomain(url))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                if (url != null && url.startsWith("http")) {
                    showingError = false
                    failoverAttempted = false
                    // النطاق الذي نجح فعلاً يصبح النطاق النشِط للإقلاعات التالية
                    AppConfig.noteSuccessfulUrl(url)
                    // حفظ آخر URL حقيقي (ليس loginScreen) لاستخدامه عند التحديث
                    if (!url.contains("loginScreen") && url != effectiveStartUrl) {
                        lastLoggedInUrl = url
                    }
                }
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
                    lastFailedUrl = failing.ifBlank { effectiveStartUrl }
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
                val failed = request.url?.toString() ?: effectiveStartUrl
                lastFailedUrl = failed

                // تجاوز فشل النطاق: النطاقات الثلاثة على نفس الـWorker ⇒ نفس الاستجابة.
                // إن حُجب النطاق الحالي (سابقة workers.dev على «يمن نت») نُجرّب التالي فوراً
                // بدل إظهار «لا إنترنت» لمستخدم إنترنته يعمل.
                val next = if (!failoverAttempted) AppConfig.nextHostUrl(failed) else null
                if (next != null) {
                    failoverAttempted = true
                    Log.w(logTag, "Host failover → $next")
                    stopIndicators()
                    loadTarget(next)
                    return
                }

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
            loadTarget(lastFailedUrl ?: effectiveStartUrl)
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
        // عودة بعد غياب طويل ⇒ أعِد إظهار شاشة القفل (بلا إطلاق الحوار تلقائياً هنا: النظام قد
        // يُطلق onPause/onResume أثناء عرض حوار البصمة نفسه ⇒ حلقة لا نهائية).
        if (BiometricLock.needsAuth(this)) {
            binding.lockOverlay.visibility = View.VISIBLE
        }
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
