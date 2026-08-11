package com.proconrers.schoolappyemen

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.FileOutputStream

/**
 * SchoolJsBridge — جسر JavaScript ↔ أندرويد (‏`window.AndroidApp`).
 *
 *   - `retry()` — يعيد تحميل الرابط الهدف الحقيقي (بدل `location.reload` الذي كان يعيد
 *     صفحة الخطأ نفسها).
 *   - `saveBase64()` — يحفظ ملفاً وُلِّد في المتصفّح (‏`blob:` مثل تصدير Excel) إلى «التنزيلات».
 *   - `setSwipeRefreshEnabled()` — يعطّل/يفعّل SwipeRefreshLayout الأصلي عند فتح الدرج الجانبي.
 *   - **`getFcmToken()`** (vc32) — رمز جهاز الإشعارات، ليرفعه الويب بعد الدخول عبر
 *     `registerDeviceTokenProtected`. كان الويب يبحث عن `AndroidPush.getToken` أو
 *     `SchoolAppNative.getFcmToken` — واسمُ الجسر الفعلي `AndroidApp`، فلم يجد شيئاً قطّ
 *     وبقيت ورقة «أجهزة_الإشعارات» فارغة (‏`ZERO_READERS`).
 *   - **بصمة** (vc32): `isBiometricAvailable` · `hasBiometricLogin` · `requestBiometricLogin`
 *     · `saveBiometricLogin` · `clearBiometricLogin` — راجع [BiometricAuthManager].
 *
 * 🔴 كل دالّة هنا تُنفَّذ على **خيط Binder** لا على خيط الواجهة، وأي لمس للواجهة يمرّ
 *    بـ`runOnUiThread`. وأي دالّة تحتاج انتظار المستخدم **لا تُرجِع نتيجة** بل ترُدّ
 *    لاحقاً بـ`evaluateJavascript` — الحجب هنا يُجمّد الصفحة.
 *
 * @param targetUrl دالة تُرجِع الرابط الذي يجب إعادة تحميله عند `retry()`.
 * @param setSwipeEnabled دالة تُبدِّل حالة `SwipeRefreshLayout.isEnabled`.
 */
class SchoolJsBridge(
    private val activity: FragmentActivity,
    private val webView: WebView,
    private val targetUrl: () -> String,
    private val setSwipeEnabled: (Boolean) -> Unit
) {

    private val biometric by lazy { BiometricAuthManager(activity) }

    @JavascriptInterface
    fun retry() {
        activity.runOnUiThread { webView.loadUrl(targetUrl()) }
    }

    @JavascriptInterface
    fun setSwipeRefreshEnabled(enabled: Boolean) {
        activity.runOnUiThread { setSwipeEnabled(enabled) }
    }

    // ── إشعارات ───────────────────────────────────────────────────────────────

    /** رمز جهاز FCM المحفوظ محلياً، أو نصّ فارغ إن لم يصل بعد. */
    @JavascriptInterface
    fun getFcmToken(): String = SchoolFcmService.savedToken(activity)

    // ── الدخول بالبصمة ────────────────────────────────────────────────────────

    @JavascriptInterface
    fun isBiometricAvailable(): Boolean = biometric.isAvailable()

    @JavascriptInterface
    fun hasBiometricLogin(scope: String): Boolean = biometric.hasSavedLogin(scope)

    /** يُستدعى بعد دخول ناجح — يعرض تأكيداً بشرياً ثم يحفظ. لا حفظ صامت. */
    @JavascriptInterface
    fun saveBiometricLogin(scope: String, username: String, password: String) {
        biometric.offerSave(activity, scope, username, password)
    }

    @JavascriptInterface
    fun clearBiometricLogin(scope: String) {
        biometric.clear(scope)
    }

    /**
     * يطلب البصمة ويردّ **غير متزامن** على الويب:
     *   نجاح ⇒ `window.__onBiometricLogin(scope, username, password)`
     *   إخفاق ⇒ `window.__onBiometricLoginFailed(scope, reason)`
     * الويب هو من يملأ حقوله ويُرسل — فلا محدِّدات نموذج داخل Kotlin تتقادم بصمت.
     */
    @JavascriptInterface
    fun requestBiometricLogin(scope: String) {
        biometric.authenticate(
            activity, scope,
            onSuccess = { u, p -> callJs("window.__onBiometricLogin", scope, u, p) },
            onFailure = { reason -> callJs("window.__onBiometricLoginFailed", scope, reason) }
        )
    }

    /** ينادي دالّة في الصفحة بوسائط نصّية مُهرَّبة بـJSON — الحارس `typeof` يمنع أي رمي. */
    private fun callJs(fn: String, vararg args: String) {
        val encoded = args.joinToString(",") { org.json.JSONObject.quote(it) }
        val js = "if (typeof $fn === 'function') { $fn($encoded); }"
        activity.runOnUiThread {
            try {
                webView.evaluateJavascript(js, null)
            } catch (e: Exception) {
                Log.e(TAG, "callJs failed: ${e.message}")
            }
        }
    }

    // ── حفظ الملفات المولَّدة في المتصفّح ──────────────────────────────────────

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

    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val TAG = "SchoolJsBridge"
    }
}
