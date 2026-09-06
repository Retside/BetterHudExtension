package me.newtale.betterhud.utils

import com.typewritermc.engine.paper.logger
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.bukkit.event.CustomPopupEvent
import kr.toxicity.hud.api.bukkit.update.BukkitEventUpdateEvent
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.popup.Popup
import kr.toxicity.hud.api.popup.PopupUpdater
import org.bukkit.entity.Player

private const val EVENT_NAME_PREFIX = "dialogue"

class BetterHudPopup(
    private val player: Player,
    private val popupId: String,
) {
    private var hudPlayer: HudPlayer? = null
    private var popup: Popup? = null
    private var popupUpdater: PopupUpdater? = null
    private var event: CustomPopupEvent? = null

    val isShown: Boolean get() = popupUpdater != null

    fun resolve(): Boolean {
        val api = BetterHudAPI.inst()
        hudPlayer = api.playerManager.getHudPlayer(player.uniqueId)
        popup = api.popupManager.getPopup(popupId)

        if (hudPlayer == null || popup == null) {
            logger.warning("Could not resolve BetterHud popup '$popupId' for ${player.name}")
            return false
        }
        return true
    }

    fun show(applyVariables: (CustomPopupEvent) -> Unit) {
        val hudPlayerRef = hudPlayer ?: return
        val popupRef = popup ?: return
        if (isShown) return

        val newEvent = CustomPopupEvent(player, popupId)
        event = newEvent
        applyVariables(newEvent)

        val update = BukkitEventUpdateEvent(newEvent, "${EVENT_NAME_PREFIX}_${player.uniqueId}")
        popupUpdater = popupRef.show(update, hudPlayerRef)

        if (popupUpdater == null) {
            logger.warning("Could not show BetterHud popup '$popupId' for ${player.name}")
        }
    }

    fun update(applyVariables: (CustomPopupEvent) -> Unit) {
        val updater = popupUpdater ?: return
        val currentEvent = event ?: return

        applyVariables(currentEvent)
        if (!updater.update()) {
            logger.info("Could not update BetterHud popup '$popupId' for ${player.name}")
        }
    }

    fun showOrUpdate(applyVariables: (CustomPopupEvent) -> Unit) {
        if (isShown) update(applyVariables) else show(applyVariables)
    }

    fun hide() {
        hudPlayer?.let { popup?.hide(it) }
        popupUpdater = null
        event = null
        hudPlayer = null
        popup = null
    }
}
