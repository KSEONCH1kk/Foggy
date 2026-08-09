# Foggy

Foggy is a per-viewer player visibility plugin for **Paper/Folia 1.21.4**, **Java 21** and
**PacketEvents 2.13.0**. It never calls `Player#hidePlayer`: client entities are removed and
restored through PacketEvents packets for one viewer at a time.

## What it does

- fully hides a target that is vanished, in spectator mode, outside every plausible FOV, or
  occluded by complete block-state OUTLINE voxel shapes;
- keeps ordinary potion/metadata-invisible entities client-side so vanilla can render their
  armor and held items and retain attack interaction while hiding the player body;
- evaluates every directed nearby pair from the viewer's repeating `EntityScheduler` task;
- restores visibility without show-side debounce by sending `SpawnEntity` plus current metadata,
  scale, equipment, potion effects, head rotation, velocity and passenger state in one flush;
- models partial-tick target motion using previous/current hitboxes;
- uses an O(n) spatial grid and sends packets only when a cached pair changes state;
- accepts optional F5/FOV/camera-offset telemetry on `foggy:camera`.

## Installation

1. Run Paper or Folia 1.21.4 on Java 21.
2. Install the standalone PacketEvents 2.13.0 Spigot plugin.
3. Copy `Foggy-1.1.0.jar` to `plugins/`.
4. Start once, edit `plugins/Foggy/config.yml`, then run `/foggy reload` or restart.

PacketEvents is a hard dependency (`depend: [packetevents]`). Foggy declares
`folia-supported: true`; it has no Bukkit global-tick task. Player state is captured by that
player's `EntityScheduler`, global reload/lifecycle work uses `GlobalRegionScheduler`, and only
immutable snapshots cross region boundaries. See [`docs/folia.md`](docs/folia.md) for the ownership
model and conservative cross-region ray fallback.

## Build

```bash
./gradlew clean test jar
./gradlew loadTest
```

The distributable is `build/libs/Foggy-1.1.0.jar`. PacketEvents and Paper are `compileOnly` and are
not shaded into it.

## Configuration

All important tradeoffs are in [`config.yml`](src/main/resources/config.yml): radius, spatial cell,
fallback FOV, F5 distance/source margin, interpolation samples, transparent/cutout policy and hide
confirmation ticks.
`hide-confirmation-ticks: 1` is the no-delay default. Increasing it affects hiding only; the first
visible result always sends the spawn snapshot immediately.

Players with `foggy.bypass` always see managed targets. The permission is not granted by default.

`invisibility.preserve-vanilla-entity: true` is the default. Potion invisibility and the ordinary
entity invisible flag then use vanilla metadata instead of `DestroyEntities`: equipment stays
visible and the entity remains attackable. Spectator, `Player#canSee=false` and supported vanish
APIs remain hard packet-hide signals. Set the option to `false` only if legacy full removal for
ordinary invisibility is explicitly required.

## Live debug

As an operator, run `/foggy debug <player>`. Foggy prints a full decision snapshot and updates an
action bar every five ticks. Viewer-only particles show fallback/exact cameras, sampled hitbox
points, representative block hits and the first clear ray that keeps the target visible.

Useful commands:

```text
/foggy debug <player>
/foggy debug status
/foggy debug particles on|off
/foggy debug off
/foggy reload
```

`/foggy reload` is also available from the server console. It validates the complete new
configuration before replacing the active services. Invalid values are rejected without stopping
the current runtime; permission `foggy.reload` defaults to operators.

Полная расшифровка значений и цветов: [`docs/debugging.md`](docs/debugging.md).

Pay particular attention to `bypass`, `managed`, `engineReason`, `optical`, and the four `packet`
flags. `bypass=true` means the viewer has `foggy.bypass` and Foggy intentionally never hides a
target for that viewer. The permission no longer defaults to OP.

## Honest limits

