package me.newtale.betterhud.entries.cinematic

import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.utils.switchContext
import com.typewritermc.engine.paper.entry.dialogue.playSpeakerSound
import com.typewritermc.engine.paper.entry.entries.*
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.utils.GenericPlayerStateProvider.EXP
import com.typewritermc.engine.paper.utils.GenericPlayerStateProvider.LEVEL
import com.typewritermc.engine.paper.utils.PlayerState
import com.typewritermc.engine.paper.utils.Sync
import com.typewritermc.engine.paper.utils.restore
import com.typewritermc.engine.paper.utils.state
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.bukkit.event.CustomPopupEvent
import kr.toxicity.hud.api.bukkit.update.BukkitEventUpdateEvent
import kr.toxicity.hud.api.player.HudPlayer
import kotlinx.coroutines.Dispatchers
import me.newtale.betterhud.utils.reconstructMiniMessageText
import me.newtale.betterhud.utils.stripMiniMessage
import org.bukkit.entity.Player
import java.util.logging.Logger

data class BetterHudDialogueSegment(
    override val startFrame: Int = 0,
    override val endFrame: Int = 0,
    @Placeholder
    @Colored
    @MultiLine
    @Help("The text to display to the player.")
    val text: Var<String> = ConstVar(""),

    @Help("BetterHud popup ID for this segment (overrides global popupId if set)")
    val popupId: Var<String> = ConstVar(""),

    @Help("Custom variables for this segment's popup")
    val customVariables: Map<String, Var<String>> = emptyMap(),
) : Segment

class BetterHudDialogueCinematicAction(
    private val player: Player,
    private val speaker: SpeakerEntry?,
    private val segments: List<BetterHudDialogueSegment>,
    private val globalPopupId: Var<String>,
    private val globalCustomVariables: Map<String, Var<String>>,
    private val splitPercentage: Double = 0.5
) : CinematicAction {

    private var previousSegment: BetterHudDialogueSegment? = null
    private var state: PlayerState? = null
    private var displayText = ""

    private var currentHudPlayer: HudPlayer? = null
    private var currentPopup: kr.toxicity.hud.api.popup.Popup? = null
    private val logger = Logger.getLogger("BetterHudCinematic")

    private var speakerDisplayName = ""
    private var currentPopupId = ""

    override suspend fun setup() {
        super.setup()
        state = player.state(EXP, LEVEL)
        speakerDisplayName = speaker?.displayName?.get(player)?.parsePlaceholders(player) ?: ""
    }

    override suspend fun tick(frame: Int) {
        super.tick(frame)
        val segment = (segments activeSegmentAt frame)

        if (segment == null) {
            if (previousSegment != null) {
                player.exp = 0f
                player.level = 0
                hideCurrentPopup()
                displayText = ""
                previousSegment = null
                currentHudPlayer = null
                currentPopup = null
            }
            return
        }

        if (previousSegment != segment) {
            hideCurrentPopup()

            player.level = 0
            player.exp = 1f
            player.playSpeakerSound(speaker)
            previousSegment = segment
            displayText = segment.text.get(player).parsePlaceholders(player)

            setupSegmentPopup(segment)
        }

        val percentage = segment percentageAt frame
        player.level = 0
        player.exp = 1 - percentage.toFloat()

        val displayPercentage = percentage / splitPercentage

        if (displayPercentage > 1.1) {
            val needsDisplay = (frame - segment.startFrame) % 20 == 0
            if (!needsDisplay) return
        }

        displayBetterHudDialogue(
            player,
            speakerDisplayName,
            displayText,
            displayPercentage.coerceAtMost(1.0),
            segment
        )
    }

    private fun setupSegmentPopup(segment: BetterHudDialogueSegment) {
        try {
            val segmentPopupId = segment.popupId.get(player)
            currentPopupId = if (segmentPopupId.isNotBlank()) {
                segmentPopupId
            } else {
                globalPopupId.get(player)
            }

            val api = BetterHudAPI.inst()
            currentHudPlayer = api.playerManager.getHudPlayer(player.uniqueId)
                ?: throw IllegalStateException("HudPlayer not found")

            currentPopup = api.popupManager.getPopup(currentPopupId)
                ?: throw IllegalStateException("Popup '$currentPopupId' not found")

        } catch (e: Exception) {
            logger.warning("BetterHud popup setup error for segment in ${player.name}: ${e.message}")
            currentHudPlayer = null
            currentPopup = null
        }
    }

    private fun displayBetterHudDialogue(
        player: Player,
        speakerName: String,
        text: String,
        displayPercentage: Double,
        segment: BetterHudDialogueSegment
    ) {
        val hudPlayerRef = currentHudPlayer ?: return
        val popupRef = currentPopup ?: return

        try {
            popupRef.hide(hudPlayerRef)

            val currentText = getCurrentText(text, displayPercentage)
            val rawText = stripMiniMessage(text)

            val event = CustomPopupEvent(player, currentPopupId).apply {
                variables.clear()
                addDialogueVariables(this, text, currentText, rawText, displayPercentage, speakerName, segment)
            }

            val updateEvent = BukkitEventUpdateEvent(event, "cinematic_dialogue_${System.currentTimeMillis()}")
            popupRef.show(updateEvent, hudPlayerRef)

        } catch (e: Exception) {
            logger.warning("Popup update error for ${player.name}: ${e.message}")
        }
    }

    private fun addDialogueVariables(
        event: CustomPopupEvent,
        fullText: String,
        currentText: String,
        rawText: String,
        progress: Double,
        speakerName: String,
        segment: BetterHudDialogueSegment
    ) {
        val isComplete = progress >= 1.0

        event.variables.apply {
            put("speaker", speakerName)
            put("text", currentText)
            put("show", "true")
            put("progress", (progress * 100).toInt().toString())
            put("percentage", String.format("%.2f", progress))
            put("is_complete", isComplete.toString())

            put("raw_text", rawText)
            put("total_chars", rawText.length.toString())
            put("visible_chars", getCurrentTextLength(rawText, progress).toString())

            put("typewriter_speaker", speakerName)
            put("typewriter_text", currentText)
            put("typewriter_show", "true")
            put("typewriter_progress", (progress * 100).toInt().toString())
            put("typewriter_percentage", String.format("%.2f", progress))
            put("typewriter_is_complete", isComplete.toString())
        }

        val segmentVariables = segment.customVariables
        val combinedVariables = globalCustomVariables + segmentVariables

        combinedVariables.forEach { (key, value) ->
            val varValue = value.get(player).parsePlaceholders(player)
            event.variables[key] = varValue
            event.variables["typewriter_$key"] = varValue
        }
    }

    private fun getCurrentText(fullText: String, progress: Double): String {
        if (progress >= 1.0) return fullText

        val rawText = stripMiniMessage(fullText)
        val visibleChars = (rawText.length * progress).toInt().coerceIn(0, rawText.length)

        return if (visibleChars > 0) {
            reconstructMiniMessageText(fullText, visibleChars)
        } else ""
    }

    private fun getCurrentTextLength(rawText: String, progress: Double): Int {
        return (rawText.length * progress).toInt().coerceIn(0, rawText.length)
    }


    private fun hideCurrentPopup() {
        currentHudPlayer?.let { player ->
            currentPopup?.hide(player)
        }
    }

    override suspend fun teardown() {
        super.teardown()
        hideCurrentPopup()
        Dispatchers.Sync.switchContext {
            player.restore(state)
        }
    }

    override fun canFinish(frame: Int): Boolean = segments canFinishAt frame
}