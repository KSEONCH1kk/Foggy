# Version support

Foggy 2.0.1 is distributed as one Java 8-compatible JAR. Runtime capabilities—not class linkage
to one Bukkit revision—select the scheduler, geometry and packet implementation.

| Server line | Support | Server JVM | Geometry source | Player spawn packet |
| --- | --- | --- | --- | --- |
| Spigot/Paper 1.8.x | supported; tested 1.8.8 | Java 8 | native state-aware collision AABB collector | `SpawnPlayer` |
| Spigot/Paper 1.16.x–1.19.x | supported; tested 1.16.5 | use the JVM required by that build | native `VoxelShape` AABBs | `SpawnPlayer` |
| Spigot/Paper 1.20.x | supported; tested 1.20.6 | Java 21 for 1.20.6 | native `VoxelShape` AABBs | `SpawnEntity` from 1.20.2 |
| Paper/Spigot 1.21.x | supported | Java 21 | native `VoxelShape` AABBs | `SpawnEntity` |
| Paper/Folia 26.1+ | supported; tested 26.1.2 and 26.2 | Java 25 | native `VoxelShape` AABBs through current Craft accessors | `SpawnEntity` |

Minecraft 1.8.9 is a client release, not a separate CraftBukkit/Spigot server release. A 1.8.9
client and the 1.8.8 server both use protocol 47; the transition test used two protocol-47 clients.

Versions 1.9 through 1.15 are not part of this release's compatibility contract. They may expose
enough fallback capabilities to load, but they are neither built against nor included in the
runtime matrix.

## JVM distinction

Foggy classfiles are capped at Java 8 major version 52. The server still decides which JVM can
launch it. Representative Paper requirements are Java 8 for the old 1.8 line, Java 16 for 1.16.5,
Java 21 for current 1.20/1.21, and Java 25 for 26.1+. Do not try to launch a modern Paper server on
Java 8 merely because Foggy uses Java 8 bytecode.

## Runtime compatibility gates

- `PlatformAdapter` reflects Folia entity/global schedulers, region ownership, tracked viewers,
  bounding boxes, invisibility flags, scale, passengers and modern world-height accessors. Classic
  Bukkit methods remain the baseline.
- `NmsShapeAccess` resolves both legacy 1.8 collision collection and the modern Craft/NMS state,
  level, position, `VoxelShape` and AABB methods. Resolved handles are cached outside the ray loop.
- PacketEvents server-version gates select `SpawnPlayer` before 1.20.2 and player-typed
  `SpawnEntity` afterward. Offhand/passengers are omitted before 1.9 and scale before 1.20.5.
- Debug action bars use chat `GAME_INFO` on 1.8–1.15, chat `GAME_INFO` with sender UUID on 1.16,
  and the dedicated action-bar packet only on 1.17+. Sending that newer packet to protocol 47
  resolves to invalid packet id `-1` and disconnects the client.
- The plugin descriptor intentionally has no `api-version`: 1.8 cannot consume a modern API
  declaration. Modern Paper can print a legacy-plugin warning and perform its compatibility setup
  during startup; this does not reintroduce Bukkit ray tracing into the hot path.

## Verified runtime matrix

The release candidate was exercised with official Paper artifacts and PacketEvents 2.13.0:

| Runtime | Verification |
| --- | --- |
| Paper 1.8.8 build 445 / Java 8 | load, reload, exact native shapes (`fallback=0`), protocol-47 chat-position-2 debug and particles, two-client directed destroy and `SpawnPlayer` restore |
| Paper 1.16.5 build 794 / Java 16 | load, reload, exact native `VoxelShape` bridge (`fallback=0`), UUID chat-position-2 debug and PacketEvents particles |
| Paper 1.20.6 build 151 / Java 21 | load, reload, exact shapes, two-client directed destroy and player `SpawnEntity` restore |
| Paper 1.21.4 build 232 / Java 21 | load, reload, two-client debug, exact `VoxelShape` boxes (`fallback=0`) |
| Paper 26.1.2 build 74 / Java 25 | load, reload, current Craft state/level access, exact shapes (`fallback=0`) |
| Paper 26.2 build 111 / Java 25 | load plus direct compensated-world probe (`bridgeUnavailable=false`, `fallback=0`) |
| Folia 26.2 build 1 / Java 25 | load, entity/global scheduler execution and owned-region shape probe (`fallback=0`) |

The representative-build matrix verifies the compatibility gates; it does not claim that every
third-party fork preserves CraftBukkit internals. `/foggy debug status` exposes bridge failures and
fallback counts explicitly so an unknown fork fails conservatively and visibly.
