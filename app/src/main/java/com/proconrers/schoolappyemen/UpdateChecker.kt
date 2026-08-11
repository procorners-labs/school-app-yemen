package com.proconrers.schoolappyemen

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * UpdateChecker — يخبر مَن لديه إصدار أقدم من المنشور فعلياً على Play، ولا يُزعج غيره.
 *
 * يسأل دالّة GAS `checkAppVersion` (‏`teacher/AppVersionCheck.js`) التي تقرأ خصائص السكربت:
 *   `ANDROID_LATEST_VERSION_CODE_<pkg>` · `ANDROID_MIN_SUPPORTED_VERSION_CODE_<pkg>` ·
 *   `ANDROID_UPDATE_MESSAGE_<pkg>` · `ANDROID_PLAY_URL_<pkg>`
 * ⇒ **نصّ الرسالة وشدّتها يتغيّران من الخادم بلا إصدار جديد** — وهذا بيت القصيد: مَن لم
 * يحدّث لا يستقبل كوداً جديداً أصلاً، فالشيء الوحيد الذي يصله هو ما يقوله الخادم.
 *
 * الفحص مكبوح **6 ساعات**، ونتيجة آخر فحص تُكاش فتظهر فوراً قبل اكتمال أي فحص جديد.
 * (‏كان 12 ساعة؛ خُفّض في vc32 لأن دفعة إجبارية قد تحتاج الوصول خلال يوم عمل واحد.)
 *
 * 🔴 الفشل صامت دائماً — لا يعطّل أي وظيفة، ويُعاد تلقائياً في الدورة التالية.
 */
class UpdateChecker(private val context: Context) {

    /**
     * @param mandatory الإصدار الحالي **دون** الحدّ الأدنى المدعوم ⇒ حوار غير قابل للإغلاق.
     *        يبقى `false` ما لم يضبط المالك `ANDROID_MIN_SUPPORTED_VERSION_CODE_<pkg>` صراحةً،
     *        فلا يمكن أن يُقفَل التطبيق على أحد بالخطأ من قيمة افتراضية.
     */
    data class UpdateInfo(
        val playUrl: String,
        val message: String,
        val mandatory: Boolean
    )

    companion object {
        private const val TAG = "UpdateChecker"
        private const val PREFS_NAME = "update_checker_prefs"
        private const val KEY_LAST_CHECK = "last_check_ts"
        private const val KEY_PLAY_URL = "cached_play_url"
        private const val KEY_MESSAGE = "cached_message"
        private const val KEY_MANDATORY = "cached_mandatory"
        private const val KEY_UPDATE_AVAILABLE = "cached_update_available"
        private const val KEY_SNOOZE_UNTIL = "snooze_until_ts"
        private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

        /** تأجيل بعد ضغط «✕» — يظهر البانر مجدداً بعد يوم، ولا يختفي للأبد. */
        private const val SNOOZE_MS = 24L * 60L * 60L * 1000L

        private const val CHECK_URL = "${AppConfig.CANONICAL_ORIGIN}/gas/teacher"

        const val DEFAULT_MESSAGE = "يتوفّر إصدار جديد من التطبيق — حدِّثه للاستفادة من آخر التحسينات"
        private const val MANDATORY_FALLBACK_MESSAGE =
            "هذا الإصدار لم يعُد مدعوماً. يُرجى تحديث التطبيق للمتابعة."
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** نتيجة آخر فحص مخزَّنة محلياً — تُعرَض فوراً بلا انتظار الشبكة. */
    fun cachedUpdateInfo(): UpdateInfo? {
        if (!prefs.getBoolean(KEY_UPDATE_AVAILABLE, false)) return null
        val url = prefs.getString(KEY_PLAY_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val mandatory = prefs.getBoolean(KEY_MANDATORY, false)
        // التأجيل لا يسري على التحديث الإجباري.
        if (!mandatory && System.currentTimeMillis() < prefs.getLong(KEY_SNOOZE_UNTIL, 0L)) return null
        return UpdateInfo(
            playUrl = url,
            message = prefs.getString(KEY_MESSAGE, null)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_MESSAGE,
            mandatory = mandatory
        )
    }

    /** يؤجّل البانر يوماً واحداً (زرّ «✕»). لا أثر له على التحديث الإجباري. */
    fun snooze() {
        prefs.edit()
            .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
            .apply()
    }

    /**
     * يفحص في الخلفية إن مرّ أكثر من [CHECK_INTERVAL_MS] منذ آخر فحص.
     * [onUpdateAvailable] يُستدعى على الخيط الرئيسي فقط إن كان هناك فعلاً إصدار أحدث.
     */
    fun checkIfNeeded(onUpdateAvailable: (info: UpdateInfo) -> Unit) {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        if ((now - last) < CHECK_INTERVAL_MS) return

        thread(start = true, isDaemon = true, name = "UpdateChecker") {
            try {
                val body = """{"fn":"checkAppVersion","args":["${context.packageName}"]}"""
                val conn = (URL(CHECK_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val outer = JSONObject(response)
                    if (outer.optBoolean("ok", false)) {
                        val result = outer.optJSONObject("result")
                        val latestCode = result?.optInt("latestVersionCode", 0) ?: 0
                        val minSupported = result?.optInt("minSupportedVersionCode", 0) ?: 0
                        val playUrl = result?.optString("playUrl", "").orEmpty()
                        val serverMsg = result?.optString("updateMessage", "").orEmpty()
                        val currentCode = currentVersionCode()

                        val isOutdated = latestCode > 0 && currentCode > 0 && latestCode > currentCode
                        val mandatory = minSupported > 0 && currentCode > 0 && currentCode < minSupported
                        val message = when {
                            serverMsg.isNotBlank() -> serverMsg
                            mandatory -> MANDATORY_FALLBACK_MESSAGE
                            else -> DEFAULT_MESSAGE
                        }

                        prefs.edit()
                            .putLong(KEY_LAST_CHECK, now)
                            .putBoolean(KEY_UPDATE_AVAILABLE, isOutdated || mandatory)
                            .putBoolean(KEY_MANDATORY, mandatory)
                            .putString(KEY_PLAY_URL, playUrl)
                            .putString(KEY_MESSAGE, message)
                            .apply()

                        if ((isOutdated || mandatory) && playUrl.isNotBlank() &&
                            context is android.app.Activity
                        ) {
                            val info = UpdateInfo(playUrl, message, mandatory)
                            context.runOnUiThread { onUpdateAvailable(info) }
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                // فشل صامت — لا تعطيل أي وظيفة أخرى، تُعاد المحاولة في الدورة التالية.
            }
        }
    }

    private fun currentVersionCode(): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }
}
