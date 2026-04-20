package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
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

    private val mainUrl = "https://script.google.com/macros/s/AKfycbzDfGEK6IpChVNl9k8xbt_iv5p6bLOktt-TvEzDp8yBpH3Ga3yNMen_0S2ZyuuvGtKFCA/exec"

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
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                return when {
                    url.contains("AKfycbwbiM1NdYlHf4XPpeftVcrJPmcrPJWm7KS2sSL4qtzZDMDtYo4sGdx6T-p8fAIArvND") -> {
                        startActivity(Intent(this@MainActivity, TeacherActivity::class.java))
                        true
                    }
                    url.contains("AKfycbz6wFJBq6RUg7buXM5LIGfEa4eVXZguPeIyrkg-T-kbOUhWlJMypO3Ame6lmcHzdcwq") -> {
                        startActivity(Intent(this@MainActivity, StudentActivity::class.java))
                        true
                    }
                    url.contains("google.com") || url.contains("googleusercontent.com") -> {
                        false
                    }
                    else -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showErrorPage()
                }
            }
        }
    }

    private fun showErrorPage() {
        val errorHtml = """
            <html dir='rtl'>
            <body style='text-align:center; padding-top:100px; font-family:sans-serif; background:#f8fafc;'>
                <h2 style='color:#e76f51;'>⚠️ عذراً، تعذر الاتصال</h2>
                <p>يرجى التأكد من الإنترنت ثم حاول مجدداً</p>
                <button onclick='location.reload()' style='padding:10px 20px; background:#0f3b5c; color:white; border:none; border-radius:5px;'>إعادة المحاولة</button>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}