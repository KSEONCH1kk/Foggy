package dev.foggy.debug;

import dev.foggy.FoggyPlugin;
import dev.foggy.camera.CameraEstimator;
import dev.foggy.camera.CameraPose;
import dev.foggy.invisibility.InvisibilityDebugSnapshot;
import dev.foggy.invisibility.InvisibilityTracker;
import dev.foggy.packet.PacketDebugState;
import dev.foggy.packet.PacketVisibilityController;
import dev.foggy.raycast.BlockGeometryHit;
import dev.foggy.raycast.BukkitRaycastService;
import dev.foggy.raycast.OpticalResult;
import dev.foggy.raycast.RayDebugLine;
import dev.foggy.raycast.RaycastDebugSnapshot;
import dev.foggy.visibility.PairDebugState;
import dev.foggy.visibility.PlayerVisibilitySnapshot;
import dev.foggy.visibility.VisibilityEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements {@code /foggy debug} with numeric state output and viewer-only particle overlays.
 */
public final class FoggyDebugCommand implements CommandExecutor, TabCompleter {
    private static final int UPDATE_INTERVAL_TICKS = 5;
    private static final int MAX_DEBUG_SHAPE_BOXES = 24;
    private static final Particle.DustOptions CAMERA_FALLBACK = dust(55, 120, 255, 1.15f);
    private static final Particle.DustOptions CAMERA_EXACT = dust(0, 255, 255, 1.3f);
    private static final Particle.DustOptions TARGET_VISIBLE = dust(40, 255, 80, 1.1f);
    private static final Particle.DustOptions TARGET_BLOCKED = dust(255, 50, 50, 1.1f);
    private static final Particle.DustOptions TARGET_OUTSIDE = dust(140, 140, 140, 0.9f);
    private static final Particle.DustOptions BLOCK_HIT = dust(255, 140, 0, 1.25f);
    private static final Particle.DustOptions TRANSPARENT_HIT = dust(190, 70, 255, 1.15f);
    private static final Particle.DustOptions SHAPE_EDGE = dust(255, 185, 40, 0.65f);
    private static final Particle.DustOptions VIEW_DIRECTION = dust(255, 230, 40, 0.9f);

    private final FoggyPlugin plugin;
    private final CameraEstimator cameraEstimator;
    private final BukkitRaycastService raycastService;
    private final InvisibilityTracker invisibilityTracker;
    private final VisibilityEngine visibilityEngine;
    private final PacketVisibilityController packetController;
    private final Map<UUID, DebugSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> viewerTicks = new ConcurrentHashMap<>();

