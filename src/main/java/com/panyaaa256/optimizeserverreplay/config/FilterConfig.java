package com.panyaaa256.optimizeserverreplay.config;

import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * The settings of the packet filters, as read from {@code config/optimize-server-replay/config.json}.
 *
 * @param ignoreEntities whether packets about entities are left out of chunk recordings
 * @param entityWhitelist the entity types that are still recorded while {@code ignoreEntities} is on
 * @param ignoreBlockAction whether all block actions are left out
 * @param blockUpdateIntervalTicks how many ticks of block updates are batched together, 0 to record them as they come
 */
public record FilterConfig(
	boolean ignoreEntities,
	Set<EntityType<?>> entityWhitelist,
	boolean ignoreBlockAction,
	int blockUpdateIntervalTicks
) {
	/** The length of a Flashback replay chunk, which a batch of block updates cannot outlast. */
	public static final int MAX_BLOCK_UPDATE_INTERVAL_TICKS = 6000;

	public static final FilterConfig DEFAULT = new FilterConfig(
		false,
		Set.of(
			EntityType.PLAYER,
			EntityType.MINECART,
			EntityType.HOPPER_MINECART,
			EntityType.ITEM_FRAME,
			EntityType.GLOW_ITEM_FRAME
		),
		false,
		0
	);

	private static volatile FilterConfig current = DEFAULT;

	/**
	 * The config as of the last {@link #reload()}. A recording keeps the one it started with.
	 */
	public static FilterConfig current() {
		return current;
	}

	/**
	 * Reads the config file again. Runs when the mod is initialized and on {@code /replay reload}.
	 */
	public static void reload() {
		// TODO: read, validate and create the config file.
	}
}
