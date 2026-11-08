plugins {
    id("fuzz.jvm.library")
}

// Заглушка. Наполняет раздел «Бэкенд и API»: PlaylistImporter для M3U/M3U8, CSV, JSON, XSPF, текста.
dependencies {
    implementation(projects.core.domain)
}