    /**
     * Creates the operator debugger.
     *
     * @param plugin plugin lifecycle owner used by {@code /foggy reload}
     * @param cameraEstimator camera estimator under test
     * @param raycastService raycast service under test
     * @param invisibilityTracker state tracker under test
     * @param visibilityEngine engine state source
     * @param packetController packet state source
     */
    public FoggyDebugCommand(FoggyPlugin plugin, CameraEstimator cameraEstimator, BukkitRaycastService raycastService,
                             InvisibilityTracker invisibilityTracker, VisibilityEngine visibilityEngine,
                             PacketVisibilityController packetController) {
        this.plugin = plugin;
        this.cameraEstimator = cameraEstimator;
        this.raycastService = raycastService;
        this.invisibilityTracker = invisibilityTracker;
        this.visibilityEngine = visibilityEngine;
        this.packetController = packetController;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("foggy.reload")) {
                sender.sendMessage(Component.text("Нет права foggy.reload.", NamedTextColor.RED));
                return true;
            }
            plugin.reloadRuntime(result -> sendReloadResult(sender, result));
            sender.sendMessage(Component.text("[Foggy] reload запланирован на global region.",
                    NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("Использование: /" + label + " reload (debug доступен только игроку)");
            return true;
        }
        if (!viewer.hasPermission("foggy.debug")) {
            viewer.sendMessage(Component.text("Нет права foggy.debug.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("debug")) {
            help(viewer, label);
            return true;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("on")) {
            PlayerVisibilitySnapshot target = nearestTarget(viewer);
            if (target == null) {
                viewer.sendMessage(Component.text("Рядом нет другого игрока; укажи /" + label
                        + " debug <ник>.", NamedTextColor.RED));
                return true;
            }
            start(viewer, target);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("off")) {
            sessions.remove(viewer.getUniqueId());
            viewer.sendActionBar(Component.empty());
            viewer.sendMessage(Component.text("Foggy debug выключен.", NamedTextColor.YELLOW));
            return true;
        }
        if (action.equals("status")) {
            DebugSession session = sessions.get(viewer.getUniqueId());
            PlayerVisibilitySnapshot target = session == null
                    ? nearestTarget(viewer) : visibilityEngine.snapshot(session.targetId());
            if (target == null) {
                viewer.sendMessage(Component.text("Debug target недоступен.", NamedTextColor.RED));
            } else {
                showDetailed(viewer, target, true);
            }
            return true;
        }
        if (action.equals("particles")) {
            toggleParticles(viewer, args.length >= 3 ? args[2] : null);
            return true;
        }
        String targetName = action.equals("target") && args.length >= 3 ? args[2] : args[1];
        PlayerVisibilitySnapshot target = visibilityEngine.snapshots().stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (target == null || target.playerId().equals(viewer.getUniqueId())) {
            viewer.sendMessage(Component.text("Игрок '" + targetName + "' не найден или это ты сам.", NamedTextColor.RED));
            return true;
        }
        start(viewer, target);
        return true;
    }

    /**
     * Updates one viewer's action bar and particles on that viewer's owning entity scheduler.
     *
     * @param viewer region-owned debug viewer
     */
    public void tickViewer(Player viewer) {
        DebugSession session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            return;
        }
        int currentTick = viewerTicks.merge(viewer.getUniqueId(), 1, Integer::sum);
        if (currentTick % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }
        PlayerVisibilitySnapshot target = visibilityEngine.snapshot(session.targetId());
        if (target == null) {
            sessions.remove(viewer.getUniqueId());
            return;
        }
        if (!viewer.getWorld().getUID().equals(target.worldId())) {
            viewer.sendActionBar(Component.text("Foggy debug: target в другом мире", NamedTextColor.RED));
            return;
        }
        DebugFrame frame = capture(viewer, target);
        viewer.sendActionBar(actionBar(frame));
        if (session.particles()) {
            renderParticles(viewer, frame.optical());
        }
    }

    /** Clears all sessions during plugin shutdown. */
    public void shutdown() {
        sessions.clear();
        viewerTicks.clear();
    }

    /**
     * Removes sessions owned by or targeting a disconnecting player.
     *
     * @param player disconnecting player
     */
    public void remove(Player player) {
        sessions.remove(player.getUniqueId());
        viewerTicks.remove(player.getUniqueId());
        sessions.entrySet().removeIf(entry -> entry.getValue().targetId().equals(player.getUniqueId()));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("foggy.debug")) {
                options.add("debug");
            }
            if (sender.hasPermission("foggy.reload")) {
                options.add("reload");
            }
            return prefix(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            List<String> options = new ArrayList<>(List.of("on", "off", "status", "particles", "target"));
            visibilityEngine.snapshots().stream().map(PlayerVisibilitySnapshot::name).forEach(options::add);
            return prefix(options, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug")) {
            if (args[1].equalsIgnoreCase("particles")) {
                return prefix(List.of("on", "off"), args[2]);
            }
            if (args[1].equalsIgnoreCase("target")) {
                return prefix(visibilityEngine.snapshots().stream().map(PlayerVisibilitySnapshot::name).toList(), args[2]);
            }
        }
        return List.of();
    }

    private void start(Player viewer, PlayerVisibilitySnapshot target) {
        sessions.put(viewer.getUniqueId(), new DebugSession(target.playerId(), true));
        viewerTicks.put(viewer.getUniqueId(), 0);
        viewer.sendMessage(Component.text("Foggy debug → " + target.name(), NamedTextColor.AQUA));
        viewer.sendMessage(Component.text(
                "Частицы: синие/голубые=камеры, серые/красные/зелёные=hitbox, "
                        + "красные лучи=block hit, оранжевый=точка блока, фиолетовый=прозрачный проход, "
                        + "золотые рёбра=все OUTLINE sub-boxes, зелёный луч=причина видимости.",
                NamedTextColor.GRAY));
        showDetailed(viewer, target, true);
    }

    private void toggleParticles(Player viewer, @Nullable String value) {
        DebugSession session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            viewer.sendMessage(Component.text("Сначала включи /foggy debug <ник>.", NamedTextColor.RED));
            return;
        }
        boolean enabled = value == null ? !session.particles() : value.equalsIgnoreCase("on");
        sessions.put(viewer.getUniqueId(), session.withParticles(enabled));
        viewer.sendMessage(Component.text("Debug particles: " + enabled, NamedTextColor.YELLOW));
    }

