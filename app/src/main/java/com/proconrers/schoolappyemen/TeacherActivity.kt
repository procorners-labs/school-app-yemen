package com.proconrers.schoolappyemen

import android.content.Intent

/**
 * TeacherActivity — منصة المعلمين.
 * كل منطق الـ WebView في [BaseWebViewActivity]؛ هنا فقط الرابط والتوجيه.
 */
class TeacherActivity : BaseWebViewActivity() {

    override val startUrl: String get() = AppConfig.TEACHER_URL
    override val logTag: String = "TeacherWebView"

    override fun routeUrl(url: String): Boolean = when {
        // رابط منصة المعلم نفسه → يبقى داخل هذا الـ WebView
        AppConfig.isTeacherUrl(url) -> false

        // رابط منصة الطالب → نفتح StudentActivity
        AppConfig.isStudentUrl(url) -> {
            startActivity(Intent(this, StudentActivity::class.java)); true
        }

        // CMS أو الصفحة الرئيسية → نعود إلى MainActivity وننهي الحالي
        AppConfig.isCmsUrl(url) || AppConfig.isHomeUrl(url) -> {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish(); true
        }

        // نطاقات Google الموثوقة → تبقى داخل الـ WebView
        WebViewSupport.isGoogleDomain(url) -> false

        // أي رابط خارجي → متصفح النظام
        else -> { openExternal(url); true }
    }
}
