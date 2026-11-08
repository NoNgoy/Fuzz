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
    }
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
