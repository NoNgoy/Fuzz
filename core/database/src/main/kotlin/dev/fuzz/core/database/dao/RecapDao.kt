package dev.fuzz.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/** Цифры итогов года за полуинтервал [from, to). */
@Dao
interface RecapDao {
    @Query("SELECT started_at, played_ms FROM listen_events WHERE started_at >= :from AND started_at < :to")
    suspend fun listens(
        from: Long,
        to: Long,
    ): List<RecapListen>

    @Query(
        """
        SELECT t.id AS id, t.title AS title, t.artist_name AS artist, t.artwork_uri AS artwork_uri,
            COUNT(*) AS plays, SUM(l.played_ms) AS played_ms
        FROM listen_events l JOIN tracks t ON t.id = l.track_id
        WHERE l.started_at >= :from AND l.started_at < :to
        GROUP BY t.id ORDER BY plays DESC, played_ms DESC LIMIT :limit
        """,
    )
    suspend fun topTracks(
        from: Long,
        to: Long,
        limit: Int,
    ): List<RecapTrackRow>

    @Query(
        """
        SELECT t.artist_name AS name, COUNT(*) AS plays, SUM(l.played_ms) AS played_ms
        FROM listen_events l JOIN tracks t ON t.id = l.track_id
        WHERE l.started_at >= :from AND l.started_at < :to
        GROUP BY t.artist_name ORDER BY played_ms DESC, plays DESC LIMIT :limit
        """,
    )
    suspend fun topArtists(
        from: Long,
        to: Long,
        limit: Int,
    ): List<RecapArtistRow>

    @Query("SELECT COUNT(DISTINCT track_id) FROM listen_events WHERE started_at >= :from AND started_at < :to")
    suspend fun distinctTracks(
        from: Long,
        to: Long,
    ): Int

    /** Артист новый за год, если его первый трек в коллекции появился в этом году. */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT artist_name FROM tracks WHERE deleted = 0 AND hidden = 0
            GROUP BY artist_name HAVING MIN(added_at) >= :from AND MIN(added_at) < :to
        )
        """,
    )
    suspend fun newArtists(
        from: Long,
        to: Long,
    ): Int
}

data class RecapListen(
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "played_ms") val playedMs: Long,
)

data class RecapTrackRow(
    val id: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String?,
    val plays: Int,
    @ColumnInfo(name = "played_ms") val playedMs: Long,
)

data class RecapArtistRow(
    val name: String,
    val plays: Int,
    @ColumnInfo(name = "played_ms") val playedMs: Long,
)
