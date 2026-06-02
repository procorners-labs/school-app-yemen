package com.proconrers.schoolappyemen

import android.content.Intent

/**
 * StudentActivity — منصة الطلاب وأولياء الأمور.
 * كل منطق الـ WebView في [BaseWebViewActivity]؛ هنا فقط الرابط والتوجيه.
 */
class StudentActivity : BaseWebViewActivity() {

    override val startUrl: String get() = AppConfig.STUDENT_URL
    override val logTag: String = "StudentWebView"

    override fun routeUrl(url: String): Boolean = when {
        // رابط منصة الطالب نفسه → يبقى داخل هذا الـ WebView
        AppConfig.isStudentUrl(url) -> false

        // رابط منصة المعلم → نفتح TeacherActivity
        AppConfig.isTeacherUrl(url) -> {
            startActivity(Intent(this, TeacherActivity::class.java)); true
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
