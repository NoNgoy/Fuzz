import dev.fuzz.buildlogic.lib
import dev.fuzz.buildlogic.libs
import dev.fuzz.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Экран-фича. По правилам архитектуры знает только домен, модель и UI-слои,
 * не знает ни других фич, ни источников, ни базы.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("fuzz.android.library")
        pluginManager.apply("fuzz.android.compose")
        pluginManager.apply("fuzz.hilt")
        pluginManager.apply(libs.pluginId("kotlin-serialization"))
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:design"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("kotlinx-collections-immutable"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("roborazzi"))
            add("testImplementation", libs.lib("roborazzi-compose"))
            add("testImplementation", libs.lib("roborazzi-junit-rule"))
        }
    }
}