    private void showDetailed(Player viewer, PlayerVisibilitySnapshot target, boolean renderNow) {
        if (!viewer.getWorld().getUID().equals(target.worldId())) {
            viewer.sendMessage(Component.text("Target в другом мире.", NamedTextColor.RED));
            return;
        }
        DebugFrame frame = capture(viewer, target);
        RaycastDebugSnapshot optical = frame.optical();
        PairDebugState engine = frame.engine();
        InvisibilityDebugSnapshot invisibility = frame.invisibility();
        PacketDebugState packet = frame.packet();
        long exactCameras = optical.cameras().stream().filter(CameraPose::companionExact).count();
        double minFov = optical.cameras().stream().mapToDouble(CameraPose::verticalFovDegrees).min().orElse(0.0);
        double maxFov = optical.cameras().stream().mapToDouble(CameraPose::verticalFovDegrees).max().orElse(0.0);

        line(viewer, "target=" + target.name() + " distance="
                + decimal(viewer.getLocation().toVector().distance(target.position()))
                + " entityId=" + target.entityId(), NamedTextColor.AQUA);
        line(viewer, "FINAL=" + finalReason(frame) + " engineReason=" + engine.reason()
                + " managed=" + engine.managed() + " hidden=" + engine.hidden()
                + " debounce=" + engine.pendingHideTicks(), frame.bypass() ? NamedTextColor.RED : NamedTextColor.YELLOW);
        line(viewer, "bypass=" + frame.bypass() + " invis[potion=" + invisibility.potionEffect()
                + ", flag=" + invisibility.entityInvisibleFlag() + ", spectator=" + invisibility.spectator()
                + ", canSee=" + invisibility.bukkitCanSee() + ", hooks=" + invisibility.vanishHooks()
                + ", mode=" + invisibility.disposition() + "]",
                frame.bypass() ? NamedTextColor.RED : NamedTextColor.GRAY);
        line(viewer, "optical=" + optical.result() + " cameras=" + optical.cameras().size()
                + " exact=" + exactCameras + " fovY=" + decimal(minFov) + ".." + decimal(maxFov)
                + " points=" + optical.targetPoints().size(), NamedTextColor.GRAY);
        line(viewer, "rays[inFov=" + optical.inFovSamples() + ", traced=" + optical.raysTraced()
                + ", blocked=" + optical.blockedRays() + "] time="
                + decimal(optical.elapsedNanos() / 1_000.0) + "µs", NamedTextColor.GRAY);
        line(viewer, "packet[hiddenId=" + packet.hiddenId() + ", observedTracked=" + packet.observedTracked()
                + ", clientKnown=" + packet.clientKnown() + ", paperTracked=" + packet.paperTracked() + "]",
                NamedTextColor.GRAY);
        RayDebugLine geometryRay = firstGeometryRay(optical);
        if (geometryRay != null && geometryRay.blockingBlock() != null) {
            BlockGeometryHit hit = geometryRay.blockingBlock();
            line(viewer, "BLOCK=" + geometrySummary(hit), NamedTextColor.GOLD);
            line(viewer, "state=" + hit.blockData(), NamedTextColor.DARK_GRAY);
        }
        BlockGeometryHit passed = firstPassThroughHit(optical);
        if (passed != null) {
            line(viewer, "PASS=" + geometrySummary(passed), NamedTextColor.LIGHT_PURPLE);
        }
        if (frame.bypass()) {
            line(viewer, "! foggy.bypass=true: этот viewer никогда не будет скрывать игроков.", NamedTextColor.RED);
        } else if (!engine.managed()) {
            line(viewer, "! Пара вне visibility.radius-blocks или ещё не обработана entity tick.", NamedTextColor.RED);
        } else if (optical.result() == OpticalResult.VISIBLE && optical.decisiveRay() != null) {
            CameraPose decisiveCamera = optical.cameras().get(optical.decisiveCameraIndex());
            line(viewer, "VISIBLE ray: camera=" + decisiveCamera.source()
                    + " pos=" + vector(decisiveCamera.position())
                    + " -> target=" + vector(optical.decisiveRay().to()), NamedTextColor.GREEN);
        }
        if (renderNow && sessions.getOrDefault(
                viewer.getUniqueId(), new DebugSession(target.playerId(), true)).particles()) {
            renderParticles(viewer, optical);
        }
    }

