Fixes memory leaks in Server Replay and Arcade that grew with every snapshot and kept every stopped recording in memory, keeps replay chunks out of memory while they are recorded, and makes entity tracking lighter. `optimize_snapshot` is now part of `optimize_entity_tracking`, so it can be removed from `config.json`. The current `config.json` has none of the new keys, so until they are added, the mod logs a warning for each and uses its default (on).

### Added
- Add `stream_replay_chunks` (default `true`). The replay chunk that is being recorded is written to its file every 4 MiB, instead of being kept in memory for up to 5 minutes and copied once more when it ends. The files stay the same
- Add `optimize_entity_tracking` (default `true`), which takes over `optimize_snapshot`. Besides sorting the entities into their chunks once for snapshots, only the entities near a chunk are looked at when it is loaded into a recording, instead of every entity of the world, and which recordings record an entity is no longer checked again for every entity whenever a player moves to another section, unless the entity moved to another chunk or a chunk recording started or stopped. The entities of a chunk that is loaded may be recorded in another order

### Changed
- `optimize_saving` keeps the terrain cache file open while chunks are added to it, instead of opening and closing it for every chunk

### Removed
- Remove `optimize_snapshot`, which is now part of `optimize_entity_tracking`

### Fixed
- Fix every recording keeping in memory a player and its packet listeners for its start and for every snapshot, and every stopped recording staying in memory, through Fabric API's networking sessions. This also applies to player recordings and the ReplayMod format
- Fix the last chunk recording that started staying in memory after it stopped, through the advancements of its invisible player
- Fix a recording whose save failed (for example when the disk is full) staying in memory, with the data of its last replay chunk and the thread of `zip_while_recording`. Its folder is left as it is
- Fix a chunk recording that saw a hidden boss bar, such as the one of the dragon fight after the dragon is dead, staying in memory after it stopped
- Fix `ignore_entities` keeping track of the entities that were removed while a recording was paused, until the recording stopped
