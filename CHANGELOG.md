Shortens the time the server stops when a Flashback chunk recording starts and for its snapshots, when there are many entities. Your `config.json` does not have the new key, so the mod logs a warning and uses its default until you add it.

- Add `optimize_snapshot`, on by default. For each chunk it sent at the start of a recording and in a snapshot, Server Replay went through every entity of the world; the entities are now sorted into their chunks once. The recorded packets stay the same
