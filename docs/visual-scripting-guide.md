# Hytale Visual Scripting: Enderman AI Behavior Design

## Overview

This document maps the Minecraft mod's Java-based AI goals to Hytale's visual scripting node system. Hytale's node system (similar to Unreal Engine Blueprints) allows creating AI behavior through connected node graphs instead of Java code.

The node behavior definitions live in `assets/behaviors/enderman_stalk.json` — this file describes the state machine, nodes, sensors, and effects that the visual editor renders as a node graph.

## Architecture: Java + Node Hybrid

| Layer | Language | Purpose |
|-------|----------|---------|
| **State Machine** | Node (JSON) | High-level state transitions (idle → stalking → chasing → fleeing → hunting) |
| **AI Behaviors** | Node (JSON) | Distance maintenance, stare tracking, wall phasing, block stealing |
| **Sensors** | Node (JSON) | Proximity, line-of-sight, FOV checks, environment queries |
| **Core Plugin** | Java | Server lifecycle, entity registration, scheduler, sound system |
| **Custom Nodes** | Java | Performance-critical logic exposed as visual nodes for designers |

## Minecraft → Hytale Node Mapping

### 1. EndermanStalkGoal → `stalk_tick` Node Graph

```
                    ┌─────────────────────────────────┐
                    │         stalk_tick              │
                    │         (Selector)              │
                    └────────────┬────────────────────┘
                                 │
               ┌─────────────────┼─────────────────┐
               ▼                 ▼                   ▼
        ┌───────────┐    ┌──────────────┐    ┌──────────────┐
        │ Distance  │    │    Stare     │    │    Wall      │
        │Maintenance│    │  Tracking    │    │   Phasing    │
        └─────┬─────┘    └──────┬───────┘    └──────┬───────┘
              │                 │                    │
        ┌─────┴─────┐    ┌──────┴───────┐    ┌──────┴───────┐
        │Tug-of-war │    │Increment if │    │If navigation │
        │20-30 block│    │player looks,│    │stuck: teleport│
        │ distance  │    │decay if not │    │closer to plyr│
        └───────────┘    └──────────────┘    └──────────────┘

               ┌─────────────────┼─────────────────┐
               ▼                 ▼                   ▼
        ┌───────────┐    ┌──────────────┐    ┌──────────────┐
        │  Block    │    │  Structure   │    │   Corridor   │
        │ Stealing  │    │  Building    │    │  Detection   │
        └───────────┘    └──────────────┘    └──────────────┘
```

### 2. EndermanChaseGoal → `chase_tick` Node Graph

```
                    ┌─────────────────────────────────┐
                    │         chase_tick              │
                    │         (Selector)              │
                    └────────────┬────────────────────┘
                                 │
               ┌─────────────────┼─────────────────┐
               ▼                 ▼                   ▼
        ┌───────────────┐ ┌──────────────┐  ┌──────────────┐
        │   Weeping     │ │    Attack    │  │    Torch     │
        │ Angel Freeze  │ │   Player     │  │ Destruction  │
        └───────┬───────┘ └──────┬───────┘  └──────┬───────┘
                │                │                   │
        ┌───────┴───────┐ ┌──────┴───────┐  ┌──────┴───────┐
        │If player looks│ │If dist < 3:  │  │Destroy torches│
        │AND dist > 4:  │ │deal 6 dmg +  │  │in 1-block    │
        │STOP movement  │ │break shield  │  │radius        │
        └───────────────┘ └──────────────┘  └──────────────┘
```

### 3. EndermanBreakInvisGoal → Visibility Check Node

```
┌─────────────────────────────────────────────┐
│          break_invis_check                  │
│                                             │
│  [Player Invisible?] ──YES──> [Break Invis] │
│       │                    │                │
│       NO                   ▼                │
│       │              [Play Stare Sound]     │
│       ▼              [Set State = CHASING]  │
│    [Continue]          [Show White Eyes]    │
└─────────────────────────────────────────────┘
```

### 4. TargetSeesMeGoal + TargetTooCloseGoal → Targeting Nodes

```
┌─────────────────────────────────────────────────────┐
│              targeting_check (Selector)               │
│                                                       │
│  [TargetSeesMe] ──YES──> [SetTargetPlayer]          │
│       │                     [SetState = CHASING]     │
│       NO                     [Play Spotted Sound]    │
│       │                                              │
│  [TargetTooClose] ──YES──> [SetTargetPlayer]        │
│       │                     [SetState = CHASING]     │
│       NO                     [SetVisible = true]     │
│       │                                              │
│    [No action]                                       │
└─────────────────────────────────────────────────────┘
```

## JSON Behavior File Structure

The behavior system uses a JSON schema that the visual editor renders as node graphs:

```json
{
  "behavior": "cave_dweller_stalk",
  "states": { /* State machine with entry/tick/exit nodes */ },
  "nodes": { /* Behavior tree nodes (Selector, Sequence, Condition) */ },
  "variables": { /* Mutable state vars (stare ticks, cooldowns) */ },
  "sensors": { /* Environment queries (distance, FOV, raycast) */ },
  "effects": { /* Actions (sound, particle, visibility, damage) */ }
}
```

## Node Reference

| Node Type | Description | Examples |
|-----------|-------------|---------|
| `sequence` | Run children in order | Entry sequences, do-this-then-that |
| `selector` | Run children until one succeeds | State tick behaviors |
| `condition` | Branch based on sensor/variable | If distance < 20 → back away |
| `cooldown_action` | Run action on cooldown timer | Wall phasing every 60 ticks |
| `timer` | Increment/decrement variable over time | Stare tracking |
| `area_effect` | Apply effect to blocks/entities in radius | Torch destruction |
| `scan` | Scan environment for patterns | Corridor detection |

## Extending with Custom Java Nodes

For complex logic that can't be expressed visually (e.g., structure building algorithms, corridor geometry detection), write Java code and expose it as a custom node:

```java
// HYTALE API: Register custom node
// BehaviorNodeRegistry.register("build_mossy_dungeon", 
//     new MossyDungeonBuilderNode());
```

The node graph then calls this custom node like any other, keeping the high-level logic visual while the heavy lifting stays in Java.
