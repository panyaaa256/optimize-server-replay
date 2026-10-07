# Optimize Server Replay

[日本語](README.ja.md)

A Fabric mod that makes the chunk recordings of [Server Replay](https://github.com/senseiwells/ServerReplay) smaller, by leaving out the packets that a timelapse does not need.

## What it does

- Leaves the packets of entities out of a recording (you choose which entity types to keep)
- Leaves block actions out, such as a piston extending or a chest opening
- Records block changes together every few ticks

Every feature is off by default. Turn on the ones you want in the [config](#configuration).

The mod only applies to **chunk recordings in the Flashback format**. It never changes chunk recordings in the ReplayMod format, or player recordings (`/replay start players`).

## Requirements

- Minecraft 1.21.11
- [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer
- Java 21 or newer
- [Server Replay](https://modrinth.com/mod/server-replay) 3.3.1 or newer, for Minecraft 1.21.11
- [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin), which Server Replay needs

Optimize Server Replay is meant to be installed on the server, together with Server Replay. Clients do not need it.

## Configuration

The config file is `config/optimize-server-replay/config.json`. If it does not exist, it is created with the defaults when the server starts.

```json
{
  "ignore_entities": false,
  "entity_whitelist": [
    "minecraft:glow_item_frame",
    "minecraft:hopper_minecart",
    "minecraft:item_frame",
    "minecraft:minecart",
    "minecraft:player"
  ],
  "ignore_block_action": false,
  "block_update_interval_ticks": 0
}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `ignore_entities` | true / false | `false` | Do not record the packets of entities |
| `entity_whitelist` | array of entity ids | the five above | The entity types that are still recorded while `ignore_entities` is `true`. Leave it empty to record no entities at all. Not used while `ignore_entities` is `false` |
| `ignore_block_action` | true / false | `false` | Do not record any block actions |
| `block_update_interval_ticks` | whole number from 0 to 6000 | `0` | Record block changes together once every this many ticks. `0` records them as they happen |

- `/replay reload` reads the config again. The new settings are used by the recordings that start after that. A recording that is already running keeps the settings it started with.
- When a value has the wrong type, a key is missing, or the file is not valid JSON, the mod logs a warning and uses the default. It never rewrites your config file.

For the packets that each setting affects, see [docs/packets.md](docs/packets.md).

### Recording block changes together

With `block_update_interval_ticks` at 1 or more, a block that changed several times in that interval is recorded once, with its last state. A larger value makes the file smaller and the movement of blocks coarser.

- The block actions of pistons are no longer recorded, even when `ignore_block_action` is `false`. A piston's block action also moves the blocks during playback, which would disagree with the block states that were recorded together. Other block actions, such as a chest opening or a note block playing, are still recorded.
- A block that a piston is pushing is recorded once it has stopped moving. A piston and the blocks around it can therefore look out of step for a moment.
- Light updates are not recorded together. If `ignore_light_packets` is `false` in Server Replay, the light can change before the blocks do.

### Players

- Players are entities too, so `ignore_entities` applies to them. The default `entity_whitelist` includes `minecraft:player`, so players are recorded unless you remove it. Without it, players are no longer recorded in the world.
- The tab list is not filtered and is always recorded. A player you removed from the whitelist still shows up there.
- The cracks on a block that is being mined are not filtered either, and are always recorded.

### Recommended Server Replay settings

These settings in `config/server-replay/config.json` work well together with this mod.

- `"default_encoding": "flashback"`: the mod only applies to the Flashback format.
- `"ignore_sound_packets": true`: leaves out sounds, such as those of pistons.
- `"optimize_entity_packets": false`: recommended with `ignore_entities`. The entities are left out anyway, so Server Replay no longer needs to optimize their packets.

## Troubleshooting

When a recording starts, the log may show this warning:

```
Optimize Server Replay is disabled: the installed Server Replay / Arcade versions do not match, ...
```

It means that the installed Server Replay is a version this mod does not support. Every feature is then turned off, and recordings are made just as they would be with Server Replay alone.

## Building from source

You need JDK 25 to run the build (the mod itself is compiled for Java 21).

```sh
./gradlew build
```

On Windows, use `gradlew.bat build` instead of `./gradlew build`.

The mod is built to `build/libs/optimize-server-replay-<version>.jar`.

## License

[MIT](LICENSE)
