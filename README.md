# BoardMod

A NeoForge mod for Minecraft 1.21.1 that adds rideable **Snowboard** and **Skateboard** items.

## Items

### Snowboard
- Craft with 3 planks in the middle row (`PPP`)
- Right-click to place under your feet and ride
- Moves at **10 blocks/sec on snow** (slow on other surfaces)
- Shift to dismount and recover the item

### Skateboard
- Craft in a cross pattern (` P ` / `PPP` / ` P `)
- Right-click to place under your feet and ride
- Moves at **10 blocks/sec on any surface**
- Shift to dismount and recover the item

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.226+

## Installation

Drop `bootmod-1.0.0.jar` into your `mods/` folder.

## Building from source

```
./gradlew build
```

Output: `build/libs/bootmod-1.0.0.jar`

> **Note:** The `neo_version` in `gradle.properties` must match your server's NeoForge version.
