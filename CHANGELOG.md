Fixes memory leaks in Server Replay and Arcade that grew with every snapshot and kept every stopped recording in memory, keeps replay chunks out of memory while they are recorded, and makes entity tracking lighter. The current `config.json` has none of the new keys, so until they are added, the mod logs a warning for each and uses its default (on).

### Added
- Add `fix_memory_leaks` (default `true`). Server Replay and Arcade kept in memory: a player and its packet listeners for every snapshot of every recording, and every stopped recording, through Fabric API's networking sessions; the last chunk recording that started, through the advancements of its invisible player; a recording whose save failed, with its buffer and the thread of `zip_while_recording`; and a recording that saw a hidden boss bar, such as the one of the dragon fight. It also applies to player recordings and the ReplayMod format
- Add `stream_replay_chunks` (default `true`). The replay chunk that is being recorded is written to its file every 4 MiB, instead of being kept in memory for up to 5 minutes and copied once more when it ends. The files stay the same
- Add `optimize_entity_tracking` (default `true`). When a chunk is loaded into a recording, only the entities near it are looked at, instead of every entity of the world. Which recordings record an entity is no longer checked again for every entity whenever a player moves to another section, unless the entity moved to another chunk or a chunk recording started or stopped. The entities of a chunk may be recorded in another order

### Changed
- `optimize_saving` keeps the terrain cache file open while chunks are added to it, instead of opening and closing it for every chunk

### Fixed
- Fix `ignore_entities` keeping track of the entities that were removed while a recording was paused, until the recording stopped
