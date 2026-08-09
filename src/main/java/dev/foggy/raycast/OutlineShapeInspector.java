package dev.foggy.raycast;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/**
 * Reads the same NMS OUTLINE {@code VoxelShape} used by the production ray for debug rendering.
 *
 * <p>Paper does not expose OUTLINE sub-box enumeration in its public API. Reflection is isolated
 * here and never participates in the visibility decision. If a future Paper mapping changes, the
 * inspector falls back to public collision sub-boxes or the outline envelope while the production
 * ray remains exact through {@code World#rayTraceBlocks}.</p>
 */
final class OutlineShapeInspector {
    private volatile Accessors accessors;
    private volatile boolean nmsUnavailable;

    /**
     * Extracts world-space OUTLINE boxes for one debug hit.
     *
     * @param block hit block
     * @return exact NMS boxes or a public-API fallback
     */
    OutlineShapeSnapshot inspect(Block block) {
        if (!nmsUnavailable) {
            try {
                Accessors current = accessors;
                if (current == null) {
                    current = Accessors.discover(block);
                    accessors = current;
                }
                return new OutlineShapeSnapshot(current.read(block), true);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                nmsUnavailable = true;
            }
        }
        return fallback(block);
    }

    private static OutlineShapeSnapshot fallback(Block block) {
        Collection<BoundingBox> collision = block.getCollisionShape().getBoundingBoxes();
        if (!collision.isEmpty()) {
            List<BoundingBox> worldBoxes = collision.stream()
                    .map(box -> translate(box, block.getX(), block.getY(), block.getZ()))
                    .toList();
            return new OutlineShapeSnapshot(worldBoxes, false);
        }
        BoundingBox outlineEnvelope = block.getBoundingBox();
        if (outlineEnvelope.getVolume() <= 0.0) {
            return new OutlineShapeSnapshot(List.of(), false);
        }
        return new OutlineShapeSnapshot(List.of(outlineEnvelope), false);
    }

    private static BoundingBox translate(BoundingBox box, double x, double y, double z) {
        return new BoundingBox(
                box.getMinX() + x, box.getMinY() + y, box.getMinZ() + z,
                box.getMaxX() + x, box.getMaxY() + y, box.getMaxZ() + z);
    }

    private record Accessors(
            Method getNms,
            Method getHandle,
            Method getPosition,
            Method getShape,
            Method toAabbs,
            Field minX,
            Field minY,
            Field minZ,
            Field maxX,
            Field maxY,
            Field maxZ
    ) {
        private static Accessors discover(Block block) throws ReflectiveOperationException {
            Class<?> craftBlockClass = block.getClass();
            Method getNms = craftBlockClass.getMethod("getNMS");
            Method getHandle = craftBlockClass.getMethod("getHandle");
            Method getPosition = craftBlockClass.getMethod("getPosition");
            Object state = getNms.invoke(block);
            Object level = getHandle.invoke(block);
            Object position = getPosition.invoke(block);
            Method getShape = findShapeMethod(state.getClass(), level, position);
            Object shape = getShape.invoke(state, level, position);
            Method toAabbs = shape.getClass().getMethod("toAabbs");
            List<?> boxes = castList(toAabbs.invoke(shape));
            Class<?> aabbClass = boxes.isEmpty()
                    ? Class.forName("net.minecraft.world.phys.AABB")
                    : boxes.getFirst().getClass();
            return new Accessors(
                    getNms, getHandle, getPosition, getShape, toAabbs,
                    aabbClass.getField("minX"), aabbClass.getField("minY"), aabbClass.getField("minZ"),
                    aabbClass.getField("maxX"), aabbClass.getField("maxY"), aabbClass.getField("maxZ"));
        }

        private List<BoundingBox> read(Block block)
                throws InvocationTargetException, IllegalAccessException {
            Object state = getNms.invoke(block);
            Object level = getHandle.invoke(block);
            Object position = getPosition.invoke(block);
            Object shape = getShape.invoke(state, level, position);
            List<?> nmsBoxes = castList(toAabbs.invoke(shape));
            List<BoundingBox> result = new ArrayList<>(nmsBoxes.size());
            for (Object box : nmsBoxes) {
                result.add(new BoundingBox(
                        number(minX, box) + block.getX(),
                        number(minY, box) + block.getY(),
                        number(minZ, box) + block.getZ(),
                        number(maxX, box) + block.getX(),
                        number(maxY, box) + block.getY(),
                        number(maxZ, box) + block.getZ()));
            }
            return result;
        }

        private static Method findShapeMethod(Class<?> stateClass, Object level, Object position)
                throws NoSuchMethodException {
            for (Method method : stateClass.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals("getShape") && parameters.length == 2
                        && parameters[0].isInstance(level) && parameters[1].isInstance(position)) {
                    return method;
                }
            }
            throw new NoSuchMethodException(stateClass.getName() + "#getShape(BlockGetter, BlockPos)");
        }

        private static List<?> castList(Object value) {
            if (value instanceof List<?> list) {
                return list;
            }
            throw new IllegalStateException("VoxelShape.toAabbs() did not return List");
        }

        private static double number(Field field, Object target) throws IllegalAccessException {
            return ((Number) field.get(target)).doubleValue();
        }
    }
}
