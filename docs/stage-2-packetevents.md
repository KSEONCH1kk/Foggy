# Stage 2 — PacketEvents 2.13.0 analysis

The selected dependency is the latest stable release at implementation time: PacketEvents 2.13.0.
Primary references: [GitHub release](https://github.com/retrooper/packetevents/releases/tag/v2.13.0),
[official dependency guide](https://github.com/retrooper/packetevents/wiki/Depending-on-pre%E2%80%90built-PacketEvents),
and [PacketEvents documentation](https://docs.packetevents.com/).

## APIs used

Listener registration and per-viewer cancellation:

```java
PacketEvents.getAPI().getEventManager().registerListener(listener);

public final class FoggyPacketListener extends PacketListenerAbstract {
    public void onPacketSend(PacketSendEvent event) {
        event.setCancelled(true); // only event.getPlayer()
    }
}
```

Point-to-point writes use the current API's `PlayerManager`, not the old/nonexistent
`PacketEvents#getPlayerUtils` name:

```java
PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer, wrapper);
```

`sendPacketSilently` is essential: Foggy's synthetic destroy/spawn snapshot bypasses its own
outgoing listener, while regular Paper packets still pass through it.

Wrappers used to build transitions:

- `WrapperPlayServerDestroyEntities(int...)`;
- `WrapperPlayServerSpawnEntity(..., EntityTypes.PLAYER, ...)` (the player spawn form used by
  1.20.2+);
- `WrapperPlayServerEntityMetadata`, populated by
  `SpigotConversionUtil.getEntityMetadata(Bukkit Entity)`;
- `WrapperPlayServerEntityEquipment`, `WrapperPlayServerEntityEffect`,
  `WrapperPlayServerEntityHeadLook`, `WrapperPlayServerEntityVelocity`,
  `WrapperPlayServerUpdateAttributes`, and `WrapperPlayServerSetPassengers`.

The listener parses/cancels server spawn, relative move/rotation, teleport, metadata, equipment,
effect add/remove, attributes, velocity, animation, status, damage/hurt, entity sound and collect
packets for a hidden entity id. A normal server `DestroyEntities` updates the per-viewer tracking
snapshot rather than being cancelled.

## Why tab-list packets are not resent

Foggy destroys only the world entity. It does not remove `PlayerInfoUpdate`/tab state, so the
client still owns the target's profile and skin when `SPAWN_ENTITY` is resent. Re-adding the tab
entry would cause visible tab churn and is unnecessary on 1.21.4. If another plugin removes that
entry, interoperability must be handled with that plugin.

## State and race handling

Each viewer has concurrent sets of hidden, server-tracked and client-known entity ids. The engine
touches them on the Paper main thread; PacketEvents may read/update them on a Netty thread. A spawn
that races after a hidden decision is cancelled. A later visible decision clears the hidden bit
before silently sending the complete snapshot.

## Complexity and performance

A naive global pair matrix performs `n(n-1)` decisions and several rays per decision each tick.
Foggy rebuilds a uniform spatial hash in O(n) and queries only players within the configured
radius. If the average nearby population is `k`, pair work is O(n·k), with O(n·k) cached state.

The expensive ray phase has early exits:

1. hard invisibility (vanish/spectator/canSee) and bypass are checked first; ordinary vanilla
   invisibility retains the client entity for equipment rendering and interaction;
2. distance/cell rejection occurs before camera or target sampling;
3. FOV rejects a point before a world ray;
4. the first unblocked point returns visible;
5. no packets are built unless a pair transitions.

Default fallback can generate many conservative camera/target combinations, so crowded servers
should profile and tune radius, `camera.distance-samples`, source margin and interpolation samples.
The deterministic `./gradlew loadTest` measures the math/ray loop; a real Paper profiler is still
required because block-state lookup cost depends on the world and hardware.
