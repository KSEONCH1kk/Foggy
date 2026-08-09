package dev.foggy.invisibility;

import dev.foggy.config.FoggyConfig;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

/**
 * Aggregates Bukkit invisibility, spectator state and optional vanish-plugin state.
 * No vanilla hide/show method is invoked; {@code Player#canSee} is read only as an interoperability signal.
 */
public final class InvisibilityTracker {
    private static final List<String> VANISH_APIS = List.of(
            "de.myzelyam.api.vanish.VanishAPI", // SuperVanish and PremiumVanish
            "org.kitteh.vanish.VanishAPI"
    );

    private final FoggyConfig config;
    private final Logger logger;
    private final List<Method> reflectiveHooks;

    /**
     * Discovers optional vanish APIs without making them hard dependencies.
     *
     * @param config invisibility integration switches
     * @param logger plugin logger
     */
    public InvisibilityTracker(FoggyConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.reflectiveHooks = config.reflectiveVanishHooks() ? discoverHooks() : List.of();
    }

    /**
     * Captures only target-owned state. This method must run on the target's entity scheduler.
     *
     * @param target target owned by the current region
     * @return immutable cross-region state
     */
    public TargetInvisibilityState captureTarget(Player target) {
        return new TargetInvisibilityState(
                target.hasPotionEffect(PotionEffectType.INVISIBILITY),
                target.isInvisible(),
                target.getGameMode() == GameMode.SPECTATOR,
                matchingHooks(target));
    }

    /**
     * Resolves a captured target against the viewer-owned Bukkit visibility signal.
     *
     * @param target captured target signals
     * @param bukkitCanSee result of {@code viewer.canSee(target)}, or true when another region owns target
     * @return effective packet treatment
     */
    public InvisibilityDisposition disposition(TargetInvisibilityState target, boolean bukkitCanSee) {
        boolean vanillaInvisible = (config.potionInvisibility() && target.potionEffect())
                || (config.entityInvisibleFlag() && target.entityInvisibleFlag());
        boolean hardHidden = (config.spectatorInvisibility() && target.spectator())
                || (config.respectCanSee() && !bukkitCanSee)
                || !target.vanishHooks().isEmpty();
        return InvisibilityDisposition.resolve(vanillaInvisible, hardHidden,
                config.preserveVanillaInvisibleEntity());
    }

    /**
     * Builds pair diagnostics from region-safe captured target state.
     *
     * @param target captured target signals
     * @param bukkitCanSee viewer-owned visibility signal
     * @return detailed pair snapshot
     */
    public InvisibilityDebugSnapshot diagnose(TargetInvisibilityState target, boolean bukkitCanSee) {
        return new InvisibilityDebugSnapshot(
                target.potionEffect(), target.entityInvisibleFlag(), target.spectator(), bukkitCanSee,
                target.vanishHooks(), disposition(target, bukkitCanSee));
    }

    private List<String> matchingHooks(Player target) {
        List<String> matches = new ArrayList<>();
        for (Method hook : reflectiveHooks) {
            try {
                Object value = hook.invoke(null, target);
                if (Boolean.TRUE.equals(value)) {
                    matches.add(hook.getDeclaringClass().getSimpleName());
                }
            } catch (IllegalAccessException | InvocationTargetException exception) {
                logger.log(Level.FINE, "Vanish hook failed: " + hook, exception);
            }
        }
        return List.copyOf(matches);
    }

    private List<Method> discoverHooks() {
        List<Method> hooks = new ArrayList<>();
        for (String className : VANISH_APIS) {
            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                try {
                    Class<?> api = Class.forName(className, false, plugin.getClass().getClassLoader());
                    Method method = api.getMethod("isInvisible", Player.class);
                    if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class) {
                        hooks.add(method);
                        logger.info("Using optional vanish hook " + className + "#isInvisible(Player)");
                        break;
                    }
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                    // Try the next enabled plugin class loader.
                }
            }
        }
        return List.copyOf(hooks);
    }
}
