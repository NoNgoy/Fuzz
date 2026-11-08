import com.diffplug.gradle.spotless.SpotlessExtension
import dev.fuzz.buildlogic.libs
import dev.fuzz.buildlogic.pluginId
import dev.fuzz.buildlogic.version
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** detekt и ktlint (через Spotless) для каждого модуля. */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("detekt"))
        pluginManager.apply(libs.pluginId("spotless"))
        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            basePath = rootProject.projectDir.absolutePath
        }
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint(libs.version("ktlint"))
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(libs.version("ktlint"))
            }
        }
    }
}
