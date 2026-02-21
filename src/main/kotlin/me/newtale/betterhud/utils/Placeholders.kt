package me.newtale.betterhud.utils

import java.time.Duration

data class TextDelay(val position: Int, val duration: Duration)

data class DelayedText(
        val originalText: String,
        val textWithoutDelays: String,
        val rawTextWithoutDelays: String,
        val delays: List<TextDelay>
) {

    fun getTotalDuration(baseDuration: Duration): Duration {
        var total = baseDuration
        for (delay in delays) {
            total = total.plus(delay.duration)
        }
        return total
    }

    fun calculatePercentage(playedTime: Duration, baseDuration: Duration): Double {
        val totalDuration = getTotalDuration(baseDuration)
        if (totalDuration.isZero) return 1.0

        val rawLength = rawTextWithoutDelays.length
        if (rawLength == 0) return 1.0

        val msPerChar = baseDuration.toMillis().toDouble() / rawLength

        var currentTime = Duration.ZERO
        var visibleChars = 0

        while (visibleChars < rawLength) {
            val delayAtPos = delays.filter { it.position == visibleChars }
            val posDelaySum = delayAtPos.fold(Duration.ZERO) { acc, d -> acc.plus(d.duration) }

            if (currentTime.plus(posDelaySum) > playedTime) {
                return visibleChars.toDouble() / rawLength
            }

            currentTime = currentTime.plus(posDelaySum)

            val charTime = Duration.ofNanos((msPerChar * 1_000_000).toLong())
            if (currentTime.plus(charTime) > playedTime) {
                val remainingTime = playedTime.minus(currentTime)
                val charProgress = remainingTime.toNanos().toDouble() / charTime.toNanos()
                return (visibleChars + charProgress) / rawLength
            }

            currentTime = currentTime.plus(charTime)
            visibleChars++
        }

        return 1.0
    }
}

fun parseDelays(text: String): DelayedText {
    val delays = mutableListOf<TextDelay>()
    val sb = StringBuilder()
    var currentRawPos = 0
    var i = 0

    while (i < text.length) {
        if (text[i] == '<') {
            val tagEnd = text.indexOf('>', i)
            if (tagEnd != -1) {
                val tagContent = text.substring(i + 1, tagEnd)
                if (tagContent.startsWith("d:")) {
                    val delayValue = tagContent.substring(2)
                    val duration = parseDuration(delayValue)
                    delays.add(TextDelay(currentRawPos, duration))
                    i = tagEnd + 1
                    continue
                } else {
                    val tag = text.substring(i, tagEnd + 1)
                    sb.append(tag)
                    i = tagEnd + 1
                    continue
                }
            }
        }

        sb.append(text[i])
        currentRawPos++
        i++
    }

    val textWithoutDelays = sb.toString()
    val rawTextWithoutDelays = stripMiniMessage(textWithoutDelays)

    val finalDelays = mutableListOf<TextDelay>()
    val delayMap = delays.groupBy { it.position }

    var currentTextPos = 0
    var currentRawTextPos = 0

    while (currentTextPos <= textWithoutDelays.length) {
        delayMap[currentTextPos]?.forEach {
            finalDelays.add(TextDelay(currentRawTextPos, it.duration))
        }

        if (currentTextPos < textWithoutDelays.length) {
            if (textWithoutDelays[currentTextPos] == '<') {
                val tagEnd = textWithoutDelays.indexOf('>', currentTextPos)
                if (tagEnd != -1) {
                    currentTextPos = tagEnd + 1
                    continue
                }
            }
            currentRawTextPos++
            currentTextPos++
        } else {
            break
        }
    }

    return DelayedText(text, textWithoutDelays, rawTextWithoutDelays, finalDelays)
}

private fun parseDuration(value: String): Duration {
    return try {
        if (value.endsWith("s")) {
            val seconds = value.substring(0, value.length - 1).toDouble()
            Duration.ofMillis((seconds * 1000).toLong())
        } else {
            Duration.ofMillis(value.toLong())
        }
    } catch (e: Exception) {
        Duration.ZERO
    }
}
