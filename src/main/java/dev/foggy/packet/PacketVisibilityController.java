package dev.foggy.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

/**
 * Applies directed visibility decisions exclusively with PacketEvents packets.
 *
 * <p>The controller retains the server's player-info/tab entry. Reappearance is therefore a
 * normal player {@code SPAWN_ENTITY} followed in the same flush by current metadata, scale,
 * equipment, effects, head rotation and velocity. Silently sent packets bypass Foggy's own
 * listener while ordinary server updates to hidden ids are cancelled per viewer.</p>
 */
public final class PacketVisibilityController {
    private final Logger logger;
    private final Map<UUID, ViewerPacketState> viewers = new ConcurrentHashMap<>();

    /**
     * Creates a controller.
     *
     * @param logger plugin logger
     */
    public PacketVisibilityController(Logger logger) {
        this.logger = logger;
    }

    /**
     * Hides a currently tracked target, or pre-arms cancellation for its future spawn.
     *
     * @param viewer receiving player
     * @param target target player
     */
    public void hide(Player viewer, Player target) {
        int entityId = target.getEntityId();
        ViewerPacketState state = state(viewer);
        state.hiddenIds.add(entityId);
        boolean trackedNow = target.getTrackedBy().contains(viewer);
        if (trackedNow) {
            state.trackedIds.add(entityId);
        }
        if (trackedNow || state.clientKnownIds.remove(entityId)) {
            sendSilently(viewer, new WrapperPlayServerDestroyEntities(entityId));
            state.clientKnownIds.remove(entityId);
        }
    }

    /**
     * Shows a target immediately when Paper's tracker says this viewer tracks it.
     *
     * @param viewer receiving player
     * @param target target player
     */
    public void show(Player viewer, Player target) {
        int entityId = target.getEntityId();
        ViewerPacketState state = state(viewer);
        state.hiddenIds.remove(entityId);
        boolean trackedNow = target.getTrackedBy().contains(viewer);
        if (!trackedNow) {
            state.trackedIds.remove(entityId);
            state.clientKnownIds.remove(entityId);
            return;
        }
        state.trackedIds.add(entityId);
        if (state.clientKnownIds.add(entityId)) {
            sendSnapshot(viewer, target);
        }
    }

    /**
     * Called by the PacketEvents listener before an ordinary server spawn is sent.
     *
     * @param viewer receiving player
     * @param entityId spawned id
     * @return whether the spawn must be cancelled
     */
    public boolean onServerSpawn(Player viewer, int entityId) {
        ViewerPacketState state = state(viewer);
        state.trackedIds.add(entityId);
        if (state.hiddenIds.contains(entityId)) {
            state.clientKnownIds.remove(entityId);
            return true;
        }
        state.clientKnownIds.add(entityId);
        return false;
    }

    /**
     * Called when the server stops tracking one or more ids for a viewer.
     *
     * @param viewer receiving player
     * @param entityIds destroyed ids
     */
    public void onServerDestroy(Player viewer, int[] entityIds) {
        ViewerPacketState state = state(viewer);
        for (int entityId : entityIds) {
            state.trackedIds.remove(entityId);
            state.clientKnownIds.remove(entityId);
        }
    }

    /**
     * Returns whether ordinary target-specific packets must be suppressed.
     *
     * @param viewer receiving player
     * @param entityId packet target id
     * @return whether the packet must be cancelled
     */
    public boolean isHidden(Player viewer, int entityId) {
        ViewerPacketState state = viewers.get(viewer.getUniqueId());
        return state != null && state.hiddenIds.contains(entityId);
    }

    /**
     * Returns packet/tracker state for one directed pair.
     *
     * @param viewer receiving player
     * @param target target player
     * @return immutable packet diagnostic
     */
    public PacketDebugState inspect(Player viewer, Player target) {
        ViewerPacketState state = viewers.get(viewer.getUniqueId());
        int entityId = target.getEntityId();
        return new PacketDebugState(
                state != null && state.hiddenIds.contains(entityId),
                state != null && state.trackedIds.contains(entityId),
                state != null && state.clientKnownIds.contains(entityId),
                target.getTrackedBy().contains(viewer));
    }

    /**
     * Forgets an obsolete entity id after respawn/id reuse.
     *
     * @param viewer receiving player
     * @param entityId obsolete id
     */
    public void forgetEntity(Player viewer, int entityId) {
        ViewerPacketState state = viewers.get(viewer.getUniqueId());
        if (state != null) {
            state.hiddenIds.remove(entityId);
            state.trackedIds.remove(entityId);
            state.clientKnownIds.remove(entityId);
        }
    }

    /**
     * Removes both viewer state and occurrences of a departing target id.
     *
     * @param player departing player
     */
    public void removePlayer(Player player) {
        viewers.remove(player.getUniqueId());
        int entityId = player.getEntityId();
        for (ViewerPacketState state : viewers.values()) {
            state.hiddenIds.remove(entityId);
            state.trackedIds.remove(entityId);
            state.clientKnownIds.remove(entityId);
        }
    }

