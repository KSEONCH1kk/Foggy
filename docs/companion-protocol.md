# Optional companion camera protocol

Channel: `foggy:camera`. Transport: standard Minecraft/Bukkit plugin message from the player's
connection. Byte order is big-endian (Java `ByteBuffer` default). Protocol v1 payload is exactly
30 bytes:

| offset | type | meaning |
|---:|---|---|
| 0 | `u8` | protocol version, currently `1` |
| 1 | `u8` | perspective: 0 first person, 1 third-person back, 2 third-person front |
| 2 | `i64` | monotonically increasing unsigned sequence |
| 10 | `f32` | current vertical FOV in degrees, after client modifiers/zoom |
| 14 | `f32` | framebuffer width / height |
| 18 | `f32` | camera offset on the view-local right axis, blocks |
| 22 | `f32` | camera offset on the view-local up axis, blocks |
| 26 | `f32` | camera offset on the view-local forward axis, blocks |

The client should publish after camera setup whenever values change and at least once per second.
Foggy rejects non-finite/range-invalid values, old sequences and samples older than
`sample-ttl-millis`; it then falls back to the conservative camera union.

The server uses its authoritative eye position and look direction. The transmitted offset is
relative, so a companion cannot directly choose an arbitrary world position; it is also bounded by
`max-camera-offset-blocks`.

This is not cryptographic attestation. A modified client can lie about its FOV/perspective/offset.
For anti-cheat use, distribute an attested companion or disable telemetry and accept the more
permissive fallback.
