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
- official 1.21.4 cutout catalog load/count, cross-version material families and opaque-base
  exclusions;
- vertical-FOV tangent and aspect-ratio conversion;
- hide-only debounce, immediate show and immediate hard vanish/spectator hide;
- vanilla-invisibility disposition: preserve entity for equipment/hits, with hard-hide priority;
- concurrent snapshot-grid world/radius filtering, negative cells and moving-cell replacement;
- live-reload transfer of a previously hidden pair state.

`./gradlew loadTest` runs a deterministic 200-player, 20-iteration synthetic pair/ray workload and
prints checks/second. Override population with `-Dfoggy.load.players=N`. It intentionally asserts
correct execution rather than a machine-specific wall-clock threshold.

## Manual two-client matrix

Run the matrix on at least one old-protocol server (1.8.8), one modern server (1.20.6 or 1.21.x)
and current Folia, with PacketEvents 2.13.0 and two matching clients. Record with 60+ FPS capture
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
5. Reappearance: break the blocking block or step out. Confirm `SPAWN_PLAYER` before 1.20.2 or
   `SPAWN_ENTITY` on newer protocols, followed by the supported snapshot packets on the first
   owning-region pass. There is no show debounce.
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

## Runtime release result

The complete representative matrix is recorded in
[`version-support.md`](version-support.md#verified-runtime-matrix). Every server must complete
load/enable, accept `/foggy reload`, initialize its native shape bridge with `fallback=0`, and shut
down without Foggy, PacketEvents or ownership exceptions.

Two-client packet timing was measured on both ends of the packet gate. On Paper 1.8.8, spectator
produced directed `DestroyEntities` after 81 ms and survival produced `SpawnPlayer` after 95 ms.
On Paper 1.20.6, the corresponding samples were 46 ms and 96 ms with player `SpawnEntity`. These
include tick and local client scheduling and demonstrate that no extra show debounce is applied.

Repeated release runs of the 200-player sample performed 243,400 checks in 181–210 ms (about
1.16–1.34 million checks/second) on the test host. This synthetic figure is a regression signal,
not a TPS guarantee for arbitrary maps or hardware.

## Timing interpretation

Measure three separate times: authoritative state change on the owning region, Foggy's
transition/send, and packet receipt on the client. Same-region decisions can make the first two
occur in one region tick. Cross-region show requires target→viewer scheduler hand-off. Packet
receipt includes network scheduling; actual display additionally waits for a client render frame.
