package com.panyaaa256.optimizeserverreplay.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
//? if >=26.2
import net.minecraft.world.entity.EntityTypes;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

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
			EntityTypes.PLAYER,
			EntityTypes.MINECART,
			EntityTypes.HOPPER_MINECART,
			EntityTypes.ITEM_FRAME,
			EntityTypes.GLOW_ITEM_FRAME
		),
		false,
		0
	);

	private static volatile FilterConfig current = DEFAULT;

	public static FilterConfig current() {
		return current;
	}

	public static void reload() {
		try {
			Path file = FabricLoader.getInstance().getConfigDir()
				.resolve(OptimizeServerReplay.MOD_ID).resolve("config.json");
			if (Files.notExists(file)) {
				writeDefault(file);
				current = DEFAULT;
				return;
			}
			current = read(file);
		} catch (Exception e) {
			OptimizeServerReplay.LOGGER.warn("Could not load the config, using the default settings", e);
			current = DEFAULT;
		}
	}

	private static void writeDefault(Path file) {
		JsonObject json = new JsonObject();
		json.addProperty("ignore_entities", DEFAULT.ignoreEntities);
		JsonArray whitelist = new JsonArray();
		// Set.of has no stable order, so write the ids in the order of the registry.
		BuiltInRegistries.ENTITY_TYPE.stream()
			.filter(DEFAULT.entityWhitelist::contains)
			.forEach(type -> whitelist.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
		json.add("entity_whitelist", whitelist);
		json.addProperty("ignore_block_action", DEFAULT.ignoreBlockAction);
		json.addProperty("block_update_interval_ticks", DEFAULT.blockUpdateIntervalTicks);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
			OptimizeServerReplay.LOGGER.info("Created the default config at {}", file);
		} catch (IOException e) {
			OptimizeServerReplay.LOGGER.warn("Could not create the config at {}, using the default settings", file, e);
		}
	}

	private static FilterConfig read(Path file) {
		JsonElement root;
		try (Reader reader = Files.newBufferedReader(file)) {
			root = JsonParser.parseReader(reader);
		} catch (IOException | RuntimeException e) {
			OptimizeServerReplay.LOGGER.warn("Could not read the config at {}, using the default settings", file, e);
			return DEFAULT;
		}
		if (!root.isJsonObject()) {
			OptimizeServerReplay.LOGGER.warn("The config at {} is not a JSON object, using the default settings", file);
			return DEFAULT;
		}
		JsonObject json = root.getAsJsonObject();
		return new FilterConfig(
			readBoolean(json, "ignore_entities", DEFAULT.ignoreEntities),
			readWhitelist(json),
			readBoolean(json, "ignore_block_action", DEFAULT.ignoreBlockAction),
			readInterval(json)
		);
	}

	private static boolean readBoolean(JsonObject json, String key, boolean fallback) {
		JsonElement value = json.get(key);
		if (value == null) {
			OptimizeServerReplay.LOGGER.warn("The config has no \"{}\", using {}", key, fallback);
			return fallback;
		}
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
			return value.getAsBoolean();
		}
		OptimizeServerReplay.LOGGER.warn("\"{}\" must be true or false, using {}", key, fallback);
		return fallback;
	}

	private static int readInterval(JsonObject json) {
		String key = "block_update_interval_ticks";
		int fallback = DEFAULT.blockUpdateIntervalTicks;
		JsonElement value = json.get(key);
		if (value == null) {
			OptimizeServerReplay.LOGGER.warn("The config has no \"{}\", using {}", key, fallback);
			return fallback;
		}
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			BigDecimal number = value.getAsBigDecimal();
			// The zeros are stripped first, so that 10.0 and 1e2 count as whole numbers.
			if (number.stripTrailingZeros().scale() <= 0) {
				if (number.signum() < 0) {
					OptimizeServerReplay.LOGGER.warn("\"{}\" must not be negative, using 0", key);
					return 0;
				}
				if (number.compareTo(BigDecimal.valueOf(MAX_BLOCK_UPDATE_INTERVAL_TICKS)) > 0) {
					OptimizeServerReplay.LOGGER.warn("\"{}\" is above {}, using {}", key,
						MAX_BLOCK_UPDATE_INTERVAL_TICKS, MAX_BLOCK_UPDATE_INTERVAL_TICKS);
					return MAX_BLOCK_UPDATE_INTERVAL_TICKS;
				}
				return number.intValue();
			}
		}
		OptimizeServerReplay.LOGGER.warn("\"{}\" must be a whole number, using {}", key, fallback);
		return fallback;
	}

	private static Set<EntityType<?>> readWhitelist(JsonObject json) {
		String key = "entity_whitelist";
		JsonElement value = json.get(key);
		if (value == null) {
			OptimizeServerReplay.LOGGER.warn("The config has no \"{}\", using the default list", key);
			return DEFAULT.entityWhitelist;
		}
		if (!value.isJsonArray()) {
			OptimizeServerReplay.LOGGER.warn("\"{}\" must be an array of entity ids, using the default list", key);
			return DEFAULT.entityWhitelist;
		}
		Set<EntityType<?>> types = new LinkedHashSet<>();
		for (JsonElement element : value.getAsJsonArray()) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				OptimizeServerReplay.LOGGER.warn("Ignoring {} in \"{}\": not a string", element, key);
				continue;
			}
			String text = element.getAsString();
			Identifier id = Identifier.tryParse(text);
			Optional<EntityType<?>> type = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
			if (type.isPresent()) {
				types.add(type.get());
			} else {
				OptimizeServerReplay.LOGGER.warn("Ignoring unknown entity id \"{}\" in \"{}\"", text, key);
			}
		}
		return Collections.unmodifiableSet(types);
	}
}
