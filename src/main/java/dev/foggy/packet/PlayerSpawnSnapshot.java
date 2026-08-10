package dev.foggy.packet;

import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import dev.foggy.platform.PlatformAdapter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Complete immutable player-spawn payload captured on the target's owning entity scheduler.
 * Packet delivery can subsequently occur on a different viewer region without reading target
 * Bukkit state from that region.
 */
public final class PlayerSpawnSnapshot {
    private final int entityId;
    private final UUID playerId;
    private final String name;
    private final com.github.retrooper.packetevents.protocol.world.Location location;
    private final Vector3d velocity;
    private final List<EntityData<?>> metadata;
    private final List<Equipment> equipment;
    private final List<EffectSnapshot> effects;
    private final WrapperPlayServerUpdateAttributes.Property scale;
    private final int vehicleId;
    private final List<Integer> passengerIds;

    /**
     * Creates a captured spawn payload.
     *
     * @param entityId network entity id
     * @param playerId player UUID
     * @param name diagnostic name
     * @param location PacketEvents location
     * @param velocity current velocity
     * @param metadata complete entity metadata
     * @param equipment complete player equipment
     * @param effects active potion effects
     * @param scale scale attribute update, or null when unavailable
     * @param vehicleId vehicle entity id, or -1
     * @param passengerIds complete vehicle passenger list
     */
    public PlayerSpawnSnapshot(int entityId, UUID playerId, String name,
                               com.github.retrooper.packetevents.protocol.world.Location location,
                               Vector3d velocity, List<EntityData<?>> metadata,
                               List<Equipment> equipment, List<EffectSnapshot> effects,
                               @Nullable WrapperPlayServerUpdateAttributes.Property scale,
                               int vehicleId, List<Integer> passengerIds) {
        this.entityId = entityId;
        this.playerId = playerId;
        this.name = name;
        this.location = location;
        this.velocity = velocity;
        this.metadata = Collections.unmodifiableList(new ArrayList<EntityData<?>>(metadata));
        this.equipment = Collections.unmodifiableList(new ArrayList<Equipment>(equipment));
        this.effects = Collections.unmodifiableList(new ArrayList<EffectSnapshot>(effects));
        this.scale = scale;
        this.vehicleId = vehicleId;
        this.passengerIds = Collections.unmodifiableList(new ArrayList<Integer>(passengerIds));
    }

    public int entityId() { return entityId; }
    public UUID playerId() { return playerId; }
    public String name() { return name; }
    public com.github.retrooper.packetevents.protocol.world.Location location() { return location; }
    public Vector3d velocity() { return velocity; }
    public List<EntityData<?>> metadata() { return metadata; }
    public List<Equipment> equipment() { return equipment; }
    public List<EffectSnapshot> effects() { return effects; }
    public @Nullable WrapperPlayServerUpdateAttributes.Property scale() { return scale; }
    public int vehicleId() { return vehicleId; }
    public List<Integer> passengerIds() { return passengerIds; }

    /**
     * Captures a target. The caller must be on the region that owns {@code player}.
     *
     * @param player region-owned target
     * @return immutable spawn payload
     */
    public static PlayerSpawnSnapshot capture(Player player, PlatformAdapter platform) {
        org.bukkit.Location bukkitLocation = player.getLocation();
        Vector bukkitVelocity = player.getVelocity();
        Entity vehicle = player.getVehicle();
        int vehicleId = vehicle == null ? -1 : vehicle.getEntityId();
        List<Integer> passengerIds = new ArrayList<>();
        if (vehicle != null) {
            for (Entity passenger : platform.passengers(vehicle)) {
                passengerIds.add(passenger.getEntityId());
            }
        }
        return new PlayerSpawnSnapshot(
                player.getEntityId(), player.getUniqueId(), player.getName(),
                SpigotConversionUtil.fromBukkitLocation(bukkitLocation),
                new Vector3d(bukkitVelocity.getX(), bukkitVelocity.getY(), bukkitVelocity.getZ()),
                SpigotConversionUtil.getEntityMetadata(player), equipment(player), effects(player),
                scale(player, platform), vehicleId, passengerIds);
    }

    private static @Nullable WrapperPlayServerUpdateAttributes.Property scale(
            Player player, PlatformAdapter platform) {
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_20_5)) {
            return null;
        }
        return new WrapperPlayServerUpdateAttributes.Property(
                Attributes.SCALE, platform.entityScale(player), Collections.emptyList());
    }

    private static List<Equipment> equipment(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<Equipment> result = new ArrayList<>(6);
        result.add(new Equipment(EquipmentSlot.MAIN_HAND, packetItem(mainHand(inventory))));
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) {
            result.add(new Equipment(EquipmentSlot.OFF_HAND, packetItem(offHand(inventory))));
        }
        result.add(new Equipment(EquipmentSlot.BOOTS, packetItem(inventory.getBoots())));
        result.add(new Equipment(EquipmentSlot.LEGGINGS, packetItem(inventory.getLeggings())));
        result.add(new Equipment(EquipmentSlot.CHEST_PLATE, packetItem(inventory.getChestplate())));
        result.add(new Equipment(EquipmentSlot.HELMET, packetItem(inventory.getHelmet())));
        return result;
    }

    private static List<EffectSnapshot> effects(Player player) {
        List<EffectSnapshot> result = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            PotionType type = SpigotConversionUtil.fromBukkitPotionEffectType(effect.getType());
            if (type == null) {
                continue;
            }
            byte flags = (byte) ((effect.isAmbient() ? 1 : 0)
                    | (effect.hasParticles() ? 2 : 0)
                    | (booleanMethod(effect, "hasIcon", false) ? 4 : 0));
            result.add(new EffectSnapshot(type, effect.getAmplifier(), effect.getDuration(), flags));
        }
        return result;
    }

    private static com.github.retrooper.packetevents.protocol.item.ItemStack packetItem(
            org.bukkit.inventory.ItemStack item) {
        return item == null
                ? com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY
                : SpigotConversionUtil.fromBukkitItemStack(item);
    }

    private static org.bukkit.inventory.ItemStack mainHand(PlayerInventory inventory) {
        org.bukkit.inventory.ItemStack item = itemMethod(inventory, "getItemInMainHand");
        return item == null ? inventory.getItemInHand() : item;
    }

    private static org.bukkit.inventory.ItemStack offHand(PlayerInventory inventory) {
        return itemMethod(inventory, "getItemInOffHand");
    }

    private static org.bukkit.inventory.ItemStack itemMethod(PlayerInventory inventory, String name) {
        try {
            Method method = inventory.getClass().getMethod(name);
            return (org.bukkit.inventory.ItemStack) method.invoke(inventory);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean booleanMethod(Object target, String name, boolean fallback) {
        try {
            Method method = target.getClass().getMethod(name);
            return Boolean.TRUE.equals(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    /** Immutable active-effect packet payload. */
    public static final class EffectSnapshot {
        private final PotionType type;
        private final int amplifier;
        private final int duration;
        private final byte flags;

        /**
         * Creates an effect packet payload.
         *
         * @param type protocol potion type
         * @param amplifier effect amplifier
         * @param duration remaining ticks
         * @param flags ambient/particles/icon bit flags
         */
        public EffectSnapshot(PotionType type, int amplifier, int duration, byte flags) {
            this.type = type;
            this.amplifier = amplifier;
            this.duration = duration;
            this.flags = flags;
        }

        public PotionType type() { return type; }
        public int amplifier() { return amplifier; }
        public int duration() { return duration; }
        public byte flags() { return flags; }
    }
}
