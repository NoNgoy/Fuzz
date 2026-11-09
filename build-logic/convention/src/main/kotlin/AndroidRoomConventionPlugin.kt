import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.LibraryExtension
import dev.fuzz.buildlogic.lib
import dev.fuzz.buildlogic.libs
import dev.fuzz.buildlogic.pluginId
import dev.fuzz.buildlogic.serializeKspTasks
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("room"))
        pluginManager.apply(libs.pluginId("ksp"))
        serializeKspTasks()
        extensions.configure<RoomExtension> {
            // Схемы коммитятся в репозиторий: по ним пишутся и проверяются миграции.
            schemaDirectory("$projectDir/schemas")
        }
        // MigrationTestHelper в Robolectric-тестах читает схемы из assets тестового набора.
        extensions.configure<LibraryExtension> {
            sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.lib("androidx-room-runtime"))
            add("implementation", libs.lib("androidx-room-ktx"))
            add("ksp", libs.lib("androidx-room-compiler"))
            add("testImplementation", libs.lib("androidx-room-testing"))
        }
    }
}
