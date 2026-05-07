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

### Motorboat
- Craft with 7 planks and 1 iron ingot (`P P` / `PIP` / `PPP`)
- Right-click to place in water and ride
- Moves at **12 blocks/sec on water**
- Shift to dismount and recover the item

### Custom Raft (Steering Wheel)
- Craft a **Steering Wheel** with 3 sticks and 2 logs (` S ` / `SLS` / ` L `)
- Build a custom shape out of any blocks (maximum 20 blocks)
- The structure must touch water and cannot touch solid ground
- Right-click the structure with the Steering Wheel to turn it into a rideable Raft!
- Moves at **7 blocks/sec on water**
- Hitting the raft breaks it back into its original blocks and the Steering Wheel

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
