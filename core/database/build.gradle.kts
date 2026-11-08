plugins {
    id("fuzz.android.library")
    id("fuzz.android.room")
    id("fuzz.hilt")
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
