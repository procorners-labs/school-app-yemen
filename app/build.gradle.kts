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
    compileSdk = 37

    defaultConfig {
        applicationId = "com.proconrers.schoolappyemen"
        minSdk = 24
        // 🔴 targetSdk 36 = Android 16 — **إلزامي قبل 31 أغسطس 2026**.
        // تحذير Play Console: «اعتبارًا من 31 أغسطس 2026، إذا كان تطبيقك لا يستهدِف مستوى
        // واجهة برمجة تطبيقات تم إطلاقه خلال عام واحد من أحدث إصدار، لن تتمكّن من تحديث
        // تطبيقك». كان 35 حتى 2026-08-12. لا يُخفَّض مجدداً.
        targetSdk = 36
        // 🔴 المنشور على Play: versionCode 31 / versionName "2.8" (نُشر 2026-07-17 07:40).
        // صُولِح 2026-08-11: كان المستودع على 30/"2.7" — أي أن vc31 بُني ونُشر دون أن
        // يُسجَّل رفع الرقم في git، فمن يقرأ هذا السطر يستنتج المنشور خطأً بإصدار واحد.
        // الحزم محفوظة في Workspace\Releases\SchoolAppyemen\ (فيه README يحدّد الحيّة).
        // ⇒ Play يرفض إعادة استخدام 31 ولو لم يعرفه المستودع، وخطؤه («رمز الإصدار
        //   مستخدَم») يبدو غامضاً بلا هذا التعليق. الإصدار الذي يليه يبدأ من 33.
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

    // ── الدخول بالبصمة ────────────────────────────────────────────────────
    // مكتبتان مستقرّتان مكتوبتان بجافا — لا بيانات وصفية Kotlin حديثة، فلا تفرضان
    // ترقية مترجم Kotlin 1.9.0 الحالي (قيد مقصود: المهلة لا تحتمل ترقية سلسلة أدوات).
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // ── Support ───────────────────────────────────────────────────────────
    implementation(libs.androidx.multidex)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
