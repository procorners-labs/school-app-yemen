package com.proconrers.schoolappyemen

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * SchoolJsBridge — جسر JavaScript ↔ أندرويد (window.AndroidApp).
 *
 * **القديم (كما هو، بلا تغيير سلوكي):**
 *   - retry(): يعيد تحميل الرابط الهدف الحقيقي (إصلاح زرّ «إعادة المحاولة»).
 *   - saveBase64(): يحفظ ملفاً وُلِّد داخل المتصفح (blob: مثل تصدير Excel) إلى «التنزيلات».
 *   - setSwipeRefreshEnabled(): يعطّل/يفعّل SwipeRefreshLayout الأصلي عند فتح الدرج الجانبي.
 *
 * **الجديد (2026-07-30):**
 *   - getAppInfo(): يُعرِّف الصفحة بإصدار التطبيق وقدراته (بصمة/إشعارات) ⇒ واجهة تتكيّف
 *     ديناميكياً بلا تخمين UA.
 *   - isBiometricAvailable / isBiometricEnabled / setBiometricEnabled / promptBiometric:
 *     قفل البصمة يُدار من صفحة الدخول نفسها (تفعيل صريح بيد المستخدم — راجع [BiometricLock]).
 *   - notify / playAlertSound: إشعار محلّي + صوت التنبيه المخصّص لحدث حيّ داخل الصفحة.
 *   - getPushToken(): توكن FCM للتسجيل بآلية المنصّة القائمة.
 *   - openPlayStore(): يفتح صفحة التطبيق على Play (زرّ «تحديث» داخل الصفحة يعمل أصلياً).
 *
 * **حارس السياق (أمان):** كل دالّة جديدة حسّاسة تتحقّق من [pageTrusted] — يضبطه النشاط من
 * `onPageStarted` بحسب [AppConfig.isTrustedSslDomain] للصفحة المحمَّلة فعلاً. السبب: الجسر
 * يُحقَن في الـWebView لا في صفحة بعينها، فأي صفحة تُفتَح داخله (إعادة توجيه غير متوقّعة مثلاً)
 * كانت ستملك نفس الصلاحيات. `retry`/`saveBase64`/`setSwipeRefreshEnabled` تبقى بلا حارس —
 * سلوكها القائم لا يجب أن يتغيّر (`retry` تحديداً تعمل على **صفحة خطأ** بلا أصل موثوق).
 *
 * @param targetUrl دالة تُرجِع الرابط الذي يجب إعادة تحميله عند retry().
 * @param setSwipeEnabled دالة تُبدِّل حالة SwipeRefreshLayout.isEnabled.
 */
