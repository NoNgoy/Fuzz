plugins {
    id("fuzz.jvm.library")
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(libs.javax.inject)
}
