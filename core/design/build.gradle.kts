plugins {
    id("fuzz.android.library")
    id("fuzz.android.compose")
}

dependencies {
    api(libs.compose.material3)
    api(libs.compose.foundation)
    api(libs.compose.ui.graphics)
    api(libs.compose.material.icons)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.core.ktx)
}
