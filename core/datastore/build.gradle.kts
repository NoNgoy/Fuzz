plugins {
    id("fuzz.android.library")
    id("fuzz.hilt")
    alias(libs.plugins.protobuf)
}

// Настройки на Proto DataStore. Секреты подключений шифрует core:data ключом из Android Keystore.
dependencies {
    api(libs.androidx.datastore)
    api(libs.protobuf.kotlin.lite)
    testImplementation(libs.robolectric)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") { option("lite") }
                register("kotlin") { option("lite") }
            }
        }
    }
}
