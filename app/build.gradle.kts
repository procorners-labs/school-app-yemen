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
        versionCode = 16
        versionName = "1.6"

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
    // ✅ المكتبات الأساسية (Core) - متوافقة تماماً مع Android 15 (SDK 35)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ✅ واجهة المستخدم (UI) - أحدث إصدار مستقر للتصاميم الحديثة
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // ✅ دعم تعدد الملفات (MultiDex) - ضروري لمنع أخطاء البناء في المشاريع الكبيرة
    implementation("androidx.multidex:multidex:2.0.1")

    // ✅ مكتبة الويب (WebView) - الإصدار الأحدث لضمان عمل منصة المدرسة ورفع الملفات بأمان
    implementation("androidx.webkit:webkit:1.12.1")

    // ✅ مكتبة Activity Compose - لدعم enableEdgeToEdge في Android 15
    implementation("androidx.activity:activity-compose:1.9.3")

    // ✅ مكتبات الاختبار (Testing) - إصدارات مستقرة ومحدثة
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}