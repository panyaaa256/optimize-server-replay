Changes what keeps a recording going with `pause_when_idle_seconds`: only block actions and the arm swings of players count now. `idle_ignore_entities` and `idle_entity_whitelist` are no longer used, so you can delete them from your `config.json`.

### Added
- Tell everyone when `pause_when_idle_seconds` pauses or resumes a recording, with the same messages as Server Replay, while `pause_notify_players` is on in Server Replay

### Changed
- Count only block actions (pistons, note blocks, chests, bells and so on, apart from spawners and end gateways) and the arm swings of players as activity for `pause_when_idle_seconds`. Block changes, the movement of entities and level events no longer count, so sheep eating grass, crops growing and players who only walk around no longer keep a recording going. Neither do players who stand still, whose position the server sends every 3 seconds
- Count block actions and arm swings even when `ignore_block_action`, `block_update_interval_ticks` or `ignore_entities` leave them out of the recording

### Removed
- Remove `idle_ignore_entities` and `idle_entity_whitelist`
