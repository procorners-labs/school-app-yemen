package com.proconrers.schoolappyemen

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * SplashActivity — AndroidX SplashScreen API migration
 *
 * BEFORE (removed):
 *   Handler(Looper.getMainLooper()).postDelayed({ ... }, 2000)
 *   - Added a fixed 2-second delay on every launch regardless of device speed
 *   - Did not integrate with the system splash screen on Android 12+
 *   - Used deprecated Handler pattern
 *   - Showed a blank white screen during initialization on slow devices
 *
 * AFTER (this file):
 *   - installSplashScreen() hooks into the system-level splash screen (API 31+)
 *   - On API 24–30: shows the launch icon splash automatically, no extra delay
 *   - Zero artificial delay — MainActivity starts as soon as the splash resolves
 *   - Compatible with the existing activity_splash.xml drawable/background
 *
 * ANDROID MANIFEST:
 *   SplashActivity must remain the LAUNCHER activity. No changes to
 *   AndroidManifest.xml are required for this migration.
 *
 * THEME REQUIREMENT:
 *   Add to res/values/themes.xml (and themes.xml (night)):
 *     <style name="Theme.SchoolApp.Splash" parent="Theme.SplashScreen">
 *       <item name="windowSplashScreenBackground">@color/ic_launcher_background</item>
 *       <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_foreground</item>
 *       <item name="postSplashScreenTheme">@style/Theme.AppCompat.Light.NoActionBar</item>
 *     </style>
 *
 *   Then in AndroidManifest.xml set SplashActivity's theme:
 *     android:theme="@style/Theme.SchoolApp.Splash"
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() — this is the API requirement
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // ✅ تهيئة AppConfig وبدء المزامنة في الخلفية (بدون انتظار)
        // هذه الدالة تقوم بتحميل أحدث روابط المنصات من الخادم وتخزينها محلياً
        AppConfig.init(applicationContext)

        // Optional: keep splash on screen until a condition is met
        // (e.g. preloading config, checking first-run state)
        // splashScreen.setKeepOnScreenCondition { viewModel.isLoading.value }
        //
        // For this app: no pre-loading needed, proceed immediately.
        // The splash screen dismisses itself after the first frame renders.

        // Navigate to MainActivity immediately — no artificial delay
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                // Prevent the user from navigating back to SplashActivity
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}