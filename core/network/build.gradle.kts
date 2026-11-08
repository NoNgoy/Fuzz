plugins {
    id("fuzz.android.library")
}

// Заглушка. Наполняет раздел «Бэкенд и API»: Ktor Client, очередь лимитов, кэш, детектор сети.
dependencies {
    implementation(projects.core.common)
}
