package dev.foggy.raycast;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/** Compact immutable list of the AABBs making up one exact NMS {@code VoxelShape}. */
final class CompensatedShape {
    static final CompensatedShape EMPTY = new CompensatedShape(new double[0]);
    private static final double CLIP_EPSILON = 1.0E-7;
    private final double[] boxes;

    CompensatedShape(double[] boxes) {
        this.boxes = boxes;
    }

    /** Allocation-free equivalent of {@code clip(...) != null} for the production hot path. */
    boolean intersects(double fromX, double fromY, double fromZ,
                       double toX, double toY, double toZ,
                       int blockX, int blockY, int blockZ) {
        double directionX = toX - fromX;
        double directionY = toY - fromY;
        double directionZ = toZ - fromZ;
        if (directionX * directionX + directionY * directionY + directionZ * directionZ < CLIP_EPSILON) {
            return false;
        }
        double insideX = fromX + directionX * 0.001;
        double insideY = fromY + directionY * 0.001;
        double insideZ = fromZ + directionZ * 0.001;
        boolean singleBox = boxes.length == 6;
        for (int offset = 0; offset < boxes.length; offset += 6) {
            double minX = boxes[offset] + blockX;
            double minY = boxes[offset + 1] + blockY;
            double minZ = boxes[offset + 2] + blockZ;
            double maxX = boxes[offset + 3] + blockX;
            double maxY = boxes[offset + 4] + blockY;
            double maxZ = boxes[offset + 5] + blockZ;
            if ((singleBox ? insideX >= minX : insideX > minX) && insideX < maxX
                    && (singleBox ? insideY >= minY : insideY > minY) && insideY < maxY
                    && (singleBox ? insideZ >= minZ : insideZ > minZ) && insideZ < maxZ) {
                return true;
            }
            if (directionX > CLIP_EPSILON || directionX < -CLIP_EPSILON) {
                double distance = ((directionX > 0.0 ? minX : maxX) - fromX) / directionX;
                double y = fromY + distance * directionY;
                double z = fromZ + distance * directionZ;
                if (distance > 0.0 && distance < 1.0
                        && y > minY - CLIP_EPSILON && y < maxY + CLIP_EPSILON
                        && z > minZ - CLIP_EPSILON && z < maxZ + CLIP_EPSILON) {
                    return true;
                }
            }
            if (directionY > CLIP_EPSILON || directionY < -CLIP_EPSILON) {
                double distance = ((directionY > 0.0 ? minY : maxY) - fromY) / directionY;
                double z = fromZ + distance * directionZ;
                double x = fromX + distance * directionX;
                if (distance > 0.0 && distance < 1.0
                        && z > minZ - CLIP_EPSILON && z < maxZ + CLIP_EPSILON
                        && x > minX - CLIP_EPSILON && x < maxX + CLIP_EPSILON) {
                    return true;
                }
            }
            if (directionZ > CLIP_EPSILON || directionZ < -CLIP_EPSILON) {
                double distance = ((directionZ > 0.0 ? minZ : maxZ) - fromZ) / directionZ;
                double x = fromX + distance * directionX;
                double y = fromY + distance * directionY;
                if (distance > 0.0 && distance < 1.0
                        && x > minX - CLIP_EPSILON && x < maxX + CLIP_EPSILON
                        && y > minY - CLIP_EPSILON && y < maxY + CLIP_EPSILON) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns the nearest segment hit against all local sub-boxes, or null. */
    ShapeHit clip(double fromX, double fromY, double fromZ,
                  double toX, double toY, double toZ,
                  int blockX, int blockY, int blockZ) {
        double directionX = toX - fromX;
        double directionY = toY - fromY;
        double directionZ = toZ - fromZ;
        if (directionX * directionX + directionY * directionY + directionZ * directionZ < CLIP_EPSILON) {
            return null;
        }
        double insideX = fromX + directionX * 0.001;
        double insideY = fromY + directionY * 0.001;
        double insideZ = fromZ + directionZ * 0.001;
        boolean singleBox = boxes.length == 6;
        for (int offset = 0; offset < boxes.length; offset += 6) {
            double minX = boxes[offset] + blockX;
            double minY = boxes[offset + 1] + blockY;
            double minZ = boxes[offset + 2] + blockZ;
            if ((singleBox ? insideX >= minX : insideX > minX) && insideX < boxes[offset + 3] + blockX
                    && (singleBox ? insideY >= minY : insideY > minY) && insideY < boxes[offset + 4] + blockY
                    && (singleBox ? insideZ >= minZ : insideZ > minZ) && insideZ < boxes[offset + 5] + blockZ) {
                return new ShapeHit(0.001, new Vector(insideX, insideY, insideZ),
                        insideFace(directionX, directionY, directionZ));
            }
        }
        // Mirrors AABB.clip(Iterable<AABB>, Vec3, Vec3, BlockPos) and clipPoint exactly:
        // only entering faces are considered, t is strictly inside (0, currentNearest), and
        // the two coordinates on the face receive Mojang's +/-1e-7 tolerance.
        double nearest = 1.0;
        BlockFace nearestFace = null;
        for (int offset = 0; offset < boxes.length; offset += 6) {
            double minX = boxes[offset] + blockX;
            double minY = boxes[offset + 1] + blockY;
            double minZ = boxes[offset + 2] + blockZ;
            double maxX = boxes[offset + 3] + blockX;
            double maxY = boxes[offset + 4] + blockY;
            double maxZ = boxes[offset + 5] + blockZ;

            if (directionX > CLIP_EPSILON || directionX < -CLIP_EPSILON) {
                double plane = directionX > 0.0 ? minX : maxX;
                double distance = (plane - fromX) / directionX;
                double y = fromY + distance * directionY;
                double z = fromZ + distance * directionZ;
                if (distance > 0.0 && distance < nearest
                        && y > minY - CLIP_EPSILON && y < maxY + CLIP_EPSILON
                        && z > minZ - CLIP_EPSILON && z < maxZ + CLIP_EPSILON) {
                    nearest = distance;
                    nearestFace = directionX > 0.0 ? BlockFace.WEST : BlockFace.EAST;
                }
            }
            if (directionY > CLIP_EPSILON || directionY < -CLIP_EPSILON) {
                double plane = directionY > 0.0 ? minY : maxY;
                double distance = (plane - fromY) / directionY;
                double z = fromZ + distance * directionZ;
                double x = fromX + distance * directionX;
                if (distance > 0.0 && distance < nearest
                        && z > minZ - CLIP_EPSILON && z < maxZ + CLIP_EPSILON
                        && x > minX - CLIP_EPSILON && x < maxX + CLIP_EPSILON) {
                    nearest = distance;
                    nearestFace = directionY > 0.0 ? BlockFace.DOWN : BlockFace.UP;
                }
            }
            if (directionZ > CLIP_EPSILON || directionZ < -CLIP_EPSILON) {
                double plane = directionZ > 0.0 ? minZ : maxZ;
                double distance = (plane - fromZ) / directionZ;
                double x = fromX + distance * directionX;
                double y = fromY + distance * directionY;
                if (distance > 0.0 && distance < nearest
                        && x > minX - CLIP_EPSILON && x < maxX + CLIP_EPSILON
                        && y > minY - CLIP_EPSILON && y < maxY + CLIP_EPSILON) {
                    nearest = distance;
                    nearestFace = directionZ > 0.0 ? BlockFace.NORTH : BlockFace.SOUTH;
                }
            }
        }
        if (nearestFace == null) {
            return null;
        }
        return new ShapeHit(
                nearest,
                new Vector(fromX + directionX * nearest,
                        fromY + directionY * nearest,
                        fromZ + directionZ * nearest),
                nearestFace);
    }

    int boxCount() {
        return boxes.length / 6;
    }

    double[] copyBoxes() {
        return boxes.clone();
    }

    /** Mirrors Direction#getApproximateNearest(delta).getOpposite(), including enum tie order. */
    private static BlockFace insideFace(double x, double y, double z) {
        float dx = (float) x;
        float dy = (float) y;
        float dz = (float) z;
        float best = Float.MIN_VALUE;
        BlockFace direction = BlockFace.NORTH;
        if (-dy > best) {
            best = -dy;
            direction = BlockFace.DOWN;
        }
        if (dy > best) {
            best = dy;
            direction = BlockFace.UP;
        }
        if (-dz > best) {
            best = -dz;
            direction = BlockFace.NORTH;
        }
        if (dz > best) {
            best = dz;
            direction = BlockFace.SOUTH;
        }
        if (-dx > best) {
            best = -dx;
            direction = BlockFace.WEST;
        }
        if (dx > best) {
            direction = BlockFace.EAST;
        }
        return direction.getOppositeFace();
    }

    /** Exact segment hit generated by the compensated shape. */
    record ShapeHit(double distanceFraction, Vector position, BlockFace face) {
    }
}
