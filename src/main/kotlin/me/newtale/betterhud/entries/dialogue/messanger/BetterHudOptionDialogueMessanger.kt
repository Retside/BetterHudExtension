package me.newtale.betterhud.entries.dialogue.messanger

import kr.toxicity.hud.api.BetterHudAPI
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.core.utils.around
import com.typewritermc.core.utils.loopingDistance
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.dialogue.*
import com.typewritermc.engine.paper.entry.entries.EventTrigger
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.utils.*
import kr.toxicity.hud.api.bukkit.event.CustomPopupEvent
import kr.toxicity.hud.api.bukkit.update.BukkitEventUpdateEvent
import kr.toxicity.hud.api.player.HudPlayer
import me.newtale.betterhud.entries.dialogue.BetterHudOptionEntry
import me.newtale.betterhud.entries.dialogue.Option
import me.newtale.betterhud.entries.dialogue.OptionContextKeys
import me.newtale.betterhud.utils.reconstructMiniMessageText
import me.newtale.betterhud.utils.stripMiniMessage
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerItemHeldEvent
import java.time.Duration
import java.util.logging.Logger
import kotlin.math.min

class BetterHudOptionDialogueMessenger(
    player: Player,
    context: InteractionContext,
    entry: BetterHudOptionEntry
) : DialogueMessenger<BetterHudOptionEntry>(player, context, entry) {

    private var confirmationKeyHandler: ConfirmationKeyHandler? = null
    private var typeDuration = Duration.ZERO

    private var selectedIndex = 0
        set(value) {
            field = value
            // 1-based index
            this.context[entry, OptionContextKeys.SELECTED_OPTION] = value + 1
        }

    private val selected get() = usableOptions.getOrNull(selectedIndex)

    private var usableOptions: List<Option> = emptyList()
    private var speakerDisplayName = ""
    private var parsedText = ""
    private var rawText = ""
    private var playTime = Duration.ZERO
    private var totalDuration = Duration.ZERO
    private var completedAnimation = false

    private var hudPlayer: HudPlayer? = null
    private var popup: kr.toxicity.hud.api.popup.Popup? = null
    private var lastDisplayedState = ""
    private var popupId = ""

    private val logger = Logger.getLogger("BetterHudDialogue")
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    override val eventTriggers: List<EventTrigger>
        get() = entry.eventTriggers + (selected?.eventTriggers ?: emptyList())

    override val modifiers: List<Modifier>
        get() = entry.modifiers + (selected?.modifiers ?: emptyList())

    override var isCompleted: Boolean
        get() = playTime >= totalDuration
        set(value) {
            playTime = if (!value) Duration.ZERO else totalDuration
        }

    private fun createPressButtonText(): String {
        val sneakKey = confirmationKey
        return "Прокрутіть для вибору, <yellow>$sneakKey</yellow> для підтвердження"
    }

    override fun init() {
        super.init()

        try {
            usableOptions = entry.options.filter { it.criteria.matches(player, context) }

            val speaker = entry.speaker.get()
            speakerDisplayName = speaker?.displayName?.get(player)?.parsePlaceholders(player) ?: "Unknown"

            val originalText = entry.text.get(player).parsePlaceholders(player)
            parsedText = originalText
            rawText = stripMiniMessage(originalText)

            typeDuration = entry.duration.get(player)
            popupId = entry.popupId.get(player)

            val typingDuration = typingDurationType.totalDuration(rawText, typeDuration)
            val optionsShowingDuration = Duration.ofMillis(usableOptions.size * 100L)
            totalDuration = typingDuration + optionsShowingDuration

            val api = BetterHudAPI.inst()

            hudPlayer = api.playerManager.getHudPlayer(player.uniqueId)
                ?: throw IllegalStateException("HudPlayer not found")

            popup = api.popupManager.getPopup(popupId)
                ?: throw IllegalStateException("Popup '$popupId' not found")

            confirmationKeyHandler = confirmationKey.handler(player) { completeOrFinish() }

            entry.playDialogueSound(player, context)

        } catch (e: Exception) {
            logger.warning("BetterHud initialization error for ${player.name}: ${e.message}")
            player.sendMessage("§cDialog initialization error: ${e.message}")
            state = MessengerState.CANCELLED
        }
    }

    @EventHandler
    private fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        if (event.player.uniqueId != player.uniqueId) return
        if (state != MessengerState.RUNNING) return

        val curSlot = event.previousSlot
        val newSlot = event.newSlot
        val dif = loopingDistance(curSlot, newSlot, 8)
        val index = selectedIndex

        event.isCancelled = true

        var newIndex = (index + dif) % usableOptions.size
        while (newIndex < 0) newIndex += usableOptions.size

        selectedIndex = newIndex

        player.playSound(Sound.sound(Key.key("block.lever.click"), Sound.Source.MASTER, 1f, 2f))

        forceUpdatePopup()
    }

    override fun tick(context: TickContext) {
        if (state != MessengerState.RUNNING) return

        val isFirst = playTime == Duration.ZERO
        playTime += context.deltaTime

        var forceSend = false

        val newOptions = entry.options.filter { it.criteria.matches(player, this.context) }

        if (newOptions != usableOptions) {
            usableOptions = newOptions
            selectedIndex = 0
            forceSend = true
        }

        if (usableOptions.isEmpty()) {
            isCompleted = true
            state = MessengerState.FINISHED
            return
        }

        if (playTime.toTicks() % 100 > 0 && completedAnimation && !isFirst && !forceSend) {
            return
        }

        updatePopupWithCurrentState()
    }

    private fun forceUpdatePopup() {
        updatePopupWithCurrentState(true)
    }

    private fun updatePopupWithCurrentState(force: Boolean = false) {
        val currentState = generateCurrentState()

        if (force || currentState != lastDisplayedState) {
            updatePopup(currentState)
            lastDisplayedState = currentState
        }
    }

    private fun generateCurrentState(): String {
        return "${playTime.toMillis()}_${selectedIndex}_${usableOptions.size}_${completedAnimation}"
    }

    private fun updatePopup(stateInfo: String) {
        val hudPlayerRef = hudPlayer ?: return
        val popupRef = popup ?: return

        try {
            popupRef.hide(hudPlayerRef)

            val event = CustomPopupEvent(player, popupId).apply {
                variables.clear()
                addDialogueVariables(this)
            }

            val updateEvent = BukkitEventUpdateEvent(event, "dialogue_${System.currentTimeMillis()}")
            popupRef.show(updateEvent, hudPlayerRef)

        } catch (e: Exception) {
            logger.warning("Popup update error fo ${player.name}: ${e.message}")
        }
    }

    private fun addDialogueVariables(event: CustomPopupEvent) {
        val typePercentage = if (typeDuration.isZero) {
            1.0
        } else {
            typingDurationType.calculatePercentage(playTime, typeDuration, rawText)
        }

        val currentText = getCurrentText(typePercentage)
        val canFinish = eventTriggers.isEmpty()
        val isComplete = typePercentage >= 1.0

        val optionsInfo = getOptionsInfo()

        val previousOption = if (isComplete) getPreviousOption() else ""
        val nextOption = if (isComplete) getNextOption() else ""

        event.variables.apply {
            // Основні змінні
            put("speaker", speakerDisplayName)
            put("text", currentText)
            put("show", "true")
            put("progress", (typePercentage * 100).toInt().toString())
            put("percentage", String.format("%.2f", typePercentage))
            put("instruction", if (canFinish) "finish" else "continue")
            put("is_complete", isComplete.toString())

            put("pressbutton", if (isComplete && usableOptions.isNotEmpty()) createPressButtonText() else "")

            put("options_count", usableOptions.size.toString())
            put("selected_index", if (isComplete) selectedIndex.toString() else "-1")
            put("selected_option", if (isComplete) (selected?.text?.get(player)?.parsePlaceholders(player) ?: "") else "")
            put("has_options", usableOptions.isNotEmpty().toString())
            put("animation_complete", completedAnimation.toString())

            put("previous_option", previousOption)
            put("next_option", nextOption)
            put("has_previous", (getPreviousIndex() != -1).toString())
            put("has_next", (getNextIndex() != -1).toString())

            put("previous_index", getPreviousIndex().toString())
            put("next_index", getNextIndex().toString())

            optionsInfo.forEachIndexed { index, optionInfo ->
                put("option_${index}_text", optionInfo.text)
                put("option_${index}_selected", optionInfo.isSelected.toString())
                put("option_${index}_visible", optionInfo.isVisible.toString())
                put("option_${index}_prefix", optionInfo.prefix)
            }

            put("raw_text", rawText)
            put("total_chars", rawText.length.toString())
            put("visible_chars", getCurrentTextLength(typePercentage).toString())

            put("typewriter_speaker", speakerDisplayName)
            put("typewriter_text", currentText)
            put("typewriter_show", "true")
            put("typewriter_progress", (typePercentage * 100).toInt().toString())
            put("typewriter_instruction", if (canFinish) "finish" else "continue")
            put("typewriter_pressbutton", if (isComplete && usableOptions.isNotEmpty()) createPressButtonText() else "")
            put("typewriter_options_count", usableOptions.size.toString())
            put("typewriter_selected_index", if (isComplete) selectedIndex.toString() else "-1")
            put("typewriter_selected_option", if (isComplete) (selected?.text?.get(player)?.parsePlaceholders(player) ?: "") else "")

            put("typewriter_previous_option", previousOption)
            put("typewriter_next_option", nextOption)
            put("typewriter_has_previous", (isComplete && getPreviousIndex() != -1 && usableOptions.size > 1).toString())
            put("typewriter_has_next", (isComplete && getNextIndex() != -1 && usableOptions.size > 1).toString())
        }

        entry.customVariables.forEach { (key, value) ->
            val varValue = value.get(player).parsePlaceholders(player)
            event.variables[key] = varValue
            event.variables["typewriter_$key"] = varValue
        }
    }

    private fun getPreviousOption(): String {
        val prevIndex = getPreviousIndex()
        return if (prevIndex != -1 && prevIndex < usableOptions.size) {
            val optionText = usableOptions[prevIndex].text.get(player).parsePlaceholders(player)
            return optionText
        } else {
            "-----"
        }
    }

    private fun getNextOption(): String {
        val nextIndex = getNextIndex()
        return if (nextIndex != -1 && nextIndex < usableOptions.size) {
            val optionText = usableOptions[nextIndex].text.get(player).parsePlaceholders(player)
            return optionText
        } else {
            "-----"
        }
    }

    private fun getPreviousIndex(): Int {
        if (usableOptions.isEmpty()) return -1

        return if (usableOptions.size == 1) {
            -1 // Якщо тільки одна опція, немає попередньої
        } else if (selectedIndex > 0) {
            selectedIndex - 1
        } else {
            // Циклічно повертаємося до останньої опції
            usableOptions.size - 1
        }
    }

    private fun getNextIndex(): Int {
        if (usableOptions.isEmpty()) return -1

        return if (usableOptions.size == 1) {
            -1 // Якщо тільки одна опція, немає наступної
        } else if (selectedIndex < usableOptions.size - 1) {
            selectedIndex + 1
        } else {
            // Циклічно повертаємося до першої опції
            0
        }
    }

    private data class OptionInfo(
        val text: String,
        val isSelected: Boolean,
        val isVisible: Boolean,
        val prefix: String
    )

    private fun getOptionsInfo(): List<OptionInfo> {
        val typingDuration = typingDurationType.totalDuration(rawText, typeDuration)
        val timeAfterTyping = playTime - typingDuration
        val limitedOptions = (timeAfterTyping.toMillis() / 100).toInt().coerceAtLeast(0)

        val around = usableOptions.around(selectedIndex, 1, 2)
        val maxOptions = min(4, around.size)
        val showingOptions = min(maxOptions, limitedOptions)

        completedAnimation = maxOptions == showingOptions

        val optionsInfo = mutableListOf<OptionInfo>()

        for (i in 0 until maxOptions) {
            val option = around.getOrNull(i)
            val isVisible = i < showingOptions
            val isSelected = selected == option

            val prefix = when {
                isSelected -> ">>>"
                i == 0 && selectedIndex > 1 && usableOptions.size > 4 -> "↑"
                i == 3 && selectedIndex < usableOptions.size - 3 && usableOptions.size > 4 -> "↓"
                else -> ""
            }

            optionsInfo.add(
                OptionInfo(
                    text = option?.text?.get(player)?.parsePlaceholders(player) ?: "",
                    isSelected = isSelected,
                    isVisible = isVisible,
                    prefix = prefix
                )
            )
        }

        return optionsInfo
    }

    private fun getCurrentText(progress: Double): String {
        if (progress >= 1.0) return parsedText

        val visibleChars = (rawText.length * progress).toInt().coerceIn(0, rawText.length)
        return if (visibleChars > 0) {
            reconstructMiniMessageText(parsedText, visibleChars)
        } else ""
    }

    private fun getCurrentTextLength(progress: Double): Int {
        return (rawText.length * progress).toInt().coerceIn(0, rawText.length)
    }

    override fun dispose() {
        super.dispose()

        entry.stopDialogueSound(player, context)

        hudPlayer?.let { player ->
            popup?.hide(player)
        }

        confirmationKeyHandler?.dispose()
        confirmationKeyHandler = null
        hudPlayer = null
        popup = null
    }
}