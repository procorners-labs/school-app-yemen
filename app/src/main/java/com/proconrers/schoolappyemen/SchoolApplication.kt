package com.proconrers.schoolappyemen

import android.app.Application

/**
 * Application — نقطة تهيئة واحدة على مستوى التطبيق كله.
 *
 *  - [AppConfig.init] هنا (لا في كل نشاط على حدة): أي نشاط قد يصبح نقطة الدخول الأولى عند النقر
 *    على إشعار، فتهيئة النطاق النشِط/الروابط يجب أن تسبق أي قراءة لـ`AppConfig.*_URL`.
 *  - قناة الإشعارات (بصوت مخصّص) تُنشَأ مبكراً عبر [NotificationHelper.ensureChannel] كي تكون
 *    جاهزة قبل وصول أي رسالة FCM، بدل الاعتماد على إنشائها كسلوك جانبي داخل الخدمة.
 */
class SchoolApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppConfig.init(this)
        NotificationHelper.ensureChannel(this)
    }
}
