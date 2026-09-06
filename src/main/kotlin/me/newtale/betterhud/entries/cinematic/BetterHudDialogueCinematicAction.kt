package me.newtale.betterhud.entries.cinematic

import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.utils.switchContext
import com.typewritermc.engine.paper.entry.dialogue.playSpeakerSound
import com.typewritermc.engine.paper.entry.dialogue.typingDurationType
import com.typewritermc.engine.paper.entry.entries.*
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.utils.*
import com.typewritermc.engine.paper.utils.GenericPlayerStateProvider.EXP
import com.typewritermc.engine.paper.utils.GenericPlayerStateProvider.LEVEL
import kotlinx.coroutines.Dispatchers
import kr.toxicity.hud.api.bukkit.event.CustomPopupEvent
import me.newtale.betterhud.utils.DelayedText
import me.newtale.betterhud.utils.BetterHudPopup
import me.newtale.betterhud.utils.asPartialMiniMessageString
import me.newtale.betterhud.utils.parseDelays
import net.kyori.adventure.sound.SoundStop
import org.bukkit.entity.Player
import java.time.Duration
import java.util.logging.Logger
import kotlin.math.abs

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

    @Help("Sound to play when this segment starts and stop when it ends")
    val sound: Var<Sound> = ConstVar(Sound.EMPTY),

    @Help("Play sound when typing text in this segment")
    val typingSound: Var<Boolean> = ConstVar(false),
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
    private var currentSegment: BetterHudDialogueSegment? = null
    private var state: PlayerState? = null
    private var displayText = ""

    private var currentPopup: BetterHudPopup? = null

    private val logger = Logger.getLogger("BetterHudCinematic")

    private var speakerDisplayName = ""
    private var lastDisplayedText = ""
    private var lastDisplayedPercentage = -1.0
    private var lastVisibleChars = 0
    private var currentDelayedText: DelayedText? = null

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
                stopDialogueSound()
                displayText = ""
                previousSegment = null
                currentSegment = null
                resetPopupState()
                lastVisibleChars = 0
            }
            return
        }

        if (previousSegment != segment) {
            hideCurrentPopup()
            stopDialogueSound()
            resetPopupState()

            player.level = 0
            player.exp = 1f
            player.playSpeakerSound(speaker)

            currentSegment = segment
            playDialogueSound(segment)

            previousSegment = segment
            val originalText = segment.text.get(player).parsePlaceholders(player)
            val parsed = parseDelays(originalText)
            currentDelayedText = parsed

            displayText = parsed.textWithoutDelays

            setupSegmentPopup(segment)

            lastDisplayedText = ""
            lastDisplayedPercentage = -1.0
            lastVisibleChars = 0
        }

        val percentage = segment percentageAt frame
        player.level = 0
        player.exp = 1 - percentage.toFloat()

        val displayPercentage = percentage / splitPercentage

        val finalPercentage =
            if (displayPercentage >= 1.0) {
                1.0
            } else {
                val totalTicks = (segment.endFrame - segment.startFrame).toDouble() * splitPercentage
                val currentTicks = (frame - segment.startFrame).toDouble()
                val playedDuration = Duration.ofMillis((currentTicks * 50).toLong())
                val baseDuration = Duration.ofMillis((totalTicks * 50).toLong())

                currentDelayedText?.calculatePercentage(typingDurationType, playedDuration, baseDuration)
                    ?: displayPercentage.coerceAtMost(1.0)
            }

        val currentText = getCurrentText(displayText, finalPercentage)
        val rawText = displayText.stripped()

        val shouldPlayTypingSound = segment.typingSound.get(player)
        if (shouldPlayTypingSound) {
            val currentVisibleChars = getCurrentTextLength(rawText, finalPercentage)

            if (currentVisibleChars > lastVisibleChars) {
                val newChar = rawText.getOrNull(lastVisibleChars)

                if (newChar != null && !newChar.isWhitespace()) {
                    playDialogueSound(segment)
                }
            }

            lastVisibleChars = currentVisibleChars
        }

        if (currentText != lastDisplayedText ||
            abs(finalPercentage - lastDisplayedPercentage) > 0.01
        ) {
            displayBetterHudDialogue(
                player,
                speakerDisplayName,
                displayText,
                finalPercentage,
                segment
            )
            lastDisplayedText = currentText
            lastDisplayedPercentage = finalPercentage
        }
    }

    private fun playDialogueSound(segment: BetterHudDialogueSegment) {
        val dialogueSound = segment.sound.get(player)
        if (dialogueSound != Sound.EMPTY) {
            player.playSound(dialogueSound, null)
        }
    }

    private fun stopDialogueSound() {
        val segment = currentSegment ?: return
        val dialogueSound = segment.sound.get(player)
        if (dialogueSound != Sound.EMPTY) {
            val soundId = dialogueSound.soundId
            val soundStop = soundId.namespacedKey?.let { SoundStop.named(it) } ?: return
            player.stopSound(soundStop)
        }
    }

    private fun setupSegmentPopup(segment: BetterHudDialogueSegment) {
        try {
            val segmentPopupId = segment.popupId.get(player)
            val popupId = segmentPopupId.ifBlank { globalPopupId.get(player) }
            currentPopup = BetterHudPopup(player, popupId).also {
                if (!it.resolve()) {
                    throw IllegalStateException("Could not resolve BetterHud popup '$popupId'")
                }
            }
        } catch (e: Exception) {
            logger.warning(
                "BetterHud popup setup error for segment in ${player.name}: ${e.message}"
            )
            resetPopupState()
        }
    }

    private fun resetPopupState() {
        currentPopup?.hide()
        currentPopup = null
    }

    private fun displayBetterHudDialogue(
        player: Player,
        speakerName: String,
        text: String,
        displayPercentage: Double,
        segment: BetterHudDialogueSegment
    ) {
        val popup = currentPopup ?: return

        try {
            popup.showOrUpdate { event ->
                addDialogueVariables(event, text, displayPercentage, speakerName, segment)
            }
        } catch (e: Exception) {
            logger.warning("Popup display error for ${player.name}: ${e.message}")
        }
    }

    private fun addDialogueVariables(
        event: CustomPopupEvent,
        fullText: String,
        progress: Double,
        speakerName: String,
        segment: BetterHudDialogueSegment
    ) {
        val currentText = getCurrentText(fullText, progress)
        val rawText = fullText.stripped()
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
        return fullText.asPartialMiniMessageString(progress, player)
    }

    private fun getCurrentTextLength(rawText: String, progress: Double): Int {
        return (rawText.length * progress).toInt().coerceIn(0, rawText.length)
    }

    private fun hideCurrentPopup() {
        currentPopup?.hide()
        currentPopup = null
    }

    override suspend fun teardown() {
        super.teardown()
        hideCurrentPopup()
        stopDialogueSound()
        resetPopupState()
        Dispatchers.Sync.switchContext { player.restore(state) }
    }

    override fun canFinish(frame: Int): Boolean = segments canFinishAt frame
}
