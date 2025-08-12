package me.newtale.betterhud.entries.actions

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.engine.paper.entry.entries.ActionTrigger
import kr.toxicity.hud.api.BetterHudAPI

@Entry("hud_remove_compass_point", "Remove BetterHud compass point.", Colors.CYAN, "material-symbols:touch-app-rounded")
class BetterHudRemoveCompassPointActionEntry(
    override val id: String = "",
    override val name: String = "",
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
    override val criteria: List<Criteria> = emptyList(),
    override val modifiers: List<Modifier> = emptyList(),
    @Help("Point name")
    val pointName: String = ""

) : ActionEntry {

    override fun ActionTrigger.execute() {

        try {
            val hudPlayer = BetterHudAPI.inst().playerManager.getHudPlayer(player.uniqueId)
            if (hudPlayer == null) {
                player.sendMessage("§cBetterHud player data not found!")
                return
            }

            hudPlayer.pointers().removeIf { pointName == it.name }
            BetterHudAPI.inst().playerManager.getHudPlayer(player.uniqueId)?.update()


        } catch (e: Exception) {
            player.sendMessage("§cError removing BetterHud compass point: ${e.message}")
            e.printStackTrace()
        }


    }

}

