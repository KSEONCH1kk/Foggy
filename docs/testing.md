# Testing and validation

## Automated tests

`./gradlew test` covers:

- full wall occlusion;
- diagonal corner occlusion (the common corner leak regression);
- thin voxel shape interception;
- lower slab: low ray blocked, high ray clear;
- multi-box stair: both occupied sections block while the missing upper quarter stays open;
- thin ladder/sign planes and transparent-shape pass-through before a later opaque blocker;
- official 1.21.4 cutout catalog load/count and opaque-base exclusions;
- vertical-FOV tangent and aspect-ratio conversion;
- hide-only debounce, immediate show and immediate hard vanish/spectator hide;
- vanilla-invisibility disposition: preserve entity for equipment/hits, with hard-hide priority.

`./gradlew loadTest` runs a deterministic 200-player, 20-iteration synthetic pair/ray workload and
prints checks/second. Override population with `-Dfoggy.load.players=N`. It intentionally asserts
correct execution rather than a machine-specific wall-clock threshold.

## Manual two-client matrix

Use a clean Paper 1.21.4 server, PacketEvents 2.13.0 and two clients. Record with 60+ FPS capture
and enable PacketEvents debug/timestamps if packet arrival measurements are required.

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
   share the first Paper end-of-tick timestamp after visibility changes. There is no show debounce.
6. Potion invisibility: armor and both held items remain rendered, the invisible player remains
   attackable, and debug reports `mode=VANILLA_ENTITY`. Spectator/vanish must instead report
   `PACKET_HIDDEN` and keep directed visibility plus tab-list stability.
7. F5: repeat rear and front perspective with and without the companion; ensure fallback never
   hides a target visible from either plausible camera.
8. Equipment/pose/effects: hide while changing armor, hand item, crouch/swim pose, scale and potion
   effects, then show. The first rendered entity must already have the final state.
9. Tracking boundary/world change/death: cross the server tracking range, teleport dimensions,
   respawn and reconnect. Confirm no ghost entity and no stale hidden id.

## Runtime smoke result

The built thin JAR was started on Paper `1.21.4-232` with the official standalone PacketEvents
`2.13.0` release JAR and Java `21.0.11`. Both plugins completed load/enable, Paper reached `Done`,
and shutdown completed without Foggy or PacketEvents exceptions. This verifies plugin metadata,
dependency order, Paper remapping and runtime class linkage; it is not a substitute for the
two-client visual matrix above.

## Timing interpretation

Measure three separate times: authoritative state change on the server, Foggy's transition/send,
and packet receipt on the client. Foggy can make the first two occur in one server tick. The third
includes network scheduling; actual display additionally waits for a client render frame.
