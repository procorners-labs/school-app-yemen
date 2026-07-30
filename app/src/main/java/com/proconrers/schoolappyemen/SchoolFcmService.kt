package com.proconrers.schoolappyemen

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * SchoolFcmService — يستقبل إشعارات Firebase Cloud Messaging ويعرضها عبر
 * [NotificationHelper] (نفس القناة والصوت المخصّص المستخدَمَين للإشعارات المحلّية).
 *
 * **حمولة data المدعومة** (كلها اختيارية، وتعمل مع إشعار `notification` أو بدونه):
 *   title · body · page (`home` | `teacher` | `student`) · url (رابط كامل على نطاق منصّة)
 * الرابط يُتحقَّق منه في [NotificationHelper.show] قبل تمريره للنشاط، فحمولة إشعار لا تستطيع
 * فتح صفحة عشوائية داخل جلسة مُصادَقة.
 *
 * التوكن يُحفَظ محلياً (SharedPreferences) ويُقرأ من صفحة الويب عبر
 * `AndroidApp.getPushToken()` — فتستطيع المنصّة تسجيله بآليتها الحالية بلا نقطة GAS جديدة
 * (إضافة نقطة تسجيل توكن على الخادم تبقى متابعة مستقبلية مقصودة، لا نطاق هذه الدفعة).
 */
class SchoolFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SchoolFcmService"
        const val PREFS_NAME = "fcm_prefs"
        const val KEY_TOKEN = "fcm_token"

        /** التوكن المحفوظ محلياً (يستخدمه الجسر). */
        fun cachedToken(context: Context): String? =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, null)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: getString(R.string.app_name)
        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: return

        NotificationHelper.show(
            context = this,
            title = title,
            body = body,
            targetPage = data["page"],
            targetUrl = data["url"]
        )
    }
}