class SchoolJsBridge(
    private val activity: Activity,
    private val webView: WebView,
    private val targetUrl: () -> String,
    private val setSwipeEnabled: (Boolean) -> Unit
) {

    /** يُضبَط من الخيط الرئيسي عند كل تنقّل صفحة؛ يُقرأ من خيط الجسر ⇒ Volatile. */
    @Volatile
    private var pageTrusted = false

    fun setPageTrusted(trusted: Boolean) {
        pageTrusted = trusted
    }

    // ─── القديم ───────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun retry() {
        activity.runOnUiThread { webView.loadUrl(targetUrl()) }
    }

    @JavascriptInterface
    fun setSwipeRefreshEnabled(enabled: Boolean) {
        activity.runOnUiThread { setSwipeEnabled(enabled) }
    }

    @JavascriptInterface
    fun saveBase64(dataUrl: String, fileName: String, mimeType: String) {
        try {
            // dataUrl صيغته: data:<mime>;base64,<DATA> → نأخذ ما بعد الفاصلة
            val base64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val name = fileName.ifBlank { "school_download_${System.currentTimeMillis()}" }
            val mime = mimeType.ifBlank { "application/octet-stream" }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, name)).use { it.write(bytes) }
            }
            toast("تم حفظ الملف في «التنزيلات»: $name")
        } catch (e: Exception) {
            Log.e(TAG, "saveBase64 failed", e)
            toast("تعذّر حفظ الملف")
        }
    }

    // ─── هوية التطبيق وقدراته ────────────────────────────────────────────────

    /**
     * JSON يصف التطبيق المضيف. تستخدمه الصفحة لإظهار/إخفاء ما يخصّ التطبيق (زرّ البصمة،
     * دعوة التحديث…) بلا الاعتماد على تحليل UA.
     */
    @JavascriptInterface
    fun getAppInfo(): String = try {
        JSONObject()
            .put("platform", "android")
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("biometricAvailable", BiometricLock.isAvailable(activity))
            .put("biometricEnrolled", !BiometricLock.needsEnrollment(activity))
            .put("biometricEnabled", BiometricLock.isEnabled(activity))
            .put("notificationsAllowed", NotificationHelper.canNotify(activity))
            .put("activeHost", AppConfig.activeHost)
            .toString()
    } catch (e: Exception) {
        "{}"
    }

    // ─── قفل البصمة ───────────────────────────────────────────────────────────

    @JavascriptInterface
    fun isBiometricAvailable(): Boolean = BiometricLock.isAvailable(activity)

    @JavascriptInterface
    fun isBiometricEnabled(): Boolean = BiometricLock.isEnabled(activity)

    /** يُفعّل/يُطفئ القفل. يُرجِع الحالة الفعلية بعد المحاولة (false إن تعذّر التفعيل). */
    @JavascriptInterface
    fun setBiometricEnabled(enabled: Boolean): Boolean {
        if (!pageTrusted) return BiometricLock.isEnabled(activity)
        if (enabled && !BiometricLock.isAvailable(activity)) {
            toast(
                if (BiometricLock.needsEnrollment(activity))
                    "لا توجد بصمة مُسجَّلة على هذا الجهاز — أضِفها من إعدادات النظام أولاً"
                else
                    "هذا الجهاز لا يدعم البصمة"
            )
            return false
        }
        BiometricLock.setEnabled(activity, enabled)
        if (enabled) BiometricLock.markAuthenticated() // لا تسأل فوراً بعد تفعيل واعٍ
        toast(if (enabled) "تم تفعيل قفل البصمة ✅" else "تم إيقاف قفل البصمة")
        return BiometricLock.isEnabled(activity)
    }

    /**
     * يطلب البصمة الآن ويُعيد النتيجة إلى دالّة JS باسم [callbackName] (تُستدعى بـ`true`/`false`).
     * الاسم يُنظَّف من أي حرف غير `A-Za-z0-9_$.` ⇒ لا حقن تعبير.
     */
    @JavascriptInterface
    fun promptBiometric(callbackName: String) {
        if (!pageTrusted) return
        val safeCb =
            callbackName.filter { it.isLetterOrDigit() || it == '_' || it == '$' || it == '.' }
        if (safeCb.isBlank()) return
        activity.runOnUiThread {
            val fragmentActivity = activity as? FragmentActivity
            if (fragmentActivity == null) {
                evaluate("if(typeof $safeCb==='function'){$safeCb(false);}")
                return@runOnUiThread
            }
            BiometricLock.authenticate(fragmentActivity) { ok ->
                evaluate("if(typeof $safeCb==='function'){$safeCb($ok);}")
            }
        }
    }

    // ─── الإشعارات والصوت ────────────────────────────────────────────────────

    /** إشعار نظام محلّي (نفس قناة FCM وصوتها المخصّص). */
    @JavascriptInterface
    fun notify(title: String, body: String) {
        if (!pageTrusted) return
        if (title.isBlank() && body.isBlank()) return
        NotificationHelper.show(
            context = activity.applicationContext,
            title = title.ifBlank { activity.getString(R.string.app_name) },
            body = body,
            targetPage = null,
            targetUrl = null
        )
    }

    /** صوت التنبيه المخصّص فقط (تنبيه داخل التطبيق وهو مفتوح، بلا إشعار نظام). */
    @JavascriptInterface
    fun playAlertSound() {
        if (!pageTrusted) return
        NotificationHelper.playChime(activity.applicationContext)
    }

    /** توكن FCM المحفوظ محلياً (فارغ إن لم يُصدَر بعد أو الصفحة غير موثوقة). */
    @JavascriptInterface
    fun getPushToken(): String =
        if (!pageTrusted) "" else (SchoolFcmService.cachedToken(activity) ?: "")

    // ─── التحديث ─────────────────────────────────────────────────────────────

    /** يفتح صفحة التطبيق على Google Play (زرّ التحديث داخل الصفحة). */
    @JavascriptInterface
    fun openPlayStore() {
        if (!pageTrusted) return
        activity.runOnUiThread {
            val market = "market://details?id=${activity.packageName}"
            val web = "https://play.google.com/store/apps/details?id=${activity.packageName}"
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(market)))
            } catch (e: Exception) {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)))
                } catch (e2: Exception) {
                    Log.e(TAG, "cannot open Play Store", e2)
                }
            }
        }
    }

    // ─── أدوات داخلية ─────────────────────────────────────────────────────────

    private fun evaluate(js: String) {
        activity.runOnUiThread {
            try {
                webView.evaluateJavascript(js, null)
            } catch (e: Exception) {
                Log.w(TAG, "evaluateJavascript failed: ${e.message}")
            }
        }
    }

    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }
    }

    companion object {
        private const val TAG = "SchoolJsBridge"
    }
}
