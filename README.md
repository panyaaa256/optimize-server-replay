# Optimize Server Replay

[日本語](README.ja.md)

An addon for [Server Replay](https://github.com/senseiwells/ServerReplay) that reduces the load in various situations, by leaving the packets you choose out of its chunk recordings.

## Packets that can be left out

- Packets about entities (you can choose which entities to keep)
- Block actions, such as a piston extending or a chest opening
- Block change packets, by recording them together once every given number of ticks (the interval is adjustable)

A recording can also be paused when nothing changes in its area for a while.

Every feature is off by default. Turn on the ones you want in the [config](#configuration).

The mod only applies to **chunk recordings in the Flashback format**. It does not work for chunk recordings in the ReplayMod format, or for player recordings (`/replay start players`).

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
  "block_update_interval_ticks": 0,
  "optimize_saving": true,
  "zip_while_recording": false,
  "optimize_snapshot": true,
  "zip_compression_level": 1,
  "pause_when_idle_seconds": 0,
  "idle_ignore_entities": true,
  "idle_entity_whitelist": [
    "minecraft:player"
  ]
}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `ignore_entities` | true / false | `false` | Do not record the packets of entities |
| `entity_whitelist` | array of entity ids | the five above | The entity types that are still recorded while `ignore_entities` is `true`. Leave it empty to record no entities at all. Not used while `ignore_entities` is `false` |
| `ignore_block_action` | true / false | `false` | Do not record any block actions |
| `block_update_interval_ticks` | whole number from 0 to 6000 | `0` | Record block changes together once every this many ticks. `0` records them as they happen |
| `optimize_saving` | true / false | `true` | Save recordings faster. The content of the recording stays the same (see [Faster saving](#faster-saving)) |
| `zip_while_recording` | true / false | `false` | Zip a recording while it records (see [Zipping while recording](#zipping-while-recording)) |
| `optimize_snapshot` | true / false | `true` | Shorten the time the server stops when a recording starts, and for the snapshot taken every 5 minutes. The content of the recording stays the same (see [Faster snapshots](#faster-snapshots)) |
| `zip_compression_level` | whole number from 0 to 9 | `1` | The zip compression level a recording is saved with. A higher level makes the file smaller and the save slower. `0` stores the files without compressing them. `1` is what Server Replay uses |
| `pause_when_idle_seconds` | whole number from 0 to 86400 | `0` | Pause a recording when the blocks and entities in its area have not changed for this many seconds. `0` never pauses it (see [Pausing when nothing changes](#pausing-when-nothing-changes)) |
| `idle_ignore_entities` | true / false | `true` | Do not count the changes of entities for `pause_when_idle_seconds`. The types in `idle_entity_whitelist` still count |
| `idle_entity_whitelist` | array of entity ids | `["minecraft:player"]` | The entity types whose changes still count while `idle_ignore_entities` is `true` |

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

### Faster saving

With `optimize_saving` at `true`, recordings are saved differently. Apart from the missing `metadata.json.old`, the unzipped files are the same as with Server Replay alone.

- The zip of a recording is written in larger pieces.
- Each chunk of terrain that is cached no longer waits until it is on the disk, just like the other files of the recording.
- `metadata.json.old`, which is only needed while recording, is left out of the zip.

### Zipping while recording

With `zip_while_recording` at `true`, the files of a recording go into its zip while it records, as soon as they are written, and are deleted from the recording folder. These are the 5 minute replay chunks and the terrain caches that are full.

- Few files are left when the recording stops, so saving is over quickly.
- While recording, the recording takes about as much disk space as its zip, rather than the unzipped recording and the zip together.
- The zip that is being written is `osr-partial.zip` in the recording folder. Saving moves it to `<recording name>.zip`.
- When the server goes down during a recording, the folder holds the unfinished zip and the remaining files. With this mod installed, it is [recovered](#recovering-recordings-after-a-crash) on the next start, but zipping the folder by hand does not make a replay that opens.
- If the files can no longer go into the zip (for example when the disk is full), the rest stay in the folder, and saving puts them together with the unfinished zip.

### Faster snapshots

The Flashback format records the current state (a snapshot) when a recording starts and every 5 minutes. For each of its chunks, Server Replay goes through every entity of the world to find the ones in that chunk, so the more chunks and entities there are, the longer the server stops.

With `optimize_snapshot` at `true`, the entities are sorted into their chunks once, before the chunks are sent. The packets that are recorded, and their order, stay the same.

In a test that recorded 31×31 chunks with 20,000 entities, the server stopped for:

- the snapshot every 5 minutes: about 0.07 seconds instead of about 1.35 seconds
- the start of the recording: about 0.15 seconds instead of about 2.7 seconds

### Pausing when nothing changes

With `pause_when_idle_seconds` at 1 or more, a recording is paused when the blocks and entities in its area have not changed for that many seconds. As soon as something changes, it resumes on the next tick. The time it was paused for is skipped in the replay.

Even with `chunk_recording_strategy` at `chunk_contains_non_spectator_player` in Server Replay, a recording keeps going while a player in survival mode is left idle in its area. With this setting, such stretches where nothing happens are left out of the recording. While a machine in the area is running, the recording keeps going even if the player is idle.

- Block changes (including block actions, the cracks on a block that is being mined, and explosions) and changes of entities count. The time of day, the weather, the tab list, chat, sounds, particles and chunks that load do not. Nor do level events that only entities send, such as the sound of a bat taking off. See [docs/packets.md](docs/packets.md#pause_when_idle_seconds) for the full list.
- What `ignore_entities` and `ignore_block_action` leave out of the recording does not count.
- With `idle_ignore_entities` at `true` (the default), only the entity types in `idle_entity_whitelist` count. By default that is only players, so mobs that just wander around do not keep the recording going. If you want to record a machine that runs on entities alone, such as a mob farm, while you are idle, add its mobs (for example `minecraft:zombified_piglin`) to the list.
- Players in spectator mode never count, whatever the list says.
- When a recording resumes, the Flashback format takes a new snapshot. Each resume therefore stops the server for as long as [the snapshot every 5 minutes](#faster-snapshots) does, and adds to the terrain caches. Avoid values of a few seconds; 30 seconds or more is recommended.
- The change that resumes a recording is not recorded itself. The state after it is in the snapshot that is taken when the recording resumes, so a short movement, such as a piston moving, or a sound is missing.
- The time of day and the weather jump ahead, by the time the recording was paused for, where it resumes.
- While Server Replay keeps a recording paused for a reason of its own (no players in the area, no chunks loaded, an empty server), that reason wins. The other way around, while this setting keeps a recording paused, it does not resume until something changes, even when the conditions of Server Replay are met.
- The pauses and resumes of this setting are logged on the server. They do not send the notifications of `pause_notify_players` in Server Replay.

## Recovering recordings after a crash

When the server goes down during a recording, Server Replay recovers it on the next start, but only in the ReplayMod format. With this mod, recordings in the Flashback format (both chunk and player recordings) are recovered too.

- The recording folder is packed into `<recording name>.zip`, as a normal save would, and the folder is deleted. If a zip with that name already exists, the recording is saved under another name, such as `<recording name> (recovered 1).zip`.
- For a recording made with `zip_while_recording`, the files that were written to the end of the unfinished zip and the files left in the folder are put together into one zip.
- The recording is kept up to the last replay chunk that was complete. The Flashback format writes to disk in 5 minute chunks, so up to the last 5 minutes before the crash are lost.
- A recording without a complete chunk (one that crashed within 5 minutes of starting), or one that could not be recovered, is left as a folder.
- Nothing is recovered when `"recover_unsaved_replays"` is `false` in Server Replay.

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

The mod is built to `build/libs/optimize-server-replay-<version>.jar`.

## Default config

The config that is created when there is no config file (`config/optimize-server-replay/config.json`).

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
  "block_update_interval_ticks": 0,
  "optimize_saving": true,
  "zip_while_recording": false,
  "optimize_snapshot": true,
  "zip_compression_level": 1,
  "pause_when_idle_seconds": 0,
  "idle_ignore_entities": true,
  "idle_entity_whitelist": [
    "minecraft:player"
  ]
}
```

## License

[MIT](LICENSE)
