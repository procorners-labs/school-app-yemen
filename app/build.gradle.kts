// ═══════════════════════════════════════════════════════════════════════════
// app/build.gradle.kts — SchoolApp Yemen
// جميع الإصدارات مُدارة عبر gradle/libs.versions.toml (version catalog)
// ═══════════════════════════════════════════════════════════════════════════

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

// Load signing credentials from keystore.properties (never committed to git)
val keystoreProps = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) keystoreProps.load(keystoreFile.inputStream())

android {
    namespace = "com.proconrers.schoolappyemen"
    // compileSdk 36 = Android 16، مدعوم رسمياً في AGP 8.13 ⇒ لم نَعُد نحتاج
    // android.suppressUnsupportedCompileSdk (أُزيل من gradle.properties في نفس الدفعة).
    compileSdk = 36

    defaultConfig {
        applicationId = "com.proconrers.schoolappyemen"
        minSdk = 24
        // targetSdk 36 (Android 16) — شرط Google Play الإلزامي: أي تحديث بعد 2026-08-31 يجب أن
        // يستهدف مستوى واجهة صدر خلال عام من أحدث إصدار Android، وأعلى مستوى غير متوافق حالياً 35.
        targetSdk = 36
        // 🔴 المنشور على Play: versionCode 31 / versionName "2.8" (نُشر 2026-07-17 07:40) —
        // صُولِح في main بـPR#14. **فلا يجوز لهذا الفرع استخدام 31**: Play يرفض إعادة استخدام
        // رقم منشور برسالة «رمز الإصدار مستخدَم». هذا الفرع كان على 31 حين كُتب (2026-07-30،
        // قبل المصالحة) فرُفِع إلى 32 عند إعادة تأسيسه على main.
        versionCode = 32
        versionName = "2.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile     = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias      = keystoreProps["keyAlias"] as String
                keyPassword   = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        disable += "OldTargetApi"
        warningsAsErrors = false
        abortOnError = false
        // تعطيل lintVital في release — Android Studio يُشغّله بشكل منفصل
        // ويحجز ملف lint-cache مما يُعطّل بناء Gradle المتزامن
        checkReleaseBuilds = false
    }
}

dependencies {
    // ── Core ──────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)

    // ── Firebase (BoM أولاً كي تُدار كل الإصدارات معاً وتتجنّب تعارضاً لاحقاً) ──
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    // ── UI ────────────────────────────────────────────────────────────────
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)

    // ── Support ───────────────────────────────────────────────────────────
    implementation(libs.androidx.multidex)
    // قفل البصمة عند الدخول (BiometricLock.kt) — يجرّ androidx.fragment المطلوب لـBiometricPrompt
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
