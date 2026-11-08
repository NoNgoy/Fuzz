plugins {
    id("fuzz.android.library")
    id("fuzz.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)

    api(libs.media3.exoplayer)
    api(libs.media3.session)
    implementation(libs.media3.common.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.service)

    testImplementation(libs.robolectric)
    testImplementation(libs.media3.test.utils)
}
