package dev.fuzz.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Общие настройки компилятора Kotlin для всех модулей: JVM 17 и предупреждения как ошибки. */
internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.version("jvmTarget")))
            allWarningsAsErrors.set(true)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-opt-in=kotlin.RequiresOptIn",
            )
        }
    }
}

/** Модуль без тестов не должен ронять `./gradlew test`. */
internal fun Project.allowModulesWithoutTests() {
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests.set(false)
    }
}

internal fun Project.configureJUnit5() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
    dependencies.add("testImplementation", dependencies.platform(libs.lib("junit5-bom")))
    dependencies.add("testImplementation", libs.lib("junit5-jupiter"))
    dependencies.add("testRuntimeOnly", libs.lib("junit5-platform-launcher"))
}
