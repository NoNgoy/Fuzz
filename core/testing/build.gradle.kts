plugins {
    id("fuzz.android.library")
    id("fuzz.android.compose")
}

// Общие помощники для тестов: корутины, Turbine и скриншоты Roborazzi во всех темах.
dependencies {
    api(projects.core.domain)
    api(projects.core.design)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.junit4)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.compose.ui.test.junit4)
}
