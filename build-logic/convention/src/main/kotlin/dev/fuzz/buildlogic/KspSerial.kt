package dev.fuzz.buildlogic

import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** Пустой сервис-замок: через maxParallelUsages = 1 задачи KSP идут по одной на всю сборку. */
abstract class KspLock : BuildService<BuildServiceParameters.None>

/**
 * KSP2 работает внутри демона Gradle, и параллельные задачи KSP разных модулей в CI изредка видят
 * неполный classpath: «type could not be resolved» для классов из core:domain или пустые
 * Hilt-агрегаты, отчего падает hiltJavaCompile. По одной задаче KSP за раз — сбоя нет, остальная
 * сборка остаётся параллельной.
 */
fun Project.serializeKspTasks() {
    val lock =
        gradle.sharedServices.registerIfAbsent("kspLock", KspLock::class.java) {
            maxParallelUsages.set(1)
        }
    tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
        usesService(lock)
    }
}