    private DebugFrame capture(Player viewer, PlayerVisibilitySnapshot target) {
        List<CameraPose> cameras = cameraEstimator.estimate(viewer);
        boolean canSee = !Bukkit.isOwnedByCurrentRegion(target.playerHandle())
                || viewer.canSee(target.playerHandle());
        return new DebugFrame(
                target,
                viewer.hasPermission("foggy.bypass"),
                invisibilityTracker.diagnose(target.invisibility(), canSee),
                raycastService.diagnose(target, cameras),
                visibilityEngine.inspect(viewer.getUniqueId(), target),
                packetController.inspect(viewer, target));
    }

    private static Component actionBar(DebugFrame frame) {
        String text = "Foggy→" + frame.target().name()
                + " final=" + finalReason(frame)
                + " optical=" + frame.optical().result()
                + " hidden=" + frame.engine().hidden() + "/" + frame.packet().hiddenId()
                + " rays=" + frame.optical().blockedRays() + "/" + frame.optical().raysTraced()
                + " " + decimal(frame.optical().elapsedNanos() / 1_000.0) + "µs";
        NamedTextColor color = frame.bypass() ? NamedTextColor.RED
                : frame.engine().hidden() ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        return Component.text(text, color);
    }

    private static String finalReason(DebugFrame frame) {
        if (frame.bypass()) {
            return "BYPASS";
        }
        if (frame.invisibility().packetHidden()) {
            return "INVISIBLE";
        }
        return frame.optical().result().name();
    }

    private static void renderParticles(Player viewer, RaycastDebugSnapshot snapshot) {
        for (CameraPose camera : snapshot.cameras()) {
            particle(viewer, camera.position(), camera.companionExact() ? CAMERA_EXACT : CAMERA_FALLBACK);
        }
        Particle.DustOptions targetColor = switch (snapshot.result()) {
            case VISIBLE, REGION_UNOWNED -> TARGET_OUTSIDE;
            case OCCLUDED -> TARGET_BLOCKED;
            case OUTSIDE_FOV -> TARGET_OUTSIDE;
        };
        for (Vector point : snapshot.targetPoints()) {
            particle(viewer, point, targetColor);
        }
        CameraPose primary = snapshot.cameras().isEmpty() ? null : snapshot.cameras().get(0);
        if (primary != null) {
            drawLine(viewer, primary.position(), primary.position().clone().add(primary.forward().clone().multiply(2.0)),
                    VIEW_DIRECTION, 16);
        }
        RayDebugLine decisive = snapshot.decisiveRay();
        if (decisive != null) {
            drawLine(viewer, decisive.from(), decisive.to(), TARGET_VISIBLE, 80);
            particle(viewer, decisive.to(), TARGET_VISIBLE);
            renderTransparentHit(viewer, decisive);
        }
        int rendered = 0;
        for (RayDebugLine ray : snapshot.displayRays()) {
            if (rendered++ >= 3 || ray.hit() == null) {
                break;
            }
            drawLine(viewer, ray.from(), ray.hit(), TARGET_BLOCKED, 40);
            particle(viewer, ray.hit(), BLOCK_HIT);
            renderTransparentHit(viewer, ray);
        }
        RayDebugLine geometryRay = firstGeometryRay(snapshot);
        if (geometryRay != null && geometryRay.blockingBlock() != null) {
            int boxes = 0;
            for (BoundingBox box : geometryRay.blockingBlock().outlineSubBoxes()) {
                if (boxes++ >= MAX_DEBUG_SHAPE_BOXES) {
                    break;
                }
                drawBox(viewer, box, SHAPE_EDGE);
            }
        }
    }

    private static void renderTransparentHit(Player viewer, RayDebugLine ray) {
        BlockGeometryHit first = ray.firstOutline();
        if (first != null && !first.opticalMode().blocksRay()) {
            particle(viewer, first.position(), TRANSPARENT_HIT);
        }
    }

    private static @Nullable RayDebugLine firstGeometryRay(RaycastDebugSnapshot snapshot) {
        for (RayDebugLine ray : snapshot.displayRays()) {
            if (ray.blockingBlock() != null) {
                return ray;
            }
        }
        return null;
    }

    private static @Nullable BlockGeometryHit firstPassThroughHit(RaycastDebugSnapshot snapshot) {
        RayDebugLine decisive = snapshot.decisiveRay();
        if (decisive != null && decisive.firstOutline() != null
                && !decisive.firstOutline().opticalMode().blocksRay()) {
            return decisive.firstOutline();
        }
        for (RayDebugLine ray : snapshot.displayRays()) {
            if (ray.firstOutline() != null
                    && !ray.firstOutline().opticalMode().blocksRay()) {
                return ray.firstOutline();
            }
        }
        return null;
    }

