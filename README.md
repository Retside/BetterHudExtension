# BetterHud Extension

A Typewriter extension that integrates BetterHud's popup and pointer systems, enabling HUD-rendered dialogues, quest markers, and immersive player interactions.

## Features

- **Spoken Dialogue** - NPC dialogue rendered through BetterHud popups with typewriter animation and typing sounds
- **Option Dialogue** - Player choice dialogues with scroll-wheel navigation, per-option criteria and triggers
- **Cinematic Dialogue** - Timeline-based dialogue sequences tied to Typewriter's cinematic system
- **Compass Points** - Add and remove BetterHud quest markers via actions or automatically based on tracked quest
- **Quest Compass Audience** - Automatically shows a compass point to players who are tracking a specific quest
- **Custom Variables** - Pass any dynamic data to your popups with full PlaceholderAPI support

## Requirements

- [Paper](https://papermc.io/)
- [Typewriter](https://github.com/typewritermc/typewriter)
- [BetterHud](https://github.com/toxicity188/BetterHud)
- Java 21

## Documentation

Full setup guides, variable reference, and examples:
https://ney.gitbook.io/docs/betterhud-extension

Purchase an official configuration:
https://builtbybit.com/resources/betterhud-dialogues.76503/

## Building from Source

```bash
git clone https://github.com/Retside/BetterHudExtension.git
cd BetterHudExtension
./gradlew build
```
