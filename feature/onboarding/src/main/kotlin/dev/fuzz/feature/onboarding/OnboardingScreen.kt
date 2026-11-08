package dev.fuzz.feature.onboarding

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.FuzzLogo
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Motion
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.onboarding.MusicFolder
import dev.fuzz.core.ui.mvi.CollectEffects

@Composable
internal fun OnboardingEntry(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onIntent(OnboardingIntent.Start(isXiaomiFirmware())) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            OnboardingEffect.Finished -> onFinished()
        }
    }
    val context = LocalContext.current
    var system by remember { mutableStateOf(context.systemStatus()) }
    LifecycleResumeEffect(Unit) {
        system = context.systemStatus()
        onPauseOrDispose { }
    }
    var permissionsAsked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsAsked = true
            system = context.systemStatus()
            if (system.audioGranted) viewModel.onIntent(OnboardingIntent.Next)
        }
    val storageLabel = stringResource(R.string.feature_onboarding_folders_storage)
    val folderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                viewModel.onIntent(
                    OnboardingIntent.AddFolder(MusicFolder(uri.toString(), treeLabel(uri, storageLabel))),
                )
            }
        }
    BackHandler(enabled = state.step != state.steps.first()) { viewModel.onIntent(OnboardingIntent.Back) }

    OnboardingScreen(
        state = state,
        system = system.copy(audioDenied = permissionsAsked && !system.audioGranted),
        actions =
            OnboardingActions(
                onIntent = viewModel::onIntent,
                onRequestPermissions = { permissionLauncher.launch(requiredPermissions()) },
                onOpenAppSettings = { context.startActivity(context.appDetailsIntent()) },
                onAddFolder = { folderLauncher.launch(null) },
                onOpenAutostart = context::openAutostartSettings,
                onRequestBattery = context::requestIgnoreBatteryOptimizations,
            ),
    )
}

/** Что известно о системе: выданные разрешения и исключение из оптимизации батареи. */
internal data class SystemStatus(
    val audioGranted: Boolean,
    val notificationsGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val audioDenied: Boolean = false,
)

private fun Context.systemStatus(): SystemStatus {
    val granted = { permission: String -> checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED }
    val notificationPermission = requiredPermissions().getOrNull(1)
    return SystemStatus(
        audioGranted = granted(audioPermission),
        notificationsGranted = notificationPermission == null || granted(notificationPermission),
        batteryUnrestricted = isIgnoringBatteryOptimizations(),
    )
}

internal class OnboardingActions(
    val onIntent: (OnboardingIntent) -> Unit,
    val onRequestPermissions: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onAddFolder: () -> Unit,
    val onOpenAutostart: () -> Unit,
    val onRequestBattery: () -> Unit,
)

@Composable
internal fun OnboardingScreen(
    state: OnboardingState,
    system: SystemStatus,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        StepDots(steps = state.steps, current = state.step)
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn(tween(Motion.SHEET)) togetherWith fadeOut(tween(Motion.PRESS)) },
            modifier = Modifier.weight(1f),
            label = "onboarding-step",
        ) { step ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Hero()
                when (step) {
                    OnboardingStep.Permissions -> PermissionsStep(system, actions)
                    OnboardingStep.Folders -> FoldersStep(state, actions)
                    OnboardingStep.Background -> BackgroundStep(system, actions)
                }
            }
        }
        BottomButtons(state = state, system = system, actions = actions)
    }
}

