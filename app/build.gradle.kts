plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt") // annotation processing - Room 사용 시에만 필요
}

android {
    namespace = "com.example.sleepshift"
    compileSdk = 34  // targetSdk와 동일하게 맞춤

    defaultConfig {
        applicationId = "com.example.sleepshift"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

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
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Lifecycle & ViewModel (버전 통일 ✅)
    val lifecycleVersion = "2.8.4"
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")

    // Activity KTX (viewModels 델리게이트)
    implementation("androidx.activity:activity-ktx:1.9.1")

    // Fragment KTX (필요시)
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Coroutines (버전 통일 ✅)
    val coroutinesVersion = "1.8.1"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // Data Persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // JSON
    implementation("com.google.code.gson:gson:2.11.0")


    // 테스트 의존성
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}