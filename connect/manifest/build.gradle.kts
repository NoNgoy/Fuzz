plugins {
    id("fuzz.android.library")
}

// Заглушка. Наполняет раздел «Бэкенд и API».
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.network)
}
