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
     * Determines whether state signals retain or completely remove the client entity.
     *
     * @param viewer receiving player
     * @param target tested player
     * @return effective packet treatment
     */
    public InvisibilityDisposition disposition(Player viewer, Player target) {
        boolean vanillaInvisible = isConfiguredVanillaInvisible(target);
        boolean hardHidden = (config.spectatorInvisibility() && target.getGameMode() == GameMode.SPECTATOR)
                || (config.respectCanSee() && !viewer.canSee(target))
                || !matchingHooks(target).isEmpty();
        return InvisibilityDisposition.resolve(
                vanillaInvisible, hardHidden, config.preserveVanillaInvisibleEntity());
    }

    /**
     * Captures individual state inputs for an operator diagnostic.
     *
     * @param viewer receiving player
     * @param target tested player
     * @return detailed state snapshot
     */
    public InvisibilityDebugSnapshot diagnose(Player viewer, Player target) {
        boolean potion = target.hasPotionEffect(PotionEffectType.INVISIBILITY);
        boolean flag = target.isInvisible();
        boolean spectator = target.getGameMode() == GameMode.SPECTATOR;
        boolean canSee = viewer.canSee(target);
        List<String> hooks = matchingHooks(target);
        boolean vanillaInvisible = (config.potionInvisibility() && potion)
                || (config.entityInvisibleFlag() && flag);
        boolean hardHidden = (config.spectatorInvisibility() && spectator)
                || (config.respectCanSee() && !canSee)
                || !hooks.isEmpty();
        InvisibilityDisposition disposition = InvisibilityDisposition.resolve(
                vanillaInvisible, hardHidden, config.preserveVanillaInvisibleEntity());
        return new InvisibilityDebugSnapshot(potion, flag, spectator, canSee, hooks, disposition);
    }

    private boolean isConfiguredVanillaInvisible(Player target) {
        return (config.potionInvisibility() && target.hasPotionEffect(PotionEffectType.INVISIBILITY))
                || (config.entityInvisibleFlag() && target.isInvisible());
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
