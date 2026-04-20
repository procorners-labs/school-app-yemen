package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. ربط واجهة شاشة البداية (تأكد من وجود ملف activity_splash.xml في مجلد layout)
        setContentView(R.layout.activity_splash)

        // 2. الانتقال التلقائي إلى الشاشة الرئيسية بعد ثانيتين (2000 مللي ثانية)
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)

            // ✅ إضافة حركة انتقال ناعمة (FadeIn/Out) لتتناسق مع احترافية التطبيق
            val options = ActivityOptions.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )

            // تشغيل النشاط الجديد مع تأثيرات الانتقال
            startActivity(intent, options.toBundle())

            // ✅ إنهاء شاشة البداية لكي لا يعود إليها المستخدم عند الضغط على زر الرجوع
            finish()

        }, 2000)
    }
}