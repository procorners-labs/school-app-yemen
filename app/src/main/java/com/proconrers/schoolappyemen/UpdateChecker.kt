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
 * UpdateChecker — يستبدل إشعار VPN الثابت السابق في صفحات الويب (كان يظهر لكل زائر بلا تمييز،
 * أصبح غير صحيح فعلياً بعد إصلاح النطاق المخصّص). يتحقّق من الخادم (دالة GAS جديدة
 * `checkAppVersion`) هل الإصدار المثبَّت أقدم من آخر إصدار منشور فعلياً على Play Store —
 * ويستهدف فقط من لديه إصدار قديم حقيقياً.
 *
 * الفحص مكبوح كل 12 ساعة (SharedPreferences) — لا يُثقِل الخادم عند كل إقلاع، ونتيجة آخر فحص
 * تُكاش محلياً فتظهر فوراً حتى قبل اكتمال أي فحص جديد.
 */
class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val PREFS_NAME = "update_checker_prefs"
        private const val KEY_LAST_CHECK = "last_check_ts"
        private const val KEY_PLAY_URL = "cached_play_url"
        private const val KEY_UPDATE_AVAILABLE = "cached_update_available"
        private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
        private const val CHECK_URL = "https://school.procorners.com/gas/teacher"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** نتيجة آخر فحص مخزَّنة محلياً — تُستخدَم لعرض البانر فوراً بلا انتظار الشبكة. */
    fun cachedPlayUrlIfOutdated(): String? {
        val available = prefs.getBoolean(KEY_UPDATE_AVAILABLE, false)
        val url = prefs.getString(KEY_PLAY_URL, null)
        return if (available && !url.isNullOrBlank()) url else null
    }

    /**
     * يفحص في الخلفية إن مرّ أكثر من 12 ساعة منذ آخر فحص. onUpdateAvailable يُستدعى على الخيط
     * الرئيسي فقط إن كان هناك فعلاً إصدار أحدث متاح.
     */
    fun checkIfNeeded(onUpdateAvailable: (playUrl: String) -> Unit) {
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
                        val playUrl = result?.optString("playUrl", "") ?: ""
                        val currentCode = currentVersionCode()
                        val isOutdated = latestCode > 0 && currentCode > 0 && latestCode > currentCode

                        prefs.edit()
                            .putLong(KEY_LAST_CHECK, now)
                            .putBoolean(KEY_UPDATE_AVAILABLE, isOutdated)
                            .putString(KEY_PLAY_URL, playUrl)
                            .apply()

                        if (isOutdated && playUrl.isNotBlank() && context is android.app.Activity) {
                            context.runOnUiThread { onUpdateAvailable(playUrl) }
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                // فشل صامت — لا تعطيل أي وظيفة أخرى، تُعاد المحاولة تلقائياً بعد 12 ساعة
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
