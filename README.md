# Panopticon

The dedicated-server companion for the [Panoptic](https://github.com/Mokich/Panoptic) mod.

Panopticon runs on the server only. It:

- answers Panoptic's seed, structure, biome and villager-trade requests;
- owns the permission system - groups, per-player nodes, the `/panopticon` command, and an in-game admin GUI;
- handles server-side actions like villager spawning and item giving.

> **Do not install Panopticon alongside Panoptic on the same server.** Panopticon replaces Panoptic server-side and refuses to load (by design) if both are present. Players still install Panoptic on their clients.

## Supported versions

| Minecraft | Forge | NeoForge | Fabric |
|:---:|:---:|:---:|:---:|
| 1.20.1 | ✅ | - | ✅ |
| 1.21.1 | ✅ | ✅ | ✅ |

## Repository layout

Five self-contained Gradle projects, one per loader target, plus a composite root:

```
Panopticon/
├── 1.20.1-forge/      ForgeGradle 6, Java 17
├── 1.21.1-forge/      ForgeGradle 6, Java 21
├── 1.21.1-neoforge/   ModDevGradle, Java 21
├── 1.21.1-fabric/     Fabric Loom, Java 21
├── 1.20.1-fabric/     Fabric Loom, Java 17 target
├── settings.gradle    composite build (forge/neoforge projects)
├── build.gradle       buildAll / cleanAll / collectJars
└── stampDates.gradle  reproducible jar timestamps
```

Each target is a full copy of the (small) server codebase adapted to that loader's APIs. The Forge/NeoForge projects build through a Gradle composite; the two Fabric projects run on their own newer Gradle via wrapper calls from the root.

## Building

Requires JDK 17 and JDK 21 installed (Gradle toolchains pick them up automatically). From the repository root:

```
./gradlew buildAll
```

Final jars for all five targets are collected in `build/libs/`. Builds are reproducible (fixed file order, constant entry timestamps).

## How it works

- **Worldgen oracle.** The server answers the client's map queries with authoritative data: structure scans are computed asynchronously per 512-block region, biome tiles are sampled on a grid and shipped RLE-compressed with a palette.
- **Permission store.** The source of truth lives in `config/panoptic/perms.json`: named groups (sets of nodes) plus per-player group, allow and deny lists. Resolution order: deny > allow > group > default group. Nodes are synced to the client on login and re-synced automatically whenever a player's effective nodes change.
- **Admin access.** A player is an admin with vanilla permission level 3, when granted through the store, or via the `panoptic.admin` permission node: Forge PermissionAPI on the Forge/NeoForge targets, fabric-permissions-api on the Fabric targets. Permission managers like LuckPerms plug in on every loader.
- **Server-side validation.** Every client request is re-checked against the store before it is served; the client is never trusted.

## Commands

```
/panopticon reload
/panopticon group create|remove|allow|revoke <group> [node]
/panopticon player group|allow|deny|unset <player> [value]
/panopticon info
/panopticon export
```

All subcommands require admin access. The same operations are available through the in-game admin GUI, including export/import of the whole permission store as JSON.

## Permission nodes

| Node | Gates |
|---|---|
| `panoptic.inspector` | Inspector |
| `panoptic.seed.view` | Seed Inspector map |
| `panoptic.seed.structures` | structure overlay on the map |
| `panoptic.trade` | Trade Inspector |
| `panoptic.trade.spawn` | spawning villagers from the trade browser |
| `panoptic.screens` | Screen Inspector |
| `panoptic.screens.give` | giving items from screenshots |
| `panoptic.admin` | admin GUI and `/panopticon` |

## Network protocol

All channels live in the `panoptic:` namespace - this is the wire contract with the Panoptic client, shared by integrated servers and Panopticon. Message names and payload layout are identical across all five targets.

| Channel | Direction | Purpose |
|---|:---:|---|
| `structure_check` | C to S | structures at the player's position |
| `structure_result` | S to C | structure list with bounds and pieces |
| `all_structures` | C to S | full structure registry request |
| `all_structures_result` | S to C | structure ids and tags |
| `perms_sync` | S to C | permission nodes for the player |
| `seed_push` | S to C | world seed (when granted) |
| `admin_open` | C to S | request admin state |
| `admin_state` | S to C | groups, players, raw store JSON |
| `admin_edit` | C to S | permission edit operation |
| `spawn_villager` | C to S | spawn a villager by profession |
| `give_request` | C to S | give up to 27 item stacks |
| `struct_region_request` | C to S | region structure scan |
| `struct_region_result` | S to C | scan result |
| `biome_tile_request` | C to S | biome tile |
| `biome_tile_result` | S to C | RLE biome tile |

## Support

If this project is useful to you, you can support its development - thank you!

- **Boosty:** https://boosty.to/velikiybogmolokich/donate
- **DonationAlerts:** https://donationalerts.com/r/mokichchannel
- **TON:** `UQAEfqFEFDGDdp5fkePrEiJ_xiGEnsTGRNN6HccyOreXWgQ_`
- **Ethereum (ERC-20):** `0xD405a3B4a479B5820F4977C54a3e3ef366fc3c57`
- **Solana (SOL):** `C973HmGDedHE3mFVcNNnLYnpGeSCwxd9D9KavtvTPgPA`

## License

Released under the [GNU Lesser General Public License v3.0](LICENSE).