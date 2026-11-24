plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.quotexm1analyzer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.quotexm1analyzer"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
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
    // Bundled OCR model: on-device text recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
