package dev.fuzz.feature.connections

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Javascript
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.component.SectionHeader
import dev.fuzz.core.design.theme.MonoLabel
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.connection.ConnectionCheck
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionKind
import dev.fuzz.core.domain.connection.RequestLine
import dev.fuzz.core.ui.mvi.CollectEffects

@Composable
internal fun ConnectionsEntry(
    onBack: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ConnectionsEffect.Saved -> {
                val text = resources.getString(R.string.feature_connections_saved, effect.name)
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }
    BackHandler(enabled = state.editor != null) { viewModel.onIntent(ConnectionsIntent.CloseEditor) }
    ConnectionsScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
internal fun ConnectionsScreen(
    state: ConnectionsState,
    onIntent: (ConnectionsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        val editor = state.editor
        TopBar(
            title =
                if (editor == null) {
                    stringResource(R.string.feature_connections_title)
                } else {
                    stringResource(editor.kind.label)
                },
            onBack = { if (editor == null) onBack() else onIntent(ConnectionsIntent.CloseEditor) },
        )
        if (editor == null) {
            ConnectionList(state, onIntent)
        } else {
            EditorForm(editor, onIntent)
        }
    }
    state.confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { onIntent(ConnectionsIntent.CancelDelete) },
            title = { Text(stringResource(R.string.feature_connections_delete_title, target.name)) },
            text = { Text(stringResource(R.string.feature_connections_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onIntent(ConnectionsIntent.ConfirmDelete) }) {
                    Text(stringResource(R.string.feature_connections_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(ConnectionsIntent.CancelDelete) }) {
                    Text(stringResource(R.string.feature_connections_cancel))
                }
            },
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.feature_connections_back),
            )
        }
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectionList(
    state: ConnectionsState,
    onIntent: (ConnectionsIntent) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s)) {
                ScreenTitle(stringResource(R.string.feature_connections_title))
                Text(
                    stringResource(R.string.feature_connections_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        }
        if (state.connections.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.feature_connections_mine)) }
            if (state.connections.size > 1) {
                item {
                    Text(
                        stringResource(R.string.feature_connections_order_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screen),
                    )
                }
            }
            itemsIndexed(state.connections, key = { _, config -> config.id }) { index, config ->
                ConnectionCard(
                    config,
                    onIntent,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.connections.lastIndex,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.feature_connections_add)) }
        items(ConnectionKind.entries.toList(), key = { it.name }) { kind ->
            KindRow(kind) { onIntent(ConnectionsIntent.Add(kind)) }
        }
    }
}

@Composable
private fun ConnectionCard(
    config: ConnectionConfig,
    onIntent: (ConnectionsIntent) -> Unit,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .clickable { onIntent(ConnectionsIntent.Edit(config.id)) },
    ) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Icon(config.kind.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Text(
                    config.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(stringResource(config.kind.label), config.baseUrl.substringAfter("://"))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = { onIntent(ConnectionsIntent.AskDelete(config.id)) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(stringResource(R.string.feature_connections_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            if (canMoveUp || canMoveDown) {
                Column {
                    IconButton(onClick = { onIntent(ConnectionsIntent.Move(config.id, -1)) }, enabled = canMoveUp) {
                        Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.feature_connections_move_up))
                    }
                    IconButton(onClick = { onIntent(ConnectionsIntent.Move(config.id, 1)) }, enabled = canMoveDown) {
                        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.feature_connections_move_down))
                    }
                }
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { onIntent(ConnectionsIntent.Toggle(config.id, it)) },
            )
        }
    }
}

@Composable
private fun KindRow(
    kind: ConnectionKind,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screen, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            kind.icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
            Text(stringResource(kind.label), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(kind.hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.feature_connections_add))
    }
}

@Composable
private fun EditorForm(
    editor: ConnectionEditor,
    onIntent: (ConnectionsIntent) -> Unit,
) {
    fun change(transform: ConnectionEditor.() -> ConnectionEditor) =
        onIntent(ConnectionsIntent.Change { it.transform() })
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ScreenTitle(
            stringResource(if (editor.isNew) R.string.feature_connections_new else R.string.feature_connections_edit),
        )
        Text(
            stringResource(editor.kind.help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Field(R.string.feature_connections_field_name, editor.name) { change { copy(name = it) } }
        when (editor.kind) {
            ConnectionKind.Manifest -> {
                CodeField(R.string.feature_connections_field_manifest, editor.manifest) {
                    change { copy(manifest = it) }
                }
            }

            ConnectionKind.Script -> {
                CodeField(R.string.feature_connections_field_script_header, editor.manifest) {
                    change { copy(manifest = it) }
                }
                CodeField(R.string.feature_connections_field_script_code, editor.script, minLines = 10) {
                    change { copy(script = it) }
                }
            }

            else -> {
                Field(
                    R.string.feature_connections_field_url,
                    editor.baseUrl,
                    keyboard = KeyboardType.Uri,
                    placeholder = stringResource(editor.kind.urlExample),
                ) { change { copy(baseUrl = it) } }
                Field(
                    if (editor.kind == ConnectionKind.Ampache) {
                        R.string.feature_connections_field_user_optional
                    } else {
                        R.string.feature_connections_field_user
                    },
                    editor.username,
                ) { change { copy(username = it) } }
            }
        }
        if (editor.kind != ConnectionKind.Script) {
            Field(
                editor.kind.secretLabel,
                editor.secret,
                keyboard = KeyboardType.Password,
                secret = true,
                placeholder =
                    stringResource(R.string.feature_connections_secret_saved).takeIf { editor.hasSavedSecret },
            ) { change { copy(secret = it) } }
        }
        editor.problem?.let { problem ->
            Text(
                problem.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(Spacing.s))
        Button(
            onClick = { onIntent(ConnectionsIntent.Save) },
            enabled = editor.canSave,
            modifier = Modifier.fillMaxWidth().height(Spacing.touchTarget),
        ) {
            if (editor.checking) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.s))
                Text(stringResource(R.string.feature_connections_checking))
            } else {
                Text(stringResource(R.string.feature_connections_check_save))
            }
        }
        Text(
            stringResource(R.string.feature_connections_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!editor.isNew) TestSection(editor, onIntent)
    }
}

