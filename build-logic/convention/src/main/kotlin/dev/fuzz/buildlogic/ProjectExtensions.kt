package dev.fuzz.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("Нет библиотеки $alias в каталоге") }

fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalArgumentException("Нет версии $alias в каталоге") }.requiredVersion

fun VersionCatalog.pluginId(alias: String): String =
    findPlugin(alias).orElseThrow { IllegalArgumentException("Нет плагина $alias в каталоге") }.get().pluginId
