package me.newtale.betterhud.entries.dialogue.messenger

import kr.toxicity.hud.api.BetterHudAPI
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.engine.paper.entry.dialogue.*
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.snippets.snippet
import kr.toxicity.hud.api.bukkit.event.CustomPopupEvent
import kr.toxicity.hud.api.bukkit.update.BukkitEventUpdateEvent
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.popup.PopupUpdater
import me.newtale.betterhud.entries.dialogue.BetterHudSpokenEntry
import org.bukkit.entity.Player
import java.time.Duration
import java.util.logging.Logger
import me.newtale.betterhud.utils.reconstructMiniMessageText
import me.newtale.betterhud.utils.stripMiniMessage
import kotlin.math.abs

val spoken_popup: String by snippet("betterhud.spoken.popup", "")

class BetterHudSpokenDialogueMessenger(
    player: Player,
    context: InteractionContext,
    entry: BetterHudSpokenEntry
) : DialogueMessenger<BetterHudSpokenEntry>(player, context, entry) {

    private var confirmationKeyHandler: ConfirmationKeyHandler? = null
    private var speakerDisplayName = ""
    private var text = ""
    private var rawText = ""
    private var typingDuration = Duration.ZERO
    private var playedTime = Duration.ZERO

    private var typingSound = false
    private var interactionContext = context;

    private var hudPlayer: HudPlayer? = null
    private var popup: kr.toxicity.hud.api.popup.Popup? = null
    private var popupUpdater: PopupUpdater? = null
    private var lastDisplayedText = ""
    private var popupId = ""
    private var isPopupShown = false

    private var lastUpdateEvent: CustomPopupEvent? = null
    private var lastPercentage = -1.0

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
            speakerDisplayName = speaker?.displayName?.get(player)?.parsePlaceholders(player) ?: "Unknown"

            val originalText = entry.text.get(player).parsePlaceholders(player)
            rawText = stripMiniMessage(originalText)
            text = originalText

            typingDuration = entry.duration.get(player)
            typingSound = entry.typingSound.get(player)
            popupId = entry.popupId.get(player).ifBlank {
                spoken_popup
            }


            val api = BetterHudAPI.inst()

            hudPlayer = api.playerManager.getHudPlayer(player.uniqueId)
                ?: throw IllegalStateException("HudPlayer not found")

            popup = api.popupManager.getPopup(popupId)
                ?: throw IllegalStateException("Popup '$popupId' not found")

            confirmationKeyHandler = confirmationKey.handler(player) {
                completeOrFinish()
            }

            entry.playDialogueSound(player, context)

            showPopupInitially()

        } catch (e: Exception) {
            logger.warning("BetterHud initialization error for ${player.name}: ${e.message}")
            player.sendMessage("§cDialog initialization error: ${e.message}")
            state = MessengerState.CANCELLED
        }
    }

    private fun showPopupInitially() {
        val hudPlayerRef = hudPlayer ?: return
        val popupRef = popup ?: return

        try {
            val percentage = typingDurationType.calculatePercentage(playedTime, typingDuration, rawText)
            val currentText = getCurrentText(percentage)

            val event = createCustomPopupEvent(currentText, percentage)
            val updateEvent = BukkitEventUpdateEvent(event, "dialogue_${System.currentTimeMillis()}")

            popupUpdater = popupRef.show(updateEvent, hudPlayerRef)
            isPopupShown = popupUpdater != null
            lastUpdateEvent = event
            lastDisplayedText = currentText
            lastPercentage = percentage

            if (!isPopupShown) {
                logger.warning("Failed to show popup for ${player.name}")
            }

        } catch (e: Exception) {
            logger.warning("Failed to show popup initially for ${player.name}: ${e.message}")
            isPopupShown = false
        }
    }

    override fun tick(context: TickContext) {
        if (state != MessengerState.RUNNING) return
        if (!isPopupShown) return

        if (!animationComplete) {
            playedTime += context.deltaTime
        }

        val percentage = typingDurationType.calculatePercentage(playedTime, typingDuration, rawText)
        val currentText = getCurrentText(percentage)

        if (typingSound && playedTime < typingDuration) {
            val previousLength = stripMiniMessage(lastDisplayedText).length
            val currentLength = getCurrentTextLength(percentage)

            if (currentLength > previousLength) {
                entry.playDialogueSound(player, interactionContext)
            }
        }

        if (currentText != lastDisplayedText || abs(percentage - lastPercentage) > 0.01) {
            updatePopup(currentText, percentage)
            lastDisplayedText = currentText
            lastPercentage = percentage
        }
    }

    private fun updatePopup(currentText: String, percentage: Double) {
        val updater = popupUpdater ?: return

        try {
            val event = lastUpdateEvent
            if (event != null) {
                event.variables.clear()
                addDialogueVariables(event, currentText, percentage)
            }

            val success = updater.update()

            if (!success) {
                logger.info("Popup update failed for ${player.name}")
            }

        } catch (e: Exception) {
            logger.warning("Popup update error for ${player.name}: ${e.message}")
        }
    }

    private fun hidePopup() {
        hudPlayer?.let { player ->
            popup?.hide(player)
        }
        popupUpdater = null
        isPopupShown = false
        lastUpdateEvent = null
    }

    private fun createCustomPopupEvent(currentText: String, percentage: Double): CustomPopupEvent {
        val event = CustomPopupEvent(player, popupId)
        event.variables.clear()
        addDialogueVariables(event, currentText, percentage)
        return event
    }

    private fun addDialogueVariables(event: CustomPopupEvent, currentText: String, percentage: Double) {
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

            put("raw_text", rawText)
            put("total_chars", rawText.length.toString())
            put("visible_chars", getCurrentTextLength(percentage).toString())

            put("typewriter_speaker", speakerDisplayName)
            put("typewriter_text", currentText)
            put("typewriter_show", "true")
            put("typewriter_progress", (percentage * 100).toInt().toString())
            put("typewriter_instruction", if (canFinish) "finish" else "continue")
        }

        entry.customVariables.forEach { (key, value) ->
            val varValue = value.get(player).parsePlaceholders(player)
            event.variables[key] = varValue
            event.variables["typewriter_$key"] = varValue
        }
    }

    private fun getCurrentText(progress: Double): String {
        if (progress >= 1.0) return text

        val visibleChars = (rawText.length * progress).toInt().coerceIn(0, rawText.length)
        return if (visibleChars > 0) {
            reconstructMiniMessageText(text, visibleChars)
        } else ""
    }

    private fun getCurrentTextLength(progress: Double): Int {
        return (rawText.length * progress).toInt().coerceIn(0, rawText.length)
    }

    override fun dispose() {
        super.dispose()

        entry.stopDialogueSound(player, context)

        hidePopup()

        confirmationKeyHandler?.dispose()
        confirmationKeyHandler = null
        hudPlayer = null
        popup = null
    }
}