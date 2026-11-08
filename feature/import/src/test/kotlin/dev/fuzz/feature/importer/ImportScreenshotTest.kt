package dev.fuzz.feature.importer

import dev.fuzz.core.domain.importer.ImportItem
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.testing.SampleTracks
import dev.fuzz.core.testing.captureThemedScreens
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h873dp-440dpi")
class ImportScreenshotTest {
    private val tracks = SampleTracks.all

    private fun ref(track: Int) = TrackRef(title = tracks[track].title.lowercase(), artist = tracks[track].artistName)

    private val rows =
        persistentListOf(
            ReportRow(ImportItem.Found(ref(0), tracks[0])),
            ReportRow(ImportItem.Doubtful(ref(1), listOf(tracks[1], tracks[2]))),
            ReportRow(ImportItem.Found(ref(3), tracks[3])),
            ReportRow(ImportItem.NotFound(TrackRef(title = "Утро в сосновом бору", artist = "Шишкин"))),
            ReportRow(ImportItem.Doubtful(ref(4), listOf(tracks[4]))),
        )

    @Test
    fun pickFile() =
        captureThemedScreens("import_pick") {
            ImportScreen(
                state = ImportState(supportedFormats = "CSV, M3U, M3U8, TXT, XSPF"),
                onIntent = {},
                onPick = {},
                onBack = {},
            )
        }

    @Test
    fun unsupported() =
        captureThemedScreens("import_unsupported") {
            ImportScreen(
                state =
                    ImportState(
                        problem = ImportProblem.Unsupported("pdf", "CSV, M3U, M3U8, TXT, XSPF"),
                        supportedFormats = "CSV, M3U, M3U8, TXT, XSPF",
                    ),
                onIntent = {},
                onPick = {},
                onBack = {},
            )
        }

    @Test
    fun reportDoubtful() =
        captureThemedScreens("import_report_doubtful") {
            ImportScreen(
                state = ImportState(stage = ImportStage.Report("Дорога домой", "M3U", rows, ReportTab.Doubtful)),
                onIntent = {},
                onPick = {},
                onBack = {},
            )
        }

    @Test
    fun reportFound() =
        captureThemedScreens("import_report_found") {
            ImportScreen(
                state = ImportState(stage = ImportStage.Report("Дорога домой", "M3U", rows, ReportTab.Found)),
                onIntent = {},
                onPick = {},
                onBack = {},
            )
        }
}
