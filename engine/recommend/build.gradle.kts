plugins {
    id("fuzz.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(libs.kotlinx.serialization.json)
}
