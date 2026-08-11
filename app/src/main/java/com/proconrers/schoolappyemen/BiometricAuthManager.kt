package com.proconrers.schoolappyemen

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * BiometricAuthManager — «الدخول بالبصمة» لمنصّتَي المعلّم والطالب.
 *
 * ── لماذا هذا التصميم بالذات ──────────────────────────────────────────────────
 * التطبيق غلاف WebView: تسجيل الدخول يقع **داخل صفحة الويب** وتُدير الجلسةَ الصفحةُ
 * نفسها (`localStorage`). فلا يملك التطبيق حقول النموذج ولا آلية الجلسة. ⇒ التقسيم:
 *
 *   • **التطبيق يملك السرّ والبصمة** — تخزين مشفَّر + `BiometricPrompt`.
 *   • **الويب يملك النموذج** — هو وحده يعرف حقوله وكيف يُرسلها.
 *
 * والجسر **غير متزامن بالضرورة**: دوال `@JavascriptInterface` تُنفَّذ على خيط Binder ولا
 * يجوز أن تحجب انتظاراً لحوار. فالويب ينادي `AndroidApp.requestBiometricLogin(scope)`،
 * والتطبيق يعرض البصمة، ثم يردّ عبر `evaluateJavascript` بنداء
 * `window.__onBiometricLogin(scope, username, password)`. هذا يُبقي محدِّدات الحقول في
 * الويب (حيث تتغيّر) لا في Kotlin (حيث تتقادم بصمت مع أول إعادة تصميم).
 *
 * ── حدّ الحماية — مُعلَن لا مُخفَّف ────────────────────────────────────────────
 * البيانات تُخزَّن في [EncryptedSharedPreferences] بمفتاح من Android Keystore (تشفير
 * عند السكون، لا يخرج المفتاح من الجهاز). والبصمة هنا **بوّابة وصول** لا رباط تشفيري:
 * `security-crypto 1.0.0` لا يتيح `setUserAuthenticationRequired` على مفتاحه الرئيسي.
 * ⇒ مهاجم يملك الجهاز **مفتوحاً وبصلاحية root** يستطيع نظرياً قراءة السرّ دون بصمة.
 * الترقية الممكنة لاحقاً: مفتاح `KeyGenParameterSpec` مخصّص بـ`CryptoObject` مربوط
 * بالمصادقة الحيوية — أُجّل عمداً لأنه يتطلّب `security-crypto 1.1.0-alpha` في بناء
 * إنتاجي، وهو ما لا تحتمله مهلة 31 أغسطس.
 */
class BiometricAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "BiometricAuth"
        private const val PREFS_NAME = "secure_login_v1"

        /** النطاقات المسموحة — أي قيمة أخرى من الويب تُرفض (الويب مصدر غير موثوق). */
        private val ALLOWED_SCOPES = setOf("teacher", "student")

        private const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        fun normalizeScope(scope: String?): String? =
            scope?.trim()?.lowercase()?.takeIf { it in ALLOWED_SCOPES }
    }

    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 🔴 فشل إنشاء التخزين المشفَّر لا يُسقط التطبيق ولا يُعطّل الدخول العادي —
            //    الميزة كلّها تُصبح «غير متاحة» وحسب (كل الدوال أدناه تتحقّق من null).
            Log.e(TAG, "EncryptedSharedPreferences unavailable: ${e.message}")
            null
        }
    }

    /** هل الجهاز يدعم بصمة/قفل شاشة صالحاً للاستخدام الآن؟ */
    fun isAvailable(): Boolean =
        prefs != null &&
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun hasSavedLogin(scope: String): Boolean {
        val s = normalizeScope(scope) ?: return false
        val p = prefs ?: return false
        return !p.getString(keyUser(s), null).isNullOrBlank() &&
            !p.getString(keyPass(s), null).isNullOrBlank()
    }

    fun clear(scope: String) {
        val s = normalizeScope(scope) ?: return
        prefs?.edit()?.remove(keyUser(s))?.remove(keyPass(s))?.apply()
    }

    /**
     * يعرض تأكيداً بشرياً ثم يحفظ. **لا حفظ صامت أبداً**: تخزين كلمة مرور قرارُ
     * مستخدمٍ لا أثرٌ جانبي لتسجيل دخول ناجح.
     */
    fun offerSave(activity: FragmentActivity, scope: String, username: String, password: String) {
        val s = normalizeScope(scope) ?: return
        if (username.isBlank() || password.isBlank()) return
        if (!isAvailable()) return
        if (hasSavedLogin(s)) return   // مفعَّلة أصلاً — لا تسأل في كل دخول

        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle(R.string.biometric_save_title)
                .setMessage(R.string.biometric_save_message)
                .setPositiveButton(R.string.biometric_save_yes) { _, _ ->
                    prefs?.edit()
                        ?.putString(keyUser(s), username)
                        ?.putString(keyPass(s), password)
                        ?.apply()
                }
                .setNegativeButton(R.string.biometric_save_no, null)
                .show()
        }
    }

    /**
     * يطلب البصمة ثم يسلّم البيانات المحفوظة عبر [onSuccess].
     * [onFailure] يُستدعى لكل ما ليس نجاحاً (إلغاء · خطأ · لا بيانات محفوظة) مع سبب
     * قابل للعرض — كي لا يقف المستخدم أمام زرّ لا يفعل شيئاً بلا تفسير.
     */
    fun authenticate(
        activity: FragmentActivity,
        scope: String,
        onSuccess: (username: String, password: String) -> Unit,
        onFailure: (reason: String) -> Unit
    ) {
        val s = normalizeScope(scope)
        if (s == null) { onFailure("نطاق غير معروف"); return }
        val p = prefs
        if (p == null) { onFailure("التخزين الآمن غير متاح على هذا الجهاز"); return }
        if (!hasSavedLogin(s)) { onFailure("لا توجد بيانات دخول محفوظة"); return }
        if (!isAvailable()) { onFailure("لا توجد بصمة أو قفل شاشة مفعَّل"); return }

        activity.runOnUiThread {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val u = p.getString(keyUser(s), "").orEmpty()
                        val pw = p.getString(keyPass(s), "").orEmpty()
                        if (u.isBlank() || pw.isBlank()) onFailure("تعذّر قراءة البيانات المحفوظة")
                        else onSuccess(u, pw)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onFailure(errString.toString())
                    }

                    override fun onAuthenticationFailed() {
                        // بصمة لم تُطابِق — النظام يسمح بمحاولة أخرى، فلا نُغلق شيئاً هنا.
                    }
                }
            )

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_title))
                .setSubtitle(activity.getString(R.string.biometric_subtitle))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()

            try {
                prompt.authenticate(info)
            } catch (e: Exception) {
                Log.e(TAG, "authenticate failed: ${e.message}")
                onFailure("تعذّر فتح مطالبة البصمة")
            }
        }
    }

    private fun keyUser(scope: String) = "${scope}_user"
    private fun keyPass(scope: String) = "${scope}_pass"
}
