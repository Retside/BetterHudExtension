package me.newtale.betterhud.utils

import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

fun reconstructMiniMessageText(originalText: String, visibleChars: Int): String {
    if (visibleChars <= 0) return ""

    var currentChars = 0
    var result = StringBuilder()
    var tagStack = mutableListOf<String>()
    var i = 0

    while (i < originalText.length && currentChars < visibleChars) {
        when {
            originalText[i] == '<' -> {
                val tagEnd = originalText.indexOf('>', i)
                if (tagEnd != -1) {
                    val tagContent = originalText.substring(i + 1, tagEnd)
                    val tag = "<$tagContent>"

                    result.append(tag)

                    if (tagContent.startsWith("/")) {
                        if (tagStack.isNotEmpty()) {
                            tagStack.removeLastOrNull()
                        }
                    } else if (!tagContent.contains(":") || !isInstantTag(tagContent)) {
                        tagStack.add(tagContent)
                    }

                    i = tagEnd + 1
                    continue
                }
            }
        }

        result.append(originalText[i])
        currentChars++

        i++
    }

    for (tag in tagStack.reversed()) {
        if (!isInstantTag(tag)) {
            val tagName = tag.split(":")[0]
            result.append("</$tagName>")
        }
    }

    return result.toString()
}

fun isInstantTag(tagContent: String): Boolean {
    val instantTags = setOf("click", "hover", "insertion", "font", "lang", "translatable")
    return instantTags.any { tagContent.startsWith("$it:") }
}

fun stripMiniMessage(text: String): String {
    return try {
        val component = miniMessage().deserialize(text)
        LegacyComponentSerializer.legacySection().serialize(component).replace("§[0-9a-fk-or]".toRegex(), "")
    } catch (e: Exception) {
        text.replace("<[^>]*>".toRegex(), "")
    }
}