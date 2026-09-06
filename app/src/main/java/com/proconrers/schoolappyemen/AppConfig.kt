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
 * AppConfig — المصدر المركزي لروابط المنصّات **ولقرار «داخلي أم خارجي»**.
 *
 * ── ما تغيّر في vc32 (2026-08-12) ولماذا ─────────────────────────────────────
 *
 * ① **النطاق الرسمي.** الروابط انتقلت من `school.procorners.com` إلى
 *    `https://yemenschoolz.com` — النطاق الرئيسي للمشروع بقرار مالك منذ 2026-07-28.
 *    النطاقان الإرثيان يبقيان **يعملان ويُعدّان داخليَّين** (نسخ قديمة مثبَّتة عند
 *    مستخدمين، ولا 301 منهما أبداً).
 *
 * ② 🔴 **التوجيه صار بالمضيف + بادئة المسار، بدل `contains` على مقطع نصّي.**
 *    القديم:  `Regex("/(home|student|teacher|cms|schedule)/")` ثم `url.contains(seg)`.
 *    وأثره المقيس أن هذه الروابط كانت تُعدّ **خارجية** فتفتح المتصفّح وتترك التطبيق فارغاً:
 *      · `yemenschoolz.com/portal`            ← رابط منصّة الطالب القصير
 *      · `yemenschoolz.com/abdaawatmuaz`      ← صفحة المدرسة العامّة (الـslug)
 *      · `yemenschoolz.com/abdaawatmuaz?news=…` ← رابط مشاركة الخبر
 *      · `…/schedule/index.html`               ← لم يطابق أي رابط منصّة إطلاقاً
 *    وكانت `isTrustedSslDomain` بنفس العلّة أمنياً: `url.contains("procorners.com")`
 *    يُمرِّر `https://evil.com/?x=procorners.com` كنطاق موثوق.
 *
 * ③ **المزامنة الديناميكية تبقى معطّلة** (`syncIfNeeded` معلّقة) — راجع تحذيرها أدناه.
 */
object AppConfig {

    private const val TAG = "AppConfig"
    private const val PREFS_NAME = "deployment_config_v3"
    private const val PREFS_LAST_UPDATE = "last_update_ts"

    /** مفتاح Intent يحمل رابطاً محدَّداً يجب فتحه (Deep Link أو ضغطة إشعار). */
    const val EXTRA_TARGET_URL = "com.proconrers.schoolappyemen.TARGET_URL"

    // مفاتيح SharedPreferences
    private const val KEY_HOME     = "url_home"
    private const val KEY_CMS      = "url_cms"
    private const val KEY_TEACHER  = "url_teacher"
    private const val KEY_STUDENT  = "url_student"
    private const val KEY_SCHEDULE = "url_schedule"
    private const val KEY_MASTER   = "url_master"

    // ─── الهوية والنطاق ───────────────────────────────────────────────────────
    /** معرّف «مدارس الإبداع والتميز الدولية» في سجل `Master_Admin_School`. */
    const val EBDAA_SCHOOL_ID = "12725ed7-c139-422c-a2d1-ec0ddd358104"

    /** الـslug العامّ لنفس المدرسة — يخدم `yemenschoolz.com/abdaawatmuaz`. */
    const val EBDAA_SLUG = "abdaawatmuaz"

    /** الأصل الرسمي. كل رابط جديد يُبنى منه. */
    const val CANONICAL_ORIGIN = "https://yemenschoolz.com"

    // ─── الروابط الافتراضية ───────────────────────────────────────────────────
    private const val DEFAULT_HOME =
        "$CANONICAL_ORIGIN/home/index.html?school=$EBDAA_SCHOOL_ID"
    private const val DEFAULT_CMS =
        "$CANONICAL_ORIGIN/cms/index.html?school=$EBDAA_SCHOOL_ID"
    private const val DEFAULT_TEACHER =
        "$CANONICAL_ORIGIN/teacher/index.html?school=$EBDAA_SCHOOL_ID"
    private const val DEFAULT_STUDENT =
        "$CANONICAL_ORIGIN/student/index.html?school=$EBDAA_SCHOOL_ID"
    private const val DEFAULT_SCHEDULE =
        "$CANONICAL_ORIGIN/schedule/index.html?school=$EBDAA_SCHOOL_ID"
    private const val DEFAULT_MASTER =
        "https://script.google.com/macros/s/AKfycbx5H6uYXb-6iVt_nT4YkdnYMhl6eZJSDxsULsKa2eyblZQcwzRo4CXR3Mh_ecRSZd4M/exec"

