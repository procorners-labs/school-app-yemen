// ═══════════════════════════════════════════════════════════════════════════
// app/build.gradle.kts — SchoolApp Yemen
// جميع الإصدارات مُدارة عبر gradle/libs.versions.toml (version catalog)
// ═══════════════════════════════════════════════════════════════════════════

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.proconrers.schoolappyemen"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.proconrers.schoolappyemen"
        minSdk = 24
        targetSdk = 35
        versionCode = 25
        versionName = "2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        // OldTargetApi: targetSdk=35 هو الحد الأعلى لـ AGP 8.5.0 المستخدم حالياً.
        // الترقية لـ API 36 تتطلب تحديث Android Studio أولاً ← تُنفَّذ لاحقاً.
        disable += "OldTargetApi"
        // إبلاغ عن الأخطاء الحقيقية فقط — لا تُوقف البناء عند التحذيرات
        warningsAsErrors = false
        abortOnError = false
    }
}

dependencies {
    // ── Core ──────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)

    // ── UI ────────────────────────────────────────────────────────────────
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)

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
