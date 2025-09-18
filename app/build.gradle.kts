plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt") // annotation processing
}

android {
    namespace = "com.example.sleepshift"
    compileSdk = 35  // 35로 업데이트

    defaultConfig {
        applicationId = "com.example.sleepshift"
        minSdk = 26
        targetSdk = 34  // 35로 업데이트
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 16KB 호환성을 위한 packaging 설정 수정
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 중복 제거 및 최신 버전으로 통합
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")  // 버전 통일

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation(libs.mediation.test.suite)
    kapt("androidx.room:room-compiler:2.6.1")

    // Filament - 최신 버전으로 업데이트 (16KB 호환성)
    implementation("com.google.android.filament:filament-android:1.53.1")  // libs 참조 대신 직접 버전 명시

    // MediaPlayer
    implementation("androidx.media:media:1.7.0")  // 최신 버전으로 업데이트
}
