plugins {
    id("fuzz.jvm.library")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
