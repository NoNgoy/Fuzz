pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "fuzz"

include(":app")

// Ядро
include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:player")
include(":core:native")
include(":core:design")
include(":core:ui")
include(":core:testing")

// Движки (чистый JVM)
include(":engine:recommend")
include(":engine:search")
include(":engine:match")

// Плагины источников, метаданных, импорта и подключений
include(":source:local")
include(":source:jamendo")
include(":source:deezer-preview")
include(":source:subsonic")
include(":meta:lastfm")
include(":meta:listenbrainz")
include(":meta:musicbrainz")
include(":meta:lyrics")
include(":import:formats")
include(":connect:templates")
include(":connect:manifest")
include(":connect:script")

// Экраны
include(":feature:home")
include(":feature:wave")
include(":feature:library")
include(":feature:search")
include(":feature:player")
include(":feature:import")
include(":feature:settings")
include(":feature:connections")
include(":feature:onboarding")

// Замеры и симуляция
// :benchmark (Macrobenchmark и Baseline Profiles) подключается, когда появятся ключевые сценарии
// include(":benchmark")
include(":sim:wave")
