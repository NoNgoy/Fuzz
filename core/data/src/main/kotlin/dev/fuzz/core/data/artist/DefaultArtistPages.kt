package dev.fuzz.core.data.artist

import dev.fuzz.core.data.search.OnlineRanking
import dev.fuzz.core.data.search.PluginOnlineSearch
import dev.fuzz.core.domain.artist.AlbumProfile
import dev.fuzz.core.domain.artist.ArtistDirectory
import dev.fuzz.core.domain.artist.ArtistPage
import dev.fuzz.core.domain.artist.ArtistPages
import dev.fuzz.core.domain.artist.ArtistProfile
import dev.fuzz.core.domain.artist.TracksPage
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.search.OnlineOrigin
import dev.fuzz.core.model.Track
import javax.inject.Inject

/**
 * Страница артиста: фото, популярное и альбомы из каталога метаданных (Deezer), а все треки —
 * из поиска по подключениям и каталогам с отбором по имени артиста.
 */
internal class DefaultArtistPages
    @Inject
    constructor(
        private val directory: ArtistDirectory,
        private val search: PluginOnlineSearch,
    ) : ArtistPages {
        override suspend fun searchArtists(query: String): List<ArtistProfile> =
            if (query.isBlank()) emptyList() else directory.searchArtists(query, SEARCH_LIMIT)

        override suspend fun page(artist: ArtistProfile): ArtistPage {
            val details = directory.details(artist) ?: return ArtistPage(artist)
            return ArtistPage(
                artist = details.artist,
                popular = details.top.mapNotNull { search.register(it, Providers.DEEZER) },
                albums = details.albums,
                related = details.related,
            )
        }

        override suspend fun album(album: AlbumProfile): List<Track> =
            directory.albumTracks(album).mapNotNull { search.register(it, Providers.DEEZER) }

        override suspend fun tracks(
            artist: String,
            page: Int,
        ): TracksPage {
            val name = OnlineRanking.normalize(artist)
            val hits = search.search(artist, OnlineOrigin.entries.toSet(), page)
            val own = hits.map { it.track }.filter { OnlineRanking.normalize(it.artistName) == name }
            return TracksPage(own, hasMore = hits.isNotEmpty())
        }

        private companion object {
            const val SEARCH_LIMIT = 10
        }
    }
