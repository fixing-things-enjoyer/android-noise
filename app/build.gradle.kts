plugins {
    id("com.android.application")
}

android {
    namespace = "dev.androidnoise.gammaclicks"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.androidnoise.gammaclicks"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
