package com.panyaaa256.optimizeserverreplay.tracking;

import com.panyaaa256.optimizeserverreplay.HookStatus;
import com.panyaaa256.optimizeserverreplay.config.FilterConfig;

/**
 * Implemented by {@code TrackedEntityMixin}, on the entities that the level tracks.
 *
 * <p>Whenever a player moves to another section, the level updates every entity it tracks, and
 * Arcade then goes through every chunk recording for each of them to see whether it should record
 * the entity ({@code ReplayChunkRecorders.updateRecordable}). The answer only changes when the
 * entity moves to another chunk, or a chunk recording starts or stops, so the last one is kept.
 */
public interface RecordableCache {
	boolean osr$isUpToDate(Object level, long chunk, int generation);

	void osr$markUpToDate(Object level, long chunk, int generation);

	/**
	 * Changed when a chunk recording is added or removed. Only used on the server thread, like
	 * the list of chunk recordings is changed and read on it.
	 */
	final class Generation {
		private static int current = 0;
		private static int hooksApplied = -1;

		private Generation() {
		}

		public static int current() {
			return current;
		}

		public static void next() {
			current++;
		}

		/**
		 * Every hook is in ReplayChunkRecorders, which is set up by the time this is first called.
		 */
		public static boolean enabled() {
			if (!FilterConfig.current().optimizeEntityTracking()) {
				return false;
			}
			if (hooksApplied < 0) {
				hooksApplied = HookStatus.handlersApplied(
					"Caching which recordings record an entity (optimize_entity_tracking)",
					HookStatus.RECORDABLE_CACHE_HOOKS,
					"Arcade checks every recording for every entity as usual."
				) ? 1 : 0;
			}
			return hooksApplied == 1;
		}
	}
}
