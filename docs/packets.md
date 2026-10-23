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

When 1 or more, the packets below count as a change. When none of them arrives for the given number of seconds, the recording is paused, and when one arrives while it is paused, the recording resumes. How these packets are recorded does not change.

| Packet | Counts when |
|---|---|
| `block_update` | always |
| `section_blocks_update` | always |
| `block_entity_data` | always |
| `block_event` | it is not left out by `ignore_block_action` |
| `block_destruction` | always |
| `level_event` | its type is not [one that only entities send](#level_event-that-only-entities-send) |
| `explode` | always |
| the entity packets in [the `ignore_entities` table](#ignore_entities) | any of its entities meets all of the conditions below |

An entity counts when:

- it is not left out by `ignore_entities`
- `idle_ignore_entities` is `false`, or its type is in `idle_entity_whitelist`
- it is not a player in spectator mode
- it is not the dummy player of the chunk recording
- it is still in the world (apart from `add_entity`, an entity that has left the world does not count)

No other packet counts, such as the time of day (`set_time`), the weather (`game_event`), the tab list, chat, the scoreboard, boss bars, sounds, particles, chunks that load (`level_chunk_with_light`) and light updates (`light_update`).

Block changes that `block_update_interval_ticks` records together count when they happen, not when they are written out.

### `level_event` that only entities send

A `level_event` that only entities (players included) send in vanilla does not count. Otherwise a bat taking off, or another entity that `idle_entity_whitelist` leaves out, would resume the recording. These come with the entity's own packets or with a block change, which count instead.

| Sent by | `level_event` type (ID) |
|---|---|
| ghasts | `SOUND_GHAST_WARNING` (1015), `SOUND_GHAST_FIREBALL` (1016) |
| the ender dragon | `SOUND_DRAGON_FIREBALL` (1017), `SOUND_DRAGON_DEATH` (1028), `PARTICLES_DRAGON_BLOCK_BREAK` (2008) |
| zombies and others breaking doors | `SOUND_ZOMBIE_WOODEN_DOOR` (1019), `SOUND_ZOMBIE_DOOR_CRASH` (1021) |
| the wither | `SOUND_WITHER_BLOCK_BREAK` (1022), `SOUND_WITHER_BOSS_SPAWN` (1023), `SOUND_WITHER_BOSS_SHOOT` (1024) |
| bats | `SOUND_BAT_LIFTOFF` (1025) |
| mobs converting | `SOUND_ZOMBIE_INFECTED` (1026), `SOUND_ZOMBIE_CONVERTED` (1027), `SOUND_ZOMBIE_TO_DROWNED` (1040), `SOUND_HUSK_TO_ZOMBIE` (1041), `SOUND_SKELETON_TO_STRAY` (1048) |
| phantoms | `SOUND_PHANTOM_BITE` (1039) |
| bees | `PARTICLES_BEE_GROWTH` (2011) |
| mace attacks | `PARTICLES_SMASH_ATTACK` (2013) |
| players using an axe, copper golems | `PARTICLES_WAX_OFF` (3004), `PARTICLES_SCRAPE` (3005) |
| entities dying with the Weaving effect | `ANIMATION_SPAWN_COBWEB` (3018) |
| thrown potions and bottles o' enchanting | `PARTICLES_SPELL_POTION_SPLASH` (2002), `PARTICLES_INSTANT_POTION_SPLASH` (2007) |
| eyes of ender | `PARTICLES_EYE_OF_ENDER_DEATH` (2003) |
| dragon fireballs | `PARTICLES_DRAGON_FIREBALL_SPLASH` (2006) |
| ominous item spawners | `PARTICLES_TRIAL_SPAWNER_SPAWN_ITEM` (3021) |
| players using an anvil, a grindstone or a smithing table | `SOUND_ANVIL_USED` (1030), `SOUND_GRINDSTONE_USED` (1042), `SOUND_SMITHING_TABLE_USED` (1044) |
| players putting an eye of ender in an end portal frame | `END_PORTAL_FRAME_FILL` (1503), `SOUND_END_PORTAL_SPAWN` (1038) |
| players going through a portal | `SOUND_PORTAL_TRAVEL` (1032) |

A type that blocks or dispensers send too still counts, even when an entity sent it, such as `PARTICLES_DESTROY_BLOCK` (2001) when a block breaks, `PARTICLES_AND_SOUND_PLANT_GROWTH` (1505) for bone meal, `SOUND_BLAZE_FIREBALL` (1018) for fire charges and `PARTICLES_ELECTRIC_SPARK` (3002) for lightning.
