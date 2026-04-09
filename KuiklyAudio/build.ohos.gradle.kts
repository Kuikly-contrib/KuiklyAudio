plugins {
    kotlin("multiplatform")
    `maven-publish`
}

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
