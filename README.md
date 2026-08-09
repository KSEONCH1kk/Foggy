# Foggy

Foggy is a per-viewer player visibility plugin for **Paper 1.21.4**, **Java 21** and
**PacketEvents 2.13.0**. It never calls `Player#hidePlayer`: client entities are removed and
restored through PacketEvents packets for one viewer at a time.

## What it does

- hides a target that is invisible, vanished, in spectator mode, outside every plausible FOV,
  or fully occluded by complete block-state OUTLINE voxel shapes;
- evaluates every directed nearby pair at `ServerTickEndEvent`, after the tick's movement;
- restores visibility without show-side debounce by sending `SpawnEntity` plus current metadata,
  scale, equipment, potion effects, head rotation, velocity and passenger state in one flush;
- models partial-tick target motion using previous/current hitboxes;
- uses an O(n) spatial grid and sends packets only when a cached pair changes state;
- accepts optional F5/FOV/camera-offset telemetry on `foggy:camera`.

## Installation

1. Run Paper 1.21.4 on Java 21.
2. Install the standalone PacketEvents 2.13.0 Spigot plugin.
3. Copy `Foggy-1.0.0.jar` to `plugins/`.
4. Start once, edit `plugins/Foggy/config.yml`, then run `/foggy reload` or restart.

PacketEvents is a hard dependency (`depend: [packetevents]`). Foggy is intentionally marked as
not Folia-compatible because its world raycasts and global pair matrix require a single Paper tick
thread.

## Build

```bash
./gradlew clean test jar
./gradlew loadTest
```

The distributable is `build/libs/Foggy-1.0.0.jar`. PacketEvents and Paper are `compileOnly` and are
not shaded into it.

## Configuration

All important tradeoffs are in [`config.yml`](src/main/resources/config.yml): radius, spatial cell,
fallback FOV, F5 distance/source margin, interpolation samples, transparent/cutout policy and hide
confirmation ticks.
`hide-confirmation-ticks: 1` is the no-delay default. Increasing it affects hiding only; the first
visible result always sends the spawn snapshot immediately.

Players with `foggy.bypass` always see managed targets. The permission is not granted by default.

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

“The same render frame” cannot be guaranteed by any server-only 20 TPS plugin. Foggy's precise
guarantee is: the decision and destroy/spawn packet are produced in the same **server tick** in
which Paper's end-of-tick state is observed. Network latency, client packet processing and render
frames remain outside the server's control.

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

## Reports and tests

- [`docs/stage-1-vanilla-analysis.md`](docs/stage-1-vanilla-analysis.md) — Mojang artifact hashes,
  exact 1.21.4 signatures and render/raycast formulas.
- [`docs/stage-2-packetevents.md`](docs/stage-2-packetevents.md) — PacketEvents API and complexity.
- [`docs/testing.md`](docs/testing.md) — automated/load/manual verification.

## License

Foggy's original source is MIT-licensed. Minecraft and PacketEvents remain under their respective
licenses and are not redistributed by this project.
