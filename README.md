# Cave Horror: White Eyes — Hytale Plugin

A port of the Minecraft NeoForge mod to Hytale. Something stalks you in the deep caves — a tall, thin creature with glowing white eyes follows from the darkness, invisible, silent, patient.

## Features

- **Stalking AI**: Entity follows players 20-30 blocks behind, phasing through walls
- **Weeping Angel Mechanic**: Entity freezes when looked at, moves when unobserved
- **White Eyes**: Glowing eyes visible in complete darkness after cave sounds play
- **Ambient Horror**: Creepy enderman sounds intensify as the entity approaches
- **Torch Extinguishing**: Torches snuff out when the entity is near (30+ blocks deep)
- **Block Stealing**: Doors, glass panes, fence gates stolen to weaken player bases
- **Structure Building**: Entity builds sand pillars, pyramids, and mossy dungeons
- **Wall Emergence**: In corridors, the entity emerges from walls with mold particle effects
- **Run-Away Stalk**: Entity disappears when you run, then ambushes you ahead

## Architecture

| Layer | Language | Purpose |
|-------|----------|---------|
| **Plugin Core** | Java 25 | Server lifecycle, entity management, sound system, torch burnout |
| **Visual Scripting** | JSON (Nodes) | Enderman AI state machine for Hytale's node editor |
| **Asset Processor** | C# (.NET 8) | Audio conversion (.oga→.ogg), texture resizing, asset validation |

### Core Plugin

Built with Hytale's service-oriented API:

```
com.hytale.api.plugin.JavaPlugin    — Base class (extends required)
com.hytale.api.HytaleServer         — Service locator
  .getPlayerService()               — Online player management
  .getEntityService()               — Entity spawning
  .getAudioService()                — Positional sound playback
  .getWorldService()                — Block queries and modification
  .getParticleService()             — Particle effects (eyes, mold)
  .getEventBus()                    — Event registration (@Subscribe)
  .getEntityRegistry()              — Custom entity type registration
  .getScheduler()                   — Task scheduling
```

### Visual Scripting Nodes (Hytale Node System)

The Enderman AI is defined as a visual scripting node graph in `src/main/resources/enderman_stalk.json`:

- **State Machine**: IDLE → STALKING → CHASING → FLEEING → HUNTING
- **Node Types**: Selector, Sequence, Condition, CooldownAction, Timer, AreaEffect, Scan
- **Sensors**: Proximity (200b), FOV check (70°), Raycast LOS, Environment, Movement tracking
- **Effects**: Visual (invisibility, eyes), Sound (scream, ambient, stare), Particle (white eyes, mold)

The design guide at `docs/visual-scripting-guide.md` maps every Minecraft AI goal to its node graph equivalent.

### C# Asset Processor

Standalone tool at `tools/csharp/AssetProcessor/`:

```bash
dotnet run -- convert-audio  input.oga output.ogg     # Audio conversion
dotnet run -- resize-texture input.png 64 64 output.png # Texture resize
dotnet run -- validate       assets/                   # Asset validation
dotnet run -- batch          assets/                   # Full batch pipeline
```

## Project Structure

```
cave-horror-white-eyes/
├── build.gradle.kts              # Gradle build (Hytale tools plugin)
├── settings.gradle.kts           # Project settings
├── gradle.properties             # Build properties
├── src/main/
│   ├── java/com/favasur/cavehorror/
│   │   ├── CaveNoisePlugin.java           # Main entry (extends JavaPlugin)
│   │   ├── AmbientSoundSystem.java         # Creepy sound management
│   │   ├── EntitySpawnSystem.java          # Entity spawn with validation
│   │   ├── entity/
│   │   │   ├── EndermanEntity.java         # Core entity + EntityDefinition
│   │   │   ├── EndermanRegistry.java       # Entity tracking
│   │   │   └── custom/
│   │   │       ├── EndermanStalkGoal.java      # Stalking AI + corridor ambush
│   │   │       ├── EndermanChaseGoal.java      # Chase AI + torch destruction
│   │   │       ├── EndermanBreakInvisGoal.java # Visibility break + sound
│   │   │       ├── EndermanTargetSeesMeGoal.java  # LOS detection (raycast)
│   │   │       └── EndermanTargetTooCloseGoal.java # Proximity trigger
│   │   ├── torch/
│   │   │   ├── TorchBurnoutSystem.java     # Torch extinguishing
│   │   │   └── PluginConfig.java           # JSON config with defaults
│   │   └── bridge/
│   │       └── BridgeServer.java           # Optional HTTP bridge
│   └── resources/
│       ├── manifest.json                   # Plugin manifest
│       ├── enderman_stalk.json             # Visual scripting node schema
│       └── assets/cavehorror/
│           ├── sounds.json                 # Sound definitions
│           ├── lang/en_us.json             # Language strings
│           └── audio/                      # .oga ambient audio files
├── tools/csharp/AssetProcessor/            # C# asset processing tool
└── docs/visual-scripting-guide.md          # Node behavior design guide
```

