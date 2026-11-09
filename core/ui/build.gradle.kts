plugins {
    id("fuzz.android.library")
    id("fuzz.android.compose")
    id("fuzz.hilt")
}

dependencies {
    api(projects.core.design)
    api(projects.core.model)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.kotlinx.collections.immutable)
    api(libs.coil.compose)
    implementation(projects.core.domain)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    testImplementation(libs.robolectric)
}
