package dev.fuzz.source.local

import dev.fuzz.core.domain.onboarding.MusicFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderFilterTest {
    @Test
    fun `relative folder matches on every volume`() {
        assertEquals(FolderLocation(null, "Music/"), MusicFolder("Music", "Музыка").toLocation())
    }

    @Test
    fun `tree on primary storage maps to external_primary`() {
        val folder = MusicFolder("content://com.android.externalstorage.documents/tree/primary%3AMusic%2FRock", "Rock")
        assertEquals(FolderLocation("external_primary", "Music/Rock/"), folder.toLocation())
    }

    @Test
    fun `tree on sd card maps to its volume`() {
        val folder = MusicFolder("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A", "SD")
        assertEquals(FolderLocation("1a2b-3c4d", ""), folder.toLocation())
    }

    @Test
    fun `like wildcards in folder names are escaped`() {
        val filter = FolderFilter.of(listOf(MusicFolder("My_Music%/", "x")))!!
        assertEquals(listOf("My\\_Music\\%/%"), filter.args)
    }

    @Test
    fun `no folders gives no filter`() {
        assertNull(FolderFilter.of(emptyList()))
    }

    @Test
    fun `format labels`() {
        assertEquals("FLAC", formatLabel("audio/flac"))
        assertEquals("MP3", formatLabel("audio/mpeg"))
        assertEquals(5, trackNumberOf(2005))
        assertNull("<unknown>".tagOrNull())
    }
}
