plugins {
    id("fuzz.android.library")
}

// Ktor Client, очередь лимитов, safeCall. Наполняет раздел «Бэкенд и API».
dependencies {
    implementation(projects.core.common)
    api(projects.core.domain)
    implementation(libs.kotlinx.coroutines.core)
}
