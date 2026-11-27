package com.panyaaa256.optimizeserverreplay.mixin.accessor;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.casual.arcade.replay.recorder.player.ReplayPlayerRecorder;
import net.casual.arcade.replay.recorder.player.ReplayPlayerRecorders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The recorders that are saving. Only changed on the server thread.
 */
@Mixin(value = ReplayPlayerRecorders.class, remap = false)
public interface ReplayPlayerRecordersAccessor {
	@Accessor("closing")
	static ObjectOpenHashSet<ReplayPlayerRecorder> osr$closing() {
		throw new AssertionError();
	}
}
