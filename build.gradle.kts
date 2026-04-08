plugins {
    //trick: for the same plugin versions in all sub-modules
    id("com.android.application").version("7.4.2").apply(false)
    id("com.android.library").version("7.4.2").apply(false)
    kotlin("android").version(Version.getKotlinVersion()).apply(false)
    kotlin("multiplatform").version(Version.getKotlinVersion()).apply(false)
    id("com.google.devtools.ksp").version("${Version.getKotlinVersion()}-2.0.1").apply(false)

}

buildscript {
    dependencies {
        classpath(BuildPlugin.kuikly)
    }
}