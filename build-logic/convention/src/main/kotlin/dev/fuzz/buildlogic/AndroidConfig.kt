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
        // Robolectric на SDK 36 лезет во внутренности FileDescriptor, а JDK 21 их закрывает.
        testOptions.unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
        // Robolectric сам качает android-all. Зеркало можно задать локально: fuzz.robolectric.repo в ~/.gradle/gradle.properties.
        val robolectricRepo = providers.gradleProperty("fuzz.robolectric.repo").orNull
        if (robolectricRepo != null) {
            testOptions.unitTests.all { it.systemProperty("robolectric.dependency.repo.url", robolectricRepo) }
        }
    }
    configureKotlin()
    allowModulesWithoutTests()
}
