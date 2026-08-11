package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
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
    private var pendingClearHistory = false
    private lateinit var netController: NetworkReloadController
    private var updateChecker: UpdateChecker? = null
    private var mandatoryDialogShown = false

    private val mainUrl: String get() = AppConfig.HOME_URL

    /**
     * الرابط المطلوب فعلياً لهذه الشاشة: ما حمله الـIntent (‏Deep Link · ضغطة إشعار ·
     * تنقّل من منصّة أخرى) وإلّا الصفحة الرئيسية. يُتحقَّق منه أنه داخلي قبل تحميله —
     * `EXTRA_TARGET_URL` يصل من `DeepLinkActivity` المُصدَّر، فلا يُوثَق به بلا فحص.
     */
    private val requestedUrl: String
        get() = intent?.getStringExtra(AppConfig.EXTRA_TARGET_URL)
            ?.takeIf { AppConfig.isInternalUrl(it) }
            ?: mainUrl

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

    // إشعارات: Android 13+ يتطلّب طلب صلاحية POST_NOTIFICATIONS صراحةً وقت التشغيل.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* لا حاجة لأي إجراء إضافي — منح/رفض يُحترَم تلقائياً عند عرض أي إشعار لاحق */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 🔴 لا تُضِف `WindowCompat.setDecorFitsSystemWindows(window, false)` هنا:
        //   `enableEdgeToEdge()` أعلاه يفعل ذلك بنفسه، والنداء الصريح **متوقّف نهائياً**
        //   ورصده Play Console على vc31 بتحذيرَين («العرض حتى حافة الشاشة قد لا تكون
        //   مفعّلة لدى جميع المستخدمين» + «واجهات متوقّفة نهائياً»). حُذف 2026-08-12.

        AppConfig.init(applicationContext)
        requestNotificationPermissionIfNeeded()

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
        checkForUpdates()
        netController = NetworkReloadController(this) { onNetworkAvailable() }
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.progress_indicator_color)
        )

        val target = requestedUrl
        Log.d(TAG, "Loading URL: $target")
        if (WebViewSupport.isOnline(this)) {
            loadTarget(target)
        } else {
            lastFailedUrl = target
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

    /**
     * يستبدل إشعار VPN الثابت السابق (كان يظهر في صفحات الويب لكل زائر بلا تمييز). يعرض بانراً
     * أصلياً غير حاجب فقط لمن لديه فعلاً إصدار قديم — القيمة المرجعية تأتي من دالة GAS جديدة
     * `checkAppVersion` (راجع UpdateChecker.kt).
     */
    private fun checkForUpdates() {
        val checker = UpdateChecker(this)
        updateChecker = checker
        checker.cachedUpdateInfo()?.let { showUpdate(it) }
        checker.checkIfNeeded { info -> showUpdate(info) }
    }

    private fun showUpdate(info: UpdateChecker.UpdateInfo) {
        if (info.mandatory) showMandatoryUpdateDialog(info) else showUpdateBanner(info)
    }

    /**
     * تحديث إجباري: حوار غير قابل للإلغاء — الإصدار المثبَّت دون الحدّ الأدنى المدعوم.
     * 🔴 لا يظهر إلا إذا ضبط المالك `ANDROID_MIN_SUPPORTED_VERSION_CODE_<pkg>` صراحةً؛
     *    لا قيمة افتراضية تستطيع إقفال التطبيق على أحد بالخطأ.
     */
    private fun showMandatoryUpdateDialog(info: UpdateChecker.UpdateInfo) {
        if (isFinishing || isDestroyed || mandatoryDialogShown) return
        mandatoryDialogShown = true
        AlertDialog.Builder(this)
            .setTitle("تحديث مطلوب")
            .setMessage(info.message)
            .setCancelable(false)
            .setPositiveButton("تحديث الآن") { _, _ ->
                LinkRouter.openExternal(this, info.playUrl)
            }
            .show()
    }

    private fun showUpdateBanner(info: UpdateChecker.UpdateInfo) {
        val playUrl = info.playUrl
        if (isFinishing || isDestroyed) return
        if (binding.mainContainer.findViewWithTag<View>(UPDATE_BANNER_TAG) != null) return

        val banner = LinearLayout(this).apply {
            tag = UPDATE_BANNER_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 20, 20, 20)
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@MainActivity, R.color.brand_primary))
                cornerRadius = 24f
            }
            elevation = 8f
        }

        val messageText = TextView(this).apply {
            // النصّ من الخادم — يتغيّر بلا إصدار جديد، وهذا شرط الوصول لمن لم يحدّث.
            text = info.message
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val updateBtn = TextView(this).apply {
            text = "تحديث"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 13f
            setPadding(24, 0, 24, 0)
            setOnClickListener { LinkRouter.openExternal(this@MainActivity, playUrl) }
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(16, 0, 8, 0)
            // إخفاءٌ ليوم واحد لا للأبد — كان الإغلاق يمحو البانر لهذه الجلسة فقط بلا
            // أثر، فيعود عند كل إقلاع؛ والآن يُحترَم الرفض مؤقّتاً ثم يُذكَّر المستخدم.
            setOnClickListener {
                updateChecker?.snooze()
                binding.mainContainer.removeView(banner)
            }
        }

        banner.addView(messageText)
        banner.addView(updateBtn)
        banner.addView(closeBtn)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            setMargins(24, 0, 24, 24)
        }
        binding.mainContainer.addView(banner, params)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        WebViewSupport.applyDefaults(webView)

        webView.addJavascriptInterface(
            SchoolJsBridge(
                this,
                webView,
                { lastFailedUrl ?: mainUrl }
            ) { enabled -> swipeRefresh.isEnabled = enabled },
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
                        // توجيه نفس الـ URL كما لو كانت نقرة عادية.
                        // ‏`HOME` هنا يعني «نفس هذه الشاشة» فلا يفعل الموجّه شيئاً —
                        // وهو الصحيح: النافذة المنبثقة أُلغيت للتوّ والصفحة قائمة.
                        LinkRouter.handle(this@MainActivity, url, AppConfig.LinkTarget.HOME)
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
                return LinkRouter.handle(
                    this@MainActivity, url, AppConfig.LinkTarget.HOME,
                    isMainFrame = request.isForMainFrame
                )
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                swipeRefresh.isEnabled = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                if (url != null && url.startsWith("http")) {
                    showingError = false
                    WebViewSupport.injectFcmToken(view, this@MainActivity)
                }
                // بعد العودة من منصة المعلم/الطالب: امسح سجل التنقل بعد اكتمال
                // تحميل الرئيسية → زر الرجوع يعرض dialog الخروج مباشرة (حالة نظيفة)
                if (pendingClearHistory && url != null && url.startsWith("http")) {
                    pendingClearHistory = false
                    view?.clearHistory()
                }
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
        val target = requestedUrl
        Log.d(TAG, "onNewIntent: loading $target")
        pendingClearHistory = true
        loadTarget(target)
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
        private const val UPDATE_BANNER_TAG = "update_banner"
    }
}
