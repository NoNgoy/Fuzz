// Плагины объявляются здесь без применения, чтобы все модули брали их из одного classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.module.graph.assertion)
}

// Правила зависимостей из документа архитектуры. Нарушение валит сборку в CI (`./gradlew assertModuleGraph`).
moduleGraphAssert {
    configurations = setOf("api", "implementation")
    allowed =
        arrayOf(
            // Фича знает только домен, модель и UI-слои
            ":feature:.* -> :core:(domain|model|ui|design)",
            // Плагины: контракты, сеть и базовые модули, но не друг друга
            ":(source|meta|connect|import):.* -> :core:(domain|model|common|network)",
            // Движки: только модель и общие утилиты
            ":(engine|sim):.* -> :core:(model|common)",
            ":sim:.* -> :engine:.*",
            // Ядро
            ":core:.* -> :core:.*",
            ":core:data -> :(engine):.*",
            // Точка сборки связывает всё
            ":app -> .*",
            ":benchmark -> :app",
        )
}
