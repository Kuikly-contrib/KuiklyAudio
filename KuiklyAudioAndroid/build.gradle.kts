plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

// 从 Gradle 参数读取发布配置
val mavenVersion: String = findProperty("mavenVersion") as? String
    ?: findProperty("MAVEN_VERSION") as? String
    ?: "1.0.0"
val groupId: String = findProperty("groupId") as? String
    ?: findProperty("GROUP_ID") as? String
    ?: "com.tencent.kuiklybase"
val mavenRepoUrl: String = findProperty("mavenRepoUrl") as? String
    ?: findProperty("MAVEN_REPO_URL") as? String
    ?: "REDACTED_MAVEN_URL"
val mavenUsername: String = findProperty("mavenUsername") as? String
    ?: findProperty("MAVEN_USERNAME") as? String
    ?: ""
val mavenPassword: String = findProperty("mavenPassword") as? String
    ?: findProperty("MAVEN_PASSWORD") as? String
    ?: ""

// 动态 Kuikly Core 版本
val kuiklyCoreVersion: String = findProperty("kuiklyCoreVersion") as? String
    ?: Version.getKuiklyVersion()

group = groupId
version = mavenVersion

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
    compileOnly("com.tencent.kuikly-open:core-render-android:${kuiklyCoreVersion}")

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

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            artifactId = "KuiklyAudioAndroid"
        }
    }
    repositories {
        maven {
            url = uri(mavenRepoUrl)
            credentials {
                username = mavenUsername
                password = mavenPassword
            }
        }
    }
}
