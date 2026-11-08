package dev.fuzz.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.model.Playlist
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.ui.R
import kotlinx.collections.immutable.ImmutableList

/** Шторка «Добавить в плейлист»: выбрать существующий или сразу создать новый. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: ImmutableList<Playlist>,
    onPick: (PlaylistId) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.core_ui_add_to_playlist),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
        )
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = Spacing.l)) {
            item {
                SheetRow(Icons.Rounded.Add, stringResource(R.string.core_ui_new_playlist), null) { naming = true }
            }
            items(playlists, key = { it.id.value }) { playlist ->
                SheetRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = playlist.name,
                    subtitle = pluralStringResource(R.plurals.core_ui_tracks, playlist.trackCount, playlist.trackCount),
                ) { onPick(playlist.id) }
            }
        }
    }
    if (naming) {
        NameDialog(
            title = stringResource(R.string.core_ui_new_playlist),
            initial = "",
            onConfirm = {
                naming = false
                onCreate(it)
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().clickable(
                onClick = onClick,
            ).padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(
                44.dp,
            ).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
