
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.quotexmianalyzer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.quotexmianalyzer"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
