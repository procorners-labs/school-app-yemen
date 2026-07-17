package com.proconrers.schoolappyemen

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle

/**
 * Application — نقطة تهيئة واحدة على مستوى التطبيق كله.
 * تُنشئ قناة إشعارات FCM (بصوت مخصص) مبكراً كي تكون جاهزة قبل وصول أي رسالة،
 * بدل الاعتماد على إنشائها كسلوك جانبي داخل SchoolFcmService.
 */
class SchoolApplication : Application() {

    companion object {
        // يُستخدَم في SchoolFcmService لكبح إشعار مكرَّر عندما يكون المستخدم داخل التطبيق فعلياً.
        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerForegroundTracking()
    }

    private fun registerForegroundTracking() {
        var startedActivityCount = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                isAppInForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) isAppInForeground = false
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
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
