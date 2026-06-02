plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.proconrers.schoolappyemen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.proconrers.schoolappyemen"
        minSdk = 24
        targetSdk = 35
        versionCode = 22
        versionName = "1.9"

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
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ── Priority 7: AndroidX Splash Screen API ─────────────────────────────
    // Required for SplashActivity migration from Handler.postDelayed.
    // Backports the Android 12+ splash screen to API 24+.
    implementation("androidx.core:core-splashscreen:1.0.1")

    // UI
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // MultiDex
    implementation("androidx.multidex:multidex:2.0.1")

    // WebView
    implementation("androidx.webkit:webkit:1.12.1")

    // Activity (enableEdgeToEdge)
    implementation("androidx.activity:activity-compose:1.9.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}