    /** Clears all thread-safe packet snapshots. */
    public void clear() {
        viewers.clear();
    }

    private ViewerPacketState state(Player viewer) {
        return viewers.computeIfAbsent(viewer.getUniqueId(), ignored -> new ViewerPacketState());
    }

    private void sendSnapshot(Player viewer, Player target) {
        try {
            org.bukkit.Location bukkitLocation = target.getLocation();
            Vector velocity = target.getVelocity();
            sendSilently(viewer, new WrapperPlayServerSpawnEntity(
                    target.getEntityId(),
                    target.getUniqueId(),
                    EntityTypes.PLAYER,
                    SpigotConversionUtil.fromBukkitLocation(bukkitLocation),
                    bukkitLocation.getYaw(),
                    0,
                    new Vector3d(velocity.getX(), velocity.getY(), velocity.getZ())));

            sendSilently(viewer, new WrapperPlayServerEntityMetadata(
                    target.getEntityId(), SpigotConversionUtil.getEntityMetadata(target)));
            sendScale(viewer, target);
            sendSilently(viewer, new WrapperPlayServerEntityEquipment(target.getEntityId(), equipment(target)));
            for (PotionEffect effect : target.getActivePotionEffects()) {
                PotionType type = SpigotConversionUtil.fromBukkitPotionEffectType(effect.getType());
                if (type == null) {
                    continue;
                }
                byte flags = (byte) ((effect.isAmbient() ? 1 : 0)
                        | (effect.hasParticles() ? 2 : 0)
                        | (effect.hasIcon() ? 4 : 0));
                sendSilently(viewer, new WrapperPlayServerEntityEffect(
                        target.getEntityId(), type, effect.getAmplifier(), effect.getDuration(), flags));
            }
            sendSilently(viewer, new WrapperPlayServerEntityHeadLook(target.getEntityId(), bukkitLocation.getYaw()));
            sendSilently(viewer, new WrapperPlayServerEntityVelocity(
                    target.getEntityId(), new Vector3d(velocity.getX(), velocity.getY(), velocity.getZ())));
            sendPassengerState(viewer, target);
        } catch (RuntimeException exception) {
            state(viewer).clientKnownIds.remove(target.getEntityId());
            logger.log(Level.SEVERE, "Could not respawn " + target.getName() + " for " + viewer.getName(), exception);
        }
    }

    private void sendScale(Player viewer, Player target) {
        AttributeInstance scale = target.getAttribute(Attribute.SCALE);
        if (scale == null) {
            return;
        }
        WrapperPlayServerUpdateAttributes.Property property = new WrapperPlayServerUpdateAttributes.Property(
                Attributes.SCALE, scale.getValue(), Collections.emptyList());
        sendSilently(viewer, new WrapperPlayServerUpdateAttributes(target.getEntityId(), List.of(property)));
    }

    private static List<Equipment> equipment(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<Equipment> equipment = new ArrayList<>(6);
        equipment.add(new Equipment(EquipmentSlot.MAIN_HAND,
                packetItem(inventory.getItemInMainHand())));
        equipment.add(new Equipment(EquipmentSlot.OFF_HAND,
                packetItem(inventory.getItemInOffHand())));
        equipment.add(new Equipment(EquipmentSlot.BOOTS,
                packetItem(inventory.getBoots())));
        equipment.add(new Equipment(EquipmentSlot.LEGGINGS,
                packetItem(inventory.getLeggings())));
        equipment.add(new Equipment(EquipmentSlot.CHEST_PLATE,
                packetItem(inventory.getChestplate())));
        equipment.add(new Equipment(EquipmentSlot.HELMET,
                packetItem(inventory.getHelmet())));
        return equipment;
    }

    private static com.github.retrooper.packetevents.protocol.item.ItemStack packetItem(
            org.bukkit.inventory.ItemStack item) {
        return item == null
                ? com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY
                : SpigotConversionUtil.fromBukkitItemStack(item);
    }

    private void sendPassengerState(Player viewer, Player target) {
        Entity vehicle = target.getVehicle();
        if (vehicle == null || !vehicle.getTrackedBy().contains(viewer)) {
            return;
        }
        int[] passengerIds = vehicle.getPassengers().stream().mapToInt(Entity::getEntityId).toArray();
        sendSilently(viewer, new WrapperPlayServerSetPassengers(vehicle.getEntityId(), passengerIds));
    }

    private static void sendSilently(Player viewer, com.github.retrooper.packetevents.wrapper.PacketWrapper<?> packet) {
        PlayerManager manager = PacketEvents.getAPI().getPlayerManager();
        manager.sendPacketSilently(viewer, packet);
    }

    private static final class ViewerPacketState {
        private final Set<Integer> hiddenIds = ConcurrentHashMap.newKeySet();
        private final Set<Integer> trackedIds = ConcurrentHashMap.newKeySet();
        private final Set<Integer> clientKnownIds = ConcurrentHashMap.newKeySet();
    }
}
