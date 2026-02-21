package me.newtale.betterhud.entries.actions

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.engine.paper.entry.entries.ActionTrigger
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.utils.toBukkitWorld
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.adapter.LocationWrapper
import kr.toxicity.hud.api.adapter.WorldWrapper
import kr.toxicity.hud.api.player.PointedLocation
import kr.toxicity.hud.api.player.PointedLocationSource

@Entry("hud_add_compass_point", "Add BetterHud compass point.", Colors.CYAN, "material-symbols:touch-app-rounded")
class BetterHudAddCompassPointActionEntry(
    override val id: String = "",
    override val name: String = "",
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
    override val criteria: List<Criteria> = emptyList(),
    override val modifiers: List<Modifier> = emptyList(),

    @Help("Point name")
    val pointName: String = "",

    @Help("Icon name")
    val icon: String = "",

    @Help("Coordinates of the point")
    val pointLocation: Var<Position> = ConstVar(Position.ORIGIN)

) : ActionEntry {

    override fun ActionTrigger.execute() {

        val pos = pointLocation.get(player)

        try {
            val hudPlayer = BetterHudAPI.inst().playerManager.getHudPlayer(player.uniqueId)
            if (hudPlayer == null) {
                player.sendMessage("§cBetterHud player data not found!")
                return
            }
            val loc = PointedLocation(
                PointedLocationSource.INTERNAL,
                pointName,
                icon,
                LocationWrapper(
                    WorldWrapper(pos.world.toBukkitWorld().name),
                    pos.x,
                    pos.y,
                    pos.z,
                    0F,
                    0F
                ))

            BetterHudAPI.inst().playerManager.getHudPlayer(player.uniqueId)?.pointers()?.add(loc)
            BetterHudAPI.inst().playerManager.getHudPlayer(player.uniqueId)?.update()


        } catch (e: Exception) {
            player.sendMessage("§cError adding BetterHud compass point: ${e.message}")
            e.printStackTrace()
        }


    }

}

