# Folia compatibility

Foggy 2.0.1 supports current Paper and Folia 26.x while retaining classic Bukkit/Paper support and
declares `folia-supported: true` in `plugin.yml`. The flag only allows Folia to load the plugin;
compatibility comes from the capability bridge, scheduler and ownership rules below.

Primary references:

- [Paper: Supporting Paper and Folia](https://docs.papermc.io/paper/dev/folia-support/)
- [Folia README and scheduler rules](https://github.com/PaperMC/Folia)
- [Folia region overview](https://docs.papermc.io/folia/reference/overview/)
- [Paper API documentation](https://jd.papermc.io/paper/)

## Scheduler model

- On Folia, each online player owns one reflective
  `EntityScheduler.runAtFixedRate(..., 1, 1)` task. It follows the entity across region merges,
  splits, teleports and worlds. On classic Bukkit the same adapter uses one main-thread task.
- A task reads only its owned player's location, camera inputs, hitbox, effects, tracker set,
  permissions and vanish state.
- It publishes a defensive `PlayerVisibilitySnapshot` to concurrent per-world X/Z cells. Other
  region threads consume only copied coordinates, hitbox samples, UUIDs, ids and booleans.
- Those snapshots and cells are owned by `CompensatedEntities`; it never dereferences a foreign
  entity to answer a nearby query.
- World rays execute only after `Bukkit.isOwnedByCurrentRegion(world, minChunkX, minChunkZ,
  maxChunkX, maxChunkZ)` proves ownership of the whole corridor.
- One ownership check covers the complete camera/target envelope. `CompensatedWorld` then reads
  only loaded owned chunks and reuses immutable primitive VoxelShape geometry across rays.
- Synthetic show data is captured on the target's entity scheduler, then sent on the viewer's
  entity scheduler. Hides and same-region shows are sent directly from the viewer-owned task.
- `/foggy reload` validates and swaps global service state on `GlobalRegionScheduler`; a player's
  result message is switched back to that player's entity scheduler.
- Respawn replaces the entity-task registration with a generation token; a delayed retirement
  callback from the old entity cannot remove the new task. Final retirement and quit paths remove
  only concurrent state using captured UUID and entity id values.

`PlatformAdapter` resolves these capabilities at startup. It has no Folia type in a class or method
descriptor, so the same Java 8 classfiles can load on a 1.8 server that has never defined those
classes.

## Cross-region safety behavior

Folia has no single main thread and does not provide an atomic, server-wide end-of-tick player
matrix. A region must not read another region's entities or chunks. Foggy therefore uses these
explicit conservative rules:

1. A target snapshot older than one second is ignored.
2. `Player#canSee(target)` is read only when the current thread also owns the target. For a normally
   tracked nearby pair Folia generally co-locates both entities; explicit vanish APIs and spectator
   state are already stored in the target-owned snapshot.
3. If any camera→hitbox ray leaves the current region's owned chunk rectangle, optical evaluation
   returns `REGION_UNOWNED`. That result means visible, never hidden. It avoids both an illegal
   world read and a false assumption that foreign blocks occlude the target.
4. Cross-region reappearance is asynchronous by necessity: capture target, then schedule viewer.
   The hidden bit is cleared before the hand-off, so ordinary fresh tracker packets are allowed if
   they win the race.

These rules make Foggy thread-correct on regionized servers. They deliberately prefer a temporary
visible entity over unsafe access or a false hide at an independently ticking region boundary.
Use `/foggy debug status`; `optical=REGION_UNOWNED` identifies this exact fallback.

## Operator checklist

1. Use a current supported Folia 26.x build, its required Java 25 runtime and PacketEvents 2.13.0.
2. Never use Bukkit `/reload` or a plugin-manager hot unload. Use Foggy's own `/foggy reload` only
   for `config.yml`; replace JARs with a full server restart.
3. Profile `visibility.radius-blocks` on the real player distribution. A very large radius examines
   more immutable pairs but cannot grant ownership of foreign chunks.
4. Test same-region and split-region movement, spectator/vanish transitions, death, world change,
   reconnect and reload with at least two real clients.
