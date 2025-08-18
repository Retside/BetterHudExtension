package me.newtale.betterhud.entries.dialogue.messanger

import kr.toxicity.hud.api.BetterHudAPI
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.engine.paper.entry.dialogue.*
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import kr.toxicity.hud.api.bukkit.event.CustomPopupEvent
import kr.toxicity.hud.api.bukkit.update.BukkitEventUpdateEvent
import kr.toxicity.hud.api.player.HudPlayer
import me.newtale.betterhud.entries.dialogue.BetterHudSpokenEntry
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import java.time.Duration
import java.util.logging.Logger
import me.newtale.betterhud.utils.reconstructMiniMessageText
import me.newtale.betterhud.utils.stripMiniMessage

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
    private var hudPlayer: HudPlayer? = null
    private var popup: kr.toxicity.hud.api.popup.Popup? = null
    private var lastDisplayedText = ""
    private var popupId = ""

    private val logger = Logger.getLogger("BetterHudDialogue")
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    override var isCompleted: Boolean
        get() = playedTime >= typingDuration
        set(value) {
            playedTime = if (!value) Duration.ZERO else typingDuration
        }

    private fun createPressButtonText(): String {
        val sneakKey = confirmationKey
        return "Натисніть <yellow>$sneakKey</yellow>, щоб продовжити"
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
            popupId = entry.popupId.get(player)

            val api = BetterHudAPI.inst()

            hudPlayer = api.playerManager.getHudPlayer(player.uniqueId)
                ?: throw IllegalStateException("HudPlayer not found")

            popup = api.popupManager.getPopup(popupId)
                ?: throw IllegalStateException("Popup ‘$popupId’ not found")

            confirmationKeyHandler = confirmationKey.handler(player) {
                handleConfirmationKey()
            }

            entry.playDialogueSound(player, context)

        } catch (e: Exception) {
            logger.warning("BetterHud initialization error for ${player.name}: ${e.message}")
            player.sendMessage("§cDialog initialization error: ${e.message}")
            state = MessengerState.CANCELLED
        }
    }

    private fun handleConfirmationKey() {
        when {
            !isCompleted -> {
                isCompleted = true
                updatePopup(text, 1.0)
            }
            else -> {
                if (eventTriggers.isNotEmpty()) {
                    state = MessengerState.FINISHED
                } else {
                    completeOrFinish()
                }
            }
        }
    }

    override fun tick(context: TickContext) {
        if (state != MessengerState.RUNNING) return

        if (!isCompleted) {
            playedTime += context.deltaTime
        }

        val percentage = typingDurationType.calculatePercentage(playedTime, typingDuration, rawText)
        val currentText = getCurrentText(percentage)

        if (currentText != lastDisplayedText) {
            updatePopup(currentText, percentage)
            lastDisplayedText = currentText
        }
    }

    private fun updatePopup(currentText: String, percentage: Double) {
        val hudPlayerRef = hudPlayer ?: return
        val popupRef = popup ?: return

        try {
            popupRef.hide(hudPlayerRef)

            val event = CustomPopupEvent(player, popupId).apply {
                variables.clear()
                addDialogueVariables(this, currentText, percentage)
            }

            val updateEvent = BukkitEventUpdateEvent(event, "dialogue_${System.currentTimeMillis()}")
            popupRef.show(updateEvent, hudPlayerRef)

        } catch (e: Exception) {
            logger.warning("Помилка оновлення popup для ${player.name}: ${e.message}")
        }
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

            put("pressbutton", if (isComplete) createPressButtonText() else "")

            put("raw_text", rawText)
            put("total_chars", rawText.length.toString())
            put("visible_chars", getCurrentTextLength(percentage).toString())

            put("typewriter_speaker", speakerDisplayName)
            put("typewriter_text", currentText)
            put("typewriter_show", "true")
            put("typewriter_progress", (percentage * 100).toInt().toString())
            put("typewriter_instruction", if (canFinish) "finish" else "continue")
            put("typewriter_pressbutton", if (isComplete) createPressButtonText() else "")
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

        if (state == MessengerState.FINISHED) {
            entry.stopDialogueSound(player, context)
        }

        hudPlayer?.let { player ->
            popup?.hide(player)
        }

        confirmationKeyHandler?.dispose()
        confirmationKeyHandler = null
        hudPlayer = null
        popup = null
    }
}