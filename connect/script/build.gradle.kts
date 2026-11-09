plugins {
    id("fuzz.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

// Скриптовые плагины (раздел 8.4 ТЗ, этап 5): JavaScript в песочнице QuickJS. Модуль чистый JVM:
// приложение получает Android-вариант quickjs-kt, тесты идут на JVM-варианте с нативной библиотекой хоста.
dependencies {
    implementation(projects.core.domain)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.quickjs.kt)

    testImplementation(libs.ktor.client.mock)
}
