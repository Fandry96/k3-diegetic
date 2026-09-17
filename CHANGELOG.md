# Changelog

All notable changes to the `k3-diegetic` mod will be documented in this file.

## [1.0.0] - 2026-09-16
### Added
- Initial project scaffolding for Fabric 1.21.1 and Java 21 LTS.
- Split environment source sets (`src/main` and `src/client`).
- Data generation pipeline via `fabricApi.configureDataGeneration()`.
- Multi-platform publishing automation via `me.modmuss50.mod-publish-plugin` with local dryRun safety gates.
- Headless GameTest harness wiring (`runGameTestServer`).
- Core entrypoints: `K3DiegeticMod`, `K3DiegeticClient`, and `K3DiegeticDataGenerator`.
