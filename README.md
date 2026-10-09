# Optimize Server Replay

[日本語](README.ja.md)

An addon for [Server Replay](https://github.com/senseiwells/ServerReplay) that reduces the load in various situations, by leaving the packets you choose out of its chunk recordings.

## Packets that can be left out

- Packets about entities (you can choose which entities to keep)
- Block actions, such as a piston extending or a chest opening
- Block change packets, by recording them together once every given number of ticks (the interval is adjustable)

Every feature is off by default. Turn on the ones you want in the [config](#configuration).

The mod only applies to **chunk recordings in the Flashback format**. It does not work for chunk recordings in the ReplayMod format, or for player recordings (`/replay start players`).

## Requirements

- Minecraft 1.21.11, 26.1, 26.2 or 26.3, with the jar of the mod built for that version
- [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer
- Java 21 or newer (Java 25 or newer for 26.1 and later)
- [Server Replay](https://modrinth.com/mod/server-replay) for the same Minecraft version, at least the version below
- [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin), which Server Replay needs

| Minecraft | Server Replay |
|---|---|
| 1.21.11 | 3.3.1 |
| 26.1 | 3.4.3 |
| 26.2 | 3.5.2 |
| 26.3 | 3.6.0-beta.3 |

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

You need JDK 25 to run the build.

```sh
./gradlew build
```

On Windows, use `gradlew.bat build` instead of `./gradlew build`.

The mod is built for every supported Minecraft version, to `versions/<minecraft version>/build/libs/optimize-server-replay-<version>+<minecraft version>.jar`.

The versions share one source tree through [Stonecutter](https://stonecutter.kikugie.dev/). The code for other versions than the active one (26.3 in the repository) is kept in `//?` comments. To edit the code of another version, switch the active version with the `"Set active project to <version>"` Gradle task, and switch back to 26.3 before committing.

## License

[MIT](LICENSE)
