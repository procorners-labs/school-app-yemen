package com.proconrers.schoolappyemen

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * AppConfig — المصدر المركزي لروابط المنصّات، مع **تعدّد نطاقات + تجاوز فشل تلقائي**.
 *
 * المعمارية (2026-07-30):
 *  1. النطاق الأساسي `yemenschoolz.com` (النطاق الرسمي للمشروع — راجع بند 61 في
 *     `school-app-yemen-gas/CLAUDE.md`).
 *  2. نطاقان احتياطيان **لم يُحذَفا ولن يُحذَفا**: `school.procorners.com` (الإرثي) و
 *     `school-teacher-proxy.procorners-shop.workers.dev` (الأقدم، تعتمده النسخ المنشورة سابقاً).
 *     الثلاثة Custom Domains على **نفس** الـWorker ⇒ نفس الاستجابة بايتياً، فالتبديل بينها آمن
 *     تماماً ولا يُغيّر أي بيانات أو جلسة.
 *  3. عند فشل تحميل الإطار الرئيسي على النطاق الحالي (حجب DNS مثلاً — وقع فعلياً لـ`workers.dev`
 *     على «يمن نت»، راجع بند 20)، يُستدعى [nextHostUrl] فيُعاد المحاولة على النطاق التالي،
 *     ويُحفَظ النطاق الناجح في SharedPreferences فيصبح هو المستخدَم في الإقلاعات التالية.
 *
 * لماذا **لا** مزامنة GAS (`syncIfNeeded`) — قرار مقصود ومُصحَّح:
 *   `parseAndStore`/`isValidUrl` تقبل **فقط** روابط `script.google.com/macros/.../exec`. تفعيل
 *   المزامنة كما هو (كما اقترح `_docs/2026-07-26-تحديث-تطبيق-اندرويد-الاصلي-مؤجل.md` بند ٢) كان
 *   سيستبدل روابط الـWorker العاملة بروابط `script.google.com` **المحجوبة على يمن نت** ⇒ انحدار
 *   كامل لكل مستخدم يمني. تجاوز الفشل بين النطاقات أعلاه يحقّق نفس الهدف (صمود بلا إصدار جديد)
 *   بلا تلك المخاطرة. الآلية تُترَك في الكود بلا استدعاء للرجوع إليها إن تغيّرت دلالة النقطة.
 */
object AppConfig {

    private const val TAG = "AppConfig"
    private const val PREFS_NAME = "deployment_config_v3"
    private const val PREFS_LAST_UPDATE = "last_update_ts"
    private const val KEY_ACTIVE_HOST = "active_host"

    // مفاتيح SharedPreferences (روابط كاملة — تُستخدم فقط إن جاءت من مزامنة مستقبلية)
    private const val KEY_HOME     = "url_home"
    private const val KEY_CMS      = "url_cms"
    private const val KEY_TEACHER  = "url_teacher"
    private const val KEY_STUDENT  = "url_student"
    private const val KEY_SCHEDULE = "url_schedule"
    private const val KEY_MASTER   = "url_master"

    /**
     * معرّف «مدارس الإبداع والتميز الدولية» في السجل المركزي `Master_Admin_School` — يُمرَّر
     * صراحةً في كل رابط بدل الاعتماد على السقوط الافتراضي لمدرسة المالك (تعدّد المستأجرين يُحلّ
     * عبر `_Tenant.js`/`withAuth` من هذا المعامل).
     */
    const val EBDAA_SCHOOL_ID = "12725ed7-c139-422c-a2d1-ec0ddd358104"

    /** النطاق الأساسي أولاً، ثم الاحتياطيان بترتيب الأولوية. */
    val HOSTS: List<String> = listOf(
        "https://yemenschoolz.com",
        "https://school.procorners.com",
        "https://school-teacher-proxy.procorners-shop.workers.dev"
    )

    private const val DEFAULT_MASTER =
        "https://script.google.com/macros/s/AKfycbx5H6uYXb-6iVt_nT4YkdnYMhl6eZJSDxsULsKa2eyblZQcwzRo4CXR3Mh_ecRSZd4M/exec"

    // مدة الكاش — 6 ساعات (تخصّ مسار المزامنة المعطَّل فقط)
    private const val SYNC_INTERVAL_MS = 6L * 60L * 60L * 1000L

    private var prefs: SharedPreferences? = null
    private var initialized = false

