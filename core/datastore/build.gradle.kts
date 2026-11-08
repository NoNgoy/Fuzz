plugins {
    id("fuzz.android.library")
}

// Настройки на Proto DataStore и хранилище секретов (Tink). Наполняется вместе с экраном настроек.
dependencies {
    implementation(projects.core.model)
}
