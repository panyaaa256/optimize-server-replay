package com.panyaaa256.optimizeserverreplay.entity;

import com.panyaaa256.optimizeserverreplay.mixin.accessor.ClientboundEntityEventPacketAccessor;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.ClientboundMoveEntityPacketAccessor;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.ClientboundRotateHeadPacketAccessor;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;

/**
 * The kinds are small ints so that looking one up does not allocate. Packets are matched by
 * their exact class, so the nested classes of {@link ClientboundMoveEntityPacket} are listed
 * on their own.
 */
final class EntityPackets {
	static final int NOT_ENTITY = -1;
	static final int ADD = 0;
	static final int REMOVE = 1;
	static final int PASSENGERS = 2;
	static final int LINK = 3;
	static final int TAKE_ITEM = 4;
	static final int SINGLE = 5;

	private static final Reference2IntOpenHashMap<Class<?>> KINDS = new Reference2IntOpenHashMap<>();

	static {
		KINDS.defaultReturnValue(NOT_ENTITY);
		KINDS.put(ClientboundAddEntityPacket.class, ADD);
		KINDS.put(ClientboundRemoveEntitiesPacket.class, REMOVE);
		KINDS.put(ClientboundSetPassengersPacket.class, PASSENGERS);
		KINDS.put(ClientboundSetEntityLinkPacket.class, LINK);
		KINDS.put(ClientboundTakeItemEntityPacket.class, TAKE_ITEM);

		Class<?>[] singles = {
			ClientboundMoveEntityPacket.Pos.class,
			ClientboundMoveEntityPacket.Rot.class,
			ClientboundMoveEntityPacket.PosRot.class,
			ClientboundEntityPositionSyncPacket.class,
			ClientboundTeleportEntityPacket.class,
			ClientboundSetEntityMotionPacket.class,
			ClientboundSetEntityDataPacket.class,
			ClientboundRotateHeadPacket.class,
			ClientboundSetEquipmentPacket.class,
			ClientboundUpdateAttributesPacket.class,
			ClientboundEntityEventPacket.class,
			ClientboundAnimatePacket.class,
			ClientboundHurtAnimationPacket.class,
			ClientboundDamageEventPacket.class,
			ClientboundUpdateMobEffectPacket.class,
			ClientboundRemoveMobEffectPacket.class,
			ClientboundProjectilePowerPacket.class
		};
		for (Class<?> type : singles) {
			KINDS.put(type, SINGLE);
		}
	}

	private EntityPackets() {
	}

	static int kindOf(Packet<?> packet) {
		return KINDS.getInt(packet.getClass());
	}

	static int singleId(Packet<?> packet) {
		return switch (packet) {
			case ClientboundMoveEntityPacket move -> ((ClientboundMoveEntityPacketAccessor) move).osr$getEntityId();
			case ClientboundEntityPositionSyncPacket sync -> sync.id();
			case ClientboundTeleportEntityPacket teleport -> teleport.id();
			//? if >=26.1 {
			/*case ClientboundSetEntityMotionPacket motion -> motion.id();
			*///?} else
			case ClientboundSetEntityMotionPacket motion -> motion.getId();
			case ClientboundSetEntityDataPacket data -> data.id();
			case ClientboundRotateHeadPacket head -> ((ClientboundRotateHeadPacketAccessor) head).osr$getEntityId();
			case ClientboundSetEquipmentPacket equipment -> equipment.getEntity();
			case ClientboundUpdateAttributesPacket attributes -> attributes.getEntityId();
			case ClientboundEntityEventPacket event -> ((ClientboundEntityEventPacketAccessor) event).osr$getEntityId();
			case ClientboundAnimatePacket animate -> animate.getId();
			case ClientboundHurtAnimationPacket hurt -> hurt.id();
			case ClientboundDamageEventPacket damage -> damage.entityId();
			case ClientboundUpdateMobEffectPacket update -> update.getEntityId();
			case ClientboundRemoveMobEffectPacket remove -> remove.entityId();
			case ClientboundProjectilePowerPacket power -> power.getId();
			default -> throw new IllegalArgumentException("Not a single-id entity packet: " + packet.getClass());
		};
	}
}