    // مدة الكاش — 6 ساعات (إعادة الجلب فقط بعد هذه المدة)
    private const val SYNC_INTERVAL_MS = 6L * 60L * 60L * 1000L

    private var prefs: SharedPreferences? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialized = true

        // ⛔ مُعطّلة عمداً: التطبيق يستخدم روابط الوسيط الثابتة.
        // 🔴 ولا تُعِد تفعيلها قبل إصلاح `isValidUrl` أدناه — فهي تقبل روابط
        //    `script.google.com/.../exec` **وحدها**، أي أن أول مزامنة ناجحة ستدهس
        //    روابط الوسيط بروابط GAS خام فتفقد المنصّة الوسيط كاملاً (حاجز التزاحم،
        //    إعادة المحاولة، إعادة كتابة /portal، رؤوس الهوية).
        // syncIfNeeded()
    }

    // ─── واجهة الاستخدام (URLs) ───────────────────────────────────────────────
    val HOME_URL: String get() = read(KEY_HOME, DEFAULT_HOME)
    val CMS_URL: String get() = read(KEY_CMS, DEFAULT_CMS)
    val TEACHER_URL: String get() = read(KEY_TEACHER, DEFAULT_TEACHER)
    val STUDENT_URL: String get() = read(KEY_STUDENT, DEFAULT_STUDENT)
    val SCHEDULE_URL: String get() = read(KEY_SCHEDULE, DEFAULT_SCHEDULE)
    val MASTER_URL: String get() = read(KEY_MASTER, DEFAULT_MASTER)

    /**
     * ذاكرةٌ وسيطة للروابط — أُضيفت 2026-09-06.
     *
     * **العلّة المقيسة:** الروابط أعلاه خصائصُ `get()`، فكلُّ قراءةٍ منها كانت تضرب
     * `prefs.getString` أي مخزنَ تفضيلاتٍ على القرص. وتُقرأ في كلّ `loadTarget`
     * وكلّ `onNewIntent` وفي `startUrl` لشاشتَي المعلّم والطالب — على الخيط الرئيسي.
     *
     * 🔴 **والإبطالُ صريحٌ لا ضمنيّ:** تُمسَح في `parseAndStore` و`reset` — وهما
     * الموضعان الوحيدان اللذان يكتبان في المخزن. فلو أُعيد تفعيلُ المزامنة يوماً
     * (وهي معطّلةٌ اليوم) بقيت الذاكرةُ صادقة. **كاشٌ بلا إبطالٍ عند الكتابة هو
     * بالضبط ما يُنتج «عدّلتُ ولم يتغيّر شيء».**
     */
    private val urlCache = HashMap<String, String>(6)

    private fun read(key: String, defaultValue: String): String {
        urlCache[key]?.let { return it }
        val value = prefs?.getString(key, defaultValue) ?: defaultValue
        urlCache[key] = value
        return value
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  التوجيه — مصدر الحقيقة الوحيد لقرار «أين يُفتح هذا الرابط؟»
    // ═══════════════════════════════════════════════════════════════════════════

    /** وجهة الرابط. `STAY` = يُحمَّل في الـWebView الحالي أياً كان النشاط. */
    enum class LinkTarget { HOME, TEACHER, STUDENT, STAY, EXTERNAL }

    /**
     * مضيفات المنصّة — كلها داخلية.
     * 🔴 الإرثيان يبقيان: نسخ أندرويد سابقة تحمل روابطهما مجمَّدة في الـAPK، وإخراجهما
     *    من هذه القائمة يقذف مستخدميها إلى المتصفّح بلا رجعة.
     */
    private val PLATFORM_HOSTS = listOf(
        "yemenschoolz.com",
        "school.procorners.com",
        "school-teacher-proxy.procorners-shop.workers.dev"
    )

    /** نطاقات Google — تبقى داخل الـWebView (إعادة توجيه Apps Script · Drive · حسابات). */
    private val GOOGLE_HOSTS = listOf(
        "google.com",
        "googleusercontent.com",
        "googleapis.com",
        "gstatic.com",
        "googlevideo.com"
    )

    /**
     * مطابقة مضيف حقيقية: تساوٍ تامّ أو نطاق فرعي صريح.
     * (‏`www.yemenschoolz.com` يُطابِق `yemenschoolz.com` بهذه القاعدة.)
     * 🔴 لا `contains` — `evil.com/?x=yemenschoolz.com` يجب أن يفشل.
     */
    private fun hostMatches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

    private fun isPlatformHost(host: String): Boolean =
        PLATFORM_HOSTS.any { hostMatches(host, it) }

    private fun isGoogleHost(host: String): Boolean =
        GOOGLE_HOSTS.any { hostMatches(host, it) }

    /** هل المسار ضمن قسم معيّن؟ يقبل `/teacher` و`/teacher/` و`/teacher/index.html`. */
    private fun inSection(path: String, section: String): Boolean =
        path == "/$section" || path == "/$section/" || path.startsWith("/$section/")

    /**
     * مسارات خدمة يجب أن تبقى في نفس الـWebView مهما كان النشاط
     * (نقاط API ووسائط وأصول — ليست صفحات تنقّل).
     */
    private val STAY_SECTIONS = listOf("gas", "media", "assets", "drive-upload", "qr-img", "qr-download")

    /**
     * يحسب وجهة أي رابط. هذه الدالّة **نقيّة** (بلا حالة ولا Android context) عمداً
     * كي تكون قابلة للاختبار مباشرةً.
     */
    fun routeTargetFor(url: String): LinkTarget {
        if (url.isBlank()) return LinkTarget.STAY

        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
            ?: return LinkTarget.EXTERNAL

        val scheme = (uri.scheme ?: "").lowercase()
        // ‏`tel:` · `mailto:` · `whatsapp:` · `intent:` … ⇒ تطبيق خارجي دائماً.
        if (scheme != "http" && scheme != "https") return LinkTarget.EXTERNAL

        val host = (uri.host ?: "").lowercase()
        if (host.isBlank()) return LinkTarget.STAY

        if (isGoogleHost(host)) return LinkTarget.STAY
        if (!isPlatformHost(host)) return LinkTarget.EXTERNAL

        val path = (uri.path ?: "/").ifBlank { "/" }.lowercase()

        if (STAY_SECTIONS.any { inSection(path, it) }) return LinkTarget.STAY
        if (inSection(path, "teacher")) return LinkTarget.TEACHER
        // ‏`/portal` رابط منصّة الطالب القصير — الوسيط يعيد كتابته داخلياً إلى
        // `/student/index.html` (‏200، والاستعلام `?school=` ينجو — مقيس 2026-08-12).
        if (inSection(path, "student") || inSection(path, "portal")) return LinkTarget.STUDENT
        // ‏`schedule` بلا نشاط مخصّص: يبقى في الـWebView الحالي بدل قذفه للمتصفّح
        // (‏كان يُعدّ خارجياً قبل vc32 لأنه لم يطابق أي رابط منصّة).
        if (inSection(path, "schedule")) return LinkTarget.STAY

        // كل ما تبقّى على مضيف المنصّة صفحةُ واجهة عامّة: الجذر · `/home/**` ·
        // `/home-all-school/**` · `/cms/**` · `/pricing` · `/<slug>` وصفحة الخبر.
        return LinkTarget.HOME
    }

    /** هل الرابط داخل المنصّة أصلاً (أي ليس `EXTERNAL`)؟ */
    fun isInternalUrl(url: String): Boolean = routeTargetFor(url) != LinkTarget.EXTERNAL

    // ─── نطاقات SSL الموثوقة ──────────────────────────────────────────────────
    /**
     * تُقرأ في `onReceivedSslError` وحده. مطابقة **مضيف** لا `contains`
     * (‏القديم كان يُمرِّر `https://evil.com/?x=procorners.com`).
     */
    private val TRUSTED_SSL_HOSTS: List<String> =
        PLATFORM_HOSTS + GOOGLE_HOSTS + listOf("procorners.com")

    fun isTrustedSslDomain(url: String): Boolean {
        val host = try { Uri.parse(url).host } catch (e: Exception) { null } ?: return false
        val h = host.lowercase()
        return TRUSTED_SSL_HOSTS.any { hostMatches(h, it) }
    }

    // ─── المزامنة من الخادم (معطّلة — راجع التحذير في init) ───────────────────

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
                val baseUrl = HOME_URL
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
            urlCache.clear()   // 🔴 إبطالٌ صريح — وإلّا بقيت الروابط القديمة حيّةً في الذاكرة
            true
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed: ${e.message}")
            false
        }
    }

    /** 🔴 يقبل روابط GAS الخام وحدها — راجع تحذير `init` قبل إعادة تفعيل المزامنة. */
    private fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.startsWith("https://script.google.com/macros/") && url.endsWith("/exec")
    }

    fun forceSync() {
        prefs?.edit()?.putLong(PREFS_LAST_UPDATE, 0L)?.apply()
        syncIfNeeded()
    }

    fun reset() {
        prefs?.edit()?.clear()?.apply()
        urlCache.clear()   // 🔴 إبطالٌ صريح — انظر تعليق `urlCache`
    }
}
