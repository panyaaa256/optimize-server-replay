package com.panyaaa256.optimizeserverreplay.idle;

import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import com.panyaaa256.optimizeserverreplay.RecordingFilterHolder;
import net.casual.arcade.replay.recorder.ReplayRecorder;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorder;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tells the players about the pauses and resumes of {@link IdlePause} the way Server Replay tells
 * them about its own: with the same message, to everyone, and only while its
 * {@code pause_notify_players} is on.
 *
 * <p>Server Replay is not on the compile classpath, so its config is read through reflection.
 */
final class PauseNotifier {
	private static final AtomicBoolean WARNED = new AtomicBoolean();
	private static final @Nullable MethodHandle NOTIFIES_PLAYERS = findNotifiesPlayers();

	private PauseNotifier() {
	}

	static void paused(ReplayChunkRecorder recorder) {
		notify(recorder, "Paused recording for " + recorder.getName());
	}

	static void resumed(ReplayChunkRecorder recorder) {
		notify(recorder, "Resumed recording for " + recorder.getName());
	}

	private static void notify(ReplayRecorder recorder, String text) {
		if (!notifiesPlayers()) {
			return;
		}
		Component message = Component.literal(text);
		// Like Server Replay, so that the message is not recorded itself.
		((RecordingFilterHolder) recorder).osr$withoutRecording(
			() -> recorder.getServer().getPlayerList().broadcastSystemMessage(message, false)
		);
	}

	private static boolean notifiesPlayers() {
		if (NOTIFIES_PLAYERS == null) {
			return false;
		}
		try {
			return (boolean) NOTIFIES_PLAYERS.invokeExact();
		} catch (Throwable e) {
			warn(e);
			return false;
		}
	}

	private static @Nullable MethodHandle findNotifiesPlayers() {
		try {
			Class<?> serverReplay = Class.forName("me.senseiwells.replay.ServerReplay");
			Class<?> config = Class.forName("me.senseiwells.replay.config.ReplayConfig");
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle getConfig = lookup.findStatic(serverReplay, "getConfig", MethodType.methodType(config));
			MethodHandle notifies = lookup.findVirtual(config, "getNotifyPlayersLoadingChunks", MethodType.methodType(boolean.class));
			return MethodHandles.filterReturnValue(getConfig, notifies);
		} catch (ReflectiveOperationException | RuntimeException e) {
			warn(e);
			return null;
		}
	}

	private static void warn(Throwable e) {
		if (WARNED.compareAndSet(false, true)) {
			OptimizeServerReplay.LOGGER.warn(
				"Could not read pause_notify_players of Server Replay, so players are not told when a recording is paused for being idle",
				e
			);
		}
	}
}
