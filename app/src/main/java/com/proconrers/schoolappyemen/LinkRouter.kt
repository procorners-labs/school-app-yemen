package com.proconrers.schoolappyemen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * LinkRouter — تنفيذ قرار [AppConfig.routeTargetFor] عملياً.
 *
 * كان منطق التوجيه مكرّراً حرفياً في أربعة مواضع (‏`MainActivity.shouldOverrideUrlLoading`
 * و`MainActivity.onCreateWindow` و`TeacherActivity.routeUrl` و`StudentActivity.routeUrl`)
 * بأربع صياغات `when` مختلفة — وأي قاعدة جديدة كانت تحتاج تعديلها كلها أو تنحرف إحداها
 * صامتةً. هنا موضع واحد.
 */
object LinkRouter {

    private const val TAG = "LinkRouter"

    /**
     * يوجّه رابطاً انطلاقاً من نشاط يمثّل [current].
     *
     * @param isMainFrame 🔴 حرجة: `shouldOverrideUrlLoading` تُستدعى **للإطارات الفرعية
     *        أيضاً** على WebView الحديثة. وصفحاتنا تُضمِّن فيديو Drive ويوتيوب داخل
     *        `<iframe>` — فبلا هذا الشرط يُقذَف تحميلُ الإطار إلى تطبيق خارجي ويظهر
     *        مكان الفيديو فارغاً. لم يكن مفحوصاً قبل vc32.
     * @return `true` إذا عُولج الرابط خارج هذا الـWebView (فلا يُكمل التحميل هنا).
     */
    fun handle(
        activity: Activity,
        url: String,
        current: AppConfig.LinkTarget,
        isMainFrame: Boolean = true
    ): Boolean {
        if (!isMainFrame) return false

        return when (AppConfig.routeTargetFor(url)) {
            AppConfig.LinkTarget.STAY -> false
            current -> false

            AppConfig.LinkTarget.EXTERNAL -> {
                openExternal(activity, url); true
            }

            AppConfig.LinkTarget.TEACHER -> {
                open(activity, TeacherActivity::class.java, url, finishCurrent = false); true
            }

            AppConfig.LinkTarget.STUDENT -> {
                open(activity, StudentActivity::class.java, url, finishCurrent = false); true
            }

            // العودة إلى الواجهة العامّة تُغلق شاشة المنصّة بدل تكديسها
            // (‏سلوك محفوظ حرفياً عمّا كان في `TeacherActivity`/`StudentActivity`).
            AppConfig.LinkTarget.HOME -> {
                open(activity, MainActivity::class.java, url, finishCurrent = true); true
            }
        }
    }

    /**
     * يفتح النشاط الهدف حاملاً الرابط **الفعلي** المطلوب — لا الرابط الافتراضي.
     * هذا ما يجعل رابط خبر مشارَك أو ضغطة إشعار تصل صفحتَها بدل الصفحة الرئيسية.
     */
    private fun open(
        activity: Activity,
        target: Class<*>,
        url: String,
        finishCurrent: Boolean
    ) {
        val intent = Intent(activity, target).apply {
            putExtra(AppConfig.EXTRA_TARGET_URL, url)
            if (finishCurrent) {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        activity.startActivity(intent)
        if (finishCurrent) activity.finish()
    }

    fun openExternal(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open external URL: $url", e)
        }
    }
}
