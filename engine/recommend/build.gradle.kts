plugins {
    id("fuzz.jvm.library")
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
}
