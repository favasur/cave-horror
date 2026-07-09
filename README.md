# Cave Horror: White Eyes — Hytale Port

A port of the Minecraft NeoForge mod "Cave Noise Nightmare" to Hytale.

## Concept

Something stalks you in the deep caves. A tall, thin creature with glowing white eyes follows you from the darkness — invisible, silent, patient. When you look directly at it, it freezes. When you look away, it moves closer.

## Features

- **Stalking AI**: Entity follows players 20-30 blocks behind, phasing through walls
- **Weeping Angel Mechanic**: Entity freezes when looked at, moves when unobserved
- **White Eyes**: Glowing eyes visible in complete darkness after cave sounds play
- **Ambient Horror**: Creepy enderman sounds intensify as the entity approaches
- **Torch Extinguishing**: Torches snuff out when the entity is near
- **Block Stealing**: Doors, glass panes, and fence gates are stolen to weaken bases
- **Structure Building**: The entity builds sand pillars, pyramids, and mossy dungeons
- **Record 13**: Music disc plays when you stare at the eyes too long
- **Wall Emergence**: In corridors, the entity emerges from walls with mold effects

## Technical Overview

Built as a Hytale server plugin using Java 25+ and Gradle.

### Project Structure

```
cave-horror-white-eyes/
├── build.gradle.kts          # Gradle build with ShadowJar
├── settings.gradle.kts       # Project settings
├── gradle.properties         # Build properties
└── src/
    └── main/
        ├── java/com/favasur/cavehorror/
        │   ├── CaveNoisePlugin.java          # Main entry point
        │   ├── AmbientSoundSystem.java        # Creepy sound management
        │   ├── EntitySpawnSystem.java         # Entity spawn logic
        │   ├── entity/
        │   │   ├── EndermanEntity.java        # Core entity class
        │   │   ├── EndermanRegistry.java      # Entity tracking
        │   │   └── custom/
        │   │       ├── EndermanStalkGoal.java       # Stalking AI
        │   │       ├── EndermanChaseGoal.java       # Chase AI
        │   │       ├── EndermanBreakInvisGoal.java  # Visibility breaker
        │   │       ├── EndermanTargetSeesMeGoal.java   # Sight detection
        │   │       └── EndermanTargetTooCloseGoal.java # Proximity trigger
        │   └── torch/
        │       ├── TorchBurnoutSystem.java    # Torch extinguishing
        │       └── PluginConfig.java          # Configuration
        └── resources/
            ├── manifest.json                  # Hytale plugin manifest
            ├── assets/cavehorror/
            │   ├── sounds.json                # Sound definitions
            │   └── lang/en_us.json            # Language strings
            └── audio/
                ├── Breathing.oga              # Ambient audio files
                ├── Corrosion1-3.oga
                ├── Damage2.oga
                ├── Decay0-3.oga
                ├── Laugh.oga
                └── SinkholeFall.oga
```

## Building

```bash
./gradlew build
```

The built JAR will be in `build/libs/cave-horror-white-eyes-0.1.0.jar`.

## Installation

1. Place the JAR in your Hytale server's `plugins/` folder
2. Restart the server
3. The plugin will generate a `config.json` on first run

## Porting Notes

This is a code-level port of the Minecraft NeoForge mod. The Hytale API is still in development, so some methods are marked as placeholders where the exact Hytale API calls need to be filled in. Search for `// Hytale API:` comments for integration points.

### Key API Integration Points

The following Hytale APIs are needed:
- **Entity API**: Registering and spawning custom entities
- **Event API**: Listening to player join/move/damage events
- **Sound API**: Playing positional audio
- **Block API**: Checking and modifying blocks (torch extinguish, structure building)
- **World API**: Raycasting for line-of-sight, height checks
- **Player API**: Position, look direction, gamemode checks
- **Scheduler API**: Ticking game loop

## License

MIT
