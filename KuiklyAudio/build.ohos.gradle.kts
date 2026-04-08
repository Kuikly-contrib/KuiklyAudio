plugins {
    kotlin("multiplatform")
    id("com.android.library")
    `maven-publish`
}

// 动态 Kotlin / Kuikly Core 版本（发布脚本通过 -PkotlinVersion / -PkuiklyCoreVersion 传入）
val kotlinVersion: String = findProperty("kotlinVersion") as? String
    ?: Version.getKotlinOhosCompilerVersion()
val kuiklyCoreVersion: String = findProperty("kuiklyCoreVersion") as? String
    ?: Version.getKuiklyOhosVersion()
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

group = groupId
version = mavenVersion

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        publishLibraryVariants("release")
    }

    js(IR) {
        browser()
        binaries.executable()
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // HarmonyOS target
    ohosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                compileOnly("com.tencent.kuikly-open:core:${kuiklyCoreVersion}")
                compileOnly("com.tencent.kuikly-open:core-annotations:${kuiklyCoreVersion}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}

android {
    namespace = "com.tencent.kuiklybase.audio"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

publishing {
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
