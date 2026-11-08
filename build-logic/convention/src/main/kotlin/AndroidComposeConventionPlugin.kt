import com.android.build.api.dsl.CommonExtension
import dev.fuzz.buildlogic.lib
import dev.fuzz.buildlogic.libs
import dev.fuzz.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.lib("compose-bom"))
            add("implementation", bom)
            add("testImplementation", bom)
            add("implementation", libs.lib("compose-ui"))
            add("implementation", libs.lib("compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("compose-ui-tooling"))
            add("testImplementation", libs.lib("compose-ui-test-junit4"))
            add("debugImplementation", libs.lib("compose-ui-test-manifest"))
        }
    }
}
