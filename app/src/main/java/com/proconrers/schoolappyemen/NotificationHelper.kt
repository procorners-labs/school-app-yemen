package com.proconrers.schoolappyemen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * NotificationHelper — نقطة واحدة لكل ما يخصّ الإشعارات **والصوت** معاً.
 *
 * كانت قناة الإشعارات تُنشَأ في [SchoolApplication] والعرض في [SchoolFcmService] بلا أي مسار
 * لإشعار محلّي من صفحة الويب. الآن الثلاثة (القناة · الإشعار · الصوت) في ملف واحد يستخدمه:
 *   - [SchoolApplication] (إنشاء القناة مبكراً قبل وصول أي رسالة)
 *   - [SchoolFcmService] (إشعارات Firebase الواردة)
 *   - [SchoolJsBridge] (إشعار/صوت محلّي تطلبه صفحة الويب عند حدث حيّ: خبر، درجة، تنبيه)
 *
 * الصوت مصدره واحد: `res/raw/notify_chime.wav` — يُضبَط على القناة (أندرويد ٨+ يتجاهل أي صوت
 * يُمرَّر للإشعار نفسه ويستخدم صوت القناة) **و**يُشغَّل مباشرةً عبر [playChime] للتنبيه داخل
 * التطبيق وهو مفتوح (حيث لا إشعار نظام).
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    /** مفتاح إضافي على الـIntent: رابط بديل يُفتَح داخل النشاط (يُتحقَّق منه قبل التحميل). */
    const val EXTRA_START_URL = "extra_start_url"

    /** أي منصّة يفتحها الإشعار: home (افتراضي) · teacher · student. */
    const val EXTRA_TARGET_PAGE = "extra_target_page"

    private var idCounter = 1000

    fun channelId(context: Context): String = context.getString(R.string.fcm_default_channel_id)

    /** يُنشئ القناة (idempotent — أندرويد يتجاهل الإنشاء المتكرّر لنفس المعرّف). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val soundUri: Uri =
            Uri.parse("android.resource://${context.packageName}/${R.raw.notify_chime}")
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            channelId(context),
            context.getString(R.string.fcm_default_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.fcm_default_channel_desc)
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            enableLights(true)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /** هل يُسمح بعرض إشعارات الآن؟ (أندرويد ١٣+ يتطلّب صلاحية صريحة) */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * يعرض إشعاراً. [targetPage] و[targetUrl] يحدّدان ما يُفتَح عند النقر؛ الرابط يُتحقَّق من
     * كونه على نطاق موثوق قبل تمريره (حمولة إشعار لا تفتح صفحة عشوائية داخل جلسة مُصادَقة).
     */
    fun show(
        context: Context,
        title: String,
        body: String,
        targetPage: String? = null,
        targetUrl: String? = null
    ) {
        ensureChannel(context)

        val activityClass = when (targetPage?.lowercase()) {
            "teacher" -> TeacherActivity::class.java
            "student" -> StudentActivity::class.java
            else -> MainActivity::class.java
        }
        val intent = Intent(context, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!targetUrl.isNullOrBlank() && AppConfig.isPlatformUrl(targetUrl)) {
                putExtra(EXTRA_START_URL, targetUrl)
            }
            if (!targetPage.isNullOrBlank()) putExtra(EXTRA_TARGET_PAGE, targetPage)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // requestCode فريد لكل إشعار: FLAG_UPDATE_CURRENT مع requestCode ثابت كان سيجعل كل
        // الإشعارات تفتح هدف آخر إشعار وصل (فخّ PendingIntent الكلاسيكي).
        val requestCode = idCounter
        val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, flags)

        val notification = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .build()

        if (!canNotify(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — notification suppressed")
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(idCounter++, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notify blocked: ${e.message}")
        }
    }

    /**
     * يشغّل صوت التنبيه المخصّص مباشرةً (للتنبيهات داخل التطبيق وهو مفتوح).
     * يُحرَّر المشغّل دائماً عند الانتهاء/الخطأ — لا تسريب موارد صوت.
     */
    fun playChime(context: Context) {
        // ترتيب إلزامي: setAudioAttributes **قبل** prepare — لذلك لا نستخدم MediaPlayer.create()
        // (يُهيّئ داخلياً فوراً، فأي ضبط سمات بعده يرمي IllegalStateException على بعض الإصدارات).
        var player: MediaPlayer? = null
        try {
            val afd = context.resources.openRawResourceFd(R.raw.notify_chime) ?: return
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setOnCompletionListener { mp ->
                    try { mp.release() } catch (e: Exception) { /* مُحرَّر مسبقاً */ }
                }
                setOnErrorListener { mp, _, _ ->
                    try { mp.release() } catch (e: Exception) { /* مُحرَّر مسبقاً */ }
                    true
                }
                prepare()
                start()
            }
            afd.close()
        } catch (e: Exception) {
            Log.w(TAG, "playChime failed: ${e.message}")
            try { player?.release() } catch (e2: Exception) { /* لا شيء لتحريره */ }
        }
    }
}
