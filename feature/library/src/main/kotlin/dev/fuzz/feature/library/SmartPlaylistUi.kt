package dev.fuzz.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberNew
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fuzz.core.domain.library.SmartPlaylist

internal val SmartPlaylist.title: Int
    get() =
        when (this) {
            SmartPlaylist.OnRepeat -> R.string.feature_library_smart_on_repeat
            SmartPlaylist.Forgotten -> R.string.feature_library_smart_forgotten
            SmartPlaylist.RecentlyAdded -> R.string.feature_library_smart_recent
            SmartPlaylist.NeverPlayed -> R.string.feature_library_smart_never_played
        }

internal val SmartPlaylist.hint: Int
    get() =
        when (this) {
            SmartPlaylist.OnRepeat -> R.string.feature_library_smart_on_repeat_hint
            SmartPlaylist.Forgotten -> R.string.feature_library_smart_forgotten_hint
            SmartPlaylist.RecentlyAdded -> R.string.feature_library_smart_recent_hint
            SmartPlaylist.NeverPlayed -> R.string.feature_library_smart_never_played_hint
        }

internal val SmartPlaylist.empty: Int
    get() =
        when (this) {
            SmartPlaylist.OnRepeat -> R.string.feature_library_smart_on_repeat_empty
            SmartPlaylist.Forgotten -> R.string.feature_library_smart_forgotten_empty
            SmartPlaylist.RecentlyAdded -> R.string.feature_library_smart_recent_empty
            SmartPlaylist.NeverPlayed -> R.string.feature_library_smart_never_played_empty
        }

internal val SmartPlaylist.icon: ImageVector
    get() =
        when (this) {
            SmartPlaylist.OnRepeat -> Icons.Rounded.Repeat
            SmartPlaylist.Forgotten -> Icons.Rounded.History
            SmartPlaylist.RecentlyAdded -> Icons.Rounded.FiberNew
            SmartPlaylist.NeverPlayed -> Icons.Rounded.TravelExplore
        }
