package dev.fuzz.core.domain.library

import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow

/** Правки коллекции руками: убрать трек, вернуть его, забыть удалённый с телефона файл. */
interface CollectionEditor {
    /** Трек пропадает из коллекции и волны, файл на телефоне не трогается. */
    suspend fun hide(track: TrackId)

    suspend fun restore(track: TrackId)

    /** Сколько треков сейчас убрано из коллекции. */
    fun observeHiddenCount(): Flow<Int>

    suspend fun restoreAll()

    /** Файл уже удалён с телефона: трек пропадает сразу, не дожидаясь пересканирования. */
    suspend fun forgetDeleted(track: TrackId)
}
