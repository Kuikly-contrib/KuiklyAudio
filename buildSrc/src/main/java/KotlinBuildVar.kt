object Version {

    private const val KUIKLY_VERSION = "2.7.0"
    private const val KOTLIN_VERSION = "2.1.21"
    // Kuikly Core 鸿蒙版本后缀（不是 Kotlin 编译器版本）
    private const val KOTLIN_OHOS_VERSION = "2.0.21-ohos"
    // 鸿蒙 Kotlin 编译器版本
    private const val KOTLIN_OHOS_COMPILER_VERSION = "2.0.21-KBA-010"

    /**
     * 获取 Kotlin 版本号（标准构建用）
     */
    fun getKotlinVersion(): String {
        return KOTLIN_VERSION
    }

    /**
     * 获取鸿蒙 Kotlin 编译器版本号
     */
    fun getKotlinOhosCompilerVersion(): String {
        return KOTLIN_OHOS_COMPILER_VERSION
    }

    /**
     * 获取 Kuikly 版本号，版本号规则：${shortVersion}-${kotlinVersion}
     * 适用于 core、core-ksp、core-annotation、core-render-android
     */
    fun getKuiklyVersion(): String {
        return "$KUIKLY_VERSION-$KOTLIN_VERSION"
    }

    /**
     * 获取 Kuikly Ohos版本号
     */
    fun getKuiklyOhosVersion(): String {
        return "$KUIKLY_VERSION-$KOTLIN_OHOS_VERSION"
    }
}

object BuildPlugin {
    val kuikly by lazy {
        "com.tencent.kuikly-open:core-gradle-plugin:${Version.getKuiklyVersion()}"
    }
}
