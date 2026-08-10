package dev.foggy.raycast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Runtime-selected bridge for exact state-dependent block geometry.
 *
 * <p>1.13+ exposes NMS {@code VoxelShape}s; their primitive AABBs are cached directly. Minecraft
 * 1.8 predates VoxelShape, so its native six-argument collision-box collector is invoked once per
 * cold/dynamic cell. That collector is the vanilla implementation used by stairs, slabs, doors,
 * fences, gates, piston heads and every other non-full block in that release.</p>
 */
final class NmsShapeAccess {
    private volatile Bridge bridge;

    Object state(Block block) throws ReflectiveOperationException {
        return bridge(block).state(block);
    }

    boolean dynamic(Object state) throws ReflectiveOperationException {
        return bridge.dynamic(state);
    }

    Geometry geometry(Block block, Object state) throws ReflectiveOperationException {
        return bridge(block).geometry(block, state);
    }

    private Bridge bridge(Block block) throws ReflectiveOperationException {
        Bridge current = bridge;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = bridge;
            if (current == null) {
                ReflectiveOperationException modernFailure;
                try {
                    current = ModernBridge.discover(block);
                } catch (ReflectiveOperationException exception) {
                    modernFailure = exception;
                    try {
                        current = LegacyBridge.discover(block);
                    } catch (ReflectiveOperationException legacyFailure) {
                        legacyFailure.addSuppressed(modernFailure);
                        throw legacyFailure;
                    }
                }
                bridge = current;
            }
            return current;
        }
    }

    static final class Geometry {
        private final CompensatedShape outline;
        private final CompensatedShape collision;

        Geometry(CompensatedShape outline, CompensatedShape collision) {
            this.outline = outline;
            this.collision = collision;
        }

        CompensatedShape outline() { return outline; }
        CompensatedShape collision() { return collision; }
    }

    private interface Bridge {
        Object state(Block block) throws ReflectiveOperationException;

        boolean dynamic(Object state) throws ReflectiveOperationException;

        Geometry geometry(Block block, Object state) throws ReflectiveOperationException;
    }

    /** Mojang/Spigot VoxelShape bridge used by 1.13 through 26+. */
    private static final class ModernBridge implements Bridge {
        private final Method stateGetter;
        private final Method levelGetter;
        private final boolean levelFromBlock;
        private final Method getPosition;
        private final Method getBlock;
        private final Method dynamic;
        private final ShapeMethod outline;
        private final ShapeMethod collision;

        private ModernBridge(Method stateGetter, Method levelGetter, boolean levelFromBlock,
                             Method getPosition, Method getBlock, Method dynamic,
                             ShapeMethod outline, ShapeMethod collision) {
            this.stateGetter = stateGetter;
            this.levelGetter = levelGetter;
            this.levelFromBlock = levelFromBlock;
            this.getPosition = getPosition;
            this.getBlock = getBlock;
            this.dynamic = dynamic;
            this.outline = outline;
            this.collision = collision;
        }

        static ModernBridge discover(Block block) throws ReflectiveOperationException {
            Class<?> craftBlock = block.getClass();
            Method stateGetter = optional(craftBlock, "getNMS");
            if (stateGetter == null) {
                // Paper 26+ renamed the native accessor after switching to fully Mojang-mapped
                // server jars.
                stateGetter = publicOrDeclared(craftBlock, "getBlockState");
            }
            Method getPosition = publicOrDeclared(craftBlock, "getPosition");
            Method levelGetter = optional(craftBlock, "getLevel");
            boolean levelFromBlock = levelGetter != null;
            if (levelGetter == null) {
                levelGetter = publicOrDeclared(block.getWorld().getClass(), "getHandle");
            }
            Object state = stateGetter.invoke(block);
            Object level = levelGetter.invoke(levelFromBlock ? block : block.getWorld());
            Object position = getPosition.invoke(block);
            Method getBlock = publicOrDeclared(state.getClass(), "getBlock");
            Object nmsBlock = getBlock.invoke(state);
            Method dynamic = optionalNoArgBoolean(nmsBlock.getClass(), "hasDynamicShape");

            List<ShapeMethod> candidates = shapeMethods(state, level, position);
            if (candidates.isEmpty()) {
                throw new NoSuchMethodException(state.getClass().getName() + " voxel shape methods");
            }
            ShapeMethod outline = named(candidates, "getShape", "j");
            ShapeMethod collision = named(candidates, "getCollisionShape", "k");
            if (collision == null) {
                collision = smallest(candidates, state, level, position);
            }
            if (outline == null) {
                // Obfuscated runtimes cannot be classified safely by a/b/c. Collider geometry is
                // conservative for optical masking and, importantly, preserves fence/gate holes.
                outline = collision;
            }
            return new ModernBridge(stateGetter, levelGetter, levelFromBlock, getPosition,
                    getBlock, dynamic, outline, collision);
        }

        @Override
        public Object state(Block block) throws ReflectiveOperationException {
            return invoke(stateGetter, block);
        }

        @Override
        public boolean dynamic(Object state) throws ReflectiveOperationException {
            if (dynamic == null) {
                return true;
            }
            Object nmsBlock = invoke(getBlock, state);
            return Boolean.TRUE.equals(invoke(dynamic, nmsBlock));
        }

        @Override
        public Geometry geometry(Block block, Object state) throws ReflectiveOperationException {
            Object level = invoke(levelGetter, levelFromBlock ? block : block.getWorld());
            Object position = invoke(getPosition, block);
            return new Geometry(outline.read(state, level, position, block),
                    collision.read(state, level, position, block));
        }

        private static List<ShapeMethod> shapeMethods(Object state, Object level, Object position) {
            List<ShapeMethod> result = new ArrayList<>();
            for (Method method : allMethods(state.getClass())) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 2 || method.getReturnType().isPrimitive()
                        || !parameters[0].isInstance(level) || !parameters[1].isInstance(position)) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Object shape = method.invoke(state, level, position);
                    Method toBoxes = findListMethod(shape);
                    if (toBoxes != null) {
                        result.add(new ShapeMethod(method, toBoxes, AabbFields.discover(shape, toBoxes)));
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Not a shape getter.
                }
            }
            return result;
        }

        private static ShapeMethod named(List<ShapeMethod> methods, String... names) {
            for (ShapeMethod method : methods) {
                for (String name : names) {
                    if (method.getter.getName().equals(name)) {
                        return method;
                    }
                }
            }
            return null;
        }

        private static ShapeMethod smallest(List<ShapeMethod> methods, Object state,
                                            Object level, Object position) {
            ShapeMethod best = methods.get(0);
            double bestVolume = Double.POSITIVE_INFINITY;
            for (ShapeMethod candidate : methods) {
                try {
                    CompensatedShape shape = candidate.read(state, level, position, null);
                    double volume = volume(shape.copyBoxes());
                    if (shape.boxCount() > 0 && volume < bestVolume) {
                        best = candidate;
                        bestVolume = volume;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Keep the first successfully discovered candidate.
                }
            }
            return best;
        }

        private static double volume(double[] boxes) {
            double result = 0.0;
            for (int offset = 0; offset < boxes.length; offset += 6) {
                result += Math.max(0.0, boxes[offset + 3] - boxes[offset])
                        * Math.max(0.0, boxes[offset + 4] - boxes[offset + 1])
                        * Math.max(0.0, boxes[offset + 5] - boxes[offset + 2]);
            }
            return result;
        }
    }

    /** Native AxisAlignedBB collector used by pre-VoxelShape Minecraft 1.8.x. */
    private static final class LegacyBridge implements Bridge {
        private final Method worldHandle;
        private final Constructor<?> positionConstructor;
        private final Method getState;
        private final Method getBlock;
        private final Method collectCollision;
        private final Constructor<?> maskConstructor;
        private final AabbFields fields;

        private LegacyBridge(Method worldHandle, Constructor<?> positionConstructor, Method getState,
                             Method getBlock, Method collectCollision, Constructor<?> maskConstructor,
                             AabbFields fields) {
            this.worldHandle = worldHandle;
            this.positionConstructor = positionConstructor;
            this.getState = getState;
            this.getBlock = getBlock;
            this.collectCollision = collectCollision;
            this.maskConstructor = maskConstructor;
            this.fields = fields;
        }

        static LegacyBridge discover(Block block) throws ReflectiveOperationException {
            World world = block.getWorld();
            Method worldHandle = publicOrDeclared(world.getClass(), "getHandle");
            Object level = worldHandle.invoke(world);
            Method getState = findStateGetter(level.getClass());
            Class<?> positionType = getState.getParameterTypes()[0];
            Constructor<?> positionConstructor = positionType.getDeclaredConstructor(
                    int.class, int.class, int.class);
            positionConstructor.setAccessible(true);
            Object position = positionConstructor.newInstance(block.getX(), block.getY(), block.getZ());
            Object state = getState.invoke(level, position);
            Method getBlock = publicOrDeclared(state.getClass(), "getBlock");
            Object nmsBlock = getBlock.invoke(state);
            Method collect = findCollisionCollector(nmsBlock.getClass(), level, position, state);
            Class<?> aabbType = collect.getParameterTypes()[3];
            Constructor<?> mask = aabbType.getDeclaredConstructor(
                    double.class, double.class, double.class, double.class, double.class, double.class);
            mask.setAccessible(true);
            return new LegacyBridge(worldHandle, positionConstructor, getState, getBlock, collect,
                    mask, AabbFields.discover(aabbType));
        }

        @Override
        public Object state(Block block) throws ReflectiveOperationException {
            Object level = invoke(worldHandle, block.getWorld());
            Object position = positionConstructor.newInstance(block.getX(), block.getY(), block.getZ());
            return invoke(getState, level, position);
        }

        @Override
        public boolean dynamic(Object state) {
            // 1.8 fence/gate/wall/stair boxes depend on neighbours and mutable block bounds.
            return true;
        }

        @Override
        public Geometry geometry(Block block, Object state) throws ReflectiveOperationException {
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            Object level = invoke(worldHandle, block.getWorld());
            Object position = positionConstructor.newInstance(x, y, z);
            Object nmsBlock = invoke(getBlock, state);
            Object mask = maskConstructor.newInstance(
                    x - 2.0, y - 2.0, z - 2.0, x + 3.0, y + 3.0, z + 3.0);
            List<Object> boxes = new ArrayList<>();
            invoke(collectCollision, nmsBlock, level, position, state, mask, boxes, null);
            CompensatedShape collision = fields.shape(boxes, x, y, z);
            return new Geometry(collision, collision);
        }

        private static Method findStateGetter(Class<?> levelType) throws NoSuchMethodException {
            for (String name : new String[] {"getType", "getBlockState"}) {
                for (Method method : allMethods(levelType)) {
                    if (method.getName().equals(name) && method.getParameterTypes().length == 1
                            && !method.getReturnType().isPrimitive()) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
            throw new NoSuchMethodException(levelType.getName() + " block-state getter");
        }

        private static Method findCollisionCollector(Class<?> blockType, Object level,
                                                     Object position, Object state)
                throws NoSuchMethodException {
            for (Method method : allMethods(blockType)) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 6 && method.getReturnType() == void.class
                        && parameters[0].isInstance(level) && parameters[1].isInstance(position)
                        && parameters[2].isInstance(state)
                        && List.class.isAssignableFrom(parameters[4])) {
                    method.setAccessible(true);
                    return method;
                }
            }
            throw new NoSuchMethodException(blockType.getName() + " legacy collision collector");
        }
    }

    private static final class ShapeMethod {
        private final Method getter;
        private final Method toBoxes;
        private final AabbFields fields;

        private ShapeMethod(Method getter, Method toBoxes, AabbFields fields) {
            this.getter = getter;
            this.toBoxes = toBoxes;
            this.fields = fields;
        }

        private CompensatedShape read(Object state, Object level, Object position, Block ignored)
                throws ReflectiveOperationException {
            Object shape = invoke(getter, state, level, position);
            Object value = invoke(toBoxes, shape);
            if (!(value instanceof List<?>)) {
                throw new ReflectiveOperationException("VoxelShape box method did not return List");
            }
            return fields.shape((List<?>) value, 0, 0, 0);
        }
    }

    private static final class AabbFields {
        private final Field minX;
        private final Field minY;
        private final Field minZ;
        private final Field maxX;
        private final Field maxY;
        private final Field maxZ;

        private AabbFields(Field minX, Field minY, Field minZ,
                           Field maxX, Field maxY, Field maxZ) {
            this.minX = accessible(minX);
            this.minY = accessible(minY);
            this.minZ = accessible(minZ);
            this.maxX = accessible(maxX);
            this.maxY = accessible(maxY);
            this.maxZ = accessible(maxZ);
        }

        static AabbFields discover(Object shape, Method toBoxes) throws ReflectiveOperationException {
            Object value = toBoxes.invoke(shape);
            if (value instanceof List<?> && !((List<?>) value).isEmpty()) {
                return discover(((List<?>) value).get(0).getClass());
            }
            String name = shape.getClass().getName().replace("VoxelShape", "AxisAlignedBB");
            try {
                return discover(Class.forName(name, false, shape.getClass().getClassLoader()));
            } catch (ClassNotFoundException ignored) {
                String packageName = shape.getClass().getPackage().getName();
                for (String simple : new String[] {"AABB", "AxisAlignedBB"}) {
                    try {
                        String parent = packageName.endsWith(".shapes")
                                ? packageName.substring(0, packageName.length() - ".shapes".length())
                                : packageName;
                        return discover(Class.forName(parent + "." + simple,
                                false, shape.getClass().getClassLoader()));
                    } catch (ClassNotFoundException ignoredAgain) {
                        // Try the known global names below.
                    }
                }
                for (String known : new String[] {
                        "net.minecraft.world.phys.AABB", "net.minecraft.server.AxisAlignedBB"}) {
                    try {
                        return discover(Class.forName(known, false, shape.getClass().getClassLoader()));
                    } catch (ClassNotFoundException ignoredAgain) {
                        // Continue.
                    }
                }
                throw new ClassNotFoundException("Cannot identify AABB class for " + shape.getClass());
            }
        }

        static AabbFields discover(Class<?> type) throws NoSuchFieldException {
            Field[] named = namedFields(type);
            if (named != null) {
                return new AabbFields(named[0], named[1], named[2], named[3], named[4], named[5]);
            }
            List<Field> doubles = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() == double.class) {
                    doubles.add(field);
                }
            }
            if (doubles.size() < 6) {
                throw new NoSuchFieldException(type.getName() + " six AABB coordinates");
            }
            Collections.sort(doubles, Comparator.comparing(Field::getName));
            return new AabbFields(doubles.get(0), doubles.get(1), doubles.get(2),
                    doubles.get(3), doubles.get(4), doubles.get(5));
        }

        CompensatedShape shape(List<?> boxes, int offsetX, int offsetY, int offsetZ)
                throws IllegalAccessException {
            if (boxes.isEmpty()) {
                return CompensatedShape.EMPTY;
            }
            double[] coordinates = new double[boxes.size() * 6];
            int offset = 0;
            for (Object box : boxes) {
                coordinates[offset++] = number(minX, box) - offsetX;
                coordinates[offset++] = number(minY, box) - offsetY;
                coordinates[offset++] = number(minZ, box) - offsetZ;
                coordinates[offset++] = number(maxX, box) - offsetX;
                coordinates[offset++] = number(maxY, box) - offsetY;
                coordinates[offset++] = number(maxZ, box) - offsetZ;
            }
            return new CompensatedShape(coordinates);
        }

        private static Field[] namedFields(Class<?> type) {
            for (String[] names : new String[][] {
                    {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"},
                    {"a", "b", "c", "d", "e", "f"}}) {
                try {
                    return new Field[] {field(type, names[0]), field(type, names[1]), field(type, names[2]),
                            field(type, names[3]), field(type, names[4]), field(type, names[5])};
                } catch (NoSuchFieldException ignored) {
                    // Try the next mapping family.
                }
            }
            return null;
        }
    }

    private static Method findListMethod(Object shape) {
        if (shape == null) {
            return null;
        }
        for (String name : new String[] {"toAabbs", "d"}) {
            try {
                Method method = publicOrDeclared(shape.getClass(), name);
                if (List.class.isAssignableFrom(method.getReturnType())) {
                    return method;
                }
            } catch (ReflectiveOperationException ignored) {
                // Scan by signature.
            }
        }
        for (Method method : allMethods(shape.getClass())) {
            if (method.getParameterTypes().length == 0
                    && List.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method optionalNoArgBoolean(Class<?> type, String name) {
        try {
            Method method = publicOrDeclared(type, name);
            return method.getReturnType() == boolean.class ? method : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static List<Method> allMethods(Class<?> type) {
        List<Method> result = new ArrayList<>();
        Collections.addAll(result, type.getMethods());
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Collections.addAll(result, current.getDeclaredMethods());
        }
        return result;
    }

    private static Method publicOrDeclared(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        try {
            Method method = type.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Method method = current.getDeclaredMethod(name, parameters);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignoredAgain) {
                    // Continue up the hierarchy.
                }
            }
            throw new NoSuchMethodException(type.getName() + "#" + name);
        }
    }

    private static Method optional(Class<?> type, String name, Class<?>... parameters) {
        try {
            return publicOrDeclared(type, name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue.
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }

    private static double number(Field field, Object target) throws IllegalAccessException {
        return ((Number) field.get(target)).doubleValue();
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) cause;
            }
            throw new ReflectiveOperationException(method + " failed", cause);
        }
    }
}
