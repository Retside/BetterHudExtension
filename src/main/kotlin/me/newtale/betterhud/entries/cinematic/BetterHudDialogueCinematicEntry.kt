package me.newtale.betterhud.entries.cinematic

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Segments
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.entries.*
import com.typewritermc.engine.paper.snippets.snippet
import org.bukkit.entity.Player

@Entry("betterhud_dialogue_cinematic", "Play a BetterHud dialogue cinematic", Colors.CYAN, "mdi:message-text")
class BetterHudDialogueCinematicEntry(
    override val id: String = "",
    override val name: String = "",
    override val criteria: List<Criteria> = emptyList(),
    val speaker: Ref<SpeakerEntry> = emptyRef(),

    @Help("BetterHud popup ID to use for displaying dialogue")
    val popupId: Var<String> = ConstVar("typewriter_dialogue"),

    @Help("Custom variables to pass to the popup")
    val customVariables: Map<String, Var<String>> = emptyMap(),

    @Segments(icon = "mdi:message-text")
    val segments: List<BetterHudDialogueSegment> = emptyList(),
) : CinematicEntry {
    override fun create(player: Player): CinematicAction {
        return BetterHudDialogueCinematicAction(
            player,
            speaker.get(),
            segments,
            popupId,
            customVariables,
            betterHudPercentage
        )
    }
}

val betterHudPercentage: Double by snippet("cinematic.betterhud_dialogue.percentage", 0.5)