plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.1.0"
}

group = "me.newtale"
version = "0.9.0-beta-171"

typewriter {
    namespace = "newtale"

    extension {
        name = "BetterHud"
        shortDescription = "Extension for pointers and popups"
        description = """
            |BetterHudExtension for Typewriter that adds support
            |for BetterHud's pointer and popup systems to Typewriter,
            |allowing the creation of quest markers and immersive
            |dialogue experiences.
            |
            |Created by Ney.
            |Discord: ney___
            """.trimMargin()
        engineVersion = "0.9.0-beta-171"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        dependencies {
            paper()
            dependency("typewritermc", "Quest")
            dependency("typewritermc", "Basic")
        }


    }
}

repositories {
    mavenCentral()
    //maven { url = uri("https://repo.codemc.io/repository/maven-releases/") }
    //maven { url = uri("https://repo.codemc.io/repository/maven-snapshots/") }
}

dependencies {
    compileOnly("io.github.toxicity188:BetterHud-standard-api:1.12.2")
    compileOnly("io.github.toxicity188:BetterHud-bukkit-api:1.12.2")
    compileOnly("io.github.toxicity188:BetterCommand:1.4.3")
    //compileOnly("com.github.retrooper:packetevents-spigot:2.8.0")
    implementation("com.typewritermc:QuestExtension:0.9.0")
    implementation("com.typewritermc:BasicExtension:0.9.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}