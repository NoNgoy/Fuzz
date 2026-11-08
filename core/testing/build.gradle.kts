plugins {
    id("fuzz.android.library")
}

dependencies {
    api(projects.core.domain)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.junit4)
}
