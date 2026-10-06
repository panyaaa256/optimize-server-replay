package com.panyaaa256.optimizeserverreplay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tells whether every hook into Server Replay and Arcade was applied.
 *
 * <p>This is loaded very early, so it must not refer to any Minecraft class. It has its own logger
 * for the same reason, rather than using the one of {@link OptimizeServerReplay}.
 */
public final class HookStatus {
	/**
	 * The names of all the hooks, as {@code Class#method}. The mixin plugin reports each one
	 * once it has found the hook in the applied class.
	 */
	public static final Set<String> ALL_HOOKS = Set.of(
		"ReplayRecorder#<init>",
		"ReplayRecorder#canRecordPacket",
		"ReplayRecorder#tick",
		"ReplayRecorder#pause(Z)",
		"ReplayRecorder#stop(Z)",
		"FlashbackWriter#startNewReplayChunk",
		"ServerReplay#reload"
	);

	private static final Logger LOGGER = LoggerFactory.getLogger("optimize-server-replay");

	private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();
	private static final AtomicBoolean WARNED = new AtomicBoolean();

	private HookStatus() {
	}

	/**
	 * Records that a hook was found in its applied target class.
	 *
	 * @param hook one of {@link #ALL_HOOKS}
	 */
	public static void markApplied(String hook) {
		APPLIED.add(hook);
	}

	/**
	 * Whether all the hooks are in place. The filters stay disabled when one is missing,
	 * because a recording would lose block updates if, say, they were batched but never flushed.
	 *
	 * <p>Called at the end of the constructor of a Flashback chunk recorder, by which time
	 * all the classes we mix into have been loaded.
	 */
	public static boolean allHooksApplied() {
		if (APPLIED.containsAll(ALL_HOOKS)) {
			return true;
		}

		// Recordings are created many times, but the warning is only for the first.
		if (WARNED.compareAndSet(false, true)) {
			Set<String> missing = new TreeSet<>(ALL_HOOKS);
			missing.removeAll(APPLIED);
			LOGGER.warn(
				"Optimize Server Replay is disabled: the installed Server Replay / Arcade versions do not match, "
					+ "so these hooks were not applied: {}. Recordings are written unfiltered.",
				missing
			);
		}
		return false;
	}
}
