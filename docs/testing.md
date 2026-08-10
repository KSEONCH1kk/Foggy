# Testing and validation

## Automated tests

`./gradlew test` covers:

- full wall occlusion;
- diagonal corner occlusion (the common corner leak regression);
- thin voxel shape interception;
- lower slab: low ray blocked, high ray clear;
- multi-box stair: both occupied sections block while the missing upper quarter stays open;
- thin ladder/sign planes and transparent-shape pass-through before a later opaque blocker;
- Mojang `VoxelShape#clip` entering-face, `1e-7`, inside-hit and endpoint semantics;
- 20,000 deterministic rays proving the allocation-free boolean hot path equals detailed clip;
- Mojang `BlockGetter#traverseBlocks` diagonal tie order, negative floor and zero-length behavior;
- official 1.21.4 cutout catalog load/count and opaque-base exclusions;
- vertical-FOV tangent and aspect-ratio conversion;
- hide-only debounce, immediate show and immediate hard vanish/spectator hide;
- vanilla-invisibility disposition: preserve entity for equipment/hits, with hard-hide priority;
- concurrent snapshot-grid world/radius filtering, negative cells and moving-cell replacement;
- live-reload transfer of a previously hidden pair state.

`./gradlew loadTest` runs a deterministic 200-player, 20-iteration synthetic pair/ray workload and
prints checks/second. Override population with `-Dfoggy.load.players=N`. It intentionally asserts
correct execution rather than a machine-specific wall-clock threshold.

## Manual two-client matrix

Run the matrix once on clean Paper 1.21.4 and once on clean Folia 1.21.4, with PacketEvents 2.13.0
and two clients. Record with 60+ FPS capture and enable PacketEvents debug/timestamps if packet
arrival measurements are required.

1. Full wall: target walks behind a two-block-high wall. Confirm one destroy transition and no
   entity movement/metadata packets for that id while hidden.
2. Running corner: target sprints diagonally behind a corner. Review frame-by-frame for a diagonal
   shoulder leak; repeat at low and high ping.
3. Slab/stair/pane/sign/ladder: rotate and change each block state. Verify `/foggy debug status`
   reports the exact `state`, that rays remain visible through genuinely open portions, and that
   every occupied OUTLINE section participates.
4. Transparency/cutout: place glass, a closed door, fence, gate, iron bars, spawner and leaves before
   a solid wall. Default config must report the first shape as `PASS=... TRANSPARENT_PASS` or
   `CUTOUT_PASS` and the wall as `BLOCK`. Add a material to `opaque-material-overrides`, restart,
   and verify it becomes the blocker.
5. Reappearance: break the blocking block or step out. Confirm `SPAWN_ENTITY` and snapshot packets
   are emitted on the first owning-region pass after visibility changes. There is no show debounce.
6. Potion invisibility: armor and both held items remain rendered, the invisible player remains
   attackable, and debug reports `mode=VANILLA_ENTITY`. Spectator/vanish must instead report
   `PACKET_HIDDEN` and keep directed visibility plus tab-list stability.
7. F5: repeat rear and front perspective with and without the companion; ensure fallback never
   hides a target visible from either plausible camera.
8. Equipment/pose/effects: hide while changing armor, hand item, crouch/swim pose, scale and potion
   effects, then show. The first rendered entity must already have the final state.
9. Tracking boundary/world change/death: cross the server tracking range, teleport dimensions,
   respawn and reconnect. Confirm no ghost entity and no stale hidden id.
10. Folia split: move players far enough apart to occupy independent regions, use spectator and
    survival transitions, then `/foggy reload`. Confirm no thread-ownership exception and that
    `REGION_UNOWNED` is visible/conservative rather than an occlusion decision.

## Runtime smoke result

The built thin JAR is smoke-tested on Paper `1.21.4-232` and Folia `1.21.4-6`, with the official
standalone PacketEvents `2.13.0` release JAR and Java 21. Both servers must complete load/enable,
reach `Done`, accept `/foggy reload`, and shut down without Foggy, PacketEvents or region-thread
exceptions. This verifies metadata, dependency order, remapping, scheduler linkage and basic
runtime behavior; it is not a substitute for the two-client visual matrix above.

The Folia smoke additionally connected two protocol-769 clients. It initializes the compensated
NMS shape bridge, places/removes a solid wall and observes directed destroy followed by the complete
spawn snapshot without invoking the guarded Bukkit fallback. It also observed ordinary potion
invisibility preserve the target entity/equipment/effect, spectator emit a directed destroy,
survival emit the complete spawn snapshot, reload preserve an already hidden id, and death/respawn
rebind the entity task. The final log contained no ownership, tick-thread or plugin exception.

## Timing interpretation

Measure three separate times: authoritative state change on the owning region, Foggy's
transition/send, and packet receipt on the client. Same-region decisions can make the first two
occur in one region tick. Cross-region show requires target→viewer scheduler hand-off. Packet
receipt includes network scheduling; actual display additionally waits for a client render frame.
