# Changelog

All notable changes to the `k3-diegetic` mod will be documented in this file.

## [1.1.0] - 2026-09-18
### Added
- **Method 2 In-World Blueprint Smithing**: Complete diegetic forging system using physical blueprint stencils placed directly on the anvil.
- **Full Tool Suite**:
  - Iron Sword (Sword Blueprint + 2 Iron Ingots + 1 Stick)
  - Iron Pickaxe (Pickaxe Blueprint + 3 Iron Ingots + 2 Sticks)
  - Iron Axe (Axe Blueprint + 3 Iron Ingots + 2 Sticks)
  - Iron Shovel (Shovel Blueprint + 1 Iron Ingot + 2 Sticks)
  - Iron Hoe (Hoe Blueprint + 2 Iron Ingots + 2 Sticks)
- **Complete Iron Armor Set**:
  - Iron Helmet (Helmet Blueprint + 5 Iron Ingots)
  - Iron Chestplate (Chestplate Blueprint + 8 Iron Ingots)
  - Iron Leggings (Leggings Blueprint + 7 Iron Ingots)
  - Iron Boots (Boots Blueprint + 4 Iron Ingots)
- **Expanded Anvil Capacity & Dual-Row BER Rendering**:
  - Anvil inventory expanded to 9 slots (`INVENTORY_SIZE = 9`), accommodating full chestplate smithing (blueprint + 8 ingots).
  - Enhanced Block Entity Renderer with organized 2-row layout rendering workpieces with dynamic elevation and strike wobble.
- **Sequential Hopper & Dropper Automation**:
  - Full support for automated item insertion with strict recipe sequence filtering.
  - Count clamping in `setStack()` to prevent hopper item stack overflows beyond single-item workstation slots.
  - World item scatter safeguard on blueprint removal (`removeStack(0)`) preventing accidental hopper ingredient eating.
- **In-Game Blacksmith's Manual**:
  - Illustrated field guide automatically granted on first world join or craftable with Book + Iron Ingot.
- **Headless Quality Assurance**:
  - 35 automated Minecraft GameTests passing headlessly with 0 OpenGL dependencies.
  - 100% Data Component compliance with 0 legacy NBT compound violations.

### Changed
- Refactored artisan anvil recipe registry to support flexible multi-ingredient recipes up to 8 secondary items.
- Cleaned up legacy single-ingredient smithing recipes in favor of blueprint-driven forging.

## [1.0.0] - 2026-09-16
### Added
- Initial project scaffolding for Fabric 1.21.1 and Java 21 LTS.
- Split environment source sets (`src/main` and `src/client`).
- Data generation pipeline via `fabricApi.configureDataGeneration()`.
- Multi-platform publishing automation via `me.modmuss50.mod-publish-plugin` with local dryRun safety gates.
- Headless GameTest harness wiring (`runGameTestServer`).
- Core entrypoints: `K3DiegeticMod`, `K3DiegeticClient`, and `K3DiegeticDataGenerator`.
