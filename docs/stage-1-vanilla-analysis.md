# Stage 1 — Minecraft 1.21.4 analysis

## Reproducible inputs

Target release: Java Edition 1.21.4 (release time 2024-12-03), Java 21.

The Mojang version manifest resolved the following official artifacts. SHA-1 and size are from the
version JSON and were rechecked locally:

| artifact | SHA-1 | bytes |
|---|---:|---:|
| `client.jar` | `a7e5a6024bfd3cd614625aa05629adf760020304` | 28,335,587 |
| `client_mappings` | `0cf2a0b7f056da1a5a5dd99fc6dc752f33987150` | 10,323,161 |
| `server.jar` | `4707d00eb834b446575d89a61a11b5d548d8c001` | 56,880,250 |
| `server_mappings` | `0b1e60cc509cfb0172573ae56b436c29febbc187` | 7,753,825 |

Primary inputs: [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json),
[1.21.4 version JSON](https://piston-meta.mojang.com/v1/packages/aafe0d11f3524b0623cc13814256d9cbd3ba2d9a/1.21.4.json),
[client mappings](https://piston-data.mojang.com/v1/objects/0cf2a0b7f056da1a5a5dd99fc6dc752f33987150/client.txt),
and [server mappings](https://piston-data.mojang.com/v1/objects/0b1e60cc509cfb0172573ae56b436c29febbc187/server.txt).

The signatures below are exact official Mojang names from those mappings. The implementation was
cross-checked against decompiled bytecode. Full Mojang method bodies are deliberately not copied
into the repository; the executable formulas and control flow relevant to Foggy are reproduced
exactly below.

## Camera and F5

Relevant official signatures:

```text
void Camera.setup(BlockGetter level, Entity entity, boolean detached,
                  boolean mirror, float partialTick)
void Camera.tick()
float Camera.getMaxZoom(float requestedDistance)
void Camera.move(float forward, float up, float left)
void Camera.setRotation(float yRot, float xRot)
void Camera.setPosition(double x, double y, double z)
Vec3 Camera.getPosition()
Camera.NearPlane Camera.getNearPlane()
```

`Camera.setup` first interpolates the focused entity and eye height:

```text
cameraPos = (
  lerp(partialTick, entity.xo, entity.x),
  lerp(partialTick, entity.yo, entity.y) + lerp(partialTick, eyeHeightOld, eyeHeight),
  lerp(partialTick, entity.zo, entity.z)
)
```

For detached/third person, mirror/front view rotates yaw by 180° and negates pitch. Camera distance
is then `getMaxZoom(4.0F * livingEntity.getScale())`; the camera moves backward by that clipped
distance. This is why Foggy's fallback default is four blocks and why scale matters.

`getMaxZoom` traces eight rays. Each source is camera position plus every sign combination of
`(±0.1, ±0.1, ±0.1)` and each destination is source minus `forwards * requestedDistance`. It uses:

```text
new ClipContext(source, destination, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, focusedEntity)
```

The shortest hit distance replaces the requested distance. Foggy approximates the eight-ray
camera volume with a configurable source margin and clips fallback positions through Bukkit's
voxel-shape ray API.

## FOV, projection and NDC

Relevant exact signatures/fields:

```text
OptionInstance Options.fov
OptionInstance Options.fovEffectScale
CameraType Options.getCameraType()
float AbstractClientPlayer.getFieldOfViewModifier(boolean firstPerson, float effectScale)
void GameRenderer.tickFov()
float GameRenderer.getFov(Camera camera, float partialTick, boolean useSetting)
Matrix4f GameRenderer.getProjectionMatrix(float fovDegrees)
void GameRenderer.renderLevel(DeltaTracker deltaTracker)
```

`tickFov` obtains the player's modifier, then smooths and clamps it:

```text
oldModifier = modifier
modifier += (targetModifier - modifier) * 0.5
modifier = clamp(modifier, 0.1, 1.5)
```

The normal world FOV begins with the configured option (70° is the default) and is multiplied by
`lerp(partialTick, oldModifier, modifier)`. Death narrows it. Water/lava multiplies it by
`lerp(fovEffectScale, 1.0, 0.85714287)`.

`getProjectionMatrix` constructs JOML perspective projection with:

```text
fovRadians = fovDegrees * PI / 180
aspect = framebufferWidth / framebufferHeight
near = 0.05
far = GameRenderer.getDepthFar()
P = perspective(fovRadians, aspect, near, far)
```

Given view-space point `(xv, yv, zv)`, a homogeneous clip point is `clip = P * V * world` and
`ndc = clip.xyz / clip.w`. For a forward-positive server test, the equivalent side-plane checks
used by Foggy are:

```text
z = dot(delta, forward), z > 0
abs(dot(delta, up) / z)    <= tan(verticalFov / 2)
abs(dot(delta, right) / z) <= tan(verticalFov / 2) * aspect
```

The camera's near-plane helper uses the same tangent: vertical half-extent is
`tan(optionFov * PI/180 / 2) * 0.05`; horizontal half-extent is that value times aspect.

## View matrix, hurt/bob transforms and frustum

Relevant exact signatures:

```text
void GameRenderer.bobHurt(PoseStack poseStack, float partialTick)
void GameRenderer.bobView(PoseStack poseStack, float partialTick)
Frustum.Frustum(Matrix4f view, Matrix4f projection)
void Frustum.calculateFrustum(Matrix4f view, Matrix4f projection)
boolean Frustum.isVisible(AABB box)
```

Camera rotation is a Y-X-Z quaternion with angles `(PI - yawRadians, -pitchRadians, 0)`. Rendering
uses its conjugate for the view rotation. Projection is multiplied by hurt/bob transforms; the
frustum stores `projection * view` and delegates AABB plane classification to JOML
`FrustumIntersection`.

Vanilla bob terms, where `walk = -(walkDistance + deltaWalk * partialTick)` and stride is lerped:

```text
translate(sin(walk*PI)*stride*0.5, -abs(cos(walk*PI)*stride), 0)
rotateZ(sin(walk*PI)*stride*3 degrees)
rotateX(abs(cos(walk*PI - 0.2)*stride)*5 degrees)
```

Foggy does not attempt to reproduce hurt, nausea, shader or bob rotation from server data. Its
fallback FOV/source margin is conservative enough that these small transforms cannot create a
false hide under default settings; a companion can provide a more exact camera.

## World clip and voxel shapes

Exact official signatures:

```text
BlockHitResult BlockGetter.clip(ClipContext context)
BlockHitResult BlockGetter.clipWithInteractionOverride(
    Vec3 from, Vec3 to, BlockPos pos, VoxelShape shape, BlockState state)
T BlockGetter.traverseBlocks(Vec3 from, Vec3 to, T context,
    BiFunction<T, BlockPos, T> visitor, Function<T, T> missFactory)
VoxelShape ClipContext.getBlockShape(BlockState state, BlockGetter level, BlockPos pos)
VoxelShape ClipContext.getFluidShape(FluidState state, BlockGetter level, BlockPos pos)
BlockHitResult VoxelShape.clip(Vec3 from, Vec3 to, BlockPos pos)
```

`BlockGetter.clip` traverses grid cells along the segment, gets the chosen block and fluid voxel
shapes, clips both, and returns the nearer hit. Foggy requests no fluid collisions and samples
multiple target hitbox points. Paper's `ignorePassableBlocks=false` mapping to
`ClipContext.Block.OUTLINE` was the reference implementation. Foggy 2.0.1 mirrors the decompiled
`BlockGetter#traverseBlocks` DDA (including its `-1e-7` boundary lerp and axis tie order) and
`VoxelShape#clip` (including the `from + delta*0.001` inside test) directly. It extracts every
OUTLINE/COLLIDER AABB from the live state once into `CompensatedWorld`; hot rays do not allocate an
NMS `ClipContext` or call `CraftWorld#rayTraceBlocks`. Optical transparency remains a separate
post-state policy. Mojang's `VISUAL` selector is still used by the client F5 camera clip and is not
exposed by Bukkit; Foggy's fallback camera clip remains the conservative COLLIDER approximation.

The 1.21.4 client class `ItemBlockRenderTypes` maps blocks to `CUTOUT_MIPPED`, `CUTOUT`,
`TRANSLUCENT` and `TRIPWIRE`. These render layers are client model/texture properties and are not
encoded by the server `VoxelShape`. Foggy therefore applies its bundled client-derived catalog as
a second filter after resolving the exact server shape. `GRASS_BLOCK` and `CACTUS` are excluded
because use of a cutout-capable layer alone does not imply an actual see-through opening. Fence and
fence-gate model gaps are added explicitly even though their vanilla layer is `SOLID`.

## Tick interpolation boundary

The client renders entity coordinates interpolated from old to current state with its current
partial tick. The server neither receives nor controls that value. Foggy stores two end-of-tick
positions and samples `lerp(t, previous, current)` for configurable `t` values (default 0, 0.5, 1),
then translates the current Paper bounding box to each position. Visibility is the union of all
samples, preferring a temporary reveal over a false occlusion caused by tick-phase mismatch.