    /** تهيئة AppConfig — تُستدعى مبكراً من [SchoolApplication]. */
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialized = true
        // ⛔ مُعطّلة عمداً — راجع شرح رأس الملف (تفعيلها انحدار حقيقي لا تحسين).
        // syncIfNeeded()
    }

    // ─── النطاق النشِط ────────────────────────────────────────────────────────

    /** النطاق المستخدَم حالياً (آخر نطاق نجح، أو الأساسي في أول إقلاع). */
    val activeHost: String
        get() {
            val saved = prefs?.getString(KEY_ACTIVE_HOST, null)
            return if (saved != null && HOSTS.contains(saved)) saved else HOSTS[0]
        }

    private fun rememberHost(host: String) {
        if (!HOSTS.contains(host)) return
        prefs?.edit()?.putString(KEY_ACTIVE_HOST, host)?.apply()
        Log.d(TAG, "Active host set to: $host")
    }

    /** يبني رابط صفحة على النطاق النشِط مع معرّف المدرسة صراحةً. */
    private fun pageUrl(page: String): String =
        "$activeHost/$page/index.html?school=$EBDAA_SCHOOL_ID"

    // ─── واجهة الاستخدام (URLs) ───────────────────────────────────────────────
    val HOME_URL: String     get() = readPage(KEY_HOME,     "home")
    val CMS_URL: String      get() = readPage(KEY_CMS,      "cms")
    val TEACHER_URL: String  get() = readPage(KEY_TEACHER,  "teacher")
    val STUDENT_URL: String  get() = readPage(KEY_STUDENT,  "student")
    val SCHEDULE_URL: String get() = readPage(KEY_SCHEDULE, "schedule")
    val MASTER_URL: String   get() = read(KEY_MASTER,       DEFAULT_MASTER)

    private fun read(key: String, defaultValue: String): String {
        return prefs?.getString(key, defaultValue) ?: defaultValue
    }

    /**
     * قيمة مخزَّنة تُستخدَم **فقط** إن كانت لا تزال على أحد نطاقات المنصّة الحالية؛ وإلا يُبنى
     * الرابط من النطاق النشِط. يمنع أن تُقيّد قيمةٌ قديمة محفوظة على جهاز مستخدِم (من إصدار
     * أقدم أو مزامنة سابقة) التطبيقَ بنطاق لم يعد مقصوداً.
     */
    private fun readPage(key: String, page: String): String {
        val stored = prefs?.getString(key, null)
        if (stored != null && isPlatformUrl(stored) && stored.contains("/$page/")) return stored
        return pageUrl(page)
    }

    // ─── تجاوز فشل النطاق ─────────────────────────────────────────────────────

    /** يستخرج النطاق (scheme+host) من رابط، أو null إن كان غير صالح. */
    private fun hostOf(url: String): String? = try {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        "$scheme://$host"
    } catch (e: Exception) {
        null
    }

    /** هل هذا الرابط على أحد نطاقات المنصّة الثلاثة؟ */
    fun isPlatformUrl(url: String): Boolean = hostOf(url)?.let { HOSTS.contains(it) } == true

    /**
     * يُرجِع نفس الرابط على **النطاق التالي** في [HOSTS]، أو null إن لم يتبقَّ نطاق (أو لم يكن
     * الرابط أصلاً على نطاق منصّة). يُستدعى عند فشل الإطار الرئيسي فقط.
     */
    fun nextHostUrl(failedUrl: String): String? {
        val currentHost = hostOf(failedUrl) ?: return null
        val idx = HOSTS.indexOf(currentHost)
        if (idx < 0 || idx == HOSTS.lastIndex) return null
        val nextHost = HOSTS[idx + 1]
        return failedUrl.replaceFirst(currentHost, nextHost)
    }

    /** يُثبِّت النطاق الذي نجح فعلاً (يُستدعى من onPageFinished لصفحة حقيقية). */
    fun noteSuccessfulUrl(url: String) {
        val host = hostOf(url) ?: return
        if (HOSTS.contains(host) && host != activeHost) rememberHost(host)
    }

    // ─── دوال التوجيه (تُستخدم في shouldOverrideUrlLoading) ──────────────────
    // مطابقة **بمسار الصفحة** لا بالنطاق ⇒ تعمل مع النطاقات الثلاثة معاً بلا أي تفريع.
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
        val id = extractDeploymentId(deploymentUrl)
        if (id != null) return url.contains(id, ignoreCase = true)
        // روابط الـ Worker: طابِق حسب مسار الصفحة (/teacher/ , /student/ ...)
        val seg = extractWorkerSegment(deploymentUrl)
        return seg != null && url.contains(seg, ignoreCase = true)
    }

    private fun extractWorkerSegment(url: String): String? {
        return Regex("/(home|student|teacher|cms|schedule)/").find(url)?.value
    }

    private fun extractDeploymentId(url: String): String? {
        val regex = Regex("""/macros/s/([^/]+)/exec""")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    // ─── النطاقات الموثوقة (SSL + سياق الجسر) ────────────────────────────────
    val trustedSslDomains: List<String> = listOf(
        "yemenschoolz.com",
        "procorners.com",
        "workers.dev",
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

    // ─── المزامنة من الخادم (محفوظة، غير مُستدعاة — راجع رأس الملف) ───────────

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
