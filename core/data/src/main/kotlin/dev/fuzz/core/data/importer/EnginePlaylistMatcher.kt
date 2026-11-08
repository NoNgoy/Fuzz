package dev.fuzz.core.data.importer

import dev.fuzz.core.domain.importer.ImportItem
import dev.fuzz.core.domain.importer.PlaylistMatcher
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.model.Track
import dev.fuzz.engine.match.MatchQuery
import dev.fuzz.engine.match.MatchResult
import dev.fuzz.engine.match.TrackMatcher
import javax.inject.Inject

internal class EnginePlaylistMatcher
    @Inject
    constructor() : PlaylistMatcher {
        override fun match(
            refs: List<TrackRef>,
            library: List<Track>,
        ): List<ImportItem> {
            val matcher = TrackMatcher(library)
            return refs.map { ref ->
                when (val result = matcher.match(MatchQuery(ref.title, ref.artist, ref.album, ref.durationMs))) {
                    is MatchResult.Found -> ImportItem.Found(ref, result.track)
                    is MatchResult.Doubtful -> ImportItem.Doubtful(ref, result.candidates)
                    MatchResult.NotFound -> ImportItem.NotFound(ref)
                }
            }
        }
    }
