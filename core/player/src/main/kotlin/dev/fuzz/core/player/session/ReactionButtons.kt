package dev.fuzz.core.player.session

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.player.R

/**
 * Кнопки «Нравится» и «Не нравится» в Android Auto, уведомлении и на экране блокировки.
 * Сердце закрашено, когда текущий трек уже лайкнут.
 */
@OptIn(UnstableApi::class)
internal class ReactionButtons(
    private val context: Context,
) {
    private var liked = false

    var current: List<CommandButton> = build()
        private set

    /** Пересобирает кнопки под оценку текущего трека; возвращает true, если они поменялись. */
    fun update(reaction: Reaction): Boolean {
        val nowLiked = reaction == Reaction.Like
        if (nowLiked == liked) return false
        liked = nowLiked
        current = build()
        return true
    }

    private fun build(): List<CommandButton> {
        return listOf(
            CommandButton
                .Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                .setDisplayName(
                    context.getString(if (liked) R.string.player_action_unlike else R.string.player_action_like),
                )
                .setSessionCommand(LIKE)
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton
                .Builder(CommandButton.ICON_THUMB_DOWN_UNFILLED)
                .setDisplayName(context.getString(R.string.player_action_dislike))
                .setSessionCommand(DISLIKE)
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    companion object {
        val LIKE = SessionCommand("dev.fuzz.action.LIKE", Bundle.EMPTY)
        val DISLIKE = SessionCommand("dev.fuzz.action.DISLIKE", Bundle.EMPTY)
    }
}
