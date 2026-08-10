package dev.foggy.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCollectItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDamageEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMovement;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHurtAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import org.bukkit.entity.Player;

/** Cancels ordinary entity packets for ids hidden from one concrete viewer. */
public final class FoggyPacketListener extends PacketListenerAbstract {
    private final PacketVisibilityController controller;

    /**
     * Creates the final-decision PacketEvents listener.
     *
     * @param controller visibility snapshot/controller
     */
    public FoggyPacketListener(PacketVisibilityController controller) {
        super(PacketListenerPriority.HIGHEST);
        this.controller = controller;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player) || event.isCancelled()) {
            return;
        }
        Player viewer = (Player) event.getPlayer();
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Server.DESTROY_ENTITIES) {
            int[] ids = new WrapperPlayServerDestroyEntities(event).getEntityIds();
            controller.onServerDestroy(viewer, ids);
            return;
        }
        if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            int entityId = new WrapperPlayServerSpawnEntity(event).getEntityId();
            event.setCancelled(controller.onServerSpawn(viewer, entityId));
            return;
        }
        if (type == PacketType.Play.Server.SPAWN_PLAYER) {
            int entityId = new WrapperPlayServerSpawnPlayer(event).getEntityId();
            event.setCancelled(controller.onServerSpawn(viewer, entityId));
            return;
        }
        int entityId = targetEntityId(type, event);
        if (entityId != Integer.MIN_VALUE && controller.isHidden(viewer, entityId)) {
            event.setCancelled(true);
        }
    }

    private static int targetEntityId(PacketTypeCommon type, PacketSendEvent event) {
        if (type == PacketType.Play.Server.ENTITY_ANIMATION) {
            return new WrapperPlayServerEntityAnimation(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_STATUS) {
            return new WrapperPlayServerEntityStatus(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            return new WrapperPlayServerEntityRelativeMove(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            return new WrapperPlayServerEntityRelativeMoveAndRotation(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_ROTATION) {
            return new WrapperPlayServerEntityRotation(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_MOVEMENT) {
            return new WrapperPlayServerEntityMovement(event).getEntityId();
        } else if (type == PacketType.Play.Server.REMOVE_ENTITY_EFFECT) {
            return new WrapperPlayServerRemoveEntityEffect(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            return new WrapperPlayServerEntityHeadLook(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_METADATA) {
            return new WrapperPlayServerEntityMetadata(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_VELOCITY) {
            return new WrapperPlayServerEntityVelocity(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            return new WrapperPlayServerEntityEquipment(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
            return new WrapperPlayServerEntityTeleport(event).getEntityId();
        } else if (type == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
            return new WrapperPlayServerUpdateAttributes(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_EFFECT) {
            return new WrapperPlayServerEntityEffect(event).getEntityId();
        } else if (type == PacketType.Play.Server.HURT_ANIMATION) {
            return new WrapperPlayServerHurtAnimation(event).getEntityId();
        } else if (type == PacketType.Play.Server.DAMAGE_EVENT) {
            return new WrapperPlayServerDamageEvent(event).getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
            return new WrapperPlayServerEntitySoundEffect(event).getEntityId();
        } else if (type == PacketType.Play.Server.COLLECT_ITEM) {
            return new WrapperPlayServerCollectItem(event).getCollectorEntityId();
        }
        return Integer.MIN_VALUE;
    }
}
