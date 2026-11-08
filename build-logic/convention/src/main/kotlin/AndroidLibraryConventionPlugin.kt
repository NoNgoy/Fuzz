import com.android.build.api.dsl.LibraryExtension
import dev.fuzz.buildlogic.configureAndroid
import dev.fuzz.buildlogic.lib
import dev.fuzz.buildlogic.libs
import dev.fuzz.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Слова, зарезервированные в Java, не могут быть частью пакета. */
private val JAVA_KEYWORD_REPLACEMENTS = mapOf("native" to "nativelib", "import" to "importer")

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        pluginManager.apply("fuzz.quality")
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            // Пространство имён выводится из пути модуля: :core:player -> dev.fuzz.core.player
            namespace = "dev.fuzz." + path.removePrefix(":").split(':').joinToString(".") { segment ->
                val clean = segment.replace("-", "")
                JAVA_KEYWORD_REPLACEMENTS[clean] ?: clean
            }
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
        dependencies {
            // Android-модули тестируются на JUnit 4 + Robolectric (так требуют Robolectric и Roborazzi).
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
            add("testImplementation", libs.lib("mockk"))
        }
    }
}
