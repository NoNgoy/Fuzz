plugins {
    id("fuzz.jvm.library")
}

// Симуляция волны из раздела 9.1 ТЗ: синтетический мир и слушатель, тесты проверяют раздел 4.3.
dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.engine.recommend)
}
