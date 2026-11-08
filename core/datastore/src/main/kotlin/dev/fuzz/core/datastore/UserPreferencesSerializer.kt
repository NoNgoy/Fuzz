package dev.fuzz.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class UserPreferencesSerializer
    @Inject
    constructor() : Serializer<UserPreferences> {
        override val defaultValue: UserPreferences = UserPreferences.getDefaultInstance()

        override suspend fun readFrom(input: InputStream): UserPreferences =
            try {
                UserPreferences.parseFrom(input)
            } catch (e: InvalidProtocolBufferException) {
                throw CorruptionException("Не читается user_preferences.pb", e)
            }

        override suspend fun writeTo(
            t: UserPreferences,
            output: OutputStream,
        ) = t.writeTo(output)
    }
