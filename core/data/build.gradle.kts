plugins {
    id("fuzz.android.library")
    id("fuzz.hilt")
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.room.ktx)
    implementation(projects.engine.match)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
