plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.kirimlayar.capture"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.kirimlayar.capture"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // Keystore debug yang ikut di repo supaya tanda tangan APK sama di setiap build CI.
    // Hanya untuk uji pribadi. Jangan dipakai untuk rilis publik.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Satu baris ini yang diubah kalau versi RootEncoder bermasalah (lihat README, bagian Pemecahan masalah).
val rootEncoderVersion = "2.7.5"

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.pedroSG94.RootEncoder:library:$rootEncoderVersion")

    testImplementation("junit:junit:4.13.2")
}