## Building

```bash
# Build the plugin JAR
./gradlew build
# JAR at: build/libs/cave-horror-white-eyes-0.1.0.jar

# Build C# asset processor (requires .NET 8 SDK)
cd tools/csharp/AssetProcessor
dotnet build
```

## Installation

1. Place `cave-horror-white-eyes-0.1.0.jar` in your Hytale server's `plugins/` folder
2. Restart the server
3. The plugin generates `plugins/CaveHorror/config.json` on first run

### Configuration (`config.json`)

| Key | Default | Description |
|-----|---------|-------------|
| `torch_extinguish_radius` | 20 | Base radius for torch extinguishment |
| `torch_extinguish_chance` | 0.3 | Per-torch chance to extinguish each tick |
| `spawn_chance_per_tick` | 0.005 | Base entity spawn probability |
| `spelunker_spawn_chance` | 0.04 | Spawn chance for underground players |
| `min_stalk_distance` | 20 | Min distance entity keeps from player |
| `max_stalk_distance` | 30 | Max distance before entity moves closer |
| `chase_trigger_distance` | 15 | Distance at which entity lunges |
| `stare_trigger_ticks` | 200 | Ticks of staring before entity chases |
| `attack_damage` | 6.0 | Melee attack damage |
| `max_health` | 65.0 | Entity health |
| `move_speed` | 0.35 | Base movement speed |
| `debug_mode` | false | Enable debug logging |

## Hytale API Integration

All `// HYTALE API: ` placeholders from the initial port have been replaced with real API calls:

- **`extends JavaPlugin`** — Proper plugin base class
- **`@Subscribe`** — Event-driven architecture (ServerTickEvent, PlayerJoinEvent, etc.)
- **`HytaleServer.getPlayerService()`** — Player enumeration and state
- **`HytaleServer.getAudioService()`** — Positional sound playback with pitch/volume
- **`HytaleServer.getWorldService()`** — Block queries, modification, raycasting
- **`HytaleServer.getEntityService()`** — Entity spawning with location validation
- **`HytaleServer.getParticleService()`** — Particle effects (white eyes, mold dust)
- **`world.rayTrace()`** — Line-of-sight detection for targeting goals
- **`entity.getPhysicsComponent()`** — Collision detection, gravity, velocity
- **`EntityDefinition`** — Custom entity attributes and registration

## AI State Machine

```
                    ┌─────────┐
                    │  IDLE   │
                    └────┬────┘
                         │ player underground
                         ▼
                    ┌──────────┐
         ┌─────────►│ STALKING │◄────────────────┐
         │          └─────┬────┘                  │
         │                │     stared 10s         │
         │                │ or player < 15 blocks  │
         │                ▼                        │
         │          ┌──────────┐                   │
         │          │ CHASING  │────► FLEEING ─────┘
         │          └────┬─────┘     (low HP)
         │               │ player out of sight
         │               ▼
         │          ┌──────────┐
         └──────────│ STALKING │ (re-stealth)
                    └──────────┘

    HUNTING (corridor ambush):
    STALKING ──► HUNTING ──► CHASING
    (corridor    (wall         (emerge,
     detected)    teleport)     chase)
```

## License

MIT
