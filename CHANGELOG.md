Changes what keeps a recording going with `pause_when_idle_seconds`: only block actions and the arm swings of players count now. `idle_ignore_entities` and `idle_entity_whitelist` are no longer used, so you can delete them from your `config.json`.

### Added
- Tell everyone when `pause_when_idle_seconds` pauses or resumes a recording, with the same messages as Server Replay, while `pause_notify_players` is on in Server Replay ([#9](https://github.com/panyaaa256/optimize-server-replay/pull/9))

### Changed
- Count only block actions (pistons, note blocks, chests, bells and so on, apart from spawners and end gateways) and the arm swings of players as activity for `pause_when_idle_seconds`. Block changes, the movement of entities and level events no longer count, so sheep eating grass, crops growing and players who only walk around no longer keep a recording going. Neither do players who stand still, whose position the server sends every 3 seconds ([#9](https://github.com/panyaaa256/optimize-server-replay/pull/9))
- Count block actions and arm swings even when `ignore_block_action`, `block_update_interval_ticks` or `ignore_entities` leave them out of the recording ([#9](https://github.com/panyaaa256/optimize-server-replay/pull/9))

### Removed
- Remove `idle_ignore_entities` and `idle_entity_whitelist` ([#9](https://github.com/panyaaa256/optimize-server-replay/pull/9))
