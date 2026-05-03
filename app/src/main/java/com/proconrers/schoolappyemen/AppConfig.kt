package com.proconrers.schoolappyemen

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * AppConfig — المصدر المركزي لروابط النشر، مع جلب ديناميكي من الخادم.
 *
 * المعمارية الجديدة:
 *  1. تُحفَظ الروابط الحالية في SharedPreferences (ذاكرة دائمة على الجهاز)
 *  2. عند الإقلاع: تُجلَب الروابط الأحدث من الخادم في الخلفية وتُحدَّث
 *  3. إذا فشل الجلب: تُستخدم القيم المحفوظة، أو الافتراضيات لو كانت أول مرة
 *
 * هذا يعني:
 *  - عند إعادة نشر أي منصة: حدّث ScriptProperties في Apps Script فقط
 *  - التطبيق سيلتقط التحديث في الإقلاع التالي (بدون نشر APK جديد)
 */
object AppConfig {

    private const val TAG = "AppConfig"
    private const val PREFS_NAME = "deployment_config_v2"
    private const val PREFS_LAST_UPDATE = "last_update_ts"

    // مفاتيح SharedPreferences
    private const val KEY_HOME     = "url_home"
    private const val KEY_CMS      = "url_cms"
    private const val KEY_TEACHER  = "url_teacher"
    private const val KEY_STUDENT  = "url_student"
    private const val KEY_SCHEDULE = "url_schedule"
    private const val KEY_MASTER   = "url_master"

    // ─── الروابط الافتراضية (تُستخدم فقط في الإقلاع الأول قبل أول مزامنة) ──
    private const val DEFAULT_HOME =
        "https://script.google.com/macros/s/AKfycbzDfGEK6IpChVNl9k8xbt_iv5p6bLOktt-TvEzDp8yBpH3Ga3yNMen_0S2ZyuuvGtKFCA/exec"
    private const val DEFAULT_CMS =
        "https://script.google.com/macros/s/AKfycbz-iAj9L3ROOn4CAjmwkVBUqpWuxIx1LkgPLwKnHu7kHLWKCy3GVJNo1vZbnekop0VlMA/exec"
    private const val DEFAULT_TEACHER =
        "https://script.google.com/macros/s/AKfycbwbiM1NdYlHf4XPpeftVcrJPmcrPJWm7KS2sSL4qtzZDMDtYo4sGdx6T-p8fAIArvND/exec"
    private const val DEFAULT_STUDENT =
        "https://script.google.com/macros/s/AKfycbz6wFJBq6RUg7buXM5LIGfEa4eVXZguPeIyrkg-T-kbOUhWlJMypO3Ame6lmcHzdcwq/exec"
    private const val DEFAULT_SCHEDULE =
        "https://script.google.com/macros/s/AKfycbwbsWcoOZ23TUWDtxVTV1RyG2LJ7IYWTWuk9Jt-15OeB1JgqRIyGSRxZo3NB8ZI2ag/exec"
    private const val DEFAULT_MASTER =
        "https://script.google.com/macros/s/AKfycbx5H6uYXb-6iVt_nT4YkdnYMhl6eZJSDxsULsKa2eyblZQcwzRo4CXR3Mh_ecRSZd4M/exec"

    // مدة الكاش — 6 ساعات (إعادة الجلب فقط بعد هذه المدة)
    private const val SYNC_INTERVAL_MS = 6L * 60L * 60L * 1000L

    private var prefs: SharedPreferences? = null
    private var initialized = false

    /**
     * تهيئة AppConfig — تُستدعى مرة واحدة في SplashActivity أو Application
     */
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialized = true

