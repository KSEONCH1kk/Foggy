package dev.foggy.raycast;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.block.Block;

/**
 * Isolated runtime bridge to the exact Mojang-mapped OUTLINE and COLLIDER voxel shapes.
 * Reflection is paid only while a compensated cell/state is cold; hot rays use primitive boxes.
 */
final class NmsShapeAccess {
    private volatile Accessors accessors;

    Object state(Block block) throws ReflectiveOperationException {
        Accessors current = accessors(block);
        try {
            return current.getNms.invokeExact((Object) block);
        } catch (Throwable throwable) {
            throw reflective("CraftBlock#getNMS", throwable);
        }
    }

    boolean dynamic(Object state) throws ReflectiveOperationException {
        Accessors current = accessors;
        try {
            Object block = current.getBlock.invokeExact(state);
            return (boolean) current.hasDynamicShape.invokeExact(block);
        } catch (Throwable throwable) {
            throw reflective("Block#hasDynamicShape", throwable);
        }
    }

    Geometry geometry(Block block, Object state) throws ReflectiveOperationException {
        Accessors current = accessors(block);
        try {
            Object level = current.getHandle.invokeExact((Object) block);
            Object position = current.getPosition.invokeExact((Object) block);
            Object outline = current.getShape.invoke(state, level, position);
            Object collision = current.getCollisionShape.invoke(state, level, position);
            return new Geometry(current.shape(outline), current.shape(collision));
        } catch (Throwable throwable) {
            throw reflective("BlockState voxel-shape extraction", throwable);
        }
    }

    private Accessors accessors(Block block) throws ReflectiveOperationException {
        Accessors current = accessors;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = accessors;
            if (current == null) {
                current = Accessors.discover(block);
                accessors = current;
            }
            return current;
        }
    }

    private static ReflectiveOperationException reflective(String operation, Throwable throwable) {
        if (throwable instanceof ReflectiveOperationException exception) {
            return exception;
        }
        return new ReflectiveOperationException(operation + " failed", throwable);
    }

    record Geometry(CompensatedShape outline, CompensatedShape collision) {
    }

    private record Accessors(
            MethodHandle getNms,
            MethodHandle getHandle,
            MethodHandle getPosition,
            MethodHandle getBlock,
            MethodHandle hasDynamicShape,
            Method getShape,
            Method getCollisionShape,
            Method toAabbs,
            Field minX,
            Field minY,
            Field minZ,
            Field maxX,
            Field maxY,
            Field maxZ
    ) {
        private static Accessors discover(Block block) throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> craftBlock = block.getClass();
            Method nmsMethod = craftBlock.getMethod("getNMS");
            Method handleMethod = craftBlock.getMethod("getHandle");
            Method positionMethod = craftBlock.getMethod("getPosition");
            Object state = nmsMethod.invoke(block);
            Object level = handleMethod.invoke(block);
            Object position = positionMethod.invoke(block);
            Method getBlockMethod = state.getClass().getMethod("getBlock");
            Object nmsBlock = getBlockMethod.invoke(state);
            Method dynamicMethod = nmsBlock.getClass().getMethod("hasDynamicShape");
            Method outlineMethod = findShapeMethod(state.getClass(), "getShape", level, position);
            Method collisionMethod = findShapeMethod(state.getClass(), "getCollisionShape", level, position);
            Object shape = outlineMethod.invoke(state, level, position);
            Method toAabbs = shape.getClass().getMethod("toAabbs");
            Class<?> aabb = Class.forName("net.minecraft.world.phys.AABB");
            return new Accessors(
                    lookup.unreflect(nmsMethod).asType(MethodType.methodType(Object.class, Object.class)),
                    lookup.unreflect(handleMethod).asType(MethodType.methodType(Object.class, Object.class)),
                    lookup.unreflect(positionMethod).asType(MethodType.methodType(Object.class, Object.class)),
                    lookup.unreflect(getBlockMethod).asType(MethodType.methodType(Object.class, Object.class)),
                    lookup.unreflect(dynamicMethod).asType(MethodType.methodType(boolean.class, Object.class)),
                    outlineMethod, collisionMethod, toAabbs,
                    aabb.getField("minX"), aabb.getField("minY"), aabb.getField("minZ"),
                    aabb.getField("maxX"), aabb.getField("maxY"), aabb.getField("maxZ"));
        }

        private CompensatedShape shape(Object nmsShape) throws ReflectiveOperationException {
            Object raw = toAabbs.invoke(nmsShape);
            if (!(raw instanceof List<?> boxes) || boxes.isEmpty()) {
                return CompensatedShape.EMPTY;
            }
            double[] coordinates = new double[boxes.size() * 6];
            int offset = 0;
            for (Object box : boxes) {
                coordinates[offset++] = number(minX, box);
                coordinates[offset++] = number(minY, box);
                coordinates[offset++] = number(minZ, box);
                coordinates[offset++] = number(maxX, box);
                coordinates[offset++] = number(maxY, box);
                coordinates[offset++] = number(maxZ, box);
            }
            return new CompensatedShape(coordinates);
        }

        private static Method findShapeMethod(Class<?> stateClass, String name, Object level, Object position)
                throws NoSuchMethodException {
            for (Method method : stateClass.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals(name) && parameters.length == 2
                        && parameters[0].isInstance(level) && parameters[1].isInstance(position)) {
                    return method;
                }
            }
            throw new NoSuchMethodException(stateClass.getName() + "#" + name + "(BlockGetter, BlockPos)");
        }

        private static double number(Field field, Object target) throws IllegalAccessException {
            return ((Number) field.get(target)).doubleValue();
        }
    }
}
