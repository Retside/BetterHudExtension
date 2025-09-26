package me.newtale.betterhud.entries.dialogue

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.dialogue.DialogueMessenger
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.DialogueEntry
import com.typewritermc.engine.paper.entry.entries.SpeakerEntry
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.utils.Sound
import com.typewritermc.engine.paper.utils.playSound
import me.newtale.betterhud.entries.dialogue.messenger.BetterHudSpokenDialogueMessenger
import net.kyori.adventure.sound.SoundStop
import org.bukkit.entity.Player
import java.time.Duration

@Entry("betterhud_spoken", "Spoken dialogue with BetterHud popup", Colors.CYAN, "mdi:message-text")
class BetterHudSpokenEntry(
    override val id: String = "",
    override val name: String = "",
    override val criteria: List<Criteria> = emptyList(),
    override val modifiers: List<Modifier> = emptyList(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
    override val speaker: Ref<SpeakerEntry> = emptyRef(),

    @Help("The text to display")
    @MultiLine
    @Placeholder
    val text: Var<String> = ConstVar(""),

    @Help("Duration for typing animation")
    val duration: Var<Duration> = ConstVar(Duration.ofSeconds(3)),

    @Help("BetterHud popup ID to use")
    val popupId: Var<String> = ConstVar("typewriter_dialogue"),

    @Help("Sound to play when opening the popup and stop when finish")
    val sound: Var<Sound> = ConstVar(Sound.EMPTY),

    @Help("Play selected sound when typing text")
    val typingSound: Var<Boolean> = ConstVar(false),

    @Help("Custom variables for popup")
    val customVariables: Map<String, Var<String>> = emptyMap(),


    ) : DialogueEntry {

    override fun messenger(player: Player, context: InteractionContext): DialogueMessenger<BetterHudSpokenEntry> {
        return BetterHudSpokenDialogueMessenger(player, context, this)
    }
    fun playDialogueSound(player: Player, context: InteractionContext) {
        player.playSound(sound.get(player, context), context)
    }

    fun stopDialogueSound(player: Player, context: InteractionContext) {
        val dialogueSound = sound.get(player, context)
        if (dialogueSound != Sound.EMPTY) {
            val soundId = dialogueSound.soundId
            val soundStop = soundId.namespacedKey?.let { SoundStop.named(it) } ?: return
            player.stopSound(soundStop)
        }
    }
}