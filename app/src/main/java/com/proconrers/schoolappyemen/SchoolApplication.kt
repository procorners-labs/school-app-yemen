package com.proconrers.schoolappyemen

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build

/**
 * Application — نقطة تهيئة واحدة على مستوى التطبيق كله.
 * تُنشئ قناة إشعارات FCM (بصوت مخصص) مبكراً كي تكون جاهزة قبل وصول أي رسالة،
 * بدل الاعتماد على إنشائها كسلوك جانبي داخل SchoolFcmService.
 */
class SchoolApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channelId = getString(R.string.fcm_default_channel_id)
        val soundUri: Uri = Uri.parse("android.resource://$packageName/${R.raw.notify_chime}")
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            channelId,
            getString(R.string.fcm_default_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.fcm_default_channel_desc)
            setSound(soundUri, audioAttributes)
            enableVibration(true)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