    private static String geometrySummary(BlockGeometryHit hit) {
        return hit.material() + "@(" + hit.blockX() + "," + hit.blockY() + "," + hit.blockZ() + ")"
                + " shape=OUTLINE_ALL mode=" + hit.opticalMode()
                + " outline=" + decimal(hit.outlineWidth()) + "x" + decimal(hit.outlineHeight())
                + "x" + decimal(hit.outlineDepth())
                + " outlineBoxes=" + hit.outlineSubBoxes().size()
                + " exactBoxes=" + hit.exactOutlineBoxes()
                + " collisionBoxes=" + hit.collisionSubBoxes()
                + " hit=" + vector(hit.position());
    }

    private static void drawBox(Player viewer, BoundingBox box, Particle.DustOptions color) {
        Vector min = new Vector(box.getMinX(), box.getMinY(), box.getMinZ());
        Vector max = new Vector(box.getMaxX(), box.getMaxY(), box.getMaxZ());
        Vector[] corners = {
                min,
                new Vector(max.getX(), min.getY(), min.getZ()),
                new Vector(max.getX(), min.getY(), max.getZ()),
                new Vector(min.getX(), min.getY(), max.getZ()),
                new Vector(min.getX(), max.getY(), min.getZ()),
                new Vector(max.getX(), max.getY(), min.getZ()),
                max,
                new Vector(min.getX(), max.getY(), max.getZ())
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            drawLine(viewer, corners[edge[0]], corners[edge[1]], color, 6);
        }
    }

    private static void drawLine(Player viewer, Vector from, Vector to, Particle.DustOptions color, int maxPoints) {
        Vector delta = to.clone().subtract(from);
        double distance = delta.length();
        if (distance < 1.0E-6) {
            particle(viewer, from, color);
            return;
        }
        int points = Math.max(2, Math.min(maxPoints, (int) Math.ceil(distance / 0.45)));
        Vector step = delta.multiply(1.0 / points);
        Vector cursor = from.clone();
        for (int index = 0; index <= points; index++) {
            particle(viewer, cursor, color);
            cursor.add(step);
        }
    }

    private static void particle(Player viewer, Vector position, Particle.DustOptions color) {
        viewer.spawnParticle(Particle.DUST, position.toLocation(viewer.getWorld()), 1, 0.0, 0.0, 0.0, 0.0, color);
    }

    private static Particle.DustOptions dust(int red, int green, int blue, float size) {
        return new Particle.DustOptions(Color.fromRGB(red, green, blue), size);
    }

    private void sendReloadResult(CommandSender sender, FoggyPlugin.ReloadResult result) {
        Component message = Component.text("[Foggy] " + result.message(),
                result.success() ? NamedTextColor.GREEN : NamedTextColor.RED);
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, ignored -> player.sendMessage(message), null);
        } else {
            sender.sendMessage(message);
        }
    }

    private static void line(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text("[Foggy] " + message, color));
    }

    private static void help(Player player, String label) {
        line(player, "/" + label + " debug <ник> — включить overlay", NamedTextColor.AQUA);
        line(player, "/" + label + " debug status — полный снимок значений", NamedTextColor.GRAY);
        line(player, "/" + label + " debug particles [on|off]", NamedTextColor.GRAY);
        line(player, "/" + label + " debug off", NamedTextColor.GRAY);
        if (player.hasPermission("foggy.reload")) {
            line(player, "/" + label + " reload — перечитать config.yml", NamedTextColor.GREEN);
        }
    }

    private @Nullable PlayerVisibilitySnapshot nearestTarget(Player viewer) {
        Vector viewerPosition = viewer.getLocation().toVector();
        UUID viewerWorld = viewer.getWorld().getUID();
        return visibilityEngine.snapshots().stream()
                .filter(candidate -> !candidate.playerId().equals(viewer.getUniqueId()))
                .filter(candidate -> candidate.worldId().equals(viewerWorld))
                .min((left, right) -> Double.compare(
                        left.position().distanceSquared(viewerPosition),
                        right.position().distanceSquared(viewerPosition)))
                .orElse(null);
    }

    private static List<String> prefix(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String vector(Vector value) {
        return "(" + decimal(value.getX()) + "," + decimal(value.getY()) + "," + decimal(value.getZ()) + ")";
    }

    private record DebugFrame(
            PlayerVisibilitySnapshot target,
            boolean bypass,
            InvisibilityDebugSnapshot invisibility,
            RaycastDebugSnapshot optical,
            PairDebugState engine,
            PacketDebugState packet
    ) {
    }
}
