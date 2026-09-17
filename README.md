# Spark & Strike: Artisan Anvils

![Spark & Strike: Artisan Anvils](docs/screenshots/hero_banner.jpg)

**Tactile, physical, in-world anvil crafting and forge automation for Minecraft 1.21.1 (Fabric).**

---

## 🔨 Overview

**Spark & Strike** replaces boring 2D crafting screens with dynamic, in-world action. Place raw ingots or gems onto the anvil, strike them with your hammer or chisel, watch fiery sparks burst on every blow, and forge masterwork blades and polished gems!

![Forging Gameplay](docs/screenshots/gameplay_forging.jpg)

### Core Features
- **100% Diegetic Interaction**: Zero 2D inventory grids. Pure spatial gameplay coordinated via vanilla 1.21 `ItemDisplayEntity` and `InteractionEntity`.
- **Dynamic Strike Harmonics**: Every tool strike modulates pitch higher until the final resonant strike completes the craft.
- **Mass-Production Automation**: Point Hoppers into the anvil or mount an overhead Dropper with a redstone clock. Stand and hold left-click with a tool to mass-produce equipment without opening a single GUI.
- **Modern Data Components**: Zero legacy NBT (`ItemStack#getTag()`). Dual-channel serialization with Mojang DFU `RecordCodecBuilder` and Netty binary `StreamCodec`.
- **Atomic World Safety**: Breaking the workstation mid-craft cleanly drops all active workpieces and despawns preview entities with zero orphan entity leaks.

---

## ⚙️ Mass-Production Automation Setup

![Forge Automation Contraption](docs/screenshots/gameplay_automation.jpg)

1. **Auto-Loading (Hoppers & Droppers)**:
   - **Hopper**: Point a hopper into the top or side of the anvil. It pushes 1 raw ingredient at a time when the anvil is empty.
   - **Dropper**: Place an overhead dropper on a redstone pulse clock. Floating dropped items immediately snap onto the anvil.
2. **Extraction Protection**:
   - Hoppers below the anvil cannot steal unworked ingots mid-craft.
   - Once crafted, finished items pop out into the world where collection hoppers underneath can safely gather them.

---

## 📦 Compatibility & Toolchain

- **Minecraft**: `1.21.1`
- **Mod Loader**: `Fabric Loader (>=0.16.0)`
- **API**: `Fabric API (>=0.103.0+1.21.1)`
- **Java**: `Java 21 LTS`
- **Gradle**: `8.10` with Fabric Loom `1.7.4`

---

## 🚀 Building & Testing

### Compile & Build Mod Jars
```bash
./gradlew build
```
Compiled binaries are output to `build/libs/k3-diegetic-1.0.0.jar`.

### Run Automated Headless GameTests (20 Tests)
```bash
./gradlew runGameTestServer
```
Executes the headless GameTest server verifying component serialization, dual-input recipe progression, atomic cleanup, hopper sided inventory, and continuous mass production.

### Launch Local Client for In-Game Play / F2 Screenshots
```bash
./gradlew runClient
```

### Publishing Dry-Run
```bash
./gradlew publishMods --dry-run
```

---

## 📜 License

Distributed under the **MIT License**. Free for personal play, modpacks, and content creation.

