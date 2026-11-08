plugins {
    id("fuzz.jvm.library")
}

// Разбор плейлистов: M3U/M3U8, CSV (Exportify, выгрузки Яндекс Музыки), XSPF и простой текст «Исполнитель — Название».
dependencies {
    implementation(projects.core.domain)
}
