package dev.fuzz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Откуда брать поток для трека из сети, сохранённого в коллекцию (лайк, плейлист). Сама строка трека лежит
 * в tracks со ссылкой `fuzz://saved/<id>`; при воспроизведении ссылка на поток запрашивается у источника заново,
 * потому что ссылки серверов и плагинов живут недолго.
 */
@Entity(
    tableName = "saved_refs",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SavedRefEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    /** id подключения или встроенного каталога, который отдал трек. */
    @ColumnInfo(name = "source_key") val sourceKey: String,
    /** Превью, открытый каталог или свой сервер: от этого зависит метка у трека. */
    val origin: String,
    /** Внешние ID трека строками `provider\tid`, по одной на строку. */
    @ColumnInfo(name = "external_ids") val externalIds: String,
)
