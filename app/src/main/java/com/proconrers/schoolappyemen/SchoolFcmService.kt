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
 * SchoolFcmService — يستقبل إشعارات Firebase Cloud Messaging ويعرضها عبر القناة
 * المُنشأة مسبقاً في [SchoolApplication] (صوت مخصص + أهمية عالية).
 *
 * ملاحظة نطاق: تسجيل التوكن على خادم GAS (لإرسال إشعارات مستهدَفة لاحقاً) يحتاج نقطة
 * API جديدة غير موجودة حالياً في `school-app-yemen-gas` — خارج نطاق هذه الجلسة عمداً.
 * التوكن يُحفَظ محلياً فقط (SharedPreferences) حتى تُضاف تلك النقطة مستقبلاً.
 */
class SchoolFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SchoolFcmService"
        private const val PREFS_NAME = "fcm_prefs"
        private const val KEY_TOKEN = "fcm_token"
        private var notificationIdCounter = 1000
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
        // TODO(مستقبلي): إرسال التوكن إلى نقطة GAS جديدة عند توفّرها لتفعيل استهداف الإشعارات.
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: getString(R.string.app_name)
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: return

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        if (SchoolApplication.isAppInForeground) {
            Log.d(TAG, "App in foreground — suppressing duplicate notification banner")
            return
        }

        val channelId = getString(R.string.fcm_default_channel_id)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(notificationIdCounter++, notification)
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — notification suppressed")
        }
    }
}
