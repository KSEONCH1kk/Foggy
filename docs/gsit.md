# GSit compatibility

Foggy treats GSit as an optional renderer integration. `plugin.yml` declares `softdepend: [GSit]`
so GSit is enabled first when installed, while the same Foggy JAR continues to load without it.
There is no compile-time GSit dependency.

## Covered actions

- `/sit` and block seats;
- player-on-player sitting and multi-entity passenger stacks;
- `/lay` and `/layback`;
- `/bellyflop` and `/spin`;
- entering or leaving a GSit pose while the authoritative player is already hidden;
- pose removal, disconnect and Foggy shutdown/reload cleanup.

Ordinary sitting uses a server entity as the vehicle. Foggy observes legacy living-entity and
modern generic-entity spawns, removes hidden player ids from `SetPassengers`, and sends the current
complete passenger list immediately after restoring the player.

GSit poses are different: GSit makes the real player invisible and sends a separate fake-player
NPC directly to each nearby viewer. Foggy listens to GSit's documented `PlayerPoseEvent` and
`PlayerStopPoseEvent`, associates that renderer id with the real player, suppresses renderer
updates while hidden, and calls GSit's existing per-viewer renderer add/remove path during a
visibility transition. This also preserves GSit's temporary bed-block packet and profile cleanup.

The pose renderer methods are not part of GSit's public API, so their discovery is reflective and
capability-checked. If a future GSit release changes them, Foggy logs one clear compatibility
warning and keeps generic seat/passenger support active instead of preventing server startup.

Runtime verification used GSit 3.5.1 on Paper 1.20.6 with two protocol-matching clients. Foggy
removed both real and fake entity ids behind a wall, then restored the real player, mount relation
and fake pose NPC in the first visible tick. GSit's source and public API documentation are at
[Gecolay/GSit](https://github.com/Gecolay/GSit).
