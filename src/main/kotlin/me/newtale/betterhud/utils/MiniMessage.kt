package me.newtale.betterhud.utils

import com.typewritermc.engine.paper.utils.asPartialFormattedMini
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage

private val miniMessage = MiniMessage.miniMessage()

fun String.asPartialMiniMessageString(
    percentage: Double,
    audience: Audience = Audience.empty(),
): String = miniMessage.serialize(this.asPartialFormattedMini(percentage, audience = audience))
