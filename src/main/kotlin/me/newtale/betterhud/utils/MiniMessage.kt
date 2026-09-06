package me.newtale.betterhud.utils

import com.typewritermc.engine.paper.utils.asPartialFormattedMini
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

private val miniMessage = MiniMessage.miniMessage()

fun String.asPartialMiniMessageString(
    percentage: Double,
    audience: Audience = Audience.empty(),
): String = miniMessage.serialize(this.asPartialFormattedMini(percentage, audience = audience))

fun stripMiniMessage(text: String): String {
    val cleanText = text.replace("<d:[^>]*>".toRegex(), "")
    return try {
        val component = miniMessage().deserialize(cleanText)
        LegacyComponentSerializer.legacySection()
            .serialize(component)
            .replace("§[0-9a-fk-or]".toRegex(), "")
    } catch (_: Exception) {
        cleanText.replace("<[^>]*>".toRegex(), "")
    }
}
