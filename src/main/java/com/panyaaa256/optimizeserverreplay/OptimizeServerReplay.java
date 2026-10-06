package com.panyaaa256.optimizeserverreplay;

import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizeServerReplay implements ModInitializer {
	public static final String MOD_ID = "optimize-server-replay";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		FilterConfig.reload();
	}
}