        // مزامنة في الخلفية لو مرّ وقت كافٍ منذ آخر تحديث
        syncIfNeeded()
    }

    // ─── واجهة الاستخدام (URLs) ───────────────────────────────────────────────
    val HOME_URL: String get() = read(KEY_HOME, DEFAULT_HOME)
    val CMS_URL: String get() = read(KEY_CMS, DEFAULT_CMS)
    val TEACHER_URL: String get() = read(KEY_TEACHER, DEFAULT_TEACHER)
    val STUDENT_URL: String get() = read(KEY_STUDENT, DEFAULT_STUDENT)
    val SCHEDULE_URL: String get() = read(KEY_SCHEDULE, DEFAULT_SCHEDULE)
    val MASTER_URL: String get() = read(KEY_MASTER, DEFAULT_MASTER)

    private fun read(key: String, defaultValue: String): String {
        return prefs?.getString(key, defaultValue) ?: defaultValue
    }

    // ─── دوال التوجيه (تُستخدم في shouldOverrideUrlLoading) ──────────────────
    fun isTeacherUrl(url: String): Boolean = matchesDeployment(url, TEACHER_URL)
    fun isStudentUrl(url: String): Boolean = matchesDeployment(url, STUDENT_URL)
    fun isCmsUrl(url: String): Boolean = matchesDeployment(url, CMS_URL)
    fun isHomeUrl(url: String): Boolean = matchesDeployment(url, HOME_URL)

    fun isKnownDeployment(url: String): Boolean =
        isTeacherUrl(url) || isStudentUrl(url) || isCmsUrl(url) || isHomeUrl(url)

    /**
     * مقارنة ذكية — تستخرج الـ deployment ID وتُطابقه
     * (يحلّ مشكلة إعادة التوجيه عبر /macros/r/)
     */
    private fun matchesDeployment(url: String, deploymentUrl: String): Boolean {
        if (url.isBlank() || deploymentUrl.isBlank()) return false
        val id = extractDeploymentId(deploymentUrl) ?: return false
        return url.contains(id, ignoreCase = true)
    }

    private fun extractDeploymentId(url: String): String? {
        val regex = Regex("""/macros/s/([^/]+)/exec""")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    // ─── النطاقات الموثوقة (SSL) ──────────────────────────────────────────────
    val trustedSslDomains: List<String> = listOf(
        "google.com",
        "script.google.com",
        "script.googleusercontent.com",
        "googleusercontent.com",
        "googleapis.com",
        "gstatic.com",
        "docs.google.com",
        "drive.google.com",
        "accounts.google.com"
    )

    fun isTrustedSslDomain(url: String): Boolean =
        trustedSslDomains.any { domain -> url.contains(domain, ignoreCase = true) }

    // ─── المزامنة من الخادم ──────────────────────────────────────────────────

    /**
     * يجلب الروابط الأحدث من Apps Script في خيط خلفي
     * يُستدعى تلقائياً من init() عند الحاجة
     */
    private fun syncIfNeeded() {
        val p = prefs ?: return
        val lastUpdate = p.getLong(PREFS_LAST_UPDATE, 0L)
        val now = System.currentTimeMillis()

        if ((now - lastUpdate) < SYNC_INTERVAL_MS) {
            Log.d(TAG, "Sync skipped (last: ${(now - lastUpdate) / 1000}s ago)")
            return
        }

        thread(start = true, isDaemon = true, name = "AppConfig-Sync") {
            try {
                val baseUrl = HOME_URL  // أي منصة فيها DeploymentRegistry تكفي
                val syncUrl = if (baseUrl.contains("?")) {
                    "$baseUrl&action=deployments"
                } else {
                    "$baseUrl?action=deployments"
                }
                Log.d(TAG, "Syncing from: $syncUrl")

                val conn = (URL(syncUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseAndStore(response)
                    if (parsed) {
                        p.edit().putLong(PREFS_LAST_UPDATE, now).apply()
                        Log.d(TAG, "✅ AppConfig synced successfully")
                    }
                } else {
                    Log.w(TAG, "Sync HTTP ${conn.responseCode}")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed: ${e.message}")
            }
        }
    }

    /**
     * تحليل JSON من الخادم وحفظ الروابط
     */
    private fun parseAndStore(jsonText: String): Boolean {
        return try {
            val outer = JSONObject(jsonText)
            if (!outer.optBoolean("success", false)) return false
            val data = outer.optJSONObject("data") ?: return false

            val editor = prefs?.edit() ?: return false
            data.optString("home").takeIf { isValidUrl(it) }?.let { editor.putString(KEY_HOME, it) }
            data.optString("cms").takeIf { isValidUrl(it) }?.let { editor.putString(KEY_CMS, it) }
            data.optString("teacher").takeIf { isValidUrl(it) }?.let { editor.putString(KEY_TEACHER, it) }
            data.optString("student").takeIf { isValidUrl(it) }?.let { editor.putString(KEY_STUDENT, it) }
            data.optString("schedule").takeIf { isValidUrl(it) }?.let { editor.putString(KEY_SCHEDULE, it) }
            data.optString("master").takeIf { isValidUrl(it) }?.let { editor.putString(KEY_MASTER, it) }
            editor.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed: ${e.message}")
            false
        }
    }

    private fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.startsWith("https://script.google.com/macros/") && url.endsWith("/exec")
    }

    /**
     * فرض إعادة المزامنة الآن (للتطوير/التشخيص)
     */
    fun forceSync() {
        prefs?.edit()?.putLong(PREFS_LAST_UPDATE, 0L)?.apply()
        syncIfNeeded()
    }

    /**
     * إعادة تعيين كاملة (للتشخيص)
     */
    fun reset() {
        prefs?.edit()?.clear()?.apply()
    }
}