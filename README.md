# Foggy

Foggy is a per-viewer player visibility plugin for one broad-compatible server JAR:

- Spigot/Paper 1.8.x (the server release is 1.8.8; 1.8.9 clients use the same protocol 47);
- Spigot/Paper 1.16.x through 1.21.x;
- Paper/Folia 26.1 and newer.

It requires the standalone **PacketEvents 2.13.0** plugin. Foggy never calls
`Player#hidePlayer`: it removes and restores the client entity for one viewer at a time through
version-aware PacketEvents packets.

## What it does

- hard-hides vanished and spectator targets, or targets occluded from every plausible camera;
- preserves ordinary potion/metadata-invisible entities so armor, held items and attack hitboxes
  keep working exactly as vanilla intends;
- restores visibility immediately, without show-side debounce;
- uses a sparse `CompensatedWorld` and `CompensatedEntities` spatial index instead of calling
  `CraftWorld#rayTraceBlocks` for every ray;
- caches every block state's complete native geometry: the collision AABB collector on 1.8 and
  `VoxelShape` boxes on 1.16+;
- conservatively passes transparent and cutout blocks whose texture/model holes cannot be
  represented by server collision geometry;
- accepts optional exact F5/FOV/camera telemetry on `foggy:camera`.

## Installation

1. Run a supported server on the Java version that server requires.
2. Install the standalone PacketEvents 2.13.0 Spigot plugin.
3. Copy `Foggy-2.0.0.jar` to `plugins/`.
4. Start the server, edit `plugins/Foggy/config.yml`, then use `/foggy reload`.

Foggy itself is Java 8 bytecode. This does **not** change the JVM required by the server: for
example, 1.8.8 runs on Java 8, 1.16.5 on Java 16, modern 1.20/1.21 on Java 21, and 26.1+ on Java
25. See the complete [version matrix](docs/version-support.md).

PacketEvents is a hard dependency (`depend: [packetevents]`). `folia-supported: true` is declared.
The same binary selects classic Bukkit scheduling or Folia entity/global schedulers at runtime,
without statically linking modern-only Bukkit classes.

## Build

```bash
./gradlew clean test check jar
./gradlew loadTest
```

The distributable is `build/libs/Foggy-2.0.0.jar`. Paper/Spigot and PacketEvents are
`compileOnly`; they are not shaded. `check` also rejects any class newer than Java 8 classfile
major version 52.

## Configuration

All performance and accuracy controls are in [`config.yml`](src/main/resources/config.yml):
visibility radius, spatial cell size, fallback FOV/F5 cameras, interpolation samples, world-cache
validation/retention, optical decision cache, transparent/cutout policy and hide confirmation.

`hide-confirmation-ticks: 1` hides on the first confirmed pass. Raising it debounces hiding only;
showing is always immediate. Players with `foggy.bypass` always see managed targets and the
permission is not granted by default.

`invisibility.preserve-vanilla-entity: true` keeps an ordinarily invisible player entity on the
client. Vanilla metadata hides its body while equipment remains visible and attacks keep working.
Spectator, `Player#canSee=false` and supported vanish APIs still cause full directed packet hiding.

## Commands and live debug

```text
/foggy reload
/foggy debug <player>
/foggy debug status
/foggy debug particles on|off
/foggy debug off
```

`/foggy reload` validates the complete replacement configuration before swapping services; an
invalid file leaves the working runtime intact. It also works from the console.

`/foggy debug <player>` shows the decision, camera samples, hitbox samples, world-cache counters,
shape bridge/fallback state and packet tracking flags. Viewer-only particles draw cameras, target
points, blockers and the first clear ray. `fallback > 0` in the cache line means that a native
shape could not be read and should be investigated. Full field/color documentation is in
[`docs/debugging.md`](docs/debugging.md).

## Geometry and performance

The hot ray path does not use Bukkit `World#rayTraceBlocks`. `CompensatedWorld` performs local
Mojang-style DDA through sparse cached cells and clips against compact primitive AABBs.

- On 1.8, where `VoxelShape` did not exist, Foggy invokes the native six-argument block collision
  collector for the exact state and neighboring world context.
- On 1.16+, it extracts every AABB from the state's native OUTLINE and COLLIDER `VoxelShape`.
- Cache invalidation follows block events immediately; identity validation also catches direct
  plugin/NMS writes.

This covers all occupied boxes of stairs, slabs, fences, walls, gates, doors, trapdoors, signs,
ladders, panes, piston heads and other partial blocks. It is not a single Bukkit bounding box and
not a full-cube approximation. If a runtime-specific native bridge genuinely cannot initialize,
Foggy uses a conservative local full-cube fallback—never the profiler-heavy Bukkit ray tracer—and
exposes the fallback counter in debug output.

Server `VoxelShape` geometry is still not the rendered mesh. Texture alpha, resource-pack models,
shaders and tiny visual openings are unavailable to an unmodified server. Foggy therefore passes
configured transparent materials and the bundled vanilla cutout catalog conservatively. Fences
and gates also pass by default because their rendered holes are finer than server shapes. Selected
materials can be forced opaque with `raycast.opaque-material-overrides`.

The visibility loop queries `CompensatedEntities` cells near each viewer, so normal pair work is
O(n·k), where `k` is the nearby population, rather than an unconditional O(n²). A ray stops at its
first clear target point, and packet wrappers are built only when a cached directed pair changes.

## Honest limits

An unmodified server does not receive the client's perspective, configured FOV, framebuffer
aspect, zoom state or render partial tick. The fallback takes the union of first-person, rear-F5
and front-F5 possibilities. That avoids false hiding, but can retain a target not visible from the
active camera. Exact values require the [companion protocol](docs/companion-protocol.md).

A server-only 20 TPS plugin cannot guarantee a particular client render frame. On classic Paper,
and within one Folia region, Foggy decides and sends in the same owning tick. Network latency and
client rendering remain outside server control; cross-region show can require one scheduler
handoff. Foggy keeps the player-info/tab entry while removing the world entity, so another plugin
that removes that entry can prevent a later player spawn.

On Folia, rays execute only when the current region owns the complete chunk corridor. Otherwise
Foggy returns `REGION_UNOWNED` and keeps the target visible rather than accessing foreign chunks or
inventing an occluder. See [`docs/folia.md`](docs/folia.md).

## Reports and verification

- [`docs/version-support.md`](docs/version-support.md) — server/JVM/protocol matrix and tested builds.
- [`docs/stage-1-vanilla-analysis.md`](docs/stage-1-vanilla-analysis.md) — exact 1.21.4 renderer and ray formulas used as the modern reference.
- [`docs/stage-2-packetevents.md`](docs/stage-2-packetevents.md) — packet APIs, version gates and complexity.
- [`docs/folia.md`](docs/folia.md) — scheduler ownership and cross-region behavior.
- [`docs/testing.md`](docs/testing.md) — automated, load and runtime verification.

## License

Foggy's original source is MIT-licensed. Minecraft and PacketEvents remain under their respective
licenses and are not redistributed by this project.
