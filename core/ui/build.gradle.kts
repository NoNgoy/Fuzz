plugins {
    id("fuzz.android.library")
    id("fuzz.android.compose")
}

dependencies {
    api(projects.core.design)
    api(projects.core.model)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.kotlinx.collections.immutable)
    api(libs.coil.compose)
    testImplementation(libs.robolectric)
}
