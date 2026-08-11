package com.proconrers.schoolappyemen

/**
 * StudentActivity — منصة الطلاب وأولياء الأمور.
 * كل منطق الـ WebView في [BaseWebViewActivity]؛ هنا فقط هوية الشاشة.
 * وجدول التوجيه كاملاً في [AppConfig.routeTargetFor] ينفّذه [LinkRouter].
 */
class StudentActivity : BaseWebViewActivity() {

    override val startUrl: String get() = AppConfig.STUDENT_URL
    override val logTag: String = "StudentWebView"
    override val screenTarget: AppConfig.LinkTarget = AppConfig.LinkTarget.STUDENT
}
