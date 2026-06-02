package com.proconrers.schoolappyemen

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log

/**
 * NetworkReloadController — يراقب الاتصال ويعيد التحميل تلقائياً عند عودته.
 *
 * مفيد جداً للشبكة المتقطّعة: إن فشل تحميل الصفحة (لا إنترنت) ثم عاد الاتصال،
 * يُستدعى [onReconnect] تلقائياً لإعادة تحميل الصفحة الفاشلة — دون تدخّل المستخدم.
 *
 * يُسجَّل في onResume ويُلغى في onPause لتفادي تسرّب الـ callback.
 */
class NetworkReloadController(
    private val activity: Activity,
    private val onReconnect: () -> Unit
) {
    private val cm =
        activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity.runOnUiThread { onReconnect() }
            }
        }
        callback = cb
        try {
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) {
            Log.e("NetworkReload", "register failed", e)
            callback = null
        }
    }

    fun stop() {
        callback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // قد يكون أُلغي مسبقاً
            }
        }
        callback = null
    }
}
