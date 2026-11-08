plugins {
    id("fuzz.android.library")
    id("fuzz.hilt")
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    testImplementation(libs.robolectric)
}
