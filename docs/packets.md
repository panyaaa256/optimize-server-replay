# Packets that each setting affects

[日本語](packets.ja.md)

This page lists what each setting of Optimize Server Replay does to the packets of a recording. For the settings themselves, see [Configuration](../README.md#configuration) in the README.

- Packets are named by their ID in Minecraft 1.21.11, without the `minecraft:` namespace. All of them are clientbound packets of the play state.
- The [Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_protocol/Packets) lists the same IDs as "Official name". The wiki describes the latest version of the game, so its numeric IDs can differ from those of 1.21.11.
- Only chunk recordings in the Flashback format are affected.
- Server Replay splits a `bundle` into its packets before it records them, so every packet inside a bundle is checked on its own.

## `ignore_entities`

When `true`, the packets below are recorded only for the entities that are kept.

- An entity is kept when its type is in `entity_whitelist`. This is decided when its `add_entity` arrives.
- The dummy player of the chunk recording is always kept.

| Packet | Recorded when |
|---|---|
| `add_entity` | the type of the entity is in `entity_whitelist` |
| `remove_entities` | any of the entities is kept |
| `move_entity_pos` | the entity is kept |
| `move_entity_rot` | the entity is kept |
| `move_entity_pos_rot` | the entity is kept |
| `entity_position_sync` | the entity is kept |
| `teleport_entity` | the entity is kept |
| `set_entity_motion` | the entity is kept |
| `set_entity_data` | the entity is kept |
| `rotate_head` | the entity is kept |
| `set_equipment` | the entity is kept |
| `update_attributes` | the entity is kept |
| `entity_event` | the entity is kept |
| `animate` | the entity is kept |
| `hurt_animation` | the entity is kept |
| `damage_event` | the damaged entity is kept |
| `update_mob_effect` | the entity is kept |
| `remove_mob_effect` | the entity is kept |
| `projectile_power` | the entity is kept |
| `set_passengers` | the vehicle or any of the passengers is kept |
| `set_entity_link` | either of the two entities is kept |
| `take_item_entity` | the item or the entity that picks it up is kept |

A packet with several entities is recorded as a whole, so it can still name an entity that was left out. The vanilla client ignores the IDs it does not know.

### Entity packets that are not filtered

| Packet | Why |
|---|---|
| `move_minecart_along_track` | Server Replay never records it in the Flashback format |
| `set_camera` | Server Replay never records it |
| `sound_entity` | It is a sound. `ignore_sound_packets` of Server Replay leaves it out |
| `block_destruction` | The cracks on a block that is being mined. Always recorded |
| `player_info_update` | The tab list. Always recorded |
| `player_info_remove` | The tab list. Always recorded |

## `ignore_block_action`

The only packet is `block_event`. Which ones are left out also depends on `block_update_interval_ticks`.

| `ignore_block_action` | `block_update_interval_ticks` | `block_event` that is left out |
|---|---|---|
| `true` | any | all |
| `false` | 1 or more | those of `minecraft:piston` and `minecraft:sticky_piston` |
| `false` | `0` | none |

## `block_update_interval_ticks`

When 1 or more, the packets below are handled like this.

| Packet | What happens |
|---|---|
| `block_update` | The packet is not recorded. Only its position is stored |
| `section_blocks_update` | The packet is not recorded. Only its positions are stored |
| `block_entity_data` | The packet is not recorded. Only its position is stored |
| `level_chunk_with_light` | Recorded as it is. The positions stored for that chunk are discarded, because the chunk data is newer |
| `light_update` | Recorded as it is |

The stored positions are written to the recording at these moments.

- Every `block_update_interval_ticks` ticks
- Before a new replay chunk starts, which is every 6000 ticks and when the recording resumes
- Just before the recording pauses
- When the recording stops

Each position is written with the state it has in the world at that moment.

| What was stored | Packet that is recorded |
|---|---|
| One position in a chunk section | `block_update` |
| Two or more positions in a chunk section | `section_blocks_update` |
| A block entity | The update packet of the block entity, normally `block_entity_data` |

These positions are skipped.

- Positions in a chunk that is not loaded
- Positions whose block is `minecraft:moving_piston` at that moment
- Block entities that no longer exist

## `pause_when_idle_seconds`

When 1 or more, only the packets below count as a change. When none of them arrives for the given number of seconds, the recording is paused, and when one arrives while it is paused, the recording resumes. How these packets are recorded does not change.

| Packet | Counts when |
|---|---|
| `block_event` | its block is not `minecraft:spawner` or `minecraft:end_gateway` |
| `animate` | it is of a player who is not in spectator mode and is not the dummy player of the chunk recording |

- Packets that are left out of the recording count too: the `block_event` that `ignore_block_action` or `block_update_interval_ticks` leave out, and the `animate` of players that `ignore_entities` leaves out.
- In vanilla, `block_event` is sent by note blocks, pistons, sticky pistons, chests, trapped chests, ender chests, shulker boxes, bells, decorated pots, spawners and end gateways. Spawners and end gateways do not count, because they send it again and again without anyone doing anything.
- The `animate` of a player is sent when they swing their arm (placing or mining a block, hitting something, using an item) and when they wake up from a bed. The `animate` of a critical hit is of the entity that is hit, so it counts only when that is a player.

No other packet counts, such as block changes (`block_update`, `section_blocks_update`, `block_entity_data`, `block_destruction`), `level_event`, `explode`, the movement and state of entities, the time of day (`set_time`), the weather (`game_event`), the tab list, chat, sounds, particles and chunks that load.
