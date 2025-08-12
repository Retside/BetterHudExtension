package me.newtale.betterhud

import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.logger

@Singleton
object BetterHudExtension : Initializable {
    override suspend fun initialize() {
        logger.info("BetterHudExtension enabled | Developed by Ney #ney___")
    }

    override suspend fun shutdown() {
        logger.info("BetterHudExtension disabled!")
    }
}