package dev.foggy.packet;

import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
public record PlayerSpawnSnapshot(
        int entityId,
        UUID playerId,
        String name,
        com.github.retrooper.packetevents.protocol.world.Location location,
        Vector3d velocity,
        List<EntityData<?>> metadata,
        List<Equipment> equipment,
        List<EffectSnapshot> effects,
        @Nullable WrapperPlayServerUpdateAttributes.Property scale,
        int vehicleId,
        List<Integer> passengerIds
) {
    /** Defensively freezes collection payloads before cross-region publication. */
    public PlayerSpawnSnapshot {
        metadata = List.copyOf(metadata);
        equipment = List.copyOf(equipment);
        effects = List.copyOf(effects);
        passengerIds = List.copyOf(passengerIds);
    }

    /**
     * Captures a target. The caller must be on the region that owns {@code player}.
     *
     * @param player region-owned target
     * @return immutable spawn payload
     */
    public static PlayerSpawnSnapshot capture(Player player) {
        org.bukkit.Location bukkitLocation = player.getLocation();
        Vector bukkitVelocity = player.getVelocity();
        Entity vehicle = player.getVehicle();
        int vehicleId = vehicle == null ? -1 : vehicle.getEntityId();
        List<Integer> passengerIds = vehicle == null ? List.of()
                : vehicle.getPassengers().stream().map(Entity::getEntityId).toList();
        return new PlayerSpawnSnapshot(
                player.getEntityId(), player.getUniqueId(), player.getName(),
                SpigotConversionUtil.fromBukkitLocation(bukkitLocation),
                new Vector3d(bukkitVelocity.getX(), bukkitVelocity.getY(), bukkitVelocity.getZ()),
                SpigotConversionUtil.getEntityMetadata(player), equipment(player), effects(player),
                scale(player), vehicleId, passengerIds);
    }

    private static @Nullable WrapperPlayServerUpdateAttributes.Property scale(Player player) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        return scale == null ? null : new WrapperPlayServerUpdateAttributes.Property(
                Attributes.SCALE, scale.getValue(), Collections.emptyList());
    }

    private static List<Equipment> equipment(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<Equipment> result = new ArrayList<>(6);
        result.add(new Equipment(EquipmentSlot.MAIN_HAND, packetItem(inventory.getItemInMainHand())));
        result.add(new Equipment(EquipmentSlot.OFF_HAND, packetItem(inventory.getItemInOffHand())));
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
                    | (effect.hasIcon() ? 4 : 0));
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

    /**
     * Immutable active-effect packet payload.
     *
     * @param type protocol potion type
     * @param amplifier effect amplifier
     * @param duration remaining ticks
     * @param flags ambient/particles/icon bit flags
     */
    public record EffectSnapshot(PotionType type, int amplifier, int duration, byte flags) {
    }
}
