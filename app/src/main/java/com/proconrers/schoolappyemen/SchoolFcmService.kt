package com.proconrers.schoolappyemen

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * SchoolFcmService — يستقبل إشعارات Firebase Cloud Messaging ويعرضها على القناة المُنشأة
 * في [SchoolApplication] (صوت مخصّص + أهمية عالية)، ويفتح الخبر المقصود عند الضغط.
 *
 * ── كيف يصل الرمز إلى الخادم (vc32) ───────────────────────────────────────────
 * الرمز يُحفَظ هنا محلياً فقط، ثم **يرفعه الويب** بعد تسجيل الدخول عبر
 * `window.registerFcmToken(token)` — لأن نقطة GAS (`registerDeviceTokenProtected`) محروسة
 * بـ`withAuth`، فلا معنى لإرسالها قبل وجود جلسة. الجسر يقرأه بـ`AndroidApp.getFcmToken()`
 * ويحقنه [BaseWebViewActivity]/[MainActivity] في `onPageFinished`.
 * (‏كان هنا `TODO` يقول «نقطة GAS غير موجودة» — وهي موجودة منذ 2026-08-05.)
 *
 * ── لماذا نبني الإشعار بأنفسنا دائماً ────────────────────────────────────────
 * 🔴 الخادم يرسل **رسائل `data` فقط** بلا كتلة `notification`. السبب: حمولة تحمل
 * `notification` والتطبيقُ في الخلفية يعرضها **SDK بنفسه** على القناة المذكورة في
 * الحمولة — فيتجاوز قناتنا وصوتنا المخصّص. أما حمولة `data` فتُوقظ [onMessageReceived]
 * في الحالتين، فيبقى الصوت والسلوك تحت سيطرتنا. راجع `teacher/PushNotify.js`.
 */
class SchoolFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SchoolFcmService"
        private const val PREFS_NAME = "fcm_prefs"
        private const val KEY_TOKEN = "fcm_token"

        /**
         * ذاكرةٌ وسيطة للرمز — أُضيفت 2026-09-06.
         *
         * **العلّة المقيسة:** [savedToken] تُنادى من موضعين ساخنين: [SchoolJsBridge.getFcmToken]
         * (خيطُ Binder بطلبِ الصفحة)، و`WebViewSupport.injectFcmToken` **في كلّ
         * `onPageFinished`** أي في كلّ صفحةٍ تُحمَّل، على الخيط الرئيسي. وكلُّ نداءٍ كان
         * يفتح مخزنَ تفضيلاتٍ ويقرأ منه.
         *
         * 🔴 **و`@Volatile` ليست تزيّناً:** الكاتبُ [onNewToken] يعمل على خيطِ Firebase،
         * والقارئان على الخيط الرئيسي وخيطِ Binder. **ثلاثةُ خيوطٍ على حقلٍ واحد.**
         * 🔴 **والإبطالُ عند الكتابة إلزاميّ** — تجديدُ الرمز يقع فعلاً وبلا إشعار،
         * ورمزٌ قديمٌ عالقٌ في الذاكرة يعني **إشعاراتٍ تذهب إلى العدم بصمت**، وهي
         * بعينُها فئةُ العطل التي كلّفتنا عشرين يوماً من قبل.
         */
        @Volatile
        private var cachedToken: String? = null

        /** يقرأه [SchoolJsBridge.getFcmToken] ليسلّمه للويب. */
        fun savedToken(context: Context): String {
            cachedToken?.let { return it }
            val token = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, "") ?: ""
            // 🔴 لا يُخزَّن الفارغ: الرمزُ يصل لاحقاً عبر [onNewToken]، وتخزينُ "" هنا
            //    يُجمّد الغيابَ إلى الأبد داخل عمر العملية.
            if (token.isNotBlank()) cachedToken = token
            return token
        }

        /**
         * معرّف الإشعار: مشتقّ من معرّف الخبر كي **لا يتكرّر الخبر الواحد** لو أُعيد بثّه،
         * ويظلّ خبران مختلفان إشعارين مستقلَّين.
         * (‏العدّاد السابق كان `private var` في companion فيُصفَّر مع كل قتل للعملية ⇒
         *  إشعارات تدهس بعضها بعد إعادة التشغيل.)
         */
        private fun notificationIdFor(newsId: String): Int =
            if (newsId.isBlank()) (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            else newsId.hashCode() and 0x7FFFFFFF
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        cachedToken = token   // 🔴 إبطالُ الذاكرة الوسيطة قبل الكتابة — انظر تعليق `cachedToken`
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
        // الرفع للخادم يتمّ من الويب بعد الدخول — راجع توثيق الصنف أعلاه.
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

        showNotification(
            title = title,
            body = body,
            newsId = data["newsId"].orEmpty(),
            slug = data["slug"].orEmpty(),
            schoolId = data["schoolId"].orEmpty()
        )
    }

    /**
     * الرابط الذي يُفتح عند الضغط. يُفضَّل الـslug القصير، ويتراجع إلى صفحة المقال
     * بالمعرّف — وهو **الشكل المطلق الوحيد الصالح**: الرابط النسبي على صفحة الـslug
     * يُحلّ إلى `/newsarticle.html` وهو 404 حيّ (بند 123).
     */
    private fun targetUrlFor(newsId: String, slug: String, schoolId: String): String {
        val origin = AppConfig.CANONICAL_ORIGIN
        if (newsId.isBlank()) return AppConfig.HOME_URL
        val s = slug.ifBlank { AppConfig.EBDAA_SLUG }
        if (s.isNotBlank()) return "$origin/$s?news=$newsId"
        val sid = schoolId.ifBlank { AppConfig.EBDAA_SCHOOL_ID }
        return "$origin/home/newsarticle.html?news=$newsId&school=$sid"
    }

    private fun showNotification(
        title: String,
        body: String,
        newsId: String,
        slug: String,
        schoolId: String
    ) {
        val channelId = getString(R.string.fcm_default_channel_id)
        val targetUrl = targetUrlFor(newsId, slug, schoolId)

        // نمرّ عبر DeepLinkActivity لا MainActivity مباشرةً: هي الموضع الوحيد الذي
        // يقرّر الشاشة من الرابط، فيبقى قرار التوجيه في مكان واحد (LinkRouter/AppConfig).
        val intent = Intent(this, DeepLinkActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(targetUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val notifId = notificationIdFor(newsId)
        // ‏requestCode فريد لكل خبر — بدونه يُعيد النظام استخدام نفس PendingIntent
        // فيفتح كل إشعار خبرَ أوّلِ إشعار (FLAG_UPDATE_CURRENT يحدّث الإضافات لا الـdata).
        val pendingIntent = PendingIntent.getActivity(this, notifId, intent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(notifId, notification)
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — notification suppressed")
        }
    }
}
