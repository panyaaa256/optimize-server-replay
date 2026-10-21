package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.HookStatus;
import com.panyaaa256.optimizeserverreplay.RecordingFilterHolder;
import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import com.panyaaa256.optimizeserverreplay.snapshot.TrackedEntities;
import net.casual.arcade.replay.recorder.ChunkSender;
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
	// Set while the chunks of a snapshot are being sent.
	@Unique
	private @Nullable TrackedEntities osr$entities;

	@Inject(method = "sendChunks", at = @At("HEAD"))
	private void osr$onSendChunks(CallbackInfo ci) {
		FilterConfig config = ((RecordingFilterHolder) this).osr$config();
		this.osr$entities = config != null && config.optimizeSnapshot() && HookStatus.snapshotHooksApplied()
			? TrackedEntities.of(this.osr$level())
			: null;
	}

	@Inject(method = "sendChunks", at = @At("RETURN"))
	private void osr$onSendChunksEnd(CallbackInfo ci) {
		this.osr$entities = null;
	}

	/**
	 * What {@code ChunkSender.sendChunk} does for a snapshot, with the entities of the chunk
	 * taken from {@link #osr$entities}. A snapshot sees every entity as already sent, so all that
	 * is left of the entities is to send their leashes and passengers again.
	 */
	@Inject(method = "sendChunk", at = @At("HEAD"), cancellable = true)
	private void osr$onSendChunk(LevelChunk chunk, ChunkSender.SeenEntities seen, CallbackInfo ci) {
		ServerLevel level = this.osr$level();
		if (seen != ChunkSender.SeenEntities.Companion.all()) {
			return;
		}
		if (this.osr$entities != null && !this.osr$entities.isCurrent(level)) {
			this.osr$entities = TrackedEntities.of(level);
		}
		if (this.osr$entities == null) {
			return;
		}

		ChunkSender sender = (ChunkSender) this;
		sender.sendChunkPacket(new ClientboundLevelChunkWithLightPacket(chunk, chunk.getLevel().getLightEngine(), null, null));

		List<Mob> leashed = new ArrayList<>();
		List<Entity> ridden = new ArrayList<>();
		for (Entity entity : this.osr$entities.in(chunk.getPos().toLong())) {
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
	private ServerLevel osr$level() {
		return ((ChunkSender) this).getLevel();
	}
}
