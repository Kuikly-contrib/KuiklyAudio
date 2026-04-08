plugins {
    //trick: for the same plugin versions in all sub-modules
    id("com.android.application").version("7.4.2").apply(false)
    id("com.android.library").version("7.4.2").apply(false)
    kotlin("android").version(Version.getKotlinOhosCompilerVersion()).apply(false)
    kotlin("multiplatform").version(Version.getKotlinOhosCompilerVersion()).apply(false)
    id("com.google.devtools.ksp").version("${Version.getKotlinOhosCompilerVersion()}-1.0.27").apply(false)

}