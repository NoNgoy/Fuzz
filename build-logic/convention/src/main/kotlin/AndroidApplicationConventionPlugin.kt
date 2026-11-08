import com.android.build.api.dsl.ApplicationExtension
import dev.fuzz.buildlogic.configureAndroid
import dev.fuzz.buildlogic.libs
import dev.fuzz.buildlogic.pluginId
import dev.fuzz.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        pluginManager.apply("fuzz.quality")
        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            // Приложение проверяется вместе с модулями: так Lint видит сервис плеера и его фильтры.
            lint.checkDependencies = true
        }
    }
}
