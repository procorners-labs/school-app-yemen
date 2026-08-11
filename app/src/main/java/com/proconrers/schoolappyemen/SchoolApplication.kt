package com.proconrers.schoolappyemen

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build

/**
 * Application — نقطة تهيئة واحدة على مستوى التطبيق كله.
 * تُنشئ قناة إشعارات FCM (بصوت مخصّص) مبكراً كي تكون جاهزة قبل وصول أي رسالة،
 * بدل الاعتماد على إنشائها كسلوك جانبي داخل SchoolFcmService.
 */
class SchoolApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return

        // ① احذف القناة القديمة أوّلاً — صوتها مثبَّت على معرّف مورد رقمي قد يكون
        //    تغيّر بين الإصدارات (‏`android.nonFinalResIds=true`) فتصمت إشعاراتها.
        //    الحذف لا يمسّ من ثبّت التطبيق للتوّ (لا قناة قديمة عنده) ولا يفقد شيئاً:
        //    القناة الجديدة تُنشأ في نفس النداء.
        runCatching { manager.deleteNotificationChannel(getString(R.string.fcm_legacy_channel_id)) }

        // ② 🔴 عنوان الصوت **بالاسم** لا بالمعرّف الرقمي.
        //    `android.resource://<pkg>/${R.raw.notify_chime}` يُخزَّن داخل القناة كرقم،
        //    وأرقام الموارد غير نهائية في هذا المشروع ⇒ ترقيم مختلف في إصدار لاحق يجعل
        //    العنوان يشير إلى «لا شيء» فيصمت الإشعار بلا أي خطأ. الصيغة بالاسم ثابتة.
        val soundUri: Uri = Uri.parse("android.resource://$packageName/raw/notify_chime")
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            getString(R.string.fcm_default_channel_id),
            getString(R.string.fcm_default_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.fcm_default_channel_desc)
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
    }
}
