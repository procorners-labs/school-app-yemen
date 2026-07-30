package com.proconrers.schoolappyemen

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * BiometricLock — قفل بصمة/وجه لبوابتَي المعلّم والطالب.
 *
 * **ما يفعله بدقّة (ولا أكثر):** بوّابة عرض محلّية على الجهاز. الجلسة الحقيقية تبقى كما هي في
 * صفحة الويب (`localStorage`، ٨ ساعات) ولا يلمسها هذا الملف إطلاقاً — فلا كلمة مرور ولا توكن
 * يُخزَّن أو يُشتَقّ هنا. الفائدة الأمنية الفعلية: جهاز مفتوح بيد شخص آخر لا يفتح لوحة معلّم
 * فيها درجات/بيانات طلاب دون بصمة صاحب الجهاز.
 *
 * **قرارات تصميم مقصودة:**
 *  - `BIOMETRIC_WEAK` فقط، **بلا** `DEVICE_CREDENTIAL`: الدمج بينهما غير مدعوم على
 *    API 28–29 في `androidx.biometric` ويُنتج أخطاء وقت التشغيل. الزرّ السلبي يتولّى الإلغاء.
 *  - القفل **لا يُفعَّل تلقائياً**: يبقى مُطفأً حتى يُفعّله المستخدم صراحةً (زرّ في صفحة الدخول
 *    عبر الجسر `AndroidApp.setBiometricEnabled(true)`). ميزة أمان تُفرَض بلا طلب = بلاغ عطل.
 *  - مهلة سماح (60 ثانية) في الذاكرة فقط: التنقّل بين نشاطَي المعلّم/الطالب لا يُعيد السؤال،
 *    لكن إعادة تشغيل التطبيق تُعيد القفل دائماً (لا حالة على القرص).
 */
object BiometricLock {

    private const val TAG = "BiometricLock"
    private const val PREFS_NAME = "security_prefs"
    private const val KEY_ENABLED = "biometric_lock_enabled"
    private const val GRACE_PERIOD_MS = 60_000L

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** آخر مصادقة ناجحة — في الذاكرة فقط عمداً. */
    private var lastAuthAtMs = 0L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** هل الجهاز يدعم بصمة/وجهاً مُسجَّلاً فعلاً الآن؟ */
    fun isAvailable(context: Context): Boolean = try {
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) {
        Log.w(TAG, "canAuthenticate failed: ${e.message}")
        false
    }

    /** هل يوجد عتاد بصمة لكن بلا أي بصمة مُسجَّلة؟ (نصّ إرشاد مختلف للمستخدم) */
    fun needsEnrollment(context: Context): Boolean = try {
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    } catch (e: Exception) {
        false
    }

    /** هل القفل مُفعَّل **ومُتاح** فعلاً؟ (إن أُلغيت البصمات من الإعدادات يسقط تلقائياً) */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false) && isAvailable(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) lastAuthAtMs = 0L
    }

    /** هل يجب طلب البصمة الآن قبل عرض المحتوى؟ */
    fun needsAuth(context: Context): Boolean {
        if (!isEnabled(context)) return false
        return (System.currentTimeMillis() - lastAuthAtMs) > GRACE_PERIOD_MS
    }

    fun markAuthenticated() {
        lastAuthAtMs = System.currentTimeMillis()
    }

    /** يُبطِل مهلة السماح (يُستدعى عند تسجيل الخروج) فيُطلَب القفل من جديد. */
    fun invalidate() {
        lastAuthAtMs = 0L
    }

    /**
     * يعرض حوار البصمة. [onResult] يُستدعى على الخيط الرئيسي دائماً:
     *  - `true` نجاح.
     *  - `false` إلغاء/فشل نهائي (المستدعي يقرّر: إبقاء القفل أو الخروج).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "تأكيد الهوية",
        subtitle: String = "استخدم بصمتك لفتح المنصّة",
        onResult: (Boolean) -> Unit
    ) {
        if (!isAvailable(activity)) {
            onResult(true) // لا عتاد/لا بصمة ⇒ لا تحجب المستخدم عن منصّته
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                markAuthenticated()
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.d(TAG, "auth error $errorCode: $errString")
                onResult(false)
            }
            // onAuthenticationFailed (بصمة غير مطابقة) لا يُنهي المحاولة — النظام يُعيد السؤال.
        }
        try {
            val prompt = BiometricPrompt(activity, executor, callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("إلغاء")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info)
        } catch (e: Exception) {
            Log.e(TAG, "prompt failed", e)
            onResult(true) // فشل نظام لا يُعامَل كفشل مستخدم — لا نحجب الوصول
        }
    }
}
