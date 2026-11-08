plugins {
    id("fuzz.jvm.library")
}

// Симуляция волны из раздела 9.1 ТЗ. Наполняется на этапе 2 вместе с движком.
dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.engine.recommend)
}
