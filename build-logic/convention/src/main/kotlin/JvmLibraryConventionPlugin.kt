import dev.fuzz.buildlogic.allowModulesWithoutTests
import dev.fuzz.buildlogic.configureJUnit5
import dev.fuzz.buildlogic.configureKotlin
import dev.fuzz.buildlogic.lib
import dev.fuzz.buildlogic.libs
import dev.fuzz.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.api.JavaVersion

/** Чистый Kotlin/JVM без Android SDK: модель, домен, движки. Тесты на JUnit 5. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        pluginManager.apply("fuzz.quality")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlin()
        configureJUnit5()
        allowModulesWithoutTests()
        dependencies {
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
            add("testImplementation", libs.lib("mockk"))
        }
    }
}