@Composable
private fun StepDots(
    steps: List<OnboardingStep>,
    current: OnboardingStep,
) {
    val description = stringResource(R.string.feature_onboarding_step, steps.indexOf(current) + 1, steps.size)
    Row(
        modifier = Modifier.padding(top = Spacing.m).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEach { step ->
            val active = step == current
            val width by animateDpAsState(if (active) 22.dp else 8.dp, tween(Motion.SHEET), label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun Hero() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(FuzzTheme.colors.accentSoft, MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        FuzzLogo(
            background = MaterialTheme.colorScheme.primary,
            wave = MaterialTheme.colorScheme.onPrimary,
            lamp = FuzzTheme.colors.lamp,
            modifier = Modifier.size(84.dp),
        )
    }
}

@Composable
private fun StepText(
    title: Int,
    body: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) { content() }
}

@Composable
private fun SettingRow(
    title: String,
    hint: String,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun GrantedMark(granted: Boolean) {
    if (granted) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}

@Composable
private fun PermissionsStep(
    system: SystemStatus,
    actions: OnboardingActions,
) {
    StepText(R.string.feature_onboarding_permissions_title, R.string.feature_onboarding_permissions_body)
    Panel {
        SettingRow(
            title = stringResource(R.string.feature_onboarding_permission_audio),
            hint = stringResource(R.string.feature_onboarding_permission_audio_hint),
            icon = Icons.Rounded.MusicNote,
        ) { GrantedMark(system.audioGranted) }
        SettingRow(
            title = stringResource(R.string.feature_onboarding_permission_notifications),
            hint = stringResource(R.string.feature_onboarding_permission_notifications_hint),
            icon = Icons.Rounded.Notifications,
        ) { GrantedMark(system.notificationsGranted) }
    }
    if (system.audioDenied) {
        Text(
            stringResource(R.string.feature_onboarding_permissions_denied),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = actions.onOpenAppSettings) {
            Text(stringResource(R.string.feature_onboarding_open_app_settings))
        }
    }
}

@Composable
private fun FoldersStep(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    StepText(R.string.feature_onboarding_folders_title, R.string.feature_onboarding_folders_body)
    Panel {
        state.folders.forEach { option ->
            SettingRow(
                title = option.folder.label,
                hint = option.folder.displayPath(),
                icon = Icons.Rounded.Folder,
                onClick = { actions.onIntent(OnboardingIntent.ToggleFolder(option.folder.id)) },
            ) {
                Checkbox(
                    checked = option.selected,
                    onCheckedChange = { actions.onIntent(OnboardingIntent.ToggleFolder(option.folder.id)) },
                )
            }
        }
    }
    OutlinedButton(onClick = actions.onAddFolder) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.s))
        Text(stringResource(R.string.feature_onboarding_folders_add))
    }
    if (!state.canContinue) {
        Text(
            stringResource(R.string.feature_onboarding_folders_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun MusicFolder.displayPath(): String =
    if (id.startsWith("content://")) {
        Uri.decode(id.toUri().lastPathSegment.orEmpty()).substringAfter(':')
    } else {
        id
    }

@Composable
private fun BackgroundStep(
    system: SystemStatus,
    actions: OnboardingActions,
) {
    StepText(R.string.feature_onboarding_background_title, R.string.feature_onboarding_background_body)
    Panel {
        SettingRow(
            title = stringResource(R.string.feature_onboarding_autostart),
            hint = stringResource(R.string.feature_onboarding_autostart_hint),
            onClick = actions.onOpenAutostart,
        ) { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
        SettingRow(
            title = stringResource(R.string.feature_onboarding_battery),
            hint = stringResource(R.string.feature_onboarding_battery_hint),
            onClick = actions.onRequestBattery.takeUnless { system.batteryUnrestricted },
        ) {
            Switch(
                checked = system.batteryUnrestricted,
                onCheckedChange = { if (!system.batteryUnrestricted) actions.onRequestBattery() },
            )
        }
    }
}

@Composable
private fun BottomButtons(
    state: OnboardingState,
    system: SystemStatus,
    actions: OnboardingActions,
) {
    val isLast = state.step == state.steps.last()
    val next = { actions.onIntent(OnboardingIntent.Next) }
    val nextLabel = if (isLast) R.string.feature_onboarding_done else R.string.feature_onboarding_next
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state.step) {
            OnboardingStep.Permissions -> {
                val allGranted = system.audioGranted && system.notificationsGranted
                Button(
                    onClick = if (allGranted) next else actions.onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(if (allGranted) nextLabel else R.string.feature_onboarding_permissions_grant))
                }
                if (!allGranted) WideTextButton(R.string.feature_onboarding_later, next)
            }

            OnboardingStep.Folders -> {
                Button(
                    onClick = next,
                    enabled = state.canContinue,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(stringResource(nextLabel)) }
            }

            OnboardingStep.Background -> {
                Button(onClick = actions.onOpenAutostart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(R.string.feature_onboarding_open_settings))
                }
                val skipLabel =
                    if (system.batteryUnrestricted) {
                        R.string.feature_onboarding_done
                    } else {
                        R.string.feature_onboarding_later
                    }
                WideTextButton(skipLabel, next)
            }
        }
    }
}

@Composable
private fun WideTextButton(
    label: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
}
