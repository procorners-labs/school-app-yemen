package com.proconrers.schoolappyemen

/**
 * TeacherActivity — منصة المعلمين.
 * كل منطق الـ WebView في [BaseWebViewActivity]؛ هنا فقط هوية الشاشة.
 * وجدول التوجيه كاملاً في [AppConfig.routeTargetFor] ينفّذه [LinkRouter].
 */
class TeacherActivity : BaseWebViewActivity() {

    override val startUrl: String get() = AppConfig.TEACHER_URL
    override val logTag: String = "TeacherWebView"
    override val screenTarget: AppConfig.LinkTarget = AppConfig.LinkTarget.TEACHER
}