An unmodified Minecraft server does not receive the client's perspective, configured FOV,
framebuffer aspect, zoom-mod state or render partial tick. Foggy's fallback deliberately takes the
union of first-person, rear-F5 and front-F5 possibilities. This avoids false hiding, but can retain
a target that the current camera could not actually see. Exact telemetry requires the optional
companion protocol described in [`docs/companion-protocol.md`](docs/companion-protocol.md).

“The same render frame” cannot be guaranteed by any server-only 20 TPS plugin. On Paper, and for
players owned by the same Folia region, the decision and destroy packet are produced in the same
region tick in which Foggy samples the state. A cross-region show needs a target-owned snapshot and
then a viewer-owned send, so scheduler hand-off can add a region tick. Network latency, client
packet processing and render frames remain outside the server's control.

Foggy uses Paper's filtered `World#rayTraceBlocks` overload with
`ignorePassableBlocks=false`. In Paper 1.21.4 this maps directly to Minecraft
`ClipContext.Block.OUTLINE`, grid traversal and `VoxelShape.clip`. The complete shape of the current
block state is tested, including every sub-box of oriented stairs, top/bottom/double slabs, fences,
walls, panes, signs, ladders, trapdoors, doors and other non-full blocks. It is not reduced to
`Block#getBoundingBox()` or a full cube.

Optical transparency is independent of geometry. By default glass, stained/tinted glass, panes,
ice, leaves and portals do not terminate visibility rays. Foggy also bundles the 280 relevant
`CUTOUT`, `CUTOUT_MIPPED` and `TRIPWIRE` registrations extracted from the official 1.21.4 client.
Those blocks conservatively pass because server shapes cannot represent texture alpha or every
gap in the baked model. Fences and fence gates are added explicitly: their server shape is coarser
than the visible post/rail model. `GRASS_BLOCK` and `CACTUS` are deliberately excluded despite
their client render layer because their visible base geometry is opaque.

Set `raycast.transparent-block-mode` or `raycast.cutout-block-mode` to `occlude`, or add selected
names/globs to `raycast.opaque-material-overrides`, to make those materials block. The catalog is
stored in [`vanilla-1.21.4-cutout-materials.txt`](src/main/resources/vanilla-1.21.4-cutout-materials.txt).

`OUTLINE` is exact vanilla **server VoxelShape geometry**, not the final rasterized client model.
Texture alpha, resource-pack model overrides, shaders and client-only geometry are unavailable to
an unmodified server. Pixel-perfect visibility through an individual ladder/leaf/door/fence
opening still requires the client depth result and the same resource pack. The chosen server-only
mode avoids a false solid wall by passing the complete cutout block conservatively; this can
intentionally keep a player visible behind the opaque part of that same block.

Foggy preserves the vanilla player-info/tab entry while an entity is destroyed. A different plugin
that independently removes or rewrites that entry can prevent the client from accepting the later
player spawn. `Player#canSee` is read as an interoperability signal, but Foggy never mutates it.

On Folia, a ray is executed only when the viewer's current region owns every chunk in its corridor.
If a configured visibility radius crosses an independently ticking region boundary, Foggy returns
`REGION_UNOWNED` and keeps the target visible instead of reading foreign chunks or guessing that a
wall exists. Nearby tracker pairs are normally region-co-located, but this conservative fallback is
part of the safety contract and is visible in `/foggy debug status`.

## Reports and tests

- [`docs/stage-1-vanilla-analysis.md`](docs/stage-1-vanilla-analysis.md) — Mojang artifact hashes,
  exact 1.21.4 signatures and render/raycast formulas.
- [`docs/stage-2-packetevents.md`](docs/stage-2-packetevents.md) — PacketEvents API and complexity.
- [`docs/folia.md`](docs/folia.md) — scheduler ownership, cross-region snapshots and limitations.
- [`docs/testing.md`](docs/testing.md) — automated/load/manual verification.

## License

Foggy's original source is MIT-licensed. Minecraft and PacketEvents remain under their respective
licenses and are not redistributed by this project.
