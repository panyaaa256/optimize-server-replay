package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.HookStatus;
import com.panyaaa256.optimizeserverreplay.RecordingFilterHolder;
import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.TrackedEntityAccessor;
import com.panyaaa256.optimizeserverreplay.snapshot.TrackedEntities;
import net.casual.arcade.replay.recorder.ChunkSender;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecordable;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorder;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

// Everything here runs on the server thread.
@Mixin(value = ReplayChunkRecorder.class, remap = false)
public abstract class ReplayChunkRecorderMixin {
	// Set while sendChunks sends the chunks of the recording.
	@Unique
	private @Nullable TrackedEntities osr$entities;

	@Inject(method = "sendChunks", at = @At("HEAD"))
	private void osr$onSendChunks(CallbackInfo ci) {
		this.osr$entities = this.osr$isOptimized() ? TrackedEntities.of(this.osr$level()) : null;
	}

	@Inject(method = "sendChunks", at = @At("RETURN"))
	private void osr$onSendChunksEnd(CallbackInfo ci) {
		this.osr$entities = null;
	}

	/**
	 * What {@code ChunkSender.sendChunk} does, with the entities of the chunk taken from
	 * {@link #osr$entities}. Snapshots and the start of a recording send their chunks through
	 * sendChunks; a chunk sent on its own is left to Arcade.
	 */
	@Inject(method = "sendChunk", at = @At("HEAD"), cancellable = true)
	private void osr$onSendChunk(LevelChunk chunk, ChunkSender.SeenEntities seen, CallbackInfo ci) {
		ServerLevel level = this.osr$level();
		if (this.osr$entities == null) {
			return;
		}
		if (!this.osr$entities.isCurrent(level)) {
			this.osr$entities = TrackedEntities.of(level);
		}

		ChunkSender sender = (ChunkSender) this;
		sender.sendChunkPacket(new ClientboundLevelChunkWithLightPacket(chunk, chunk.getLevel().getLightEngine(), null, null));

		List<Mob> leashed = new ArrayList<>();
		List<Entity> ridden = new ArrayList<>();
		int viewDistance = level.getServer().getPlayerList().getViewDistance();
		for (Object tracked : this.osr$entities.in(chunk.getPos().toLong())) {
			TrackedEntityAccessor accessor = (TrackedEntityAccessor) tracked;
			Entity entity = accessor.osr$entity();
			if (!seen.has(entity.getId())) {
				double range = Math.min(accessor.osr$range(), viewDistance * 16);
				if (sender.shouldTrackEntity(entity, range)) {
					// What ReplayChunkRecorder.addTrackedEntity does.
					((ReplayChunkRecordable) tracked).addRecorder((ReplayChunkRecorder) (Object) this);
					seen.add(entity.getId());
				}
			}

			if (entity instanceof Mob mob && mob.getLeashHolder() != null) {
				leashed.add(mob);
			}
			if (!entity.getPassengers().isEmpty()) {
				ridden.add(entity);
			}
		}
		for (Mob mob : leashed) {
			sender.sendChunkPacket(new ClientboundSetEntityLinkPacket(mob, mob.getLeashHolder()));
		}
		for (Entity entity : ridden) {
			sender.sendChunkPacket(new ClientboundSetPassengersPacket(entity));
		}

		sender.onChunkSent(chunk);
		ci.cancel();
	}

	@Unique
	private boolean osr$isOptimized() {
		FilterConfig config = ((RecordingFilterHolder) this).osr$config();
		return config != null && config.optimizeSnapshot() && HookStatus.snapshotHooksApplied();
	}

	@Unique
	private ServerLevel osr$level() {
		return ((ChunkSender) this).getLevel();
	}
}
