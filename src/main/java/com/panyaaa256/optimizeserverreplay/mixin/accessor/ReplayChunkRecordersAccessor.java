package com.panyaaa256.optimizeserverreplay.mixin.accessor;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorder;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The recorders that are saving. Only changed on the server thread.
 */
@Mixin(value = ReplayChunkRecorders.class, remap = false)
public interface ReplayChunkRecordersAccessor {
	@Accessor("closing")
	static ObjectOpenHashSet<ReplayChunkRecorder> osr$closing() {
		throw new AssertionError();
	}
}
