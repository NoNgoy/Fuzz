package dev.fuzz.feature.connections

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.connection.ConnectionCheck
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionKind
import dev.fuzz.core.domain.connection.ConnectionsRepository
import dev.fuzz.core.domain.connection.RequestLine
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Форма подключения. Секрет на экран не возвращается: пустое поле при правке значит «оставить прежний». */
data class ConnectionEditor(
    val kind: ConnectionKind,
    val id: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val secret: String = "",
    val manifest: String = "",
    /** Код JS-плагина; описание плагина лежит в [manifest]. */
    val script: String = "",
    val hasSavedSecret: Boolean = false,
    val checking: Boolean = false,
    val problem: ConnectionCheck? = null,
    /** Проверка сохранённого подключения кнопкой «Проверить»: идёт и чем закончилась. */
    val testing: Boolean = false,
    val testResult: ConnectionCheck? = null,
    /** Последние запросы сохранённого подключения, новые сверху. */
    val journal: ImmutableList<RequestLine> = persistentListOf(),
) {
    val isNew: Boolean get() = id == null
    val canSave: Boolean
        get() =
            !checking &&
                when (kind) {
                    ConnectionKind.Manifest -> manifest.isNotBlank()
                    ConnectionKind.Script -> manifest.isNotBlank() && script.isNotBlank()
                    else -> baseUrl.isNotBlank()
                }
}

data class ConnectionsState(
    val connections: ImmutableList<ConnectionConfig> = persistentListOf(),
    val editor: ConnectionEditor? = null,
    /** Подключение, которое просят удалить: ждёт подтверждения. */
    val confirmDelete: ConnectionConfig? = null,
) : UiState

sealed interface ConnectionsIntent : UiIntent {
    data class Add(
        val kind: ConnectionKind,
    ) : ConnectionsIntent

    data class Edit(
        val id: String,
    ) : ConnectionsIntent

    data class Change(
        val transform: (ConnectionEditor) -> ConnectionEditor,
    ) : ConnectionsIntent

    data object Save : ConnectionsIntent

    /** «Проверить» у сохранённого подключения: пробный поиск и свежий журнал запросов. */
    data object Test : ConnectionsIntent

    data object CloseEditor : ConnectionsIntent

    data class Toggle(
        val id: String,
        val enabled: Boolean,
    ) : ConnectionsIntent

    /** Выше (−1) или ниже (+1) в порядке источников. */
    data class Move(
        val id: String,
        val offset: Int,
    ) : ConnectionsIntent

    data class AskDelete(
        val id: String,
    ) : ConnectionsIntent

    data object ConfirmDelete : ConnectionsIntent

    data object CancelDelete : ConnectionsIntent
}

sealed interface ConnectionsEffect : UiEffect {
    data class Saved(
        val name: String,
    ) : ConnectionsEffect
}

@HiltViewModel
class ConnectionsViewModel
    @Inject
    constructor(
        private val repository: ConnectionsRepository,
    ) : MviViewModel<ConnectionsState, ConnectionsIntent, ConnectionsEffect>(ConnectionsState()) {
        init {
            viewModelScope.launch {
                repository.connections.collect { list -> setState { copy(connections = list.toImmutableList()) } }
            }
        }

        override fun onIntent(intent: ConnectionsIntent) {
            when (intent) {
                is ConnectionsIntent.Add -> {
                    setState { copy(editor = ConnectionEditor(intent.kind)) }
                }

                is ConnectionsIntent.Edit -> {
                    edit(intent.id)
                }

                is ConnectionsIntent.Change -> {
                    updateEditor { intent.transform(this).copy(problem = null) }
                }

                ConnectionsIntent.Save -> {
                    save()
                }

                ConnectionsIntent.Test -> {
                    test()
                }

                ConnectionsIntent.CloseEditor -> {
                    setState { copy(editor = null) }
                }

                is ConnectionsIntent.Toggle -> {
                    viewModelScope.launch { repository.setEnabled(intent.id, intent.enabled) }
                }

                is ConnectionsIntent.Move -> {
                    viewModelScope.launch { repository.move(intent.id, intent.offset) }
                }

                is ConnectionsIntent.AskDelete -> {
                    setState {
                        copy(
                            confirmDelete =
                                connections.firstOrNull {
                                    it.id ==
                                        intent.id
                                },
                        )
                    }
                }

                ConnectionsIntent.ConfirmDelete -> {
                    delete()
                }

                ConnectionsIntent.CancelDelete -> {
                    setState { copy(confirmDelete = null) }
                }
            }
        }

        private fun edit(id: String) {
            val config = currentState.connections.firstOrNull { it.id == id } ?: return
            setState {
                copy(
                    editor =
                        ConnectionEditor(
                            kind = config.kind,
                            id = config.id,
                            name = config.name,
                            baseUrl = config.baseUrl,
                            username = config.username,
                            manifest = config.manifest,
                            script = config.script,
                            hasSavedSecret = config.hasSecret,
                            journal = repository.journal(config.id).toImmutableList(),
                        ),
                )
            }
        }

        private fun save() {
            val editor = currentState.editor ?: return
            if (!editor.canSave) return
            updateEditor { copy(checking = true, problem = null) }
            viewModelScope.launch {
                val existing = currentState.connections.firstOrNull { it.id == editor.id }
                val config =
                    ConnectionConfig(
                        id = editor.id ?: UUID.randomUUID().toString(),
                        kind = editor.kind,
                        name = editor.name.trim().ifEmpty { defaultName(editor) },
                        baseUrl = editor.baseUrl.trim(),
                        username = editor.username.trim(),
                        manifest = editor.manifest.trim(),
                        script = editor.script.trim(),
                        enabled = existing?.enabled ?: true,
                    )
                // При правке пустое поле пароля значит «не менять».
                val secret = if (editor.secret.isEmpty() && editor.hasSavedSecret) null else editor.secret
                when (val check = repository.save(config, secret)) {
                    ConnectionCheck.Ok -> {
                        setState { copy(editor = null) }
                        sendEffect(ConnectionsEffect.Saved(config.name))
                    }

                    else -> {
                        updateEditor { copy(checking = false, problem = check) }
                    }
                }
            }
        }

        private fun test() {
            val id = currentState.editor?.id ?: return
            updateEditor { copy(testing = true, testResult = null) }
            viewModelScope.launch {
                val result = repository.check(id)
                updateEditor {
                    if (this.id != id) {
                        this
                    } else {
                        copy(testing = false, testResult = result, journal = repository.journal(id).toImmutableList())
                    }
                }
            }
        }

        private fun delete() {
            val target = currentState.confirmDelete ?: return
            setState { copy(confirmDelete = null) }
            viewModelScope.launch { repository.remove(target.id) }
        }

        private fun updateEditor(change: ConnectionEditor.() -> ConnectionEditor) =
            setState { editor?.let { copy(editor = it.change()) } ?: this }

        /** Без названия берём хост адреса, а у манифеста и плагина — поле name из описания. */
        private fun defaultName(editor: ConnectionEditor): String =
            runCatching { java.net.URI(editor.baseUrl.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: NAME_FIELD.find(editor.manifest)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?: editor.kind.name

        private companion object {
            val NAME_FIELD = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
        }
    }