/** У сохранённого подключения: «Проверить» и журнал последних запросов, чтобы разобраться, что не так. */
@Composable
private fun TestSection(
    editor: ConnectionEditor,
    onIntent: (ConnectionsIntent) -> Unit,
) {
    HorizontalDivider(Modifier.padding(vertical = Spacing.s))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        OutlinedButton(onClick = { onIntent(ConnectionsIntent.Test) }, enabled = !editor.testing) {
            Text(stringResource(R.string.feature_connections_test))
        }
        when {
            editor.testing -> {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            editor.testResult == ConnectionCheck.Ok -> {
                Text(stringResource(R.string.feature_connections_test_ok), color = MaterialTheme.colorScheme.primary)
            }

            editor.testResult != null -> {
                Text(
                    editor.testResult.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (editor.journal.isNotEmpty()) {
        Text(
            stringResource(R.string.feature_connections_journal),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Spacing.s),
        )
        editor.journal.forEach { JournalRow(it) }
    }
}

@Composable
private fun JournalRow(line: RequestLine) {
    val ok = line.status != null && line.status in HTTP_OK
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
        Text(
            line.status?.toString() ?: "—",
            style = MonoLabel,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.width(32.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(line.path, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            line.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(
            stringResource(R.string.feature_connections_journal_time, formatClock(line.atMs), line.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val HTTP_OK = 200..299

private fun formatClock(atMs: Long): String =
    java.time.Instant
        .ofEpochMilli(atMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))

@Composable
private fun CodeField(
    label: Int,
    value: String,
    minLines: Int = 6,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        minLines = minLines,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Field(
    label: Int,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConnectionCheck.message(): String =
    when (this) {
        ConnectionCheck.Ok -> {
            ""
        }

        ConnectionCheck.Unauthorized -> {
            stringResource(R.string.feature_connections_error_auth)
        }

        ConnectionCheck.Unreachable -> {
            stringResource(R.string.feature_connections_error_network)
        }

        is ConnectionCheck.Invalid -> {
            val reason = reason
            if (reason != null) {
                stringResource(R.string.feature_connections_error_invalid_reason, reason)
            } else {
                stringResource(R.string.feature_connections_error_invalid)
            }
        }
    }

private val ConnectionKind.label: Int
    get() =
        when (this) {
            ConnectionKind.Subsonic -> R.string.feature_connections_kind_subsonic
            ConnectionKind.Jellyfin -> R.string.feature_connections_kind_jellyfin
            ConnectionKind.Ampache -> R.string.feature_connections_kind_ampache
            ConnectionKind.WebDav -> R.string.feature_connections_kind_webdav
            ConnectionKind.Manifest -> R.string.feature_connections_kind_manifest
            ConnectionKind.Script -> R.string.feature_connections_kind_script
        }

private val ConnectionKind.hint: Int
    get() =
        when (this) {
            ConnectionKind.Subsonic -> R.string.feature_connections_hint_subsonic
            ConnectionKind.Jellyfin -> R.string.feature_connections_hint_jellyfin
            ConnectionKind.Ampache -> R.string.feature_connections_hint_ampache
            ConnectionKind.WebDav -> R.string.feature_connections_hint_webdav
            ConnectionKind.Manifest -> R.string.feature_connections_hint_manifest
            ConnectionKind.Script -> R.string.feature_connections_hint_script
        }

private val ConnectionKind.help: Int
    get() =
        when (this) {
            ConnectionKind.Subsonic -> R.string.feature_connections_help_subsonic
            ConnectionKind.Jellyfin -> R.string.feature_connections_help_jellyfin
            ConnectionKind.Ampache -> R.string.feature_connections_help_ampache
            ConnectionKind.WebDav -> R.string.feature_connections_help_webdav
            ConnectionKind.Manifest -> R.string.feature_connections_help_manifest
            ConnectionKind.Script -> R.string.feature_connections_help_script
        }

private val ConnectionKind.urlExample: Int
    get() =
        when (this) {
            ConnectionKind.WebDav -> R.string.feature_connections_url_example_webdav
            else -> R.string.feature_connections_url_example
        }

private val ConnectionKind.secretLabel: Int
    get() =
        when (this) {
            ConnectionKind.Ampache -> R.string.feature_connections_field_password_or_key
            ConnectionKind.Manifest -> R.string.feature_connections_field_token
            else -> R.string.feature_connections_field_password
        }

private val ConnectionKind.icon: ImageVector
    get() =
        when (this) {
            ConnectionKind.Subsonic -> Icons.Rounded.LibraryMusic
            ConnectionKind.Jellyfin -> Icons.Rounded.Movie
            ConnectionKind.Ampache -> Icons.Rounded.Dns
            ConnectionKind.WebDav -> Icons.Rounded.Folder
            ConnectionKind.Manifest -> Icons.Rounded.Code
            ConnectionKind.Script -> Icons.Rounded.Javascript
        }
