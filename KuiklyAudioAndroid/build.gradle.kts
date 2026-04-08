plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.tencent.kuiklybase.audio.android"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Kuikly Render Android（compileOnly，由宿主提供）
    compileOnly("com.tencent.kuikly-open:core-render-android:${Version.getKuiklyVersion()}")

    // AndroidX Core（NotificationCompat 等）
    implementation("androidx.core:core-ktx:1.12.0")

    // Media3 ExoPlayer（排除 kotlin-stdlib 避免 D8 版本冲突）
    implementation("androidx.media3:media3-exoplayer:1.1.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("androidx.media3:media3-session:1.1.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("androidx.media3:media3-common:1.1.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
}
