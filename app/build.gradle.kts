plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nova.vpn.wear"
    compileSdk = 35

    defaultConfig {
        // Must match the handheld application ID for Wearable Data Layer security checks.
        applicationId = "com.nova.vpn.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.3.7"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("com.github.singbox-android:libbox:1.14.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
