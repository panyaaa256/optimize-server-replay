package com.panyaaa256.optimizeserverreplay;

/**
 * Tells whether every hook into Server Replay and Arcade was applied.
 */
public final class HookStatus {
	private HookStatus() {
	}

	/**
	 * Whether all the hooks are in place. The filters stay disabled when one is missing,
	 * because a recording would lose block updates if, say, they were batched but never flushed.
	 *
	 * <p>Called at the end of the constructor of a Flashback chunk recorder, by which time
	 * all the classes we mix into have been loaded.
	 */
	public static boolean allHooksApplied() {
		// TODO: have OptimizeServerReplayMixinPlugin report the hooks, and warn when one is missing.
		return false;
	}
}
