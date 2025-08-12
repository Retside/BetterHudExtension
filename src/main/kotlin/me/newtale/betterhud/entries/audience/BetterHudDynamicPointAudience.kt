package me.newtale.betterhud.entries.audience

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.entry.entries.*
import com.typewritermc.engine.paper.utils.toBukkitWorld
import com.typewritermc.engine.paper.facts.FactListenerSubscription
import com.typewritermc.engine.paper.facts.listenForFacts
import com.typewritermc.engine.paper.plugin
import com.typewritermc.quest.QuestEntry
import com.typewritermc.quest.events.AsyncTrackedQuestUpdate
import com.typewritermc.quest.trackedQuest
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.adapter.LocationWrapper
import kr.toxicity.hud.api.adapter.WorldWrapper
import kr.toxicity.hud.api.player.PointedLocation
import kr.toxicity.hud.api.player.PointedLocationSource
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Entry("hud_compass_point_audience", "Show BetterHud compass point for players with specific quest.", Colors.CYAN, "material-symbols:touch-app-rounded")
/**
 * The `BetterHudCompassPointAudienceEntry` shows a compass point in BetterHUD for players with specific quest.
 *
 * ## How could this be used?
 * This could be used to show quest waypoints, objectives, or important locations to players who have specific quests.
 */
class BetterHudCompassPointAudience(
    override val id: String = "",
    override val name: String = "",
    @Help("Quest that the player should have")
    val quest: Ref<QuestEntry> = emptyRef(),
    @Help("Point name")
    val pointName: Var<String> = ConstVar(""),
    @Help("Icon name")
    val icon: Var<String> = ConstVar(""),
    @Help("Coordinates of the point")
    val pointLocation: Var<Position> = ConstVar(Position.ORIGIN),
    @Help("Criteria for displaying a compass point")
    val criteria: List<Criteria> = emptyList(),
) : AudienceEntry {

    override suspend fun display(): AudienceDisplay {
        return CompassPointDisplay(quest, pointName, icon, pointLocation, criteria)
    }
}

class CompassPointDisplay(
    private val quest: Ref<QuestEntry>,
    private val pointName: Var<String>,
    private val icon: Var<String>,
    private val pointLocation: Var<Position>,
    private val criteria: List<Criteria>,
) : AudienceFilter(quest) {

    private val activePoints = ConcurrentHashMap<UUID, String>()
    private val factWatcherSubscriptions = ConcurrentHashMap<UUID, FactListenerSubscription>()

    private fun isBetterHudAvailable(): Boolean {
        return try {
            val betterHudPlugin = Bukkit.getPluginManager().getPlugin("BetterHud")
            betterHudPlugin != null && betterHudPlugin.isEnabled
        } catch (e: Exception) {
            false
        }
    }

    override fun filter(player: Player): Boolean {
        return criteria.isEmpty() || criteria.matches(player)
    }

    override fun onPlayerAdd(player: Player) {

        if (criteria.isNotEmpty()) {
            factWatcherSubscriptions.compute(player.uniqueId) { _, subscription ->
                subscription?.cancel(player)
                return@compute player.listenForFacts(criteria.map { it.fact }, ::onFactChange)
            }
        }

        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                if (quest.isSet) {
                    if (player.trackedQuest() == quest) {
                        super.onPlayerAdd(player)
                    }
                } else {
                    if (player.trackedQuest() != null) {
                        super.onPlayerAdd(player)
                    }
                }
            },
            5L
        )
    }

    private fun onFactChange(player: Player, fact: Ref<ReadableFactEntry>) {
        player.refresh()
    }

    @EventHandler
    private fun onTrackedQuestUpdate(event: AsyncTrackedQuestUpdate) {
        val player = event.player
        player.refresh()

        if (quest.isSet) {
            if (event.from == quest && event.to != quest) {
                removeCompassPoint(player)
                player.updateFilter(false)
                return
            }

            if (event.to == quest) {
                val shouldShow = criteria.isEmpty() || criteria.matches(player)
                player.updateFilter(shouldShow)
                return
            }

            player.updateFilter(false)
        } else {
            val shouldShow = event.to != null
            player.updateFilter(shouldShow)
        }
    }

    override fun onPlayerRemove(player: Player) {
        super.onPlayerRemove(player)
        factWatcherSubscriptions.remove(player.uniqueId)?.cancel(player)
        removeCompassPoint(player)
    }

    override fun onPlayerFilterAdded(player: Player) {
        super.onPlayerFilterAdded(player)

        if (quest.isSet) {
            if (player.trackedQuest() != quest) {
                return
            }
        } else {
            if (player.trackedQuest() == null) {
                return
            }
        }

        addCompassPoint(player)
    }

    override fun onPlayerFilterRemoved(player: Player) {
        super.onPlayerFilterRemoved(player)
        removeCompassPoint(player)
    }

    private fun addCompassPoint(player: Player, attempts: Int = 0) {

        if (player.trackedQuest() != quest) {
            return
        }

        if (!isBetterHudAvailable()) {
            return
        }

        try {
            val hudPlayer = BetterHudAPI.inst().playerManager.getHudPlayer(player.uniqueId)
            if (hudPlayer == null) {

                if (attempts >= 10) {
                    return
                }

                Bukkit.getScheduler().runTaskLater(
                    plugin,
                    Runnable {
                        addCompassPoint(player, attempts + 1)
                    },
                    20L
                )
                return
            }

            val pos = pointLocation.get(player)

            val uniquePointName = pointName.get(player)

            if (activePoints.containsKey(player.uniqueId)) {
                return
            }

            val pointedLocation = PointedLocation(
                PointedLocationSource.INTERNAL,
                uniquePointName,
                icon.get(player),
                LocationWrapper(
                    WorldWrapper(pos.world.toBukkitWorld().name),
                    pos.x,
                    pos.y,
                    pos.z,
                    0F,
                    0F
                )
            )

            hudPlayer.pointers().add(pointedLocation)
            hudPlayer.update()

            activePoints[player.uniqueId] = uniquePointName

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeCompassPoint(player: Player, attempts: Int = 0) {
        val uniquePointName = activePoints.remove(player.uniqueId) ?: return

        if (!isBetterHudAvailable()) return

        try {
            val hudPlayer = BetterHudAPI.inst().playerManager.getHudPlayer(player.uniqueId)
            if (hudPlayer == null) {
                if (attempts >= 10) {
                    return
                }

                Bukkit.getScheduler().runTaskLater(
                    plugin,
                    Runnable {
                        removeCompassPoint(player, attempts + 1)
                    },
                    20L
                )
                return
            }

            hudPlayer.let {
                val removed = it.pointers().removeIf { pointer -> pointer.name == uniquePointName }
                if (removed) {
                    it.update()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun dispose() {
        super.dispose()

        factWatcherSubscriptions.forEach { (playerId, subscription) ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null) {
                subscription.cancel(player)
            }
        }
        factWatcherSubscriptions.clear()

        if (isBetterHudAvailable()) {
            activePoints.forEach { (playerId, uniquePointName) ->
                try {
                    val hudPlayer = BetterHudAPI.inst().playerManager.getHudPlayer(playerId)
                    hudPlayer?.let {
                        it.pointers().removeIf { pointer -> pointer.name == uniquePointName }
                        it.update()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        activePoints.clear()
    }
}