plugins {
    id("fuzz.android.application")
    id("fuzz.android.compose")
    id("fuzz.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.fuzz.app"
    defaultConfig {
        applicationId = "dev.fuzz"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Ключи API не коммитятся: ~/.gradle/gradle.properties или переменные окружения CI.
        buildConfigField("String", "LASTFM_API_KEY", "\"${secret("fuzz.lastfm.apiKey", "FUZZ_LASTFM_API_KEY")}\"")
        buildConfigField(
            "String",
            "JAMENDO_CLIENT_ID",
            "\"${secret("fuzz.jamendo.clientId", "FUZZ_JAMENDO_CLIENT_ID")}\"",
        )
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    packaging {
        resources.excludes +=
            setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.design)
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.player)
    implementation(projects.source.local)
    implementation(projects.import.formats)
    implementation(projects.engine.recommend)
    implementation(projects.meta.lastfm)
    implementation(projects.meta.listenbrainz)
    implementation(projects.source.jamendo)
    implementation(projects.source.deezerPreview)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(projects.feature.home)
    implementation(projects.feature.wave)
    implementation(projects.feature.library)
    implementation(projects.feature.search)
    implementation(projects.feature.player)
    implementation(projects.feature.import)
    implementation(projects.feature.settings)
    implementation(projects.feature.connections)
    implementation(projects.feature.onboarding)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
}

fun secret(
    property: String,
    env: String,
): String =
    providers
        .gradleProperty(property)
        .orElse(providers.environmentVariable(env))
        .getOrElse("")
