plugins {
    id("fuzz.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.ktor.client.mock)
}
