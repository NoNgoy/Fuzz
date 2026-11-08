package dev.fuzz.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/** Общие настройки Android для приложения и библиотек. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        lint.apply {
            warningsAsErrors = true
            abortOnError = true
            checkDependencies = false
            // Версии обновляет Renovate отдельными PR, сборку это не ломает.
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
        }
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
    }
    configureKotlin()
    allowModulesWithoutTests()
}
