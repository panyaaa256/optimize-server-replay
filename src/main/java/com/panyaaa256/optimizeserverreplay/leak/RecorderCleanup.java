package com.panyaaa256.optimizeserverreplay.leak;

import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.PlayerAdvancementsAccessor;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.PlayerListAccessor;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.ReplayChunkRecordersAccessor;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.ReplayPlayerRecordersAccessor;
import com.panyaaa256.optimizeserverreplay.save.FlashbackWriterHolder;
import net.casual.arcade.replay.io.writer.ReplayWriter;
import net.casual.arcade.replay.recorder.ReplayRecorder;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorder;
import net.casual.arcade.replay.recorder.player.ReplayPlayerRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * Lets go of what Arcade keeps of a recording after it stops ({@code fix_memory_leaks}).
 */
public final class RecorderCleanup {
	private RecorderCleanup() {
	}

	/**
	 * Arcade takes a recorder off its list of recordings that are saving only once saving
	 * succeeds, and closes the writer only then, so a failed save (a full disk, say) kept the whole
	 * recorder for as long as the server ran, with the buffer of its last replay chunk and the
	 * thread of {@code zip_while_recording}. The recording folder is left as it is.
	 *
	 * <p>Called on the thread that failed to save, after the writer stopped taking tasks.
	 */
	public static void onSaveFailed(ReplayRecorder recorder, ReplayWriter writer) {
		OptimizeServerReplay.LOGGER.warn(
			"Saving the recording {} failed, its folder {} is left as it is", recorder.getName(), recorder.getLocation()
		);
		try {
			if (writer instanceof FlashbackWriterHolder flashback) {
				flashback.osr$releaseAfterFailedSave();
			}
		} catch (Exception e) {
			OptimizeServerReplay.LOGGER.warn("Failed to free the writer of the recording {}", recorder.getName(), e);
		}

		MinecraftServer server = recorder.getServer();
		server.execute(() -> {
			try {
				if (recorder instanceof ReplayChunkRecorder chunk) {
					ReplayChunkRecordersAccessor.osr$closing().remove(chunk);
				} else if (recorder instanceof ReplayPlayerRecorder player) {
					ReplayPlayerRecordersAccessor.osr$closing().remove(player);
				}
			} catch (Exception e) {
				OptimizeServerReplay.LOGGER.warn("Failed to let go of the recording {}", recorder.getName(), e);
			}
		});
	}

	/**
	 * The packet listener of the dummy player stays in Fabric API's networking sessions, see
	 * {@link NetworkingSessions}.
	 *
	 * <p>The dummy player of a chunk recording is made like a real player, so the player list keeps
	 * its advancements, which point back at the player, by its UUID. As every chunk recording
	 * shares that UUID, the advancements kept the last chunk recording that started, with all its
	 * state, after it stopped. They are let go of like the player list does for a player who leaves,
	 * unless a newer chunk recording uses them.
	 *
	 * <p>Called on the server thread.
	 */
	public static void forgetDummy(ReplayChunkRecorder recorder) {
		ServerPlayer dummy = recorder.getDummyPlayer();
		NetworkingSessions.end(dummy.connection);

		UUID uuid = dummy.getUUID();
		Map<UUID, PlayerAdvancements> advancements = ((PlayerListAccessor) recorder.getServer().getPlayerList()).osr$advancements();
		PlayerAdvancements kept = advancements.get(uuid);
		if (kept != null && kept == dummy.getAdvancements() && ((PlayerAdvancementsAccessor) kept).osr$player() == dummy) {
			kept.stopListening();
			advancements.remove(uuid);
		}
	}
}
