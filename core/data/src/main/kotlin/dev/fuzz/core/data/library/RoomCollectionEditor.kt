package dev.fuzz.core.data.library

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.domain.library.CollectionEditor
import dev.fuzz.core.model.TrackId
import javax.inject.Inject

internal class RoomCollectionEditor
    @Inject
    constructor(
        private val dao: TrackDao,
        private val clock: Clock,
    ) : CollectionEditor {
        override suspend fun hide(track: TrackId) = dao.setHidden(track.value, hidden = true, now = clock.nowMs())

        override suspend fun restore(track: TrackId) = dao.setHidden(track.value, hidden = false, now = clock.nowMs())

        override suspend fun forgetDeleted(track: TrackId) = dao.markDeleted(listOf(track.value), clock.nowMs())
    }
