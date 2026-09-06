package me.newtale.betterhud.entries.dialogue.messenger

import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.engine.paper.entry.dialogue.*
import com.typewritermc.engine.paper.interaction.*
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.snippets.snippet
import com.typewritermc.engine.paper.utils.stripped
import kr.toxicity.hud.api.bukkit.event.CustomPopupEvent
import me.newtale.betterhud.entries.dialogue.BetterHudSpokenEntry
import me.newtale.betterhud.utils.BetterHudPopup
import me.newtale.betterhud.utils.DelayedText
import me.newtale.betterhud.utils.asPartialMiniMessageString
import me.newtale.betterhud.utils.parseDelays
import org.bukkit.entity.Player
import java.time.Duration
import java.util.logging.Logger

val spoken_popup: String by snippet("betterhud.spoken.popup", "")

class BetterHudSpokenDialogueMessenger(
        player: Player,
        context: InteractionContext,
        entry: BetterHudSpokenEntry
) : DialogueMessenger<BetterHudSpokenEntry>(player, context, entry) {

    private var confirmation: Confirmation? = null
    private var speakerDisplayName = ""
    private var text = ""
    private var rawText = ""
    private var delayedText: DelayedText? = null
    private var typingDuration = Duration.ZERO
    private var playedTime = Duration.ZERO

    private var typingSound = false
    private var interactionContext = context

    private var betterHudPopup: BetterHudPopup? = null
    private var lastDisplayedText = ""

    private val logger = Logger.getLogger("BetterHudDialogue")

    override var animationComplete: Boolean
        get() = playedTime >= typingDuration
        set(value) {
            playedTime = if (!value) Duration.ZERO else typingDuration
        }

    override fun init() {
        super.init()

        try {
            val speaker = entry.speaker.get()
            speakerDisplayName =
                    speaker?.displayName?.get(player)?.parsePlaceholders(player) ?: "Unknown"

            val originalText = entry.text.get(player).parsePlaceholders(player)
            val parsed = parseDelays(originalText)
            delayedText = parsed

            rawText = parsed.rawTextWithoutDelays
            text = parsed.textWithoutDelays

            val baseDuration = entry.duration.get(player)
            typingDuration = parsed.getTotalDuration(typingDurationType, baseDuration)

            typingSound = entry.typingSound.get(player)
            val popupId = entry.popupId.get(player).ifBlank { spoken_popup }
            betterHudPopup = BetterHudPopup(player, popupId).also {
                if (!it.resolve()) {
                    throw IllegalStateException("Could not resolve BetterHud popup '$popupId'")
                }
            }

            confirmation = player.awaitConfirmation { completeOrFinish() }

            entry.playDialogueSound(player, context)

            showPopupInitially()
        } catch (e: Exception) {
            logger.warning("BetterHud initialization error for ${player.name}: ${e.message}")
            player.sendMessage("§cDialog initialization error: ${e.message}")
            state = MessengerState.CANCELLED
        }
    }

    private fun showPopupInitially() {
        val popup = betterHudPopup ?: return

        try {
            val baseDuration = entry.duration.get(player)
            val percentage =
                    if (baseDuration.isZero) 1.0
                    else {
                        delayedText?.calculatePercentage(typingDurationType, playedTime, baseDuration)
                                ?: typingDurationType.calculatePercentage(playedTime, baseDuration, rawText)
                    }
            val currentText = getCurrentText(percentage)

            popup.show { event -> addDialogueVariables(event, currentText, percentage) }
            lastDisplayedText = currentText

            if (!popup.isShown) {
                logger.warning("Failed to show popup for ${player.name}")
            }
        } catch (e: Exception) {
            logger.warning("Failed to show popup initially for ${player.name}: ${e.message}")
        }
    }

    override fun tick(context: TickContext) {
        if (state != MessengerState.RUNNING) return
        if (betterHudPopup?.isShown != true) return

        if (!animationComplete) {
            playedTime += context.deltaTime
        }

        val baseDuration = entry.duration.get(player)
        val percentage =
                if (baseDuration.isZero) 1.0
                else {
                    delayedText?.calculatePercentage(typingDurationType, playedTime, baseDuration)
                            ?: typingDurationType.calculatePercentage(playedTime, baseDuration, rawText)
                }
        val currentText = getCurrentText(percentage)

        if (typingSound) {
            val previousLength = lastDisplayedText.stripped().length
            val currentLength = getCurrentTextLength(percentage)

            if (currentLength > previousLength) {
                val newChar = rawText.getOrNull(previousLength)

                if (newChar != null && !newChar.isWhitespace()) {
                    entry.playDialogueSound(player, interactionContext)
                }
            }
        }

        updatePopup(currentText, percentage)
        lastDisplayedText = currentText
    }

    private fun updatePopup(currentText: String, percentage: Double) {
        val popup = betterHudPopup ?: return

        try {
            popup.update { event -> addDialogueVariables(event, currentText, percentage) }
        } catch (e: Exception) {
            logger.warning("Popup update error for ${player.name}: ${e.message}")
        }
    }

    private fun hidePopup() {
        betterHudPopup?.hide()
        betterHudPopup = null
    }

    private fun confirmationKeyText(): String {
        val key = confirmationKey
        return key.label(player)
    }

    private fun addDialogueVariables(
            event: CustomPopupEvent,
            currentText: String,
            percentage: Double
    ) {
        val canFinish = eventTriggers.isEmpty()
        val isComplete = percentage >= 1.0

        event.variables.apply {
            put("speaker", speakerDisplayName)
            put("text", currentText)
            put("show", "true")
            put("progress", (percentage * 100).toInt().toString())
            put("percentage", String.format("%.2f", percentage))
            put("instruction", if (canFinish) "finish" else "continue")
            put("is_complete", isComplete.toString())

            put("confirmation_key", confirmationKeyText())

            put("raw_text", rawText)
            put("total_chars", rawText.length.toString())
            put("visible_chars", getCurrentTextLength(percentage).toString())

            put("typewriter_speaker", speakerDisplayName)
            put("typewriter_text", currentText)
            put("typewriter_show", "true")
            put("typewriter_progress", (percentage * 100).toInt().toString())
            put("typewriter_instruction", if (canFinish) "finish" else "continue")
            put("confirmation_key", confirmationKeyText())
        }

        entry.customVariables.forEach { (key, value) ->
            val varValue = value.get(player).parsePlaceholders(player)
            event.variables[key] = varValue
            event.variables["typewriter_$key"] = varValue
        }
    }

    private fun getCurrentText(progress: Double): String {
        return text.asPartialMiniMessageString(progress, player)
    }

    private fun getCurrentTextLength(progress: Double): Int {
        return (rawText.length * progress).toInt().coerceIn(0, rawText.length)
    }

    override fun dispose() {
        super.dispose()

        entry.stopDialogueSound(player, context)

        hidePopup()

        confirmation?.dispose()
        confirmation = null
    }
}
