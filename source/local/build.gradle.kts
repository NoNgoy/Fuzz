plugins {
    id("fuzz.android.library")
    id("fuzz.hilt")
}

// Локальная музыка через MediaStore: FLAC, MP3, AAC, OGG, OPUS, WAV и всё, что индексирует система.
dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